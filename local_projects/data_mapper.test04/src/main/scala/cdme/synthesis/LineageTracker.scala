// Implements: REQ-F-INT-001, REQ-F-INT-005
package cdme.synthesis

import java.time.Instant

/**
 * A lineage record for a derived value.
 *
 * Records the source entity, epoch, and morphism path for every derived
 * value. Lineage is stored in a structured, machine-readable format (JSON).
 *
 * @param sourceEntity  the entity where the source data originated
 * @param sourceEpoch   the epoch context of the source data
 * @param morphismPath  the ordered list of morphism IDs used in derivation
 * @param timestamp     when the derivation was performed
 * @param outputKey     the key of the derived record
 */
final case class LineageRecord(
    sourceEntity: String,
    sourceEpoch: String,
    morphismPath: List[String],
    timestamp: Instant,
    outputKey: String
)

/**
 * Lineage tracker: records source-to-target traceability for derived values.
 *
 * Accumulates lineage records during execution and provides query capabilities
 * for tracing derived values back to their sources.
 */
trait LineageTracker:

  /**
   * Record a lineage entry for a derived value.
   *
   * @param record the lineage record
   */
  def record(record: LineageRecord): Unit

  /**
   * Query lineage for a specific output key.
   *
   * @param outputKey the key to trace
   * @return all lineage records for the given key
   */
  def queryByOutputKey(outputKey: String): List[LineageRecord]

  /**
   * Get all lineage records.
   */
  def allRecords: List[LineageRecord]

/**
 * In-memory lineage tracker implementation.
 */
class InMemoryLineageTracker extends LineageTracker:
  private val records: scala.collection.mutable.ListBuffer[LineageRecord] =
    scala.collection.mutable.ListBuffer.empty

  override def record(lineageRecord: LineageRecord): Unit =
    records += lineageRecord

  override def queryByOutputKey(outputKey: String): List[LineageRecord] =
    records.filter(_.outputKey == outputKey).toList

  override def allRecords: List[LineageRecord] =
    records.toList
