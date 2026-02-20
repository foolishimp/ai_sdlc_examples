package com.cdme.compiler.grain
// Implements: REQ-F-TRV-002, REQ-BR-GRN-001, REQ-F-SYN-002, REQ-F-SYN-005

import com.cdme.model._
import com.cdme.model.grain.{Grain, Atomic}
import com.cdme.model.morphism.{StructuralMorphism, ComputationalMorphism, AlgebraicMorphism}
import com.cdme.model.morphism.{Expression, ColumnRef, Literal, FunctionCall, Conditional, Priority}
import com.cdme.compiler.{grainRank, GrainError, IncompatibleGrains, MultiGrainConflict}
import com.cdme.compiler.path.ValidatedPath

/**
 * An ordered hierarchy of grains from finest to coarsest.
 *
 * Used by the grain checker to determine whether one grain is finer than,
 * equal to, or coarser than another. The ordering is defined by position
 * in the `levels` list: index 0 is the finest.
 *
 * @param levels ordered list from finest to coarsest grain
 */
case class GrainHierarchy(levels: List[Grain]) {

  /** Returns the rank (index) of a grain in the hierarchy. Lower = finer. */
  def rankOf(grain: Grain): Option[Int] = {
    val idx = levels.indexWhere(g => grainRank(g) == grainRank(grain))
    if (idx >= 0) Some(idx) else None
  }

  /** Check whether `finer` is strictly finer than `coarser`. */
  def isFinerThan(finer: Grain, coarser: Grain): Boolean =
    (rankOf(finer), rankOf(coarser)) match {
      case (Some(f), Some(c)) => f < c
      case _ => grainRank(finer) < grainRank(coarser)
    }

  /** Check whether two grains are compatible (same rank). */
  def isCompatible(a: Grain, b: Grain): Boolean =
    grainRank(a) == grainRank(b)
}

/**
 * A path that has passed grain safety checks.
 *
 * @param path  the underlying validated path
 * @param grain the resolved grain of the path (the coarsest grain encountered,
 *              which must have been reached via an algebraic morphism)
 */
case class GrainSafePath(
  path: ValidatedPath,
  grain: Grain
)

/**
 * Enforces the grain safety invariant on traversal paths.
 *
 * Rules:
 * - Moving from fine grain to coarse grain requires an algebraic (aggregation)
 *   morphism. Traversing a structural or computational morphism across a grain
 *   boundary is rejected.
 * - There is NO override mechanism. This is a topological invariant.
 */
object GrainChecker {

  /**
   * Check that a validated path respects grain safety.
   *
   * @param path  the validated path (morphisms resolved)
   * @param graph the LDM graph (used to look up entity grain metadata)
   * @return a [[GrainSafePath]] on success, or a [[GrainError]] on violation
   */
  def check(
    path: ValidatedPath,
    graph: LdmGraph
  ): Either[GrainError, GrainSafePath] = {
    val entityGrains: Map[EntityId, Grain] =
      graph.nodes.map { case (eid, entity) => eid -> entity.grain }

    var currentGrain: Grain = entityGrains.getOrElse(path.sourceEntity, Atomic)

    for (morphism <- path.morphisms) {
      val targetEntityId = morphismCodomain(morphism)
      val targetGrain = entityGrains.getOrElse(targetEntityId, Atomic)

      if (grainRank(targetGrain) != grainRank(currentGrain)) {
        // Grain changes: only algebraic morphisms may cross grain boundaries
        morphism match {
          case _: AlgebraicMorphism =>
            // Algebraic morphism provides aggregation — grain transition is valid
            currentGrain = targetGrain
          case _ =>
            return Left(IncompatibleGrains(
              finerGrain = if (grainRank(currentGrain) < grainRank(targetGrain)) currentGrain else targetGrain,
              coarserGrain = if (grainRank(currentGrain) < grainRank(targetGrain)) targetGrain else currentGrain,
              morphismId = morphismId(morphism)
            ))
        }
      }
    }

    Right(GrainSafePath(path, currentGrain))
  }

  /**
   * Check that a synthesis expression does not mix incompatible grains
   * without proper aggregation.
   *
   * @param expression   the synthesis expression to validate
   * @param contextGrain the grain of the target context
   * @param graph        the LDM graph
   * @return Unit on success, or a [[GrainError]] on violation
   */
  def checkMultiGrainFormulation(
    expression: Expression,
    contextGrain: Grain,
    graph: LdmGraph
  ): Either[GrainError, Unit] = {
    // Extract all grain references from the expression and verify they are
    // compatible with the context grain.
    val referencedGrains = extractGrainsFromExpression(expression, graph)
    val incompatible = referencedGrains.filter(g => grainRank(g) != grainRank(contextGrain))

    incompatible.headOption match {
      case Some(badGrain: Grain) =>
        Left(MultiGrainConflict(
          expressionDesc = expressionDescription(expression),
          contextGrain = contextGrain,
          conflictingGrain = badGrain
        ))
      case None =>
        Right(())
    }
  }

  // ---------------------------------------------------------------------------
  // Private helpers
  // ---------------------------------------------------------------------------

  private def extractGrainsFromExpression(expr: Expression, graph: LdmGraph): List[Grain] = {
    // Walk the expression tree and collect grains from referenced entities.
    // ColumnRef has a single `name` field (not entity-qualified at this level).
    // Complex entity-qualified grain extraction deferred to TDD.
    expr match {
      case ColumnRef(_) =>
        // ColumnRef(name) — no entity ID to look up grain from at this level
        Nil
      case FunctionCall(_, args) =>
        args.flatMap(extractGrainsFromExpression(_, graph))
      case Conditional(cond, thenExpr, elseExpr) =>
        extractGrainsFromExpression(cond, graph) ++
          extractGrainsFromExpression(thenExpr, graph) ++
          extractGrainsFromExpression(elseExpr, graph)
      case Priority(exprs) =>
        exprs.flatMap(extractGrainsFromExpression(_, graph))
      case _: Literal =>
        Nil
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.ToString"))
  private def expressionDescription(expr: Expression): String = expr match {
    case ColumnRef(name) => name
    case FunctionCall(name, _) => s"$name(...)"
    case Conditional(_, _, _) => "CASE(...)"
    case Priority(exprs) => s"PRIORITY(${exprs.length} branches)"
    case Literal(v, _) => v.toString
  }

  private def morphismCodomain(m: Morphism): EntityId = m match {
    case s: StructuralMorphism     => s.codomain
    case c: ComputationalMorphism  => c.codomain
    case a: AlgebraicMorphism      => a.codomain
  }

  private def morphismId(m: Morphism): String = m match {
    case s: StructuralMorphism     => s.id.value
    case c: ComputationalMorphism  => c.id.value
    case a: AlgebraicMorphism      => a.id.value
  }
}

/**
 * Extract the numeric rank from a [[Grain]] instance.
 * Used across the compiler for grain comparisons.
 */
private[compiler] object GrainRankExtractor {
  def apply(grain: Grain): Int = grainRank(grain)
}
