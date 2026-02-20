package com.cdme.model.error

// Implements: REQ-F-ERR-001, REQ-F-ERR-004

import com.cdme.model.config.Epoch
import com.cdme.model.entity.EntityId
import com.cdme.model.grain.Grain
import com.cdme.model.morphism.MorphismId
import com.cdme.model.types.{CdmeType, Predicate}

/**
 * The Error Domain ADT — a sealed hierarchy of structured runtime errors.
 *
 * Every runtime error in CDME is a typed, immutable value (not an exception).
 * Errors are routed via `Either[CdmeError, T]` semantics: `Left` values flow
 * to the Error Domain sink, while `Right` values flow to the success sink.
 *
 * Each error carries full context for debugging and audit:
 *  - `failureType`: a machine-readable classification string
 *  - `offendingValues`: the data values that caused the failure
 *  - `sourceEntity`: the entity where the error originated
 *  - `sourceEpoch`: the processing epoch
 *  - `morphismPath`: the chain of morphisms leading to the failure point
 *
 * This design satisfies REQ-F-ERR-004 (structured error objects) and
 * REQ-F-ERR-001 (Either monad error routing).
 */
sealed trait CdmeError {
  /** Machine-readable failure classification (e.g. "refinement_violation"). */
  def failureType: String

  /** The data values that triggered the error. */
  def offendingValues: Any

  /** The entity where the error originated. */
  def sourceEntity: EntityId

  /** The processing epoch in which the error occurred. */
  def sourceEpoch: Epoch

  /** The chain of morphism IDs traversed before the error. */
  def morphismPath: List[MorphismId]
}

/**
 * A data value violated a refinement type predicate.
 *
 * @param predicate       the predicate that was violated
 * @param offendingValues the value(s) that failed the check
 * @param sourceEntity    originating entity
 * @param sourceEpoch     processing epoch
 * @param morphismPath    morphism chain
 */
case class RefinementViolation(
  predicate: Predicate,
  offendingValues: Any,
  sourceEntity: EntityId,
  sourceEpoch: Epoch,
  morphismPath: List[MorphismId]
) extends CdmeError {
  val failureType: String = "refinement_violation"
}

/**
 * A type mismatch was detected at runtime.
 *
 * This is a defensive error — the compiler should catch type mismatches
 * at compile time, but the runtime verifies types as a safety net.
 *
 * @param expected        the expected CDME type
 * @param actual          the actual CDME type found
 * @param offendingValues the value(s) with the wrong type
 * @param sourceEntity    originating entity
 * @param sourceEpoch     processing epoch
 * @param morphismPath    morphism chain
 */
case class TypeMismatchError(
  expected: CdmeType,
  actual: CdmeType,
  offendingValues: Any,
  sourceEntity: EntityId,
  sourceEpoch: Epoch,
  morphismPath: List[MorphismId]
) extends CdmeError {
  val failureType: String = "type_mismatch"
}

/**
 * A grain safety violation — data at an incompatible grain was encountered.
 *
 * @param expectedGrain   the required grain
 * @param actualGrain     the grain of the data
 * @param offendingValues the value(s) at the wrong grain
 * @param sourceEntity    originating entity
 * @param sourceEpoch     processing epoch
 * @param morphismPath    morphism chain
 */
case class GrainViolation(
  expectedGrain: Grain,
  actualGrain: Grain,
  offendingValues: Any,
  sourceEntity: EntityId,
  sourceEpoch: Epoch,
  morphismPath: List[MorphismId]
) extends CdmeError {
  val failureType: String = "grain_violation"
}

/**
 * A principal attempted to traverse a morphism without the required role.
 *
 * @param morphismId      the morphism that was denied
 * @param principal       the identity that was rejected
 * @param offendingValues the access context
 * @param sourceEntity    originating entity
 * @param sourceEpoch     processing epoch
 * @param morphismPath    morphism chain
 */
case class AccessDenied(
  morphismId: MorphismId,
  principal: Principal,
  offendingValues: Any,
  sourceEntity: EntityId,
  sourceEpoch: Epoch,
  morphismPath: List[MorphismId]
) extends CdmeError {
  val failureType: String = "access_denied"
}

/**
 * An external (registered) morphism calculator returned an error.
 *
 * @param calculatorId      the registered calculator identifier
 * @param calculatorVersion the version of the calculator
 * @param offendingValues   the input that caused the failure
 * @param sourceEntity      originating entity
 * @param sourceEpoch       processing epoch
 * @param morphismPath      morphism chain
 */
case class ExternalMorphismFailure(
  calculatorId: String,
  calculatorVersion: String,
  offendingValues: Any,
  sourceEntity: EntityId,
  sourceEpoch: Epoch,
  morphismPath: List[MorphismId]
) extends CdmeError {
  val failureType: String = "external_morphism_failure"
}

/**
 * A lookup (data-backed or logic-backed) could not resolve a key.
 *
 * @param lookupId        the lookup identifier
 * @param key             the key that could not be resolved
 * @param offendingValues the input context
 * @param sourceEntity    originating entity
 * @param sourceEpoch     processing epoch
 * @param morphismPath    morphism chain
 */
case class LookupResolutionError(
  lookupId: String,
  key: Any,
  offendingValues: Any,
  sourceEntity: EntityId,
  sourceEpoch: Epoch,
  morphismPath: List[MorphismId]
) extends CdmeError {
  val failureType: String = "lookup_resolution_error"
}

/**
 * The circuit breaker tripped because the error rate exceeded
 * the configured failure threshold.
 *
 * @param errorCount      the number of errors observed
 * @param threshold       the configured threshold description
 * @param offendingValues summary of recent errors
 * @param sourceEntity    originating entity
 * @param sourceEpoch     processing epoch
 * @param morphismPath    morphism chain
 */
case class CircuitBreakerTripped(
  errorCount: Long,
  threshold: String,
  offendingValues: Any,
  sourceEntity: EntityId,
  sourceEpoch: Epoch,
  morphismPath: List[MorphismId]
) extends CdmeError {
  val failureType: String = "circuit_breaker_tripped"
}

/**
 * A security principal — the identity executing a pipeline.
 *
 * @param id    unique principal identifier
 * @param roles the set of role names assigned to this principal
 */
case class Principal(id: String, roles: Set[String])
