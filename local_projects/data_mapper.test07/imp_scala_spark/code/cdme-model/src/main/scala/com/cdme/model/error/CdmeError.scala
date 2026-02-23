// Implements: REQ-TYP-03, REQ-ERROR-01
// Sealed ADT for all CDME error types. Errors are data, not exceptions (Axiom 10).
package com.cdme.model.error

import com.cdme.model.{EntityId, EpochId, MorphismId}
import com.cdme.model.grain.Grain
import com.cdme.model.types.CdmeType

/**
 * Sealed error hierarchy carrying full diagnostic context.
 * Every error records where it occurred (entity, epoch, morphism path).
 *
 * Implements: REQ-TYP-03, REQ-ERROR-01
 * See ADR-003: Either-Based Error Handling
 */
sealed trait CdmeError {
  def code: String
  def message: String
  def sourceEntity: Option[EntityId]
  def sourceEpoch: Option[EpochId]
  def morphismPath: List[MorphismId]
}

// --- Type errors ---

/** Type unification failure during morphism composition. */
final case class TypeUnificationError(
    expected: CdmeType,
    actual: CdmeType,
    morphismPath: List[MorphismId],
    sourceEntity: Option[EntityId] = None,
    sourceEpoch: Option[EpochId] = None
) extends CdmeError {
  val code: String    = "TYPE_UNIFICATION"
  val message: String = s"Expected $expected but found $actual"
}

/** Refinement predicate violation at runtime. */
final case class RefinementViolationError(
    predicateName: String,
    predicateExpr: String,
    offendingValue: String,
    morphismPath: List[MorphismId],
    sourceEntity: Option[EntityId] = None,
    sourceEpoch: Option[EpochId] = None
) extends CdmeError {
  val code: String    = "REFINEMENT_VIOLATION"
  val message: String = s"Value '$offendingValue' violates refinement '$predicateName'"
}

// --- Grain errors ---

/** Grain safety violation: incompatible grains mixed without aggregation. */
final case class GrainViolationError(
    sourceGrain: Grain,
    targetGrain: Grain,
    morphismPath: List[MorphismId],
    sourceEntity: Option[EntityId] = None,
    sourceEpoch: Option[EpochId] = None
) extends CdmeError {
  val code: String    = "GRAIN_VIOLATION"
  val message: String = s"Incompatible grains: $sourceGrain and $targetGrain"
}

// --- Composition / path errors ---

/** Path not found or invalid in the LDM topology. */
final case class PathNotFoundError(
    path: String,
    reason: String,
    morphismPath: List[MorphismId] = Nil,
    sourceEntity: Option[EntityId] = None,
    sourceEpoch: Option[EpochId] = None
) extends CdmeError {
  val code: String    = "PATH_NOT_FOUND"
  val message: String = s"Invalid path '$path': $reason"
}

/** Morphism composition failure: codomain/domain mismatch. */
final case class CompositionError(
    leftMorphism: MorphismId,
    rightMorphism: MorphismId,
    reason: String,
    morphismPath: List[MorphismId] = Nil,
    sourceEntity: Option[EntityId] = None,
    sourceEpoch: Option[EpochId] = None
) extends CdmeError {
  val code: String    = "COMPOSITION_ERROR"
  val message: String = s"Cannot compose $leftMorphism with $rightMorphism: $reason"
}

// --- Access control errors ---

/** Access denied for a morphism traversal. */
final case class AccessDeniedError(
    morphismId: MorphismId,
    principal: String,
    morphismPath: List[MorphismId],
    sourceEntity: Option[EntityId] = None,
    sourceEpoch: Option[EpochId] = None
) extends CdmeError {
  val code: String    = "ACCESS_DENIED"
  val message: String = s"Principal '$principal' denied traversal of morphism $morphismId"
}

// --- Runtime errors ---

/** Budget exceeded during cost estimation. */
final case class BudgetExceededError(
    estimatedCost: Long,
    budgetLimit: Long,
    morphismPath: List[MorphismId] = Nil,
    sourceEntity: Option[EntityId] = None,
    sourceEpoch: Option[EpochId] = None
) extends CdmeError {
  val code: String    = "BUDGET_EXCEEDED"
  val message: String = s"Estimated cost $estimatedCost exceeds budget $budgetLimit"
}

