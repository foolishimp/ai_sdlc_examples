package cdme.testdata

import java.time.{LocalDate, LocalDateTime}
import java.time.format.DateTimeFormatter
import java.io.{File, PrintWriter}
import io.circe.{Json, Encoder}
import io.circe.syntax._
import io.circe.generic.auto._

/**
 * Run Configuration for Transformer/Accounting Processing
 *
 * The critical distinction:
 * - DATA GENERATION: Produces stable, reusable datasets (no timestamps needed)
 *   - test-data/datasets/airline-clean/
 *   - test-data/datasets/airline-errors-10pct/
 *
 * - TRANSFORMER RUNS: Processes datasets through the mapper (timestamped)
 *   - test-data/runs/MMDD_HHmmss_<runId>/
 *
 * Directory Structure:
 *   test-data/
 *     datasets/                    <- Source data (stable, reusable)
 *       airline-clean/
 *         reference/
 *         bookings/
 *       airline-errors-10pct/
 *         reference/
 *         bookings/
 *         error_manifest.jsonl
 *
 *     runs/                        <- Transformer runs (timestamped)
 *       MMDD_HHmmss_<runId>/
 *         config.json              - Run configuration
 *         accounting/              - Processed output
 *           2024-01-15/
 *             daily_revenue_summary.jsonl
 *           ...
 *         processing_report.json   - Run results
 *
 * Usage:
 *   val config = TransformerRunConfig(
 *     runId = "baseline-test",
 *     datasetPath = "test-data/datasets/airline-clean",
 *     flightDateStart = LocalDate.of(2024, 1, 15),
 *     flightDateEnd = LocalDate.of(2024, 2, 15)
 *   )
 *
 *   val outputDir = config.initialize()
 *   // Returns: "test-data/runs/0115_143022_baseline-test"
 */
case class TransformerRunConfig(
  // Run identification
  runId: String,
  description: String = "",

  // Input dataset (stable, no timestamp)
  datasetPath: String,

  // Output organization (timestamped runs)
  runsBaseDir: String = "test-data/runs",
  timestamp: LocalDateTime = LocalDateTime.now(),

  // Processing date range (flight dates to process)
  flightDateStart: LocalDate = LocalDate.of(2024, 1, 15),
  flightDateEnd: LocalDate = LocalDate.of(2024, 2, 15),

  // Booking date range (where to find source segments)
  bookingDateStart: LocalDate = LocalDate.of(2024, 1, 1),
  bookingDateEnd: LocalDate = LocalDate.of(2024, 1, 10)
) {

  private val timestampFormatter = DateTimeFormatter.ofPattern("MMdd_HHmmss")

  /**
   * Generate the run folder name: MMDD_HHmmss_<runId>
   */
  def runFolderName: String = {
    val ts = timestamp.format(timestampFormatter)
    s"${ts}_$runId"
  }

  /**
   * Full output directory path for this run
   */
  def outputDir: String = s"$runsBaseDir/$runFolderName"

  /**
   * Accounting output directory within this run
   */
  def accountingDir: String = s"$outputDir/accounting"

  /**
   * Create output directories and write config
   */
  def initialize(): String = {
    val dir = new File(outputDir)
    if (!dir.exists()) dir.mkdirs()

    val accDir = new File(accountingDir)
    if (!accDir.exists()) accDir.mkdirs()

    // Write config file
    writeConfigFile()

    outputDir
  }

  /**
   * Write run configuration to config.json
   */
  private def writeConfigFile(): Unit = {
    val configFile = new File(s"$outputDir/config.json")
    val writer = new PrintWriter(configFile)

    writer.write(
      s"""{
         |  "run_id": "$runId",
         |  "description": "$description",
         |  "timestamp": "${timestamp.toString}",
         |  "folder_name": "$runFolderName",
         |  "input": {
         |    "dataset_path": "$datasetPath",
         |    "booking_date_start": "${bookingDateStart.toString}",
         |    "booking_date_end": "${bookingDateEnd.toString}"
         |  },
         |  "processing": {
         |    "flight_date_start": "${flightDateStart.toString}",
         |    "flight_date_end": "${flightDateEnd.toString}"
         |  },
         |  "output": {
         |    "runs_base_dir": "$runsBaseDir",
         |    "output_dir": "$outputDir",
         |    "accounting_dir": "$accountingDir"
         |  }
         |}""".stripMargin
    )
    writer.close()
  }

  /**
   * Summary for logging
   */
  def summary: String = {
    s"""Transformer Run: $runId
       |  Input dataset: $datasetPath
       |  Flight dates: $flightDateStart to $flightDateEnd
       |  Booking dates: $bookingDateStart to $bookingDateEnd
       |  Output: $outputDir""".stripMargin
  }
}

