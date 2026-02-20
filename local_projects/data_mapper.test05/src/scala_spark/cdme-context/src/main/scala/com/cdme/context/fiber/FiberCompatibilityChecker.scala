package com.cdme.context.fiber
// Implements: REQ-F-CTX-001

import com.cdme.model._
import com.cdme.model.config.Epoch

/**
 * Temporal semantics declaration for runtime fiber compatibility checking.
 *
 * Mirrors the compile-time temporal semantics from the compiler module.
 * The context module provides runtime enforcement when compile-time
 * information is insufficient (e.g., dynamically resolved epochs).
 */
sealed trait RuntimeTemporalSemantics

/** Use the right side's data as of the left side's epoch boundary. */
case object RuntimeAsOf extends RuntimeTemporalSemantics

/** Use the latest available version of the right side's data. */
case object RuntimeLatest extends RuntimeTemporalSemantics

/** Require exact epoch match (assertion mode). */
case object RuntimeExact extends RuntimeTemporalSemantics

/**
 * Runtime fiber compatibility checker.
 *
 * The compiler performs fiber checks at compile time using declared epochs.
 * This checker handles cases where epochs are resolved dynamically at
 * runtime (e.g., parameterized boundary definitions that depend on
 * runtime context).
 *
 * The runtime check is a defensive measure: if the compiler validated
 * the plan, this should never fail. It exists for defense-in-depth.
 */
object FiberCompatibilityChecker {

  /**
   * Check whether two runtime-resolved epochs are compatible for a join.
   *
   * @param leftEpoch          the resolved epoch of the left join partner
   * @param rightEpoch         the resolved epoch of the right join partner
   * @param declaredSemantics  temporal semantics declared in the compiled plan
   * @return the effective temporal semantics on success, or a [[FiberIncompatibilityError]]
   */
  def checkCompatibility(
    leftEpoch: Epoch,
    rightEpoch: Epoch,
    declaredSemantics: Option[RuntimeTemporalSemantics]
  ): Either[FiberIncompatibilityError, RuntimeTemporalSemantics] = {
    if (leftEpoch == rightEpoch) {
      Right(RuntimeExact)
    } else {
      declaredSemantics match {
        case Some(RuntimeExact) =>
          Left(FiberIncompatibilityError(
            s"Exact epoch match required but epochs differ: left=$leftEpoch, right=$rightEpoch",
            leftEpoch,
            rightEpoch
          ))
        case Some(semantics) =>
          Right(semantics)
        case None =>
          Left(FiberIncompatibilityError(
            s"Epochs differ (left=$leftEpoch, right=$rightEpoch) " +
              "but no temporal semantics declared. This should have been caught at compile time.",
            leftEpoch,
            rightEpoch
          ))
      }
    }
  }
}

/**
 * Error raised when runtime fiber compatibility check fails.
 *
 * This is a context-specific error type (does not extend the sealed CdmeError trait).
 * Convert to a model-level error at the boundary if needed.
 *
 * @param message    description of the incompatibility
 * @param leftEpoch  the left join partner's epoch
 * @param rightEpoch the right join partner's epoch
 */
case class FiberIncompatibilityError(
  message: String,
  leftEpoch: Epoch,
  rightEpoch: Epoch
)
