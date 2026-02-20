package cdme.lineage

// Implements: REQ-F-ACC-001, REQ-F-ACC-002, REQ-F-ACC-003, REQ-F-ACC-004, REQ-F-ACC-005, REQ-F-INT-003

/** Adjoint metadata capture for record accounting and backward traversal.
  *
  * Every input record must be accounted for in exactly one output partition:
  * processed, filtered, errored, or aggregated (REQ-F-ACC-001).
  */
object AdjointMetadataCapture:

  /** Reverse-join metadata for aggregations (REQ-F-ACC-003). */
  case class ReverseJoinMetadata(
      aggregateKey: String,
      contributingSourceKeys: Set[String]
  )

  /** Filtered keys metadata (REQ-F-ACC-003). */
  case class FilteredKeysMetadata(
      excludedKeys: Set[String],
      filterCondition: String
  )

  /** Accounting ledger proving the invariant holds (REQ-F-ACC-002). */
  case class AccountingLedger(
      runId: String,
      inputCount: Long,
      sourceKeyField: String,
      processedCount: Long,
      processedMetadataLocation: String,
      filteredCount: Long,
      filteredMetadataLocation: String,
      erroredCount: Long,
      errorMetadataLocation: String,
      verificationStatus: VerificationStatus
  )

  enum VerificationStatus:
    case Balanced
    case Unbalanced(missingKeys: Set[String], extraKeys: Set[String])

  /** Verify the accounting invariant (REQ-F-ACC-001, REQ-F-ACC-004).
    *
    * |input_keys| = |reverse_join_keys| + |filtered_keys| + |error_keys|
    */
  def verifyInvariant(
      inputKeys: Set[String],
      processedKeys: Set[String],
      filteredKeys: Set[String],
      errorKeys: Set[String]
  ): VerificationStatus =
    val accountedKeys = processedKeys ++ filteredKeys ++ errorKeys
    val missing = inputKeys -- accountedKeys
    val extra = accountedKeys -- inputKeys
    if missing.isEmpty && extra.isEmpty then
      VerificationStatus.Balanced
    else
      VerificationStatus.Unbalanced(missing, extra)

  /** Backward traversal: given an output key, find source keys (REQ-F-ACC-005).
    * No recomputation required — metadata is sufficient.
    */
  def backwardTraverse(
      outputKey: String,
      reverseJoinMetadata: Map[String, ReverseJoinMetadata]
  ): Set[String] =
    reverseJoinMetadata.get(outputKey).map(_.contributingSourceKeys).getOrElse(Set.empty)
