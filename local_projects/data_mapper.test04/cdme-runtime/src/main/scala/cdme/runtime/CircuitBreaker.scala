// Implements: REQ-DATA-QUAL-001, REQ-DATA-QUAL-002
// ADR-012: Circuit Breaker with Sampling Pre-Phase
package cdme.runtime

import cdme.compiler.CircuitBreakerConfig

/**
 * Results from the sampling pre-phase.
 *
 * @param sampleSize   number of records sampled
 * @param errorCount   number of errors in the sample
 * @param errorDetails sample of error descriptions for diagnostics
 */
final case class SampleResults(
    sampleSize: Int,
    errorCount: Int,
    errorDetails: List[String] = Nil
) {
  /** Error rate as a fraction. */
  def errorRate: Double =
    if (sampleSize == 0) 0.0
    else errorCount.toDouble / sampleSize
}

/**
 * Structural error detected by the circuit breaker.
 *
 * @param message   description of the structural error
 * @param errorRate the observed error rate in the sample
 * @param threshold the configured threshold
 */
final case class StructuralError(
    message: String,
    errorRate: Double,
    threshold: Double
)

/**
 * Circuit breaker: sampling-based pre-phase to distinguish structural/configuration
 * errors from data quality errors.
 *
 * The circuit breaker runs as a pre-phase:
 *   1. Sample N records (default 10,000)
 *   2. Execute full validation and transformation on the sample
 *   3. Check failure rate against structural error threshold (default 5%)
 *   4. If exceeded, abort with configuration error (prevents flooding Error Sink)
 *
 * This prevents flooding the Error Sink with millions of identical structural errors.
 */
object CircuitBreaker {

  /**
   * Evaluate the sample results against the circuit breaker configuration.
   *
   * @param sampleResults the results from the sampling pre-phase
   * @param config        the circuit breaker configuration
   * @return Right(Continue) if the error rate is acceptable,
   *         Left(StructuralError) if the threshold is exceeded
   */
  def evaluate(
      sampleResults: SampleResults,
      config: CircuitBreakerConfig
  ): Either[StructuralError, CircuitBreakerResult] = {
    val errorRate = sampleResults.errorRate

    if (errorRate > config.structuralErrorThreshold) {
      Left(StructuralError(
        message = s"Circuit breaker tripped: error rate ${formatPercent(errorRate)} " +
          s"exceeds structural threshold ${formatPercent(config.structuralErrorThreshold)}. " +
          s"This indicates a configuration or structural error, not a data quality issue. " +
          s"Sample errors: ${sampleResults.errorDetails.take(5).mkString("; ")}",
        errorRate = errorRate,
        threshold = config.structuralErrorThreshold
      ))
    } else {
      Right(CircuitBreakerResult.Passed(
        sampleSize = sampleResults.sampleSize,
        errorRate = errorRate
      ))
    }
  }

  private def formatPercent(d: Double): String =
    f"${d * 100}%.2f%%"
}
