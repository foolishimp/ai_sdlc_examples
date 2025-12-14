package cdme.executor

/**
 * Adjoint wrappers for capturing reverse-join metadata during forward operations.
 *
 * The Adjoint system enables backward traversal through data transformations by
 * capturing metadata during forward operations. This is essential for:
 * - Data lineage and impact analysis
 * - Backward reconciliation
 * - Root cause analysis for data quality issues
 *
 * Key Concepts:
 * - Forward operations (e.g., groupBy, filter) produce output data
 * - Adjoint metadata captures the relationship between output and input
 * - Classification indicates how invertible the operation is
 *
 * Implements: REQ-ADJ-04, REQ-ADJ-05, REQ-ADJ-06, REQ-ADJ-11
 * Satisfies: ADR-005 (Adjoint Metadata Storage Strategy)
 *
 * @see docs/design/design_spark/adrs/ADR-005-adjoint-metadata.md
 */

/**
 * Classification of adjoint morphisms based on invertibility characteristics.
 *
 * This classification scheme determines how much information is preserved during
 * transformation and what metadata is needed for backward traversal:
 *
 * - ISOMORPHISM: Perfect 1:1 mapping, fully invertible without metadata
 * - EMBEDDING: N:1 mapping, can identify which inputs produced each output
 * - PROJECTION: 1:N mapping or aggregation, requires reverse-join metadata
 * - LOSSY: Information discarded, backward traversal not possible
 *
 * Implements: REQ-ADJ-04, REQ-ADJ-05, REQ-ADJ-06
 */
sealed trait AdjointClassification {
  override def toString: String = this match {
    case AdjointClassification.ISOMORPHISM => "ISOMORPHISM"
    case AdjointClassification.EMBEDDING => "EMBEDDING"
    case AdjointClassification.PROJECTION => "PROJECTION"
    case AdjointClassification.LOSSY => "LOSSY"
  }
}

object AdjointClassification {
  /**
   * 1:1 mappings (fully invertible).
   *
   * Characteristics:
   * - Bijective: Each input maps to exactly one output and vice versa
   * - No metadata needed for backward traversal
   * - Example: Column rename, type cast (when lossless)
   *
   * Use Case: Simple transformations where you can compute the inverse directly
   */
  case object ISOMORPHISM extends AdjointClassification

  /**
   * N:1 mappings (subset preservation).
   *
   * Characteristics:
   * - Multiple inputs can map to one output
   * - Backward: Given output, can identify all contributing inputs
   * - Metadata: Optional (filtered-out keys for complete recovery)
   *
   * Example: Filter operation
   * - Forward: Keep rows where status = 'ACTIVE'
   * - Backward: Given output row, know it came from input (preserved keys)
   * - Metadata: Optionally capture filtered-out keys
   */
  case object EMBEDDING extends AdjointClassification

  /**
   * 1:N mappings or many-to-one aggregations (requires reverse-join metadata).
   *
   * Characteristics:
   * - Forward: Aggregation (N→1) or explosion (1→N)
   * - Backward: Requires explicit metadata to reconstruct relationship
   * - Metadata: Reverse-join table (group_key → source_keys)
   *
   * Examples:
   * - GroupBy: Many orders → one customer total (requires reverse-join)
   * - Explode: One parent → many children (requires parent-child mapping)
   */
  case object PROJECTION extends AdjointClassification

  /**
   * Operations without backward metadata capture (information loss).
   *
   * Characteristics:
   * - Information is intentionally discarded
   * - No backward traversal possible
   * - Example: Filter without capturing filtered-out keys
   *
   * Use Case: When backward traversal is not needed and storage optimization
   * is more important than complete lineage
   */
  case object LOSSY extends AdjointClassification
}

/**
 * Metadata captured during forward operations to enable backward traversal.
 *
 * This sealed trait represents different types of metadata that can be captured
 * during data transformations to enable backward lineage tracking and impact analysis.
 *
 * Implements: REQ-ADJ-11 (Adjoint Metadata Storage)
 */
sealed trait AdjointMetadata

/**
 * No metadata captured (lossy operation).
 *
 * Used when backward traversal is not required and storage optimization
 * is prioritized over complete lineage.
 */
case object NoMetadata extends AdjointMetadata