object TransformerRunConfig {

  /**
   * Parse from command-line arguments
   */
  def fromArgs(args: Array[String]): TransformerRunConfig = {
    val runId = if (args.length > 0) args(0) else "default"
    val datasetPath = if (args.length > 1) args(1) else "test-data/datasets/airline-clean"
    val flightDateStart = if (args.length > 2) LocalDate.parse(args(2)) else LocalDate.of(2024, 1, 15)
    val flightDateEnd = if (args.length > 3) LocalDate.parse(args(3)) else LocalDate.of(2024, 2, 15)
    val bookingDateStart = if (args.length > 4) LocalDate.parse(args(4)) else LocalDate.of(2024, 1, 1)
    val bookingDateEnd = if (args.length > 5) LocalDate.parse(args(5)) else LocalDate.of(2024, 1, 10)
    val runsBaseDir = if (args.length > 6) args(6) else "test-data/runs"

    TransformerRunConfig(
      runId = runId,
      datasetPath = datasetPath,
      runsBaseDir = runsBaseDir,
      flightDateStart = flightDateStart,
      flightDateEnd = flightDateEnd,
      bookingDateStart = bookingDateStart,
      bookingDateEnd = bookingDateEnd
    )
  }

  /**
   * Print usage information
   */
  def printUsage(): Unit = {
    println(
      """Usage: AccountingRunner <runId> <datasetPath> [flightDateStart] [flightDateEnd] [bookingDateStart] [bookingDateEnd] [runsBaseDir]
        |
        |Arguments:
        |  runId            - Unique identifier for this run (required)
        |  datasetPath      - Path to input dataset (required)
        |  flightDateStart  - First flight date to process, YYYY-MM-DD (default: 2024-01-15)
        |  flightDateEnd    - Last flight date to process, YYYY-MM-DD (default: 2024-02-15)
        |  bookingDateStart - First booking date with data, YYYY-MM-DD (default: 2024-01-01)
        |  bookingDateEnd   - Last booking date with data, YYYY-MM-DD (default: 2024-01-10)
        |  runsBaseDir      - Base directory for runs (default: test-data/runs)
        |
        |Examples:
        |  # Process clean dataset
        |  AccountingRunner baseline-clean test-data/datasets/airline-clean
        |
        |  # Process error dataset
        |  AccountingRunner error-test test-data/datasets/airline-errors-10pct
        |
        |Output:
        |  test-data/runs/MMDD_HHmmss_<runId>/
        |    config.json
        |    accounting/
        |      2024-01-15/daily_revenue_summary.jsonl
        |      ...
        |    processing_report.json
        |""".stripMargin
    )
  }
}


// ============================================
// Dataset Configuration (for data generation)
// ============================================

/**
 * Configuration for dataset generation (stable, reusable)
 *
 * Datasets are stored without timestamps since they're meant to be reused
 * across multiple transformer runs.
 */
case class DatasetConfig(
  // Dataset identification
  datasetId: String,
  description: String = "",

  // Output location (stable, no timestamp)
  baseDir: String = "test-data/datasets",

  // Data generation parameters
  startDate: LocalDate = LocalDate.of(2024, 1, 1),
  daysToGenerate: Int = 10,
  journeysPerDay: Int = 10000,
  seed: Long = 42L,

  // Error injection (optional)
  errorRate: Double = 0.0,
  errorSeed: Option[Long] = None
) {

  /**
   * Full output directory path
   */
  def outputDir: String = s"$baseDir/$datasetId"

  /**
   * Create output directories and write config
   */
  def initialize(): String = {
    val dir = new File(outputDir)
    if (!dir.exists()) dir.mkdirs()

    writeConfigFile()
    outputDir
  }

  private def writeConfigFile(): Unit = {
    val configFile = new File(s"$outputDir/dataset_config.json")
    val writer = new PrintWriter(configFile)

    writer.write(
      s"""{
         |  "dataset_id": "$datasetId",
         |  "description": "$description",
         |  "generation": {
         |    "start_date": "${startDate.toString}",
         |    "days_to_generate": $daysToGenerate,
         |    "journeys_per_day": $journeysPerDay,
         |    "seed": $seed,
         |    "error_rate": $errorRate,
         |    "error_seed": ${errorSeed.map(_.toString).getOrElse("null")}
         |  }
         |}""".stripMargin
    )
    writer.close()
  }

  def hasErrors: Boolean = errorRate > 0.0

  def effectiveErrorSeed: Long = errorSeed.getOrElse(seed + 1000)

  def summary: String = {
    val errorInfo = if (hasErrors) s" (${(errorRate * 100).toInt}% errors)" else " (clean)"
    s"Dataset: $datasetId$errorInfo | ${startDate} for $daysToGenerate days @ ${journeysPerDay}/day | Output: $outputDir"
  }
}

