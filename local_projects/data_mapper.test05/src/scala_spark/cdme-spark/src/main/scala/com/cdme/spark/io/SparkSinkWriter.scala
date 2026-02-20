package com.cdme.spark.io

// Implements: REQ-F-PDM-001

import com.cdme.model.pdm.{PdmBinding, StorageLocation, ParquetLocation, DeltaLocation, JdbcLocation, CsvLocation}
import org.apache.spark.sql.{DataFrame, SaveMode}

/**
 * Writes Spark DataFrames to physical storage sinks.
 *
 * The writer pattern-matches on the [[StorageLocation]] type within each
 * [[PdmBinding]] to select the appropriate Spark write strategy.
 * All writes use `Overwrite` mode by default -- idempotent re-runs
 * produce identical output.
 */
object SparkSinkWriter {

  /**
   * Write a DataFrame to the storage location specified in the binding.
   *
   * @param binding the PDM binding mapping a logical entity to physical storage
   * @param df      the DataFrame to persist
   */
  def write(binding: PdmBinding, df: DataFrame): Unit =
    writeToLocation(binding.location, df)

  /**
   * Dispatch to the correct Spark writer based on [[StorageLocation]] type.
   */
  private def writeToLocation(location: StorageLocation, df: DataFrame): Unit =
    location match {

      case ParquetLocation(path) =>
        df.write.mode(SaveMode.Overwrite).parquet(path)

      case DeltaLocation(path) =>
        df.write.format("delta").mode(SaveMode.Overwrite).save(path)

      case JdbcLocation(url, table) =>
        df.write
          .format("jdbc")
          .option("url", url)
          .option("dbtable", table)
          .mode(SaveMode.Overwrite)
          .save()

      case CsvLocation(path, delimiter) =>
        df.write
          .option("delimiter", delimiter)
          .option("header", "true")
          .mode(SaveMode.Overwrite)
          .csv(path)
    }
}
