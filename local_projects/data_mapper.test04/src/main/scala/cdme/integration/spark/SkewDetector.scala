// Implements: REQ-NFR-PERF-002
package cdme.integration.spark

/**
 * Whale key: a join key with disproportionately many records.
 *
 * @param key        the whale key value
 * @param count      the number of records with this key
 * @param medianCount the median key count for comparison
 */
final case class WhaleKey(
    key: String,
    count: Long,
    medianCount: Long
)

/**
 * Detects skewed (whale) join keys by sampling.
 *
 * Samples join keys to identify keys with disproportionate record counts
 * (> threshold * median). These keys are then handled by the SaltedJoinStrategy.
 *
 * TODO: Implement with Spark DataFrame sampling.
 */
object SkewDetector:

  /**
   * Detect whale keys in a dataset by sampling.
   *
   * @param keyColumn   the join key column name
   * @param sampleSize  number of records to sample
   * @param threshold   threshold multiplier (default: 100x median)
   * @return list of detected whale keys
   */
  def detect(
      keyColumn: String,
      sampleSize: Int = 10000,
      threshold: Long = 100L
  ): List[WhaleKey] =
    // TODO: Implement with Spark DataFrame sampling
    // 1. Sample sampleSize records
    // 2. Count records per key
    // 3. Compute median count
    // 4. Return keys with count > threshold * median
    Nil