object DatasetConfig {

  def clean(
    datasetId: String,
    baseDir: String = "test-data/datasets",
    startDate: LocalDate = LocalDate.of(2024, 1, 1),
    days: Int = 10,
    journeysPerDay: Int = 10000,
    seed: Long = 42L
  ): DatasetConfig = DatasetConfig(
    datasetId = datasetId,
    description = "Clean dataset (no errors)",
    baseDir = baseDir,
    startDate = startDate,
    daysToGenerate = days,
    journeysPerDay = journeysPerDay,
    seed = seed
  )

  def withErrors(
    datasetId: String,
    errorRate: Double = 0.10,
    baseDir: String = "test-data/datasets",
    startDate: LocalDate = LocalDate.of(2024, 1, 1),
    days: Int = 10,
    journeysPerDay: Int = 10000,
    seed: Long = 42L
  ): DatasetConfig = DatasetConfig(
    datasetId = datasetId,
    description = s"Dataset with ${(errorRate * 100).toInt}% error injection",
    baseDir = baseDir,
    startDate = startDate,
    daysToGenerate = days,
    journeysPerDay = journeysPerDay,
    seed = seed,
    errorRate = errorRate,
    errorSeed = Some(seed + 1000)
  )

  def fromArgs(args: Array[String]): DatasetConfig = {
    val datasetId = if (args.length > 0) args(0) else "default"
    val startDate = if (args.length > 1) LocalDate.parse(args(1)) else LocalDate.of(2024, 1, 1)
    val days = if (args.length > 2) args(2).toInt else 10
    val journeysPerDay = if (args.length > 3) args(3).toInt else 10000
    val errorRate = if (args.length > 4) args(4).toDouble else 0.0
    val seed = if (args.length > 5) args(5).toLong else 42L
    val baseDir = if (args.length > 6) args(6) else "test-data/datasets"

    DatasetConfig(
      datasetId = datasetId,
      baseDir = baseDir,
      startDate = startDate,
      daysToGenerate = days,
      journeysPerDay = journeysPerDay,
      seed = seed,
      errorRate = errorRate,
      errorSeed = if (errorRate > 0) Some(seed + 1000) else None
    )
  }

  def printUsage(): Unit = {
    println(
      """Usage: DatasetGenerator <datasetId> [startDate] [days] [journeysPerDay] [errorRate] [seed] [baseDir]
        |
        |Arguments:
        |  datasetId      - Unique identifier for this dataset (required)
        |  startDate      - First booking date, YYYY-MM-DD (default: 2024-01-01)
        |  days           - Number of days to generate (default: 10)
        |  journeysPerDay - Journeys per day (default: 10000)
        |  errorRate      - Error injection rate 0.0-1.0 (default: 0.0 = clean)
        |  seed           - Random seed for reproducibility (default: 42)
        |  baseDir        - Base output directory (default: test-data/datasets)
        |
        |Examples:
        |  # Clean dataset
        |  DatasetGenerator airline-clean 2024-01-01 10 10000 0.0 42
        |
        |  # Error injection dataset (10% errors)
        |  DatasetGenerator airline-errors-10pct 2024-01-01 10 10000 0.10 42
        |
        |Output:
        |  test-data/datasets/<datasetId>/
        |    dataset_config.json
        |    reference/
        |    bookings/
        |    generation_report.json
        |""".stripMargin
    )
  }
}
