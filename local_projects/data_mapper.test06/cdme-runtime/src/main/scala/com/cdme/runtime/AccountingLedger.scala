package com.cdme.runtime

// Implements: REQ-ACC-01 through REQ-ACC-05
case class AccountingLedger(
  runId: String,
  inputCount: Long,
  processedCount: Long,
  filteredCount: Long,
  erroredCount: Long,
  morphismAccounting: Map[String, MorphismAccounting] = Map.empty,
  verified: Boolean = false
) {
  /** REQ-ACC-01: Accounting invariant must hold */
  def isBalanced: Boolean =
    inputCount == processedCount + filteredCount + erroredCount

  /** REQ-ACC-04: Run completion gate — verify before marking complete */
  def verify(): AccountingLedger = {
    require(isBalanced, s"Accounting invariant violated: $inputCount != $processedCount + $filteredCount + $erroredCount")
    copy(verified = true)
  }
}

case class MorphismAccounting(
  morphismName: String,
  inputCount: Long,
  outputCount: Long,
  errorCount: Long,
  filterCount: Long
)
