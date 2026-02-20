// Implements: REQ-F-ACC-001, REQ-F-ACC-002, REQ-F-ACC-003, REQ-F-ACC-004
package cdme.runtime

import java.net.URI

/**
 * Verification status for an accounting ledger.
 */
sealed trait VerificationStatus
object VerificationStatus {
  /** Ledger has been verified: invariant holds. */
  case object Verified extends VerificationStatus
  /** Ledger has failed verification: invariant violated. */
  final case class Failed(reason: String) extends VerificationStatus
  /** Verification has not yet been performed. */
  case object Pending extends VerificationStatus
}

/**
 * Result of a circuit breaker evaluation.
 */
sealed trait CircuitBreakerResult
object CircuitBreakerResult {
  /** Circuit breaker passed; execution may proceed. */
  final case class Passed(sampleSize: Int, errorRate: Double) extends CircuitBreakerResult
  /** Circuit breaker tripped; execution aborted. */
  final case class Tripped(sampleSize: Int, errorRate: Double, threshold: Double) extends CircuitBreakerResult
}

/**
 * Record accounting ledger for a single execution run.
 *
 * Enforces the fundamental accounting invariant:
 *   |input| = |processed| + |filtered| + |errored|
 *
 * The ledger is written atomically (write-then-rename pattern) at run
 * completion. Verification runs automatically before COMPLETE status.
 *
 * @param runId                     unique run identifier
 * @param inputCount                total input record count
 * @param sourceKeyField            the key field used for record identification
 * @param processedCount            records successfully processed
 * @param filteredCount             records filtered out
 * @param erroredCount              records that produced errors
 * @param verificationStatus        current verification status
 * @param adjointMetadataLocations  URIs of persisted adjoint metadata
 * @param circuitBreakerResult      result of circuit breaker pre-phase, if run
 */
final case class AccountingLedger(
    runId: String,
    inputCount: Long,
    sourceKeyField: String,
    processedCount: Long,
    filteredCount: Long,
    erroredCount: Long,
    verificationStatus: VerificationStatus = VerificationStatus.Pending,
    adjointMetadataLocations: Map[String, URI] = Map.empty,
    circuitBreakerResult: Option[CircuitBreakerResult] = None
) {
  /**
   * Check the accounting invariant.
   *
   * @return true if |input| = |processed| + |filtered| + |errored|
   */
  def invariantHolds: Boolean =
    inputCount == processedCount + filteredCount + erroredCount

  /**
   * The total accounted records (processed + filtered + errored).
   */
  def totalAccounted: Long =
    processedCount + filteredCount + erroredCount

  /**
   * The discrepancy (if any) between input and accounted records.
   */
  def discrepancy: Long =
    inputCount - totalAccounted
}

object AccountingLedger {
  /**
   * Create a new ledger for a run with known input count.
   *
   * @param runId         run identifier
   * @param inputCount    total input records
   * @param sourceKeyField key field name
   * @return a new ledger with all counts at zero
   */
  def create(runId: String, inputCount: Long, sourceKeyField: String): AccountingLedger =
    AccountingLedger(
      runId = runId,
      inputCount = inputCount,
      sourceKeyField = sourceKeyField,
      processedCount = 0L,
      filteredCount = 0L,
      erroredCount = 0L
    )
}
