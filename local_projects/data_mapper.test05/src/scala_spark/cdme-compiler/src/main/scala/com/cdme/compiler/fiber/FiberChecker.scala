package com.cdme.compiler.fiber
// Implements: REQ-F-CTX-001, REQ-F-TRV-003

import com.cdme.model._
import com.cdme.compiler.{FiberError, EpochMismatch}

/**
 * Temporal semantics declaration for cross-epoch joins.
 *
 * When two stages reference different epochs, the mapping author must
 * declare which temporal semantics apply. Without a declaration, the
 * compiler rejects the join.
 */
sealed trait TemporalSemantics

/** Use the right side's data as of the left side's epoch boundary. */
case object AsOf extends TemporalSemantics

/** Use the latest available version of the right side's data. */
case object Latest extends TemporalSemantics

/** Require exact epoch match (assertion mode). */
case object Exact extends TemporalSemantics

/**
 * Checks fiber compatibility for joins between stages that may reference
 * different processing epochs.
 *
 * "Fiber compatibility" is the CDME term (inspired by sheaf theory) for
 * ensuring that joined datasets share a consistent temporal context.
 * If epochs differ, the join author must explicitly declare temporal
 * semantics so the engine can resolve the mismatch deterministically.
 */
object FiberChecker {

  /**
   * Check whether two epochs are compatible for a join operation.
   *
   * @param leftEpoch          the epoch of the left join partner
   * @param rightEpoch         the epoch of the right join partner
   * @param declaredSemantics  optional temporal semantics declared by the mapping author
   * @return the resolved [[TemporalSemantics]] on success, or a [[FiberError]] on failure
   */
  def checkJoinCompatibility(
    leftEpoch: Epoch,
    rightEpoch: Epoch,
    declaredSemantics: Option[TemporalSemantics]
  ): Either[FiberError, TemporalSemantics] = {
    if (epochsMatch(leftEpoch, rightEpoch)) {
      // Same epoch: Exact semantics, no declaration needed
      Right(Exact)
    } else {
      declaredSemantics match {
        case Some(semantics) =>
          // Author has declared how to handle the epoch mismatch
          semantics match {
            case Exact =>
              // Author asserted exact match but epochs differ
              Left(EpochMismatch(leftEpoch, rightEpoch))
            case other =>
              Right(other)
          }
        case None =>
          // No declaration for mismatched epochs: reject
          Left(EpochMismatch(leftEpoch, rightEpoch))
      }
    }
  }

  /**
   * Compare two epochs for equality.
   *
   * Epoch equality is structural: same boundary definition and parameters
   * produce the same epoch.
   */
  private def epochsMatch(left: Epoch, right: Epoch): Boolean =
    left == right
}
