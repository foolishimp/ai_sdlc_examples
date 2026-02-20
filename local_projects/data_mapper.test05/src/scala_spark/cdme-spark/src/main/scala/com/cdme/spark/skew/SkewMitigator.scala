package com.cdme.spark.skew

// Implements: REQ-NFR-PERF-002

import org.apache.spark.sql.{Column, DataFrame, SparkSession}
import org.apache.spark.sql.functions._

/**
 * Mitigates data skew in Spark join operations via salted joins.
 *
 * When a join key has a highly skewed distribution (a few keys account for
 * a disproportionate share of records), standard hash-partitioned joins
 * create stragglers. The salted join technique redistributes hot keys
 * across multiple partitions by appending a random salt value.
 *
 * Algorithm:
 *  1. Add a random salt column `_cdme_salt` in `[0, saltFactor)` to the left side
 *  2. Explode the right side with all salt values `[0, saltFactor)` to create
 *     `saltFactor` copies of each right-side row
 *  3. Join on `(key, _cdme_salt)` instead of just `(key)`
 *  4. Drop the `_cdme_salt` column from the result
 *
 * This spreads hot-key work across `saltFactor` tasks at the cost of
 * replicating the smaller (right) side.
 */
object SkewMitigator {

  private val SaltColumnName = "_cdme_salt"

  /**
   * Perform a salted join to mitigate skew on the join key.
   *
   * @param left       the larger (potentially skewed) DataFrame
   * @param right      the smaller DataFrame to replicate
   * @param key        the join key column expression
   * @param saltFactor the number of salt buckets (higher = more parallelism,
   *                   but more replication of the right side)
   * @param spark      implicit SparkSession
   * @return the joined DataFrame with the salt column removed
   */
  def saltedJoin(
    left: DataFrame,
    right: DataFrame,
    key: Column,
    saltFactor: Int
  )(implicit spark: SparkSession): DataFrame = {
    require(saltFactor > 0, s"saltFactor must be positive, got $saltFactor")

    import spark.implicits._

    // Step 1: Add random salt to left side
    val saltedLeft = left.withColumn(SaltColumnName, (rand() * saltFactor).cast("int"))

    // Step 2: Explode right side with all salt values
    val saltValues = (0 until saltFactor).toList
    val saltDf = spark.createDataset(saltValues).toDF(SaltColumnName)
    val saltedRight = right.crossJoin(saltDf)

    // Step 3: Join on (key, salt)
    // We alias the DataFrames to avoid ambiguous column references
    val leftAlias = saltedLeft.alias("_left")
    val rightAlias = saltedRight.alias("_right")

    val joinCondition =
      col(s"_left.${SaltColumnName}") === col(s"_right.${SaltColumnName}") &&
        key

    val joined = leftAlias.join(rightAlias, joinCondition, "inner")

    // Step 4: Drop salt columns from both sides
    joined
      .drop(col(s"_left.${SaltColumnName}"))
      .drop(col(s"_right.${SaltColumnName}"))
  }
}
