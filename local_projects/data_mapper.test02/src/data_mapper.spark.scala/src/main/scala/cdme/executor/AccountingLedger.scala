package cdme.executor

import io.circe._
import io.circe.generic.semiauto._
import java.nio.file.{Files, Paths}

/**
 * AccountingLedger - Zero-loss verification system for data transformations
 *
 * Implements the Fundamental Accounting Invariant from ADR-012:
 *   INPUT_RECORDS = OUTPUT_RECORDS + FILTERED_RECORDS + ERROR_RECORDS + AGGREGATED_SOURCES
 *
 * This component provides mathematical proof that no records are lost during transformation
 * by tracking all input keys and verifying they appear in exactly one output partition.
 *
 * Key Features:
 * - Completeness: All input keys must appear in some partition
 * - Uniqueness: No key appears in multiple partitions (collision detection)
 * - Traceability: Each key's partition is recorded with adjoint location
 *
 * Implements: REQ-ACC-01, REQ-ACC-02, REQ-ACC-03, REQ-ACC-04
 * Satisfies: ADR-013 (AccountingLedger Component)
 *
 * @see docs/design/design_spark/adrs/ADR-013-accounting-ledger.md
 */

// ===== Case Classes =====

/**
 * Input dataset accounting information.
 *
 * Tracks the source dataset metadata including total records,
 * the field used as unique identifier, and optional hash for reproducibility.
 *
 * Implements: REQ-ACC-01 (Input Key Registration)
 *
 * @param totalRecords Total number of input records
 * @param sourceKeyField Field name used as unique identifier (e.g., "segment_id")
 * @param inputHash Optional cryptographic hash of input dataset for reproducibility
 */
case class InputAccounting(
  totalRecords: Long,
  sourceKeyField: String,
  inputHash: Option[String]
)

/**
 * Output partition information.
 *
 * Represents one partition of the output (e.g., OUTPUT, FILTERED, ERROR, AGGREGATED).
 * Each partition tracks its records, type, and location of adjoint metadata.
 *
 * Implements: REQ-ACC-02 (Partition Registration)
 *
 * @param partitionType Type of partition (OUTPUT, FILTERED, ERROR, AGGREGATED)
 * @param description Human-readable description of partition purpose
 * @param recordCount Number of records in this partition
 * @param adjointType Type of adjoint metadata (PassThrough, FilteredKeysMetadata, ErrorRecords, ReverseJoinMetadata)
 * @param adjointLocation Path to adjoint metadata for this partition
 */
case class OutputPartition(
  partitionType: String,
  description: String,
  recordCount: Long,
  adjointType: String,
  adjointLocation: String
)

/**
 * Accounting verification result.
 *
 * Contains the outcome of verification and proof method used.
 * If balanced=true, accounting invariant holds. If false, see AccountingDiscrepancy.
 *
 * Implements: REQ-ACC-03 (Accounting Verification)
 *
 * @param balanced True if accounting invariant holds (all keys accounted, no collisions)
 * @param proofMethod Method used for verification (e.g., "set_equality_check")
 * @param missingKeys Number of missing keys (if any), None if balanced
 * @param extraKeys Number of extra keys (if any), None if balanced
 */
case class AccountingVerification(
  balanced: Boolean,
  proofMethod: String,
  missingKeys: Option[Long],
  extraKeys: Option[Long]
)

/**
 * Complete accounting ledger.
 *
 * The final ledger providing cryptographic proof of zero-loss transformation.
 * Written to ledger.json and serves as immutable audit trail.
 *
 * Implements: REQ-ACC-04 (Ledger Structure & Persistence)
 *
 * @param ledgerVersion Version of ledger schema (currently "1.0")
 * @param runId Unique identifier for this transformation run
 * @param inputDataset Path to input dataset
 * @param inputAccounting Input accounting metadata
 * @param partitions List of output partitions with their accounting info
 * @param verification Verification result proving accounting invariant
 */
case class AccountingLedger(
  ledgerVersion: String,
  runId: String,
  inputDataset: String,
  inputAccounting: InputAccounting,
  partitions: List[OutputPartition],
  verification: AccountingVerification
) {
  /**
   * Write ledger to file as JSON.
   *
   * Serializes the ledger to JSON and writes to specified path.
   * Creates parent directories if they don't exist.
   *
   * Implements: REQ-ACC-04 (Ledger Persistence)
   *
   * @param path File path for ledger.json
   */
  def writeLedger(path: String): Unit = {
    val json = AccountingLedger.toJson(this)
    val filePath = Paths.get(path)

    // Ensure parent directory exists
    Option(filePath.getParent).foreach(Files.createDirectories(_))

    Files.write(filePath, json.getBytes)
  }
}