/**
 * Reverse-join metadata for aggregations (group_key → source_keys).
 *
 * Captures the mapping from aggregated output rows back to their source rows.
 * Essential for:
 * - Impact analysis: "Which source rows contributed to this aggregated value?"
 * - Reconciliation: "Show me all orders that make up customer C1's total"
 * - Debugging: "Why is this customer total incorrect?"
 *
 * Structure (MVP - simplified):
 * - Key: Group key (e.g., "customer_C1")
 * - Value: List of source row IDs that mapped to this group
 *
 * Future enhancement: Use DataFrame or Roaring Bitmaps for efficiency
 * (see ADR-005 for storage strategy options)
 *
 * Implements: REQ-ADJ-04 (Backward for aggregations)
 *
 * @param reverseJoinData Map from group key to source row IDs
 */
case class ReverseJoinMetadata(reverseJoinData: Map[String, Any]) extends AdjointMetadata

/**
 * Filtered-out keys for filter operations.
 *
 * Captures the row IDs that were filtered out (failed the predicate).
 * Enables:
 * - Complete backward recovery: "Show me ALL input rows (kept + filtered)"
 * - Impact analysis: "Which rows would be affected if I change the filter?"
 * - Debugging: "Why was this order excluded from the report?"
 *
 * Note: This is optional. Filter can operate in EMBEDDING mode (capture metadata)
 * or LOSSY mode (discard filtered rows without tracking).
 *
 * Implements: REQ-ADJ-05 (Backward for filters)
 *
 * @param filteredKeys List of row IDs that failed the filter predicate
 */
case class FilteredKeysMetadata(filteredKeys: List[Long]) extends AdjointMetadata

/**
 * Parent-child mapping for explode (1:N) operations.
 *
 * Captures the relationship between parent rows and their exploded children.
 * Essential for:
 * - Backward aggregation: "Which parent did this child come from?"
 * - Impact analysis: "If parent P changes, which children are affected?"
 * - Reconciliation: "Aggregate children back to parent level"
 *
 * Example:
 * Input: Order with items = [Item1, Item2]
 * Output: Two rows (one per item)
 * Metadata: child_1 → parent_order_id, child_2 → parent_order_id
 *
 * Implements: REQ-ADJ-06 (Backward for Kleisli/1:N)
 *
 * @param parentChildMapping Map from child row ID to parent row ID
 */
case class ParentChildMetadata(parentChildMapping: Map[Long, Long]) extends AdjointMetadata

/**
 * Result wrapper containing output data, adjoint metadata, and classification.
 *
 * This is the core return type for all adjoint-aware operations. It bundles:
 * - The transformed output data (forward result)
 * - Metadata enabling backward traversal
 * - Classification indicating invertibility characteristics
 *
 * Type Parameter:
 * @tparam T The type of output data (e.g., DataFrame, List[Map], Dataset[T])
 *
 * Fields:
 * @param output The forward transformation result
 * @param metadata Captured adjoint metadata (NoMetadata if lossy)
 * @param classification How invertible this operation is
 *
 * Implements: REQ-ADJ-11 (Adjoint Result Structure)
 *
 * Example:
 * {{{
 *   val result = SparkAdjointWrapper.groupByWithAdjoint(df, List("customer_id"))
 *   result.output // Aggregated DataFrame
 *   result.metadata // ReverseJoinMetadata with group → source mapping
 *   result.classification // PROJECTION (requires reverse-join for backward)
 * }}}
 */
case class AdjointResult[T](
  output: T,
  metadata: AdjointMetadata,
  classification: AdjointClassification
)

/**
 * Spark adjoint wrapper for capturing backward metadata during forward operations.
 *
 * This object provides adjoint-aware wrappers for common Spark operations:
 * - groupByWithAdjoint: Aggregations with reverse-join capture
 * - filterWithAdjoint: Filters with optional filtered-out key tracking
 * - explodeWithAdjoint: Array explosion with parent-child mapping
 *
 * Design Philosophy:
 * - Forward operations produce output data as normal
 * - Adjoint metadata is captured in parallel
 * - Classification indicates invertibility level
 * - Caller decides whether to persist metadata (based on lineage mode)
 *
 * MVP Implementation Note:
 * This is a simplified implementation using List[Map[String, Any]] for testing
 * without Spark/Java incompatibility issues. Production version would use
 * DataFrame operations with Delta/Iceberg storage for metadata.
 *
 * Future Enhancements:
 * - DataFrame-based implementations (requires compatible Spark/Java)
 * - Roaring Bitmap compression for high-cardinality groups (see ADR-005)
 * - Configurable retention policies per lineage mode
 * - Sampling for SAMPLED lineage mode
 *
 * Implements: REQ-ADJ-04, REQ-ADJ-05, REQ-ADJ-06
 * Satisfies: ADR-005 (Adjoint Metadata Storage Strategy)
 *
 * @see docs/design/design_spark/SPARK_IMPLEMENTATION_DESIGN.md Section: Adjoint Wrappers
 */
