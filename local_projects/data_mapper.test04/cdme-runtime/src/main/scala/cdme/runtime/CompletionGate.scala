// Implements: REQ-F-ACC-001, REQ-F-ACC-002, REQ-F-ACC-003
package cdme.runtime

/**
 * A verified ledger (one that has passed the completion gate).
 *
 * @param ledger the verified accounting ledger
 */
final case class VerifiedLedger(ledger: AccountingLedger)

/**
 * Verification failure when the accounting invariant does not hold.
 *
 * @param message description of the failure
 * @param ledger  the ledger that failed verification
 */
final case class VerificationFailure(
    message: String,
    ledger: AccountingLedger
)

/**
 * Completion gate: gates run completion on accounting verification.
 *
 * Verification runs automatically before COMPLETE status; no manual override
 * is available. The gate checks:
 *   1. The accounting invariant: |input| = |processed| + |filtered| + |errored|
 *   2. No records are unaccounted for
 */
object CompletionGate {

  /**
   * Verify an accounting ledger.
   *
   * @param ledger the ledger to verify
   * @return Right(VerifiedLedger) if the invariant holds,
   *         Left(VerificationFailure) if it does not
   */
  def verify(ledger: AccountingLedger): Either[VerificationFailure, VerifiedLedger] =
    if (ledger.invariantHolds) {
      Right(VerifiedLedger(
        ledger.copy(verificationStatus = VerificationStatus.Verified)
      ))
    } else {
      Left(VerificationFailure(
        message = s"Accounting invariant violated: input=${ledger.inputCount}, " +
          s"processed=${ledger.processedCount}, filtered=${ledger.filteredCount}, " +
          s"errored=${ledger.erroredCount}, discrepancy=${ledger.discrepancy}",
        ledger = ledger.copy(
          verificationStatus = VerificationStatus.Failed(
            s"Discrepancy of ${ledger.discrepancy} records"
          )
        )
      ))
    }
}