/**
 * Accounting discrepancy (when invariant fails).
 *
 * Contains detailed information about why the accounting invariant failed:
 * - missingKeys: Keys in input but not in any partition (silent drops)
 * - extraKeys: Keys in partitions but not in input (unexpected records)
 * - collisions: Keys appearing in multiple partitions
 *
 * Implements: REQ-ACC-03 (Discrepancy Detection)
 *
 * @param missingKeys Set of keys from input not accounted in any partition
 * @param extraKeys Set of keys in partitions that weren't in input
 * @param collisions Map of key → list of partitions it appears in (for duplicates)
 */
case class AccountingDiscrepancy(
  missingKeys: Set[String],
  extraKeys: Set[String],
  collisions: Map[String, List[String]]
)

// ===== JSON Codecs =====

object AccountingLedger {
  // Circe automatic codec derivation for JSON serialization
  implicit val inputAccountingEncoder: Encoder[InputAccounting] = deriveEncoder[InputAccounting]
  implicit val outputPartitionEncoder: Encoder[OutputPartition] = deriveEncoder[OutputPartition]
  implicit val verificationEncoder: Encoder[AccountingVerification] = deriveEncoder[AccountingVerification]
  implicit val ledgerEncoder: Encoder[AccountingLedger] = deriveEncoder[AccountingLedger]

  /**
   * Serialize ledger to JSON string.
   *
   * Uses circe for automatic JSON encoding with pretty printing.
   *
   * Implements: REQ-ACC-04 (JSON Serialization)
   *
   * @param ledger The ledger to serialize
   * @return JSON string representation
   */
  def toJson(ledger: AccountingLedger): String = {
    import io.circe.syntax._
    ledger.asJson.spaces2
  }
}

// ===== Builder =====

/**
 * Builder for AccountingLedger with verification logic.
 *
 * This builder collects input keys and partition information, then verifies
 * the accounting invariant before producing the final ledger.
 *
 * Workflow:
 * 1. Create builder with run metadata
 * 2. Set input keys from source dataset
 * 3. Add partitions as they are created during transformation
 * 4. Call verify() to check accounting invariant
 * 5. On success: receive ledger, write to disk
 * 6. On failure: receive discrepancy, investigate root cause
 *
 * Usage Example:
 * {{{
 *   val builder = new AccountingLedgerBuilder(runId, inputDataset, sourceKeyField)
 *   builder.setInputKeys(inputKeys)
 *   builder.addPartition("OUTPUT", "Output records", outputKeys, "adjoint/output")
 *   builder.addPartition("ERROR", "Error records", errorKeys, "adjoint/errors")
 *
 *   builder.verify() match {
 *     case Right(ledger) =>
 *       ledger.writeLedger("output/ledger.json")
 *       println(s"✓ Accounting verified: all ${ledger.inputAccounting.totalRecords} records accounted")
 *
 *     case Left(discrepancy) =>
 *       println(s"✗ Accounting failed: ${discrepancy.missingKeys.size} missing, ${discrepancy.collisions.size} collisions")
 *       // Investigate discrepancy...
 *   }
 * }}}
 *
 * Implements: REQ-ACC-01, REQ-ACC-02, REQ-ACC-03, REQ-ACC-04
 */
