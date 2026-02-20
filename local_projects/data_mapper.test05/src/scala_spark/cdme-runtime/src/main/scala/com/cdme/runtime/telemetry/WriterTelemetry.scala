package com.cdme.runtime.telemetry

// Implements: REQ-F-TRV-004, REQ-NFR-OBS-002

import com.cdme.model.morphism.MorphismId

/**
 * Telemetry record for a single morphism execution.
 *
 * Each morphism in the execution plan produces one [[MorphismTelemetry]] entry
 * capturing throughput, error rates, and latency.  The `reqKeys` field enables
 * REQ key tagging in telemetry output (REQ-NFR-OBS-002).
 *
 * @param morphismId  identifier of the executed morphism
 * @param inputCount  number of input records
 * @param outputCount number of output records (success partition)
 * @param errorCount  number of records routed to the Error Domain
 * @param latencyMs   wall-clock execution time in milliseconds
 * @param reqKeys     requirement keys traced by this morphism (for observability)
 */
case class MorphismTelemetry(
  morphismId: MorphismId,
  inputCount: Long,
  outputCount: Long,
  errorCount: Long,
  latencyMs: Long,
  reqKeys: List[String]
)

/**
 * Accumulator for per-morphism telemetry records, implementing Writer effect
 * semantics.
 *
 * The Writer effect pattern accumulates telemetry entries as a side-channel
 * alongside the main data pipeline, without requiring mutable shared state.
 * New entries are appended via [[record]], producing a new [[WriterTelemetry]]
 * instance (or mutating in-place for performance in batch scenarios).
 *
 * @param entries the accumulated telemetry entries (in execution order)
 */
case class WriterTelemetry(entries: List[MorphismTelemetry] = Nil) {

  /**
   * Record a new telemetry entry, returning an updated accumulator.
   *
   * @param entry the morphism telemetry to append
   * @return a new [[WriterTelemetry]] with the entry appended
   */
  def record(entry: MorphismTelemetry): WriterTelemetry =
    WriterTelemetry(entries :+ entry)

  /**
   * Combine this accumulator with another (monoid-like append).
   *
   * @param other the other accumulator to merge
   * @return a new [[WriterTelemetry]] with entries from both
   */
  def combine(other: WriterTelemetry): WriterTelemetry =
    WriterTelemetry(entries ++ other.entries)

  /** Total input records across all morphisms. */
  def totalInputCount: Long = entries.map(_.inputCount).sum

  /** Total output records across all morphisms. */
  def totalOutputCount: Long = entries.map(_.outputCount).sum

  /** Total error records across all morphisms. */
  def totalErrorCount: Long = entries.map(_.errorCount).sum

  /** Total latency across all morphisms (wall-clock sum, not parallel). */
  def totalLatencyMs: Long = entries.map(_.latencyMs).sum

  /** All distinct REQ keys referenced by the executed morphisms. */
  def allReqKeys: List[String] = entries.flatMap(_.reqKeys).distinct
}

/**
 * Companion object providing an empty accumulator.
 */
object WriterTelemetry {

  /** An empty telemetry accumulator. */
  val empty: WriterTelemetry = WriterTelemetry(Nil)
}
