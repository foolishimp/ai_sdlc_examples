// Implements: REQ-LDM-03, REQ-TRV-06
// Output of the topological compiler: a validated, executable plan.
package com.cdme.compiler

import com.cdme.model._
import com.cdme.model.adjoint.AdjointClassification

/**
 * The validated execution plan produced by the compiler.
 * This is the contract between compile phase and execute phase.
 *
 * Implements: REQ-LDM-03
 */
final case class ValidatedPlan(
    executionDag: ExecutionDag,
    costEstimate: CostEstimate,
    lineageClassification: Map[MorphismId, LineageClassification],
    adjointComposition: Map[PathId, ComposedAdjoint],
    warnings: List[CompilerWarning]
)

/**
 * The execution DAG: compiled representation of the mapping.
 */
final case class ExecutionDag(
    nodes: Map[NodeId, DagNode],
    edges: Map[EdgeId, DagEdge],
    sources: Set[NodeId],
    sinks: Set[NodeId]
)

/** A node in the execution DAG. */
final case class DagNode(
    id: NodeId,
    entityId: EntityId,
    nodeType: DagNodeType
)

sealed trait DagNodeType
object DagNodeType {
  case object Source      extends DagNodeType
  case object Transform   extends DagNodeType
  case object Aggregation extends DagNodeType
  case object Sink        extends DagNodeType
}

/** An edge in the execution DAG. */
final case class DagEdge(
    id: EdgeId,
    source: NodeId,
    target: NodeId,
    morphismId: MorphismId
)

/**
 * Cost estimate for execution.
 * Implements: REQ-TRV-06
 */
final case class CostEstimate(
    estimatedOutputRows: Long,
    estimatedJoinDepth: Int,
    estimatedIntermediateSize: Long,
    withinBudget: Boolean,
    details: Map[String, Long]
)

/** Lineage classification of a morphism. */
sealed trait LineageClassification
object LineageClassification {
  case object Lossless extends LineageClassification
  case object Lossy    extends LineageClassification
}

/** Composed adjoint for a morphism path. */
final case class ComposedAdjoint(
    pathId: PathId,
    morphismIds: List[MorphismId],
    composedClassification: AdjointClassification
)

/** Compiler warning (non-fatal). */
final case class CompilerWarning(
    code: String,
    message: String
)

/** Verified path: result of path verification. */
final case class VerifiedPath(
    segments: List[MorphismId],
    sourceEntity: EntityId,
    targetEntity: EntityId
)

/** Data statistics for cost estimation. */
final case class DataStatistics(
    rowCounts: Map[EntityId, Long],
    averageRowSizes: Map[EntityId, Long],
    cardinalityEstimates: Map[MorphismId, Double]
)

/** Contract violation for covariance contracts. */
final case class ContractViolation(
    contractId: String,
    invariantName: String,
    message: String
)

/** Covariance contract definition. */
final case class CovarianceContract(
    id: String,
    sourceDomain: String,
    targetDomain: String,
    invariants: List[String],
    enforcementMode: String
)
