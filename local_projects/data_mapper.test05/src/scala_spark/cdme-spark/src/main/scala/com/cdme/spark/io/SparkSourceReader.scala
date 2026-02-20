package com.cdme.spark.io

// Implements: REQ-F-PDM-001

import com.cdme.model.config.Epoch
import com.cdme.model.pdm.{PdmBinding, StorageLocation, ParquetLocation, DeltaLocation, JdbcLocation, CsvLocation}
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.sql.functions.col

/**
 * Reads source data from physical storage into Spark DataFrames.
 *
 * The reader pattern-matches on the [[StorageLocation]] type within each
 * [[PdmBinding]] to select the appropriate Spark read strategy. Epoch
 * boundary filtering is applied after the initial read to scope data
 * to the processing window.
 */
object SparkSourceReader {

  /**
   * Read a dataset from the storage location specified in the binding,
   * scoped to the given epoch.
   *
   * @param binding the PDM binding mapping a logical entity to physical storage
   * @param epoch   the processing epoch defining the time window
   * @param spark   implicit SparkSession
   * @return a DataFrame containing the source data for the epoch
   */
  def read(
    binding: PdmBinding,
    epoch: Epoch
  )(implicit spark: SparkSession): DataFrame = {
    val raw = readFromLocation(binding.location)
    applyEpochFilter(raw, binding, epoch)
  }

  /**
   * Dispatch to the correct Spark reader based on [[StorageLocation]] type.
   */
  private def readFromLocation(
    location: StorageLocation
  )(implicit spark: SparkSession): DataFrame = location match {

    case ParquetLocation(path) =>
      spark.read.parquet(path)

    case DeltaLocation(path) =>
      spark.read.format("delta").load(path)

    case JdbcLocation(url, table) =>
      spark.read
        .format("jdbc")
        .option("url", url)
        .option("dbtable", table)
        .load()

    case CsvLocation(path, delimiter) =>
      spark.read
        .option("delimiter", delimiter)
        .option("header", "true")
        .option("inferSchema", "true")
        .csv(path)
  }

  /**
   * Apply epoch boundary filtering to the raw DataFrame.
   *
   * Uses the boundary definition from the binding to determine the
   * filter column. Rows outside the epoch window `[start, end)` are
   * filtered out. If no column name is specified in the boundary parameters,
   * the full dataset is returned (snapshot semantics).
   *
   * @param df      the unfiltered DataFrame
   * @param binding the PDM binding (contains boundary definition)
   * @param epoch   the processing epoch
   * @return the filtered DataFrame
   */
  private def applyEpochFilter(
    df: DataFrame,
    binding: PdmBinding,
    epoch: Epoch
  ): DataFrame = {
    binding.boundary.parameters.get("column_name") match {
      case Some(columnName) =>
        df.filter(
          col(columnName) >= epoch.start.toString &&
            col(columnName) < epoch.end.toString
        )
      case None =>
        // No boundary column — snapshot grain, return full dataset
        df
    }
  }
}
