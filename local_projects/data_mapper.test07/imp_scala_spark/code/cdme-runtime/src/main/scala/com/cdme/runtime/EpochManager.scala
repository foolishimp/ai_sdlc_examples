// Implements: REQ-SHF-01, REQ-TRV-03
// Epoch manager: manages execution temporal windows.
package com.cdme.runtime

import com.cdme.model.EpochId
import com.cdme.model.error.{BoundaryMisalignError, CdmeError}

/**
 * Manages execution temporal windows and validates epoch consistency.
 * Implements: REQ-SHF-01, REQ-TRV-03
 */
object EpochManager {

  /**
   * Validate that two epochs are compatible for joining.
   */
  def validateEpochCompatibility(
      left: Epoch,
      right: Epoch
  ): Either[CdmeError, Unit] = {
    if (left.id == right.id) Right(())
    else if (epochsOverlap(left, right)) Right(())
    else Left(BoundaryMisalignError(
      left.id, right.id,
      s"Epochs do not overlap: left=[${left.start},${left.end}), right=[${right.start},${right.end})"
    ))
  }

  /**
   * Check if two epochs have temporal overlap.
   */
  def epochsOverlap(a: Epoch, b: Epoch): Boolean =
    a.start.isBefore(b.end) && b.start.isBefore(a.end)

  /**
   * Create a new epoch with a unique ID.
   */
  def createEpoch(
      id: EpochId,
      start: java.time.Instant,
      end: java.time.Instant
  ): Either[CdmeError, Epoch] = {
    if (!start.isBefore(end)) {
      Left(BoundaryMisalignError(id, id, s"Epoch start ($start) must be before end ($end)"))
    } else {
      Right(Epoch(id, start, end))
    }
  }
}
