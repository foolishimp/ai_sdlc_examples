package cdme.errors

// Implements: REQ-F-TYP-003, REQ-F-TYP-004, REQ-F-ERR-001, REQ-DATA-BATCH-001, REQ-DATA-BATCH-002

/** Error domain semantics via Either Monad.
  *
  * All failures produce Left(CdmeError), never exceptions.
  * Invalid records are routed to Error Sink with full context (REQ-F-ERR-001).
  * Error handling is idempotent: same input + same config = same errors (REQ-F-TYP-004).
  */
object ErrorRouter:

  /** Minimal error object content (REQ-F-ERR-001). */
  case class CdmeError(
      constraintType: String,    // Type of constraint that failed
      offendingValues: List[Any], // The values that violated the constraint
      sourceEntity: String,      // Source entity name
      sourceEpoch: String,       // Source epoch/batch identifier
      morphismPath: String       // Morphism path where failure occurred
  )

  /** Batch failure threshold config (REQ-DATA-BATCH-001). */
  case class ThresholdConfig(
      maxErrorCount: Option[Long] = None,
      maxErrorPercentage: Option[Double] = None
  )

  /** Check if batch threshold has been exceeded. */
  def checkThreshold(
      errorCount: Long,
      totalCount: Long,
      config: ThresholdConfig
  ): Boolean =
    config.maxErrorCount.exists(errorCount >= _) ||
      config.maxErrorPercentage.exists { pct =>
        totalCount > 0 && (errorCount.toDouble / totalCount) >= pct
      }

  /** Probabilistic circuit breaker (REQ-DATA-BATCH-002).
    * If early-stage failure rate exceeds threshold, halt with config error.
    */
  def circuitBreak(
      earlyErrors: Long,
      sampleSize: Long,
      threshold: Double = 0.05
  ): Boolean =
    sampleSize > 0 && (earlyErrors.toDouble / sampleSize) >= threshold
