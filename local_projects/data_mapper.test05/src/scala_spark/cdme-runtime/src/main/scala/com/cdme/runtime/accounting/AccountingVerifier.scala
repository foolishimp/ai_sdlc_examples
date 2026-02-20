package com.cdme.runtime.accounting

// Implements: REQ-F-ACC-001, REQ-F-ACC-004

/**
 * Result of the zero-loss accounting verification.
 *
 * The accounting equation is:
 * {{{
 *   inputCount == processedCount + filteredCount + errorCount
 * }}}
 *
 * Additionally, the three output partitions must be mutually exclusive:
 * no key may appear in more than one partition.
 *
 * @param inputCount     total records in the input dataset
 * @param processedCount records in the success output partition
 * @param filteredCount  records in the filtered output partition
 * @param errorCount     records in the error output partition
 * @param balanced       true if the accounting equation holds and partitions
 *                       are mutually exclusive and complete
 * @param discrepancies  human-readable descriptions of any violations found
 */
case class AccountingResult(
  inputCount: Long,
  processedCount: Long,
  filteredCount: Long,
  errorCount: Long,
  balanced: Boolean,
  discrepancies: List[String]
)

/**
 * Verifies the zero-loss accounting invariant: every input record must appear
 * in exactly one output partition (processed, filtered, or errored).
 *
 * Verification checks three properties:
 *  1. '''Mutual exclusivity''': No key appears in more than one partition
 *  2. '''Completeness''': Every input key appears in exactly one partition
 *  3. '''Equation balance''': `|input| == |processed| + |filtered| + |errored|`
 *
 * The pipeline runner gates run completion on accounting verification pass
 * (REQ-F-ACC-004): a run cannot be marked successful if accounting fails.
 */
object AccountingVerifier {

  /**
   * Verify the zero-loss accounting invariant using key sets.
   *
   * @param inputKeys     the complete set of input keys
   * @param processedKeys keys in the success output partition
   * @param filteredKeys  keys in the filtered output partition
   * @param errorKeys     keys in the error output partition
   * @return an [[AccountingResult]] describing the verification outcome
   */
  def verify(
    inputKeys: Set[Any],
    processedKeys: Set[Any],
    filteredKeys: Set[Any],
    errorKeys: Set[Any]
  ): AccountingResult = {
    val discrepancies = List.newBuilder[String]

    // Check 1: Mutual exclusivity
    val processedAndFiltered = processedKeys.intersect(filteredKeys)
    if (processedAndFiltered.nonEmpty) {
      discrepancies += s"Keys appear in both processed and filtered partitions: ${processedAndFiltered.size} keys (first: ${processedAndFiltered.head})"
    }

    val processedAndError = processedKeys.intersect(errorKeys)
    if (processedAndError.nonEmpty) {
      discrepancies += s"Keys appear in both processed and error partitions: ${processedAndError.size} keys (first: ${processedAndError.head})"
    }

    val filteredAndError = filteredKeys.intersect(errorKeys)
    if (filteredAndError.nonEmpty) {
      discrepancies += s"Keys appear in both filtered and error partitions: ${filteredAndError.size} keys (first: ${filteredAndError.head})"
    }

    // Check 2: Completeness — every input key must appear in exactly one partition
    val allOutputKeys = processedKeys ++ filteredKeys ++ errorKeys
    val missingKeys = inputKeys -- allOutputKeys
    if (missingKeys.nonEmpty) {
      discrepancies += s"Input keys missing from all output partitions: ${missingKeys.size} keys (first: ${missingKeys.head})"
    }

    val extraKeys = allOutputKeys -- inputKeys
    if (extraKeys.nonEmpty) {
      discrepancies += s"Output keys not present in input: ${extraKeys.size} keys (first: ${extraKeys.head})"
    }

    // Check 3: Equation balance
    val inputCount = inputKeys.size.toLong
    val processedCount = processedKeys.size.toLong
    val filteredCount = filteredKeys.size.toLong
    val errorCount = errorKeys.size.toLong
    val outputTotal = processedCount + filteredCount + errorCount

    if (inputCount != outputTotal) {
      discrepancies += s"Accounting equation imbalanced: input=$inputCount != processed=$processedCount + filtered=$filteredCount + errored=$errorCount (= $outputTotal)"
    }

    val issues = discrepancies.result()

    AccountingResult(
      inputCount = inputCount,
      processedCount = processedCount,
      filteredCount = filteredCount,
      errorCount = errorCount,
      balanced = issues.isEmpty,
      discrepancies = issues
    )
  }
}
