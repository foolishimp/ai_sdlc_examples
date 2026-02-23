// Implements: RIC-ERR-01, REQ-TYP-03-A
// Circuit breaker: detects cascading failures and trips after threshold.
package com.cdme.runtime

import com.cdme.model.error.{CdmeError, CircuitBreakerTripped}

/**
 * Monitors error rate in the first N records and halts execution
 * if the rate exceeds the configured threshold.
 *
 * Implements: RIC-ERR-01, REQ-TYP-03-A
 */
final case class CircuitBreaker(
    windowSize: Long,
    thresholdPercent: Double,
    state: CircuitBreakerState
) {

  /**
   * Record a processing result (success or error) and check if the breaker should trip.
   */
  def record(success: Boolean): Either[CdmeError, CircuitBreaker] = {
    state match {
      case CircuitBreakerState.Tripped =>
        Left(CircuitBreakerTripped(
          errorCount = 0, totalCount = 0, thresholdPercent = thresholdPercent
        ))
      case CircuitBreakerState.Monitoring(totalSeen, errorsSeen) =>
        val newTotal  = totalSeen + 1
        val newErrors = if (success) errorsSeen else errorsSeen + 1

        if (newTotal >= windowSize) {
          val errorRate = newErrors.toDouble / newTotal * 100
          if (errorRate > thresholdPercent) {
            Left(CircuitBreakerTripped(newErrors, newTotal, thresholdPercent))
          } else {
            // Window complete, breaker cleared
            Right(copy(state = CircuitBreakerState.Cleared))
          }
        } else {
          // Check early trip: if even all remaining records were successes, would we still exceed?
          val bestCaseErrors = newErrors.toDouble / windowSize * 100
          if (bestCaseErrors > thresholdPercent) {
            Left(CircuitBreakerTripped(newErrors, newTotal, thresholdPercent))
          } else {
            Right(copy(state = CircuitBreakerState.Monitoring(newTotal, newErrors)))
          }
        }
      case CircuitBreakerState.Cleared =>
        Right(this)
    }
  }
}

sealed trait CircuitBreakerState
object CircuitBreakerState {
  final case class Monitoring(totalSeen: Long, errorsSeen: Long) extends CircuitBreakerState
  case object Tripped extends CircuitBreakerState
  case object Cleared extends CircuitBreakerState
}

object CircuitBreaker {
  def create(windowSize: Long, thresholdPercent: Double): CircuitBreaker =
    CircuitBreaker(windowSize, thresholdPercent, CircuitBreakerState.Monitoring(0, 0))
}