/** Circuit breaker tripped: too many errors in initial records. */
final case class CircuitBreakerTripped(
    errorCount: Long,
    totalCount: Long,
    thresholdPercent: Double,
    morphismPath: List[MorphismId] = Nil,
    sourceEntity: Option[EntityId] = None,
    sourceEpoch: Option[EpochId] = None
) extends CdmeError {
  val code: String    = "CIRCUIT_BREAKER_TRIPPED"
  val message: String =
    s"Circuit breaker tripped: $errorCount/$totalCount errors (${errorCount.toDouble / totalCount * 100}% > $thresholdPercent%)"
}

/** Batch failure threshold exceeded. */
final case class BatchThresholdExceeded(
    errorCount: Long,
    totalCount: Long,
    morphismPath: List[MorphismId] = Nil,
    sourceEntity: Option[EntityId] = None,
    sourceEpoch: Option[EpochId] = None
) extends CdmeError {
  val code: String    = "BATCH_THRESHOLD_EXCEEDED"
  val message: String = s"Batch failure threshold exceeded: $errorCount errors out of $totalCount records"
}

/** Boundary/epoch misalignment error. */
final case class BoundaryMisalignError(
    leftEpoch: EpochId,
    rightEpoch: EpochId,
    reason: String,
    morphismPath: List[MorphismId] = Nil,
    sourceEntity: Option[EntityId] = None,
    sourceEpoch: Option[EpochId] = None
) extends CdmeError {
  val code: String    = "BOUNDARY_MISALIGN"
  val message: String = s"Boundary misalignment: epochs '$leftEpoch' and '$rightEpoch': $reason"
}

/** Accounting invariant violation. */
final case class AccountingInvariantError(
    inputCount: Long,
    processedCount: Long,
    filteredCount: Long,
    erroredCount: Long,
    morphismPath: List[MorphismId] = Nil,
    sourceEntity: Option[EntityId] = None,
    sourceEpoch: Option[EpochId] = None
) extends CdmeError {
  val code: String    = "ACCOUNTING_INVARIANT"
  val message: String =
    s"Accounting invariant violated: input=$inputCount != processed=$processedCount + filtered=$filteredCount + errored=$erroredCount"
}

/** External calculator error. */
final case class ExternalCalculatorError(
    calculatorId: String,
    reason: String,
    morphismPath: List[MorphismId] = Nil,
    sourceEntity: Option[EntityId] = None,
    sourceEpoch: Option[EpochId] = None
) extends CdmeError {
  val code: String    = "EXTERNAL_CALCULATOR"
  val message: String = s"External calculator '$calculatorId' failed: $reason"
}

/** Contract breach error for covariance contracts. */
final case class ContractBreachError(
    contractId: String,
    invariantName: String,
    observedValue: String,
    threshold: String,
    morphismPath: List[MorphismId] = Nil,
    sourceEntity: Option[EntityId] = None,
    sourceEpoch: Option[EpochId] = None
) extends CdmeError {
  val code: String    = "CONTRACT_BREACH"
  val message: String = s"Contract '$contractId' breached: $invariantName observed=$observedValue threshold=$threshold"
}

/** Late data arrival error. */
final case class LateArrivalError(
    epochId: EpochId,
    reason: String,
    morphismPath: List[MorphismId] = Nil,
    sourceEntity: Option[EntityId] = None,
    sourceEpoch: Option[EpochId] = None
) extends CdmeError {
  val code: String    = "LATE_ARRIVAL"
  val message: String = s"Late data arrival for epoch '$epochId': $reason"
}

/** Generic validation error. */
final case class ValidationError(
    detail: String,
    morphismPath: List[MorphismId] = Nil,
    sourceEntity: Option[EntityId] = None,
    sourceEpoch: Option[EpochId] = None
) extends CdmeError {
  val code: String    = "VALIDATION_ERROR"
  val message: String = detail
}

/** Configuration error. */
final case class ConfigError(
    detail: String,
    morphismPath: List[MorphismId] = Nil,
    sourceEntity: Option[EntityId] = None,
    sourceEpoch: Option[EpochId] = None
) extends CdmeError {
  val code: String    = "CONFIG_ERROR"
  val message: String = detail
}
