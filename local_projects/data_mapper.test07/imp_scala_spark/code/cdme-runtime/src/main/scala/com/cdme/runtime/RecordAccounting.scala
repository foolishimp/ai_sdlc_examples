// Implements: REQ-ACC-01, REQ-ACC-02, REQ-ACC-04
// Record accounting: enforces |input| = |processed| + |filtered| + |errored|.
package com.cdme.runtime

import com.cdme.model._
import com.cdme.model.error.{AccountingInvariantError, CdmeError}

/**
 * Accounting ledger: records the fate of every input record.
 * The fundamental invariant: |input_keys| = |reverse_join_keys| + |filtered_keys| + |error_keys|
 *
 * Implements: REQ-ACC-01, REQ-ACC-02
 */
final case class AccountingLedger(
    runId: RunId,
    inputRecordCount: Long,
    sourceKeyField: String,
    processedCount: Long,
    filteredCount: Long,
    erroredCount: Long,
    reverseJoinMetadataLocation: String,
    filteredKeysMetadataLocation: String,
    errorDatasetLocation: String,
    balanced: Boolean,
    discrepancy: Option[DiscrepancyDetails]
)

/** Details about an accounting discrepancy. */
final case class DiscrepancyDetails(
    expected: Long,
    actual: Long,
    difference: Long,
    message: String
)

/**
 * Verifies the accounting invariant.
 * Implements: REQ-ACC-04
 */
object RecordAccounting {

  /**
   * Build an accounting ledger and verify the invariant.
   */
  def buildLedger(
      runId: RunId,
      sourceKeyField: String,
      inputCount: Long,
      processedCount: Long,
      filteredCount: Long,
      erroredCount: Long,
      reverseJoinLocation: String,
      filteredKeysLocation: String,
      errorLocation: String
  ): AccountingLedger = {
    val total = processedCount + filteredCount + erroredCount
    val balanced = total == inputCount
    val discrepancy = if (balanced) None
    else Some(DiscrepancyDetails(
      expected = inputCount,
      actual = total,
      difference = inputCount - total,
      message = s"Accounting mismatch: input=$inputCount, total=$total (processed=$processedCount + filtered=$filteredCount + errored=$erroredCount)"
    ))

    AccountingLedger(
      runId = runId,
      inputRecordCount = inputCount,
      sourceKeyField = sourceKeyField,
      processedCount = processedCount,
      filteredCount = filteredCount,
      erroredCount = erroredCount,
      reverseJoinMetadataLocation = reverseJoinLocation,
      filteredKeysMetadataLocation = filteredKeysLocation,
      errorDatasetLocation = errorLocation,
      balanced = balanced,
      discrepancy = discrepancy
    )
  }

  /**
   * Verify the accounting invariant: |input| = |processed| + |filtered| + |errored|.
   * Returns the ledger if balanced, or an error if not.
   */
  def verifyInvariant(ledger: AccountingLedger): Either[CdmeError, AccountingLedger] = {
    if (ledger.balanced) Right(ledger)
    else Left(AccountingInvariantError(
      inputCount = ledger.inputRecordCount,
      processedCount = ledger.processedCount,
      filteredCount = ledger.filteredCount,
      erroredCount = ledger.erroredCount
    ))
  }
}
