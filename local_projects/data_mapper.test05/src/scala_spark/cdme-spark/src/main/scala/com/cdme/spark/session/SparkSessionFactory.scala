package com.cdme.spark.session

import org.apache.spark.sql.SparkSession

/**
 * Factory for creating [[SparkSession]] instances with standard CDME configuration.
 *
 * Provides a single entry point for session creation, ensuring consistent
 * defaults (app name, serializer settings, etc.) across all CDME Spark jobs.
 */
object SparkSessionFactory {

  /**
   * Create a new [[SparkSession]] with CDME defaults.
   *
   * If a session with the given `appName` already exists in the current JVM,
   * the existing session is returned (Spark's `getOrCreate` semantics).
   *
   * @param appName the Spark application name (visible in the Spark UI)
   * @param config  additional Spark configuration key-value pairs
   *                (e.g. `"spark.sql.shuffle.partitions" -> "200"`)
   * @return a configured [[SparkSession]]
   */
  def create(
    appName: String,
    config: Map[String, String] = Map.empty
  ): SparkSession = {
    val builder = SparkSession.builder()
      .appName(appName)
      .config("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
      .config("spark.sql.sources.partitionOverwriteMode", "dynamic")

    config.foreach { case (k, v) => builder.config(k, v) }

    builder.getOrCreate()
  }
}
