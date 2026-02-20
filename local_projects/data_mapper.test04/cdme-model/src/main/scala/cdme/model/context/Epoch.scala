// Implements: REQ-F-TRV-003, REQ-F-PDM-002, REQ-BR-LDM-002
// ADR-001: Opaque type for Epoch -> Scala 2.13 case class
package cdme.model.context

import java.time.Instant

final case class Epoch(
    id: EpochId,
    startTime: Instant,
    endTime: Instant,
    metadata: Map[String, String] = Map.empty
) {
  require(endTime.isAfter(startTime) || endTime == startTime,
    s"Epoch end time must be >= start time: start=$startTime, end=$endTime")

  def durationMs: Long = endTime.toEpochMilli - startTime.toEpochMilli

  def contains(instant: Instant): Boolean =
    !instant.isBefore(startTime) && !instant.isAfter(endTime)
}

case class EpochId(value: String) {
  def show: String = s"EpochId($value)"
}