class AccountingLedgerBuilder(
  runId: String,
  inputDataset: String,
  sourceKeyField: String
) {

  private var inputKeys: Set[String] = Set.empty
  private val partitions = scala.collection.mutable.ListBuffer[(String, String, Set[String], String)]()

  /**
   * Set input keys from source dataset.
   *
   * Registers all unique keys from the input dataset. These will be used
   * during verification to ensure completeness (all keys accounted).
   *
   * Implements: REQ-ACC-01 (Input Key Registration)
   *
   * @param keys Set of unique keys from input dataset
   */
  def setInputKeys(keys: Set[String]): Unit = {
    inputKeys = keys
  }

  /**
   * Add a partition with its keys.
   *
   * Registers an output partition created during transformation.
   * The keys represent which input records went into this partition.
   *
   * Implements: REQ-ACC-02 (Partition Registration)
   *
   * @param partitionType Type of partition (OUTPUT, FILTERED, ERROR, AGGREGATED)
   * @param description Human-readable description of partition purpose
   * @param keys Set of record keys in this partition
   * @param adjointLocation Path to adjoint metadata for backward traversal
   */
  def addPartition(
    partitionType: String,
    description: String,
    keys: Set[String],
    adjointLocation: String
  ): Unit = {
    partitions += ((partitionType, description, keys, adjointLocation))
  }

  /**
   * Convenience overload for addPartition (without explicit description).
   *
   * Automatically generates description from partition type and key count.
   *
   * @param partitionType Type of partition
   * @param keys Set of record keys in this partition
   * @param adjointLocation Path to adjoint metadata
   */
  def addPartition(
    partitionType: String,
    keys: Set[String],
    adjointLocation: String
  ): Unit = {
    val description = s"$partitionType partition with ${keys.size} records"
    addPartition(partitionType, description, keys, adjointLocation)
  }

  /**
   * Verify the accounting invariant.
   *
   * Performs set-based verification that:
   * 1. Completeness: All input keys appear in some partition
   * 2. Uniqueness: No key appears in multiple partitions (collision detection)
   * 3. No extras: No keys in partitions that weren't in input
   *
   * Algorithm:
   * - Build map of key → partitions it appears in
   * - Detect collisions (key appears in multiple partitions)
   * - Detect missing keys (in input but not in any partition)
   * - Detect extra keys (in partitions but not in input)
   * - Return Right(ledger) if balanced, Left(discrepancy) otherwise
   *
   * Implements: REQ-ACC-03 (Accounting Verification)
   *
   * @return Right(ledger) if accounting invariant holds, Left(discrepancy) if violated
   */
  def verify(): Either[AccountingDiscrepancy, AccountingLedger] = {
    // 1. Build map of key → partitions it appears in
    val keyToPartitions = scala.collection.mutable.Map[String, List[String]]()

    partitions.foreach { case (partitionType, _, keys, _) =>
      keys.foreach { key =>
        val existing = keyToPartitions.getOrElse(key, List.empty)
        keyToPartitions(key) = partitionType :: existing
      }
    }

    // 2. Extract all accounted keys
    val allAccountedKeys = keyToPartitions.keySet.toSet

    // 3. Detect discrepancies
    val missingKeys = inputKeys -- allAccountedKeys
    val extraKeys = allAccountedKeys -- inputKeys
    val collisions = keyToPartitions.filter(_._2.size > 1).map {
      case (key, partitionTypes) => (key, partitionTypes.reverse)
    }.toMap

    // 4. Check if balanced
    val balanced = missingKeys.isEmpty && extraKeys.isEmpty && collisions.isEmpty

    if (balanced) {
      // Success - build ledger
      val outputPartitions = partitions.map { case (pType, desc, keys, adjointLoc) =>
        OutputPartition(
          partitionType = pType,
          description = desc,
          recordCount = keys.size,
          adjointType = inferAdjointType(pType),
          adjointLocation = adjointLoc
        )
      }.toList

      val ledger = AccountingLedger(
        ledgerVersion = "1.0",
        runId = runId,
        inputDataset = inputDataset,
        inputAccounting = InputAccounting(
          totalRecords = inputKeys.size,
          sourceKeyField = sourceKeyField,
          inputHash = None // Could be enhanced to compute actual hash
        ),
        partitions = outputPartitions,
        verification = AccountingVerification(
          balanced = true,
          proofMethod = "set_equality_check",
          missingKeys = None,
          extraKeys = None
        )
      )

      Right(ledger)
    } else {
      // Failure - return discrepancy with details
      Left(AccountingDiscrepancy(
        missingKeys = missingKeys.toSet,
        extraKeys = extraKeys.toSet,
        collisions = collisions
      ))
    }
  }

  /**
   * Infer adjoint type from partition type.
   *
   * Maps partition type to the expected adjoint metadata type:
   * - OUTPUT → PassThrough (1:1 transformation)
   * - FILTERED → FilteredKeysMetadata (excluded keys)
   * - ERROR → ErrorRecords (DLQ records)
   * - AGGREGATED → ReverseJoinMetadata (N:1 mapping)
   *
   * @param partitionType The partition type string
   * @return The corresponding adjoint metadata type
   */
  private def inferAdjointType(partitionType: String): String = {
    partitionType match {
      case "OUTPUT" => "PassThrough"
      case "FILTERED" => "FilteredKeysMetadata"
      case "ERROR" => "ErrorRecords"
      case "AGGREGATED" => "ReverseJoinMetadata"
      case _ => "Unknown"
    }
  }
}
