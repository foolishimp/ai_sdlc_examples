// Implements: REQ-NFR-PERF-001, REQ-NFR-PERF-002, REQ-NFR-SEC-001
// ADR-009: Spark as Reference Execution Binding
package cdme.runtime

import cdme.compiler.ExecutionArtifact

/**
 * Execution error for runtime failures.
 *
 * @param message   error description
 * @param cause     optional underlying exception
 * @param stepIndex the step at which the error occurred, if applicable
 */
final case class ExecutionError(
    message: String,
    cause: Option[Throwable] = None,
    stepIndex: Option[Int] = None
)

/**
 * Skew mitigation configuration.
 *
 * @param threshold       key count threshold for whale key detection (default: 100x median)
 * @param saltFactor      number of salt partitions for skewed keys (default: 10)
 * @param detectionStrategy detection approach: "sampling" or "histogram"
 */
final case class SkewMitigation(
    threshold: Long = 100L,
    saltFactor: Int = 10,
    detectionStrategy: String = "sampling"
)

/**
 * Result of an execution run.
 *
 * @param runId                unique run identifier
 * @param telemetry            accumulated telemetry entries
 * @param accountingLedger     the accounting ledger for this run
 * @param adjointMetadataLocations where adjoint metadata was persisted
 */
final case class ExecutionResult(
    runId: String,
    telemetry: Vector[TelemetryEntry],
    accountingLedger: AccountingLedger,
    adjointMetadataLocations: Map[String, String]
)

/**
 * Abstract execution engine trait.
 *
 * The executor is framework-agnostic (ADR-009). The Spark binding is the
 * reference implementation, but any distributed compute framework can
 * implement this trait.
 *
 * Morphism execution on each node is stateless and pure; all state is
 * in the distributed datasets.
 */
trait ExecutionEngine {

  /**
   * Execute a sealed execution artifact.
   *
   * @param artifact the validated, sealed execution artifact
   * @return Right(result) with telemetry, accounting, and adjoint metadata,
   *         or Left(error) for execution failures
   */
  def execute(artifact: ExecutionArtifact): Either[ExecutionError, ExecutionResult]

  /**
   * Execute with skew mitigation enabled.
   *
   * @param artifact the execution artifact
   * @param skewConfig skew mitigation configuration
   * @return the execution result
   */
  def executeWithSkewMitigation(
      artifact: ExecutionArtifact,
      skewConfig: SkewMitigation
  ): Either[ExecutionError, ExecutionResult] =
    // Default implementation delegates to execute; concrete implementations
    // may override for framework-specific skew handling.
    execute(artifact)
}
