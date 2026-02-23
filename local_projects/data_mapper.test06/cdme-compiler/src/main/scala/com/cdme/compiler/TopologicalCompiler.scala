package com.cdme.compiler

import com.cdme.model._

// Implements: REQ-LDM-03 (Composition), REQ-AI-01 (Hallucination Prevention), REQ-TRV-02 (Grain)
class TopologicalCompiler {

  /**
   * Compile a mapping definition against a category.
   * Validates all paths, types, grains, access control, and lookup versions.
   */
  def compile(
    mapping: MappingDefinition,
    category: Category,
    principal: Principal
  ): Either[List[CompilationError], CompiledPlan] = {
    val errors = scala.collection.mutable.ListBuffer.empty[CompilationError]

    // Validate target entity exists
    if (!category.hasEntity(mapping.targetEntity)) {
      errors += CompilationError.HallucinatedEntity(mapping.targetEntity)
    }

    // Validate each field mapping
    val steps = mapping.fieldMappings.flatMap { fm =>
      validateFieldMapping(fm, category, principal) match {
        case Left(errs) =>
          errors ++= errs
          Nil
        case Right(s) => s
      }
    }

    // Validate lookup versions (REQ-INT-06)
    mapping.lookups.foreach { case (name, binding) =>
      if (binding.version.isEmpty) {
        errors += CompilationError.MissingLookupVersion(name)
      }
    }

    // Check cost budget (REQ-TRV-06)
    mapping.costBudget.foreach { budget =>
      val estimated = estimateOutputRows(steps)
      if (estimated > budget.maxOutputRows) {
        errors += CompilationError.BudgetExceeded(estimated, budget.maxOutputRows)
      }
    }

    if (errors.nonEmpty) {
      Left(errors.toList)
    } else {
      Right(CompiledPlan(
        steps = steps,
        estimatedCost = CostEstimate(
          estimatedOutputRows = estimateOutputRows(steps),
          maxJoinDepth = steps.count(_.isInstanceOf[TraverseStep]),
          maxIntermediateSize = estimateOutputRows(steps) * 2
        ),
        grainMap = Map.empty,
        typeMap = mapping.fieldMappings.map(fm => fm.targetField -> fm.targetType).toMap,
        requiredLookups = mapping.lookups.map { case (k, v) => k -> LookupVersion(v.lookupName, v.version) }
      ))
    }
  }

  /**
   * Validate a single path expression against the topology.
   * REQ-LDM-03: path a.b.c valid iff all morphisms exist and compose correctly.
   */
  def validatePath(
    path: String,
    category: Category,
    principal: Principal
  ): Either[List[CompilationError], ComposedMorphism] = {
    val segments = path.split("\\.").toList
    if (segments.isEmpty) {
      return Left(List(CompilationError.InvalidPath(path, "Empty path")))
    }

    val errors = scala.collection.mutable.ListBuffer.empty[CompilationError]
    val morphisms = scala.collection.mutable.ListBuffer.empty[Morphism]

    // First segment must be an entity
    if (!category.hasEntity(segments.head)) {
      return Left(List(CompilationError.HallucinatedEntity(segments.head)))
    }

    // Each subsequent segment is a morphism
    var currentEntity = segments.head
    segments.tail.foreach { seg =>
      val found = category.morphismsBetween(currentEntity, seg)
        .headOption
        .orElse(category.morphisms.values.find(m => m.source == currentEntity && m.name == seg))

      found match {
        case Some(m) =>
          // Check RBAC (REQ-LDM-05)
          if (m.metadata.rbacRoles.nonEmpty && m.metadata.rbacRoles.intersect(principal.roles).isEmpty) {
            errors += CompilationError.AccessDenied(m.name, principal.name)
          }
          morphisms += m
          currentEntity = m.target
        case None =>
          errors += CompilationError.HallucinatedMorphism(seg)
      }
    }

    // Check grain safety across the path (REQ-TRV-02)
    if (morphisms.size >= 2) {
      val grains = morphisms.toList.flatMap { m =>
        for {
          src <- category.objects.get(m.source)
          tgt <- category.objects.get(m.target)
        } yield (src.grain, tgt.grain)
      }
      grains.foreach { case (srcGrain, tgtGrain) =>
        GrainChecker.check(srcGrain, tgtGrain, hasAggregation = false) match {
          case Left(err) => errors += err
          case Right(_) => ()
        }
      }
    }

    // Check type unification along the path (REQ-TYP-06)
    morphisms.toList.sliding(2).foreach {
      case List(f, g) =>
        TypeUnifier.unify(f.codomainType, g.domainType) match {
          case Left(err) => errors += err
          case Right(_) => ()
        }
      case _ => ()
    }

    if (errors.nonEmpty) {
      Left(errors.toList)
    } else if (morphisms.isEmpty) {
      Right(ComposedMorphism(
        steps = Nil,
        resultType = category.objects(segments.head).attributes.values.headOption.getOrElse(SemanticType.StringType),
        resultGrain = category.objects(segments.head).grain,
        resultCardinality = Cardinality.OneToOne
      ))
    } else {
      val resultCardinality = morphisms.toList.map(_.cardinality).reduce { (a, b) =>
        (a, b) match {
          case (Cardinality.OneToOne, x) => x
          case (x, Cardinality.OneToOne) => x
          case (Cardinality.OneToN, _) => Cardinality.OneToN
          case (_, Cardinality.OneToN) => Cardinality.OneToN
          case _ => Cardinality.NToOne
        }
      }
      Right(ComposedMorphism(
        steps = morphisms.toList,
        resultType = morphisms.last.codomainType,
        resultGrain = category.objects.get(morphisms.last.target).map(_.grain).getOrElse(Grain.Atomic),
        resultCardinality = resultCardinality
      ))
    }
  }

  private def validateFieldMapping(
    fm: FieldMapping,
    category: Category,
    principal: Principal
  ): Either[List[CompilationError], List[ExecutionStep]] = {
    fm.expression match {
      case MappingExpression.Path(p) =>
        validatePath(p, category, principal).map { composed =>
          composed.steps.map(m => TraverseStep(m))
        }
      case MappingExpression.Aggregate(p, monoid) =>
        validatePath(p, category, principal).map { composed =>
          composed.steps.map(m => TraverseStep(m)) :+ AggregateStep(composed.steps.lastOption.getOrElse(
            Morphism("identity", "", "", Cardinality.OneToOne, MorphismType.Computational,
              SemanticType.StringType, SemanticType.StringType)
          ), monoid)
        }
      case MappingExpression.Literal(_, _) =>
        Right(Nil)
      case MappingExpression.Function(fn, args) =>
        Right(List(SynthesisStep(fn, fm.targetType)))
      case MappingExpression.Conditional(_, ifTrue, ifFalse) =>
        Right(List(SynthesisStep("conditional", fm.targetType)))
    }
  }

  private def estimateOutputRows(steps: List[ExecutionStep]): Long = {
    steps.foldLeft(1000L) { (count, step) =>
      step match {
        case _: TraverseStep => count
        case _: AggregateStep => count / 10
        case _: FilterStep => count / 2
        case _ => count
      }
    }
  }
}
