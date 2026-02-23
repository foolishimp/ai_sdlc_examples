package com.cdme.runtime

import java.time.Instant

// Implements: REQ-TRV-04 (Writer Monad for Telemetry)
case class ExecutionTelemetry(
  entries: List[TelemetryEntry] = Nil
) {
  def append(entry: TelemetryEntry): ExecutionTelemetry =
    copy(entries = entries :+ entry)

  def totalLatencyMs: Long = entries.map(_.latencyMs).sum

  def totalRows: Long = entries.map(_.rowCount).sum
}

case class TelemetryEntry(
  morphismName: String,
  rowCount: Long,
  latencyMs: Long,
  qualityStats: Map[String, Double] = Map.empty,
  timestamp: Instant
)
