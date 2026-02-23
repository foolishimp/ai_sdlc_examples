// Implements: REQ-TRV-03, REQ-SHF-01
// Context/epoch consistency validation.
package com.cdme.compiler

import com.cdme.model._
import com.cdme.model.error.{BoundaryMisalignError, CdmeError}

/**
 * Validates epoch and temporal consistency for morphism composition.
 * Cross-boundary traversals require declared temporal semantics.
 *
 * Implements: REQ-TRV-03, REQ-SHF-01
 */
object ContextChecker {

  /** Temporal semantics declaration for cross-epoch joins. */
  sealed trait TemporalSemantics
  object TemporalSemantics {
    case object AsOf   extends TemporalSemantics
    case object Latest extends TemporalSemantics
    case object Exact  extends TemporalSemantics
  }

  /**
   * Validate that two epochs are compatible for a join operation.
   * Same-epoch joins are always valid; cross-epoch joins require temporal semantics.
   */
  def validateFiberCompatibility(
      leftEpoch: EpochId,
      rightEpoch: EpochId,
      temporalSemantics: Option[TemporalSemantics]
  ): Either[CdmeError, Unit] = {
    if (leftEpoch == rightEpoch) {
      Right(())
    } else {
      temporalSemantics match {
        case Some(_) => Right(()) // Cross-epoch join with declared semantics is ok
        case None =>
          Left(BoundaryMisalignError(
            leftEpoch, rightEpoch,
            "Cross-epoch join requires declared temporal semantics (AsOf, Latest, or Exact)"
          ))
      }
    }
  }
}
