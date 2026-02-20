package com.cdme.runtime.error

// Implements: REQ-F-ERR-002, REQ-DATA-QAL-001

import com.cdme.model.config.{AbsoluteThreshold, FailureThreshold, PercentageThreshold}

/**
 * Configuration for the circuit breaker that halts pipeline execution when
 * the error rate exceeds a configured threshold.
 *
 * @param threshold the failure threshold (absolute count or percentage-based)
 */
case class CircuitBreakerConfig(threshold: FailureThreshold)

/**
 * Signals that the circuit breaker has tripped because the error rate
 * exceeded the configured threshold.
 *
 * @param errorCount   total errors observed
 * @param totalCount   total records processed
 * @param errorRate    computed error rate (errorCount / totalCount)
 * @param threshold    the threshold that was exceeded
 * @param message      human-readable description
 */
case class CircuitBreakerTripped(
  errorCount: Long,
  totalCount: Long,
  errorRate: Double,
  threshold: FailureThreshold,
  message: String
)

/**
 * Monitors the error rate against a configured threshold and halts execution
 * if the rate is exceeded.
 *
 * The circuit breaker supports two threshold modes (determined by
 * [[FailureThreshold]]):
 *  - '''Absolute''': trips when `errorCount >= absoluteLimit`
 *  - '''Percentage''': trips when `(errorCount / totalCount) * 100 >= percentageLimit`
 *
 * This is a probabilistic circuit breaker: it samples the error rate at
 * check points rather than on every record, reducing overhead in high-throughput
 * pipelines.
 */
object CircuitBreaker {

  /**
   * Check whether the circuit breaker should trip given current error statistics.
   *
   * @param errorCount total errors observed so far
   * @param totalCount total records processed so far
   * @param config     the circuit breaker configuration
   * @return `Right(())` if within threshold, `Left(CircuitBreakerTripped)` if exceeded
   */
  def check(
    errorCount: Long,
    totalCount: Long,
    config: CircuitBreakerConfig
  ): Either[CircuitBreakerTripped, Unit] = {
    if (totalCount <= 0L) {
      // No records processed yet — nothing to check
      Right(())
    } else {
      val errorRate: Double = errorCount.toDouble / totalCount.toDouble * 100.0

      config.threshold match {
        case AbsoluteThreshold(maxErrors) =>
          if (errorCount >= maxErrors)
            Left(CircuitBreakerTripped(
              errorCount = errorCount,
              totalCount = totalCount,
              errorRate = errorRate,
              threshold = config.threshold,
              message = s"Circuit breaker tripped: $errorCount errors >= absolute limit $maxErrors"
            ))
          else
            Right(())

        case PercentageThreshold(maxPercent, sampleWindow) =>
          // Only check after processing at least sampleWindow records
          if (totalCount >= sampleWindow.toLong && errorRate >= maxPercent)
            Left(CircuitBreakerTripped(
              errorCount = errorCount,
              totalCount = totalCount,
              errorRate = errorRate,
              threshold = config.threshold,
              message = s"Circuit breaker tripped: ${f"$errorRate%.2f"}% errors >= ${maxPercent}% threshold (after $totalCount records)"
            ))
          else
            Right(())
      }
    }
  }
}
