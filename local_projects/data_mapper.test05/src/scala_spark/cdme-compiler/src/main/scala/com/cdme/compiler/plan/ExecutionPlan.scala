package com.cdme.compiler.plan
// Implements: REQ-F-LDM-003, REQ-F-TRV-005

import com.cdme.model._
import com.cdme.model.config.LineageMode
import com.cdme.model.morphism.Cardinality
import com.cdme.model.monoid.Monoid
import com.cdme.compiler.cost.CostEstimate
import com.cdme.compiler.fiber.TemporalSemantics
import com.cdme.compiler.lineage.LossinessTag

/**
 * A unique identifier for a compiled execution plan.
 *
 * @param value UUID string identifying this plan instance
 */
case class PlanId(value: String)

/**
 * A reference to a previously computed stage in the execution plan.
 * Used to wire the DAG of stages together without cyclic dependencies.
 *
 * @param index zero-based position in the plan's stage list
 */
case class StageRef(index: Int)

// ---------------------------------------------------------------------------
// Checkpoint policy
// ---------------------------------------------------------------------------

/** Policy controlling when key envelopes are persisted for lineage reconstruction. */
sealed trait CheckpointPolicy

/** Checkpoint at every lossy morphism plus graph boundaries. */
case object CheckpointAtLossy extends CheckpointPolicy

/** Checkpoint at graph input and output only. */
case object CheckpointBoundaryOnly extends CheckpointPolicy

/** No checkpointing (summary lineage only). */
case object NoCheckpoint extends CheckpointPolicy

// ---------------------------------------------------------------------------
// Validated morphism wrapper
// ---------------------------------------------------------------------------

/**
 * A morphism that has passed all compile-time checks (path, grain, type, access).
 * The runtime may trust this without re-validation.
 */
case class ValidatedMorphism(
  morphismId: MorphismId,
  domain: EntityId,
  codomain: EntityId,
  cardinality: Cardinality
)

// ---------------------------------------------------------------------------
// Join condition (structural morphism detail carried into the plan)
// ---------------------------------------------------------------------------

/**
 * Describes how two stages should be joined.
 *
 * @param leftKeys  column names on the left side
 * @param rightKeys column names on the right side
 */
case class JoinCondition(
  leftKeys: List[String],
  rightKeys: List[String]
)

// ---------------------------------------------------------------------------
// Segment identifier for checkpointing
// ---------------------------------------------------------------------------

/** Identifies a segment in the execution graph for checkpoint purposes. */
case class SegmentId(value: String)

// ---------------------------------------------------------------------------
// Execution stages — sealed hierarchy
// ---------------------------------------------------------------------------

/**
 * A single stage in the compiled execution plan.
 *
 * The runtime walks these stages in order, respecting [[StageRef]] dependencies.
 */
sealed trait ExecutionStage

/**
 * Read source data from a physical binding for a given epoch.
 *
 * @param binding physical storage binding for the logical entity
 * @param epoch   the processing epoch to scope the read
 */
case class ReadStage(
  binding: PdmBinding,
  epoch: Epoch
) extends ExecutionStage

/**
 * Apply a validated morphism (map / compute) to the output of a prior stage.
 *
 * @param morphism   the compile-validated morphism to apply
 * @param input      reference to the input stage
 * @param lossiness  whether this morphism is lossless or lossy (for checkpoint policy)
 */
case class TransformStage(
  morphism: ValidatedMorphism,
  input: StageRef,
  lossiness: LossinessTag
) extends ExecutionStage

/**
 * Join two stages together.
 *
 * @param left              reference to the left input stage
 * @param right             reference to the right input stage
 * @param condition         join keys
 * @param temporalSemantics declared temporal semantics for cross-epoch joins (None if same epoch)
 */
case class JoinStage(
  left: StageRef,
  right: StageRef,
  condition: JoinCondition,
  temporalSemantics: Option[TemporalSemantics]
) extends ExecutionStage

/**
 * Aggregate rows using a declared monoid.
 *
 * @param monoid    the monoidal combiner
 * @param groupKeys columns to group by
 * @param input     reference to the input stage
 */
case class AggregateStage(
  monoid: Monoid[_],
  groupKeys: List[String],
  input: StageRef
) extends ExecutionStage

/**
 * Write results to a physical sink.
 *
 * @param binding physical storage binding for the target entity
 * @param input   reference to the input stage
 */
case class WriteStage(
  binding: PdmBinding,
  input: StageRef
) extends ExecutionStage

/**
 * Persist a key envelope checkpoint for lineage reconstruction.
 *
 * @param segmentId identifies this checkpoint segment
 * @param input     reference to the stage whose keys are captured
 */
case class CheckpointStage(
  segmentId: SegmentId,
  input: StageRef
) extends ExecutionStage

// ---------------------------------------------------------------------------
// Execution plan — the compiler's output
// ---------------------------------------------------------------------------

/**
 * The fully compiled, validated execution plan.
 *
 * Produced by [[com.cdme.compiler.TopologicalCompiler]]. The runtime consumes
 * this plan and trusts it without re-validation.
 *
 * @param planId            unique plan identifier
 * @param stages            ordered list of execution stages
 * @param costEstimate      estimated computational cost
 * @param lineageMode       lineage capture granularity
 * @param checkpointPolicy  when to persist key envelopes
 * @param artifactVersions  version metadata for all input artifacts
 */
case class ExecutionPlan(
  planId: PlanId,
  stages: List[ExecutionStage],
  costEstimate: CostEstimate,
  lineageMode: LineageMode,
  checkpointPolicy: CheckpointPolicy,
  artifactVersions: Map[String, ArtifactVersion]
)
