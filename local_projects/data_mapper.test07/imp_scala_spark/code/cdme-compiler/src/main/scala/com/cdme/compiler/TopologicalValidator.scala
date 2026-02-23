// Implements: REQ-AI-01, REQ-LDM-03
// Topological validator: the main entry point for the compile phase.
package com.cdme.compiler

import com.cdme.model._
import com.cdme.model.error._
import com.cdme.model.adjoint.AdjointClassification
import com.cdme.compiler.types.TypeUnifier

/**
 * The topological compiler: validates mappings against the LDM before execution.
 * Rejects hallucinated morphisms, type mismatches, grain violations, and budget overruns.
 *
 * Implements: REQ-AI-01, REQ-LDM-03
 */
object TopologicalValidator {

  /**
   * Validate a compilation input and produce a ValidatedPlan.
   * This is the main entry point for the compile phase.
   */
  def validate(input: CompilationInput): Either[List[CdmeError], ValidatedPlan] = {
    val errors = scala.collection.mutable.ListBuffer[CdmeError]()

    // Step 1: Verify all morphism paths exist and compose validly
    val pathResult = PathCompiler.verifyMorphismChain(input.mapping.morphismPath, input.ldm)
    pathResult.left.foreach(e => errors += e)

    // Step 2: Check grain safety
    val grainResult = GrainChecker.checkGrainSafety(input.mapping.morphismPath, input.ldm)
    grainResult.left.foreach(errs => errors ++= errs)

    // Step 3: Verify type compatibility along morphism chain
    val typeErrors = verifyTypeCompatibility(input.mapping.morphismPath, input.ldm)
    errors ++= typeErrors

    // Step 4: Check access control
    val accessErrors = verifyAccessControl(input.mapping.morphismPath, input.ldm, input.principal)
    errors ++= accessErrors

    if (errors.nonEmpty) {
      return Left(errors.toList)
    }

    // Step 5: Build execution DAG
    val dag = buildExecutionDag(input)

    // Step 6: Estimate cost
    val costResult = CostEstimator.estimateCost(dag, None, input.jobConfig)
    val costEstimate = costResult match {
      case Right(est) => est
      case Left(err) =>
        errors += err
        return Left(errors.toList)
    }

    // Step 7: Classify lineage
    val lineageClassification = classifyLineage(input.mapping.morphismPath, input.ldm)

    // Step 8: Compose adjoint classifications
    val adjointComposition = composeAdjoints(input.mapping.morphismPath, input.ldm)

    Right(ValidatedPlan(
      executionDag = dag,
      costEstimate = costEstimate,
      lineageClassification = lineageClassification,
      adjointComposition = adjointComposition,
      warnings = Nil
    ))
  }

  private def verifyTypeCompatibility(
      morphismIds: List[MorphismId],
      category: Category
  ): List[CdmeError] = {
    val morphisms = morphismIds.flatMap(category.morphism)
    morphisms.sliding(2).flatMap {
      case List(f, g) =>
        // Check that codomain entity attributes are compatible with domain entity attributes
        val codomainEntity = category.entity(f.codomain)
        val domainEntity   = category.entity(g.domain)
        (codomainEntity, domainEntity) match {
          case (Some(ce), Some(de)) =>
            // Check overlapping attributes for type compatibility
            val commonAttrs = ce.attributes.keySet.intersect(de.attributes.keySet)
            commonAttrs.flatMap { attr =>
              val codomainType = ce.attributes(attr)
              val domainType   = de.attributes(attr)
              TypeUnifier.unify(codomainType, domainType, com.cdme.model.types.SubtypeRegistry.empty) match {
                case Left(err) => Some(err)
                case Right(_)  => None
              }
            }
          case _ => Nil
        }
      case _ => Nil
    }.toList
  }

  private def verifyAccessControl(
      morphismIds: List[MorphismId],
      category: Category,
      principal: Principal
  ): List[CdmeError] = {
    morphismIds.flatMap { mid =>
      category.morphism(mid).flatMap { morphism =>
        if (morphism.accessControl.isAccessible(principal.roles)) None
        else Some(AccessDeniedError(mid, principal.name, morphismIds))
      }
    }
  }

  private def buildExecutionDag(input: CompilationInput): ExecutionDag = {
    val sourceNode = DagNode("source_0", input.mapping.sourceEntity, DagNodeType.Source)
    val sinkNode   = DagNode("sink_0", input.mapping.targetEntity, DagNodeType.Sink)

    val transformNodes = input.mapping.morphismPath.zipWithIndex.map { case (mid, idx) =>
      val entityId = input.ldm.morphism(mid).map(_.codomain).getOrElse(s"unknown_$idx")
      DagNode(s"transform_$idx", entityId, DagNodeType.Transform)
    }

    val allNodes = (sourceNode :: transformNodes :+ sinkNode).map(n => n.id -> n).toMap

    val edges = (sourceNode.id :: transformNodes.map(_.id)).zip(transformNodes.map(_.id) :+ sinkNode.id)
      .zip(input.mapping.morphismPath ++ List("identity"))
      .zipWithIndex.map { case (((src, tgt), mid), idx) =>
        val edgeId = s"edge_$idx"
        edgeId -> DagEdge(edgeId, src, tgt, mid)
      }.toMap

    ExecutionDag(allNodes, edges, Set(sourceNode.id), Set(sinkNode.id))
  }

  private def classifyLineage(
      morphismIds: List[MorphismId],
      category: Category
  ): Map[MorphismId, LineageClassification] = {
    morphismIds.map { mid =>
      val classification = category.morphism(mid).flatMap(_.adjoint) match {
        case Some(adj) if adj.classification == AdjointClassification.Isomorphism =>
          LineageClassification.Lossless
        case Some(adj) if adj.classification == AdjointClassification.Embedding =>
          LineageClassification.Lossless
        case _ =>
          LineageClassification.Lossy
      }
      mid -> classification
    }.toMap
  }

  private def composeAdjoints(
      morphismIds: List[MorphismId],
      category: Category
  ): Map[PathId, ComposedAdjoint] = {
    if (morphismIds.isEmpty) return Map.empty

    val classifications = morphismIds.flatMap { mid =>
      category.morphism(mid).flatMap(_.adjoint).map(_.classification)
    }

    val composed = classifications.foldLeft(AdjointClassification.Isomorphism: AdjointClassification) {
      AdjointClassification.compose
    }

    val pathId = morphismIds.mkString("->")
    Map(pathId -> ComposedAdjoint(pathId, morphismIds, composed))
  }
}