object SparkAdjointWrapper {

  /**
   * Wrap groupBy aggregation to capture reverse-join metadata.
   *
   * This method performs a standard groupBy aggregation while simultaneously
   * capturing the reverse-join relationship: which source rows contributed
   * to each aggregated output row.
   *
   * Use Case Example:
   * Input: 1000 orders across 10 customers
   * Output: 10 customer summary rows
   * Metadata: For each customer, list of order IDs that contributed
   *
   * Forward Operation:
   * - Group rows by specified columns (e.g., customer_id)
   * - Aggregate within each group (e.g., SUM, COUNT)
   *
   * Backward Metadata:
   * - Reverse-join table: group_key → List[source_row_ids]
   * - Enables: "Show me all orders for customer C1" (backward traversal)
   *
   * Classification: PROJECTION
   * - Many source rows → one output row per group
   * - Requires reverse-join metadata for backward traversal
   *
   * Implements: REQ-ADJ-04 (Backward for aggregations)
   *
   * @param inputData Input data as list of maps (MVP: simplified structure)
   * @param groupCols Columns to group by (e.g., List("customer_id", "order_date"))
   * @return AdjointResult with aggregated output, reverse-join metadata, PROJECTION classification
   *
   * Example:
   * {{{
   *   val orders = List(
   *     Map("customer_id" -> "C1", "amount" -> 100.0, "_row_id" -> 1L),
   *     Map("customer_id" -> "C1", "amount" -> 200.0, "_row_id" -> 2L),
   *     Map("customer_id" -> "C2", "amount" -> 300.0, "_row_id" -> 3L)
   *   )
   *
   *   val result = groupByWithAdjoint(orders, List("customer_id"))
   *   // result.output: aggregated rows (one per customer)
   *   // result.metadata: C1 → [1L, 2L], C2 → [3L]
   *   // result.classification: PROJECTION
   * }}}
   */
  def groupByWithAdjoint(
    inputData: List[Map[String, Any]],
    groupCols: List[String]
  ): AdjointResult[List[Map[String, Any]]] = {

    // Forward: group by extracting group keys
    val grouped = inputData.groupBy { row =>
      groupCols.map(col => row.getOrElse(col, "")).mkString("_")
    }

    // Backward: Capture reverse-join metadata
    // For each group, track which source rows contributed
    val reverseJoinData = grouped.map { case (groupKey, rows) =>
      groupKey -> rows.flatMap(_.get("_row_id"))
    }.toMap

    // Forward: Simplified aggregation (count per group)
    // Production would use actual Spark aggregation functions
    val aggregated = grouped.map { case (groupKey, rows) =>
      val firstRow = rows.head
      groupCols.map(col => col -> firstRow.getOrElse(col, "")).toMap +
        ("count" -> rows.size)
    }.toList

    AdjointResult(
      output = aggregated,
      metadata = ReverseJoinMetadata(reverseJoinData.asInstanceOf[Map[String, Any]]),
      classification = AdjointClassification.PROJECTION
    )
  }

