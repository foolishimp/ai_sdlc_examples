// Implements: REQ-F-ERR-004, REQ-NFR-SEC-001
package cdme.config

import cdme.compiler.{ErrorSinkConfig, CardinalityBudget, BatchThresholdConfig, CircuitBreakerConfig, SurrogateKeyConfig}
import cdme.runtime.SkewMitigation

/**
 * Per-run execution configuration.
 *
 * Passed to each execution run to configure error handling, budgets,
 * circuit breaker, and skew mitigation.
 *
 * @param errorSink           error sink configuration
 * @param budget              cardinality budget
 * @param batchThreshold      batch failure threshold
 * @param circuitBreaker      circuit breaker configuration
 * @param surrogateKey        surrogate key configuration
 * @param skewMitigation      skew mitigation configuration
 * @param adjointMetadataBudgetBytes maximum bytes for adjoint metadata (warning at 1GB)
 */
final case class ExecutionConfig(
    errorSink: ErrorSinkConfig,
    budget: CardinalityBudget,
    batchThreshold: BatchThresholdConfig = BatchThresholdConfig(),
    circuitBreaker: CircuitBreakerConfig = CircuitBreakerConfig(),
    surrogateKey: SurrogateKeyConfig = SurrogateKeyConfig(),
    skewMitigation: SkewMitigation = SkewMitigation(),
    adjointMetadataBudgetBytes: Long = 1073741824L // 1GB default
)

object ExecutionConfig {
  /**
   * Create a default execution config with the given error sink location.
   *
   * @param errorSinkLocation the path/URI for the error sink
   * @return a default ExecutionConfig
   */
  def default(errorSinkLocation: String): ExecutionConfig =
    ExecutionConfig(
      errorSink = ErrorSinkConfig("filesystem", errorSinkLocation),
      budget = CardinalityBudget(
        maxOutputRows = 100000000L,
        maxJoinFanOut = 10000L,
        maxIntermediateSize = 500000000L
      )
    )
}
