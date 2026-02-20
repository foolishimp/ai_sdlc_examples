// Implements: REQ-F-TRV-005, REQ-NFR-MAINT-001
// ADR-007: Writer Monad for Telemetry
package cdme.runtime

import cdme.model.grain.Monoid

/**
 * A telemetry entry captured at a morphism execution point.
 *
 * @param morphismId     the morphism that produced this entry
 * @param rowCount       number of records processed
 * @param nullRate        fraction of null values in the output
 * @param typeViolationCount number of type violations detected
 * @param latencyMs      execution latency in milliseconds
 * @param additionalMetrics optional key-value metrics
 */
final case class TelemetryEntry(
    morphismId: String,
    rowCount: Long,
    nullRate: Double,
    typeViolationCount: Long,
    latencyMs: Long,
    additionalMetrics: Map[String, Double] = Map.empty
)

object TelemetryEntry:
  /** Monoid instance for combining telemetry entries into a log. */
  given telemetryVectorMonoid: Monoid[Vector[TelemetryEntry]] with
    def empty: Vector[TelemetryEntry] = Vector.empty
    def combine(a: Vector[TelemetryEntry], b: Vector[TelemetryEntry]): Vector[TelemetryEntry] = a ++ b
    def name: String = "TelemetryVectorMonoid"

/**
 * Writer monad effect for accumulating telemetry alongside transformation results.
 *
 * The Writer monad accumulates a Vector[TelemetryEntry] alongside each
 * transformation. Telemetry collection is strictly a side-channel; it cannot
 * influence transformation logic (ADR-007).
 *
 * @tparam W the log type (typically Vector[TelemetryEntry])
 * @tparam A the computation result type
 * @param run the (result, log) pair
 */
final case class WriterEffect[W, A](run: (A, W)):

  /** Extract the computation result. */
  def value: A = run._1

  /** Extract the accumulated log. */
  def log: W = run._2

  /**
   * Map over the result value, preserving the log.
   *
   * @tparam B the new result type
   * @param f the mapping function
   * @return a new WriterEffect with the mapped result
   */
  def map[B](f: A => B): WriterEffect[W, B] =
    WriterEffect((f(run._1), run._2))

  /**
   * FlatMap: compose two writer computations, combining their logs.
   *
   * @tparam B the new result type
   * @param f the function producing the next WriterEffect
   * @param m monoid for combining logs
   * @return the composed WriterEffect
   */
  def flatMap[B](f: A => WriterEffect[W, B])(using m: Monoid[W]): WriterEffect[W, B] =
    val next = f(run._1)
    WriterEffect((next.value, m.combine(run._2, next.log)))

object WriterEffect:

  /**
   * Lift a pure value into the Writer with an empty log.
   *
   * @tparam W the log type
   * @tparam A the value type
   * @param a the value
   * @param m monoid for the log type
   * @return a WriterEffect with the value and empty log
   */
  def pure[W, A](a: A)(using m: Monoid[W]): WriterEffect[W, A] =
    WriterEffect((a, m.empty))

  /**
   * Create a Writer that only produces a log entry, with Unit result.
   *
   * @tparam W the log type
   * @param w the log entry
   * @return a WriterEffect with Unit result and the log entry
   */
  def tell[W](w: W): WriterEffect[W, Unit] =
    WriterEffect(((), w))

/**
 * Metrics exporter interface for operational monitoring.
 *
 * Exports telemetry to JMX or Prometheus-compatible endpoints.
 */
trait MetricsExporter:
  /**
   * Export a batch of telemetry entries.
   *
   * @param entries the telemetry entries to export
   */
  def export(entries: Vector[TelemetryEntry]): Unit

  /**
   * Export interval in milliseconds (default: 30 seconds).
   */
  def intervalMs: Long = 30000L