  /**
   * Wrap filter operation to optionally capture filtered-out keys.
   *
   * This method performs standard filtering while optionally capturing metadata
   * about which rows were filtered out. This enables complete backward recovery
   * and impact analysis.
   *
   * Use Case Example:
   * Input: 1000 orders (mix of ACTIVE, COMPLETED, CANCELLED)
   * Filter: status = 'ACTIVE'
   * Output: 300 ACTIVE orders
   * Metadata (if captured): 700 row IDs that were filtered out
   *
   * Forward Operation:
   * - Apply predicate to each row
   * - Keep rows where predicate returns true
   *
   * Backward Metadata (configurable):
   * - If captureFiltered = true: Track filtered-out row IDs
   * - If captureFiltered = false: No metadata (LOSSY mode)
   *
   * Classification:
   * - EMBEDDING (if capture enabled): Can recover all input rows
   * - LOSSY (if capture disabled): Cannot recover filtered-out rows
   *
   * When to use each mode:
   * - EMBEDDING: Regulatory/audit scenarios needing full lineage
   * - LOSSY: Performance-critical pipelines where backward not needed
   *
   * Implements: REQ-ADJ-05 (Backward for filters)
   *
   * @param inputData Input data as list of maps
   * @param predicate Filter predicate function (row => Boolean)
   * @param captureFiltered Whether to capture filtered-out keys (default: true)
   * @return AdjointResult with filtered output, optional metadata, classification
   *
   * Example:
   * {{{
   *   val orders = List(
   *     Map("status" -> "ACTIVE", "_row_id" -> 1L),
   *     Map("status" -> "INACTIVE", "_row_id" -> 2L)
   *   )
   *
   *   // With metadata capture
   *   val result1 = filterWithAdjoint(orders, r => r("status") == "ACTIVE", true)
   *   // result1.metadata: FilteredKeysMetadata([2L])
   *   // result1.classification: EMBEDDING
   *
   *   // Without metadata capture (performance mode)
   *   val result2 = filterWithAdjoint(orders, r => r("status") == "ACTIVE", false)
   *   // result2.metadata: NoMetadata
   *   // result2.classification: LOSSY
   * }}}
   */
  def filterWithAdjoint(
    inputData: List[Map[String, Any]],
    predicate: Map[String, Any] => Boolean,
    captureFiltered: Boolean = true
  ): AdjointResult[List[Map[String, Any]]] = {

    // Forward: Standard filter operation
    val passed = inputData.filter(predicate)

    // Backward: Optionally capture filtered-out keys
    val metadata = if (captureFiltered) {
      val filteredOut = inputData.filterNot(predicate)
      val filteredKeys = filteredOut.flatMap(_.get("_row_id").map {
        case l: Long => l
        case i: Int => i.toLong
        case _ => 0L
      })
      FilteredKeysMetadata(filteredKeys)
    } else {
      NoMetadata
    }

    AdjointResult(
      output = passed,
      metadata = metadata,
      classification = if (captureFiltered) AdjointClassification.EMBEDDING else AdjointClassification.LOSSY
    )
  }

  /**
   * Wrap explode (1:N expansion) to capture parent-child mapping.
   *
   * This method performs array explosion (1 row → N rows) while capturing
   * the parent-child relationship. This enables backward aggregation and
   * impact analysis.
   *
   * Use Case Example:
   * Input: 100 orders, each with 1-10 line items
   * Output: 500 line item rows
   * Metadata: Each child row knows its parent order
   *
   * Forward Operation:
   * - For each parent row with array field
   * - Create one child row per array element
   * - Assign unique child_key to each child
   *
   * Backward Metadata:
   * - Parent-child mapping: child_key → parent_key
   * - Enables: "Which parent did this child come from?"
   * - Enables: "Aggregate children back to parent level"
   *
   * Classification: PROJECTION
   * - One parent → many children
   * - Requires metadata for backward aggregation
   *
   * Implements: REQ-ADJ-06 (Backward for Kleisli/1:N)
   *
   * @param inputData Input data as list of maps
   * @param arrayField Field containing array to explode (e.g., "line_items")
   * @return AdjointResult with exploded output, parent-child metadata, PROJECTION classification
   *
   * Example:
   * {{{
   *   val orders = List(
   *     Map("order_id" -> "O1", "items" -> List("A", "B"), "_row_id" -> 1L),
   *     Map("order_id" -> "O2", "items" -> List("C"), "_row_id" -> 2L)
   *   )
   *
   *   val result = explodeWithAdjoint(orders, "items")
   *   // result.output:
   *   //   [Map("parent_key" -> 1L, "child" -> "A", "child_key" -> 1L),
   *   //    Map("parent_key" -> 1L, "child" -> "B", "child_key" -> 2L),
   *   //    Map("parent_key" -> 2L, "child" -> "C", "child_key" -> 3L)]
   *   // result.metadata: ParentChildMetadata(Map(1L -> 1L, 2L -> 1L, 3L -> 2L))
   *   // result.classification: PROJECTION
   * }}}
   */
  def explodeWithAdjoint(
    inputData: List[Map[String, Any]],
    arrayField: String
  ): AdjointResult[List[Map[String, Any]]] = {

    var childKey = 0L
    val exploded = scala.collection.mutable.ListBuffer[Map[String, Any]]()
    val parentChildMap = scala.collection.mutable.Map[Long, Long]()

    inputData.foreach { row =>
      val parentKey = row.getOrElse("_row_id", 0L).asInstanceOf[Long]
      val items = row.get(arrayField) match {
        case Some(list: List[_]) => list
        case _ => List()
      }

      // Create one child row per array element
      items.foreach { item =>
        childKey += 1
        exploded += Map("parent_key" -> parentKey, "child" -> item, "child_key" -> childKey)
        parentChildMap(childKey) = parentKey
      }
    }

    AdjointResult(
      output = exploded.toList,
      metadata = ParentChildMetadata(parentChildMap.toMap),
      classification = AdjointClassification.PROJECTION
    )
  }
}
