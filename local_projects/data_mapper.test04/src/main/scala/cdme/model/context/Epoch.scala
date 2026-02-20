// Implements: REQ-F-TRV-003, REQ-F-PDM-002, REQ-BR-LDM-002
// ADR-001: Opaque type for Epoch
package cdme.model.context

import java.time.Instant

/**
 * An epoch represents a bounded processing scope.
 *
 * Epochs define the temporal boundaries of data processing runs. All records
 * within an epoch are processed together, and lookup immutability is enforced
 * within an epoch (REQ-BR-LDM-002).
 *
 * @param id        unique identifier for this epoch
 * @param startTime the start boundary of the epoch
 * @param endTime   the end boundary of the epoch
 * @param metadata  optional key-value metadata
 */
final case class Epoch(
    id: EpochId,
    startTime: Instant,
    endTime: Instant,
    metadata: Map[String, String] = Map.empty
):
  require(endTime.isAfter(startTime) || endTime == startTime,
    s"Epoch end time must be >= start time: start=$startTime, end=$endTime")

  /** Duration of this epoch in milliseconds. */
  def durationMs: Long = endTime.toEpochMilli - startTime.toEpochMilli

  /** Check whether an instant falls within this epoch. */
  def contains(instant: Instant): Boolean =
    !instant.isBefore(startTime) && !instant.isAfter(endTime)

/**
 * Opaque type for epoch identifiers.
 */
opaque type EpochId = String

object EpochId:
  def apply(value: String): EpochId = value
  extension (id: EpochId)
    def value: String = id
    def show: String = s"EpochId($id)"
