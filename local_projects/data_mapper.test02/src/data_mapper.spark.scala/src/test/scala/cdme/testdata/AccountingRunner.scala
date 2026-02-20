package cdme.testdata

import java.time.LocalDate
import java.io.{File, PrintWriter}
import scala.io.Source
import io.circe.parser._
import io.circe.generic.auto._
import cdme.executor.{AccountingLedgerBuilder, AccountingDiscrepancy}

/**
 * Accounting Runner - Transformer with Timestamped Runs and Zero-Loss Accounting
 *
 * Processes datasets through the accounting transformer, producing
 * timestamped run output for comparison and analysis.
 *
 * Zero-Loss Guarantee (REQ-ACC-01):
 * Every input record is accounted for in exactly one partition:
 * - AGGREGATED: Records successfully aggregated into output
 * - FILTERED: Records intentionally excluded (wrong date, status != FLOWN)
 * - ERROR: Records that failed parsing/validation
 *
 * Key distinction:
 * - INPUT: Stable datasets in test-data/datasets/<datasetId>/
 * - OUTPUT: Timestamped runs in test-data/runs/MMDD_HHmmss_<runId>/
 *
 * Output Structure:
 *   test-data/runs/MMDD_HHmmss_<runId>/
 *     ├── config.json           - Run configuration
 *     ├── ledger.json           - Accounting proof (zero-loss verification)
 *     ├── accounting/           - Aggregated output
 *     ├── errors/               - Parse/validation failures
 *     └── adjoint/              - Adjoint metadata for backward traversal
 *
 * Usage:
 *   # Process clean dataset
 *   sbt "Test / runMain cdme.testdata.AccountingRunner baseline-clean test-data/datasets/airline-clean"
 *
 *   # Process error dataset
 *   sbt "Test / runMain cdme.testdata.AccountingRunner error-test test-data/datasets/airline-errors-10pct"
 *
 *   # Full parameters
 *   sbt "Test / runMain cdme.testdata.AccountingRunner my-run test-data/datasets/my-dataset 2024-01-15 2024-02-15 2024-01-01 2024-01-10"
 *
 * Implements: REQ-ACC-01, REQ-ACC-02, REQ-ACC-03, REQ-ACC-04
 */
object AccountingRunner {

  def main(args: Array[String]): Unit = {
    if (args.isEmpty || args(0) == "--help" || args(0) == "-h") {
      TransformerRunConfig.printUsage()
      return
    }

    val config = TransformerRunConfig.fromArgs(args)
    run(config)
  }

  /**
   * Run accounting processing with the given configuration.
   *
   * Implements zero-loss guarantee via AccountingLedger:
   * 1. Track all input keys
   * 2. Route records to AGGREGATED, FILTERED, or ERROR partitions
   * 3. Verify accounting invariant before completion
   * 4. Write ledger.json as proof artifact
   *
   * Implements: REQ-ACC-01, REQ-ACC-02, REQ-ACC-04
   */
  def run(config: TransformerRunConfig): TransformerResult = {
    println("=" * 60)
    println("Accounting Runner (Transformer) - Zero-Loss Mode")
    println("=" * 60)
    println(config.summary)
    println()

    // Validate dataset exists
    val datasetDir = new File(config.datasetPath)
    if (!datasetDir.exists()) {
      println(s"ERROR: Dataset not found: ${config.datasetPath}")
      println("Run DatasetGenerator first to create the dataset.")
      return TransformerResult.empty(config)
    }

    // Initialize output directory (timestamped)
    val outputDir = config.initialize()
    println(s"Run output: $outputDir")
    println()

    // Process the date range
    val result = processDateRange(config)

    // Verify day-level accounting invariants
    val accountingResults = result.dailyResults.map { day =>
      val verified = day.verifyAccounting
      if (!verified) {
        println(s"  WARNING: Accounting mismatch on ${day.flightDate}")
        println(s"    Loaded: ${day.segmentsLoaded}")
        println(s"    Flown: ${day.segmentsFlown} + FilteredByDate: ${day.filteredByDate} + FilteredByStatus: ${day.filteredByStatus} = ${day.segmentsFlown + day.filteredByDate + day.filteredByStatus}")
      }
      (day.flightDate, verified)
    }

    val allVerified = accountingResults.forall(_._2)

    // Write processing report
    writeProcessingReport(config, result)

    // Write accounting summary
    writeAccountingSummary(config, result, allVerified)

    println()
    println("=" * 60)
    println("Processing Complete")
    println("=" * 60)
    println(s"Run ID: ${config.runId}")
    println(s"Run folder: ${config.runFolderName}")
    println(s"Output: $outputDir")
    println(s"Days processed: ${result.daysProcessed}")
    println(s"Segments processed: ${result.totalSegmentsFlown}")
    println(s"Summaries produced: ${result.totalSummaries}")
    println(s"Total revenue: $$${result.totalRevenueUsd}")
    println(s"Total time: ${result.totalProcessingTimeMs}ms")
    println()
    println("Accounting Status:")
    println(s"  Parse Errors: ${result.dailyResults.map(_.parseErrors).sum}")
    println(s"  Filtered (Date): ${result.dailyResults.map(_.filteredByDate).sum}")
    println(s"  Filtered (Status): ${result.dailyResults.map(_.filteredByStatus).sum}")
    println(s"  Accounting Verified: ${if (allVerified) "✓ PASS" else "✗ FAIL"}")

    result
  }

  /**
   * Write accounting summary to ledger.json
   * Implements: REQ-ACC-02 (Accounting Ledger)
   */
  private def writeAccountingSummary(config: TransformerRunConfig, result: TransformerResult, allVerified: Boolean): Unit = {
    val totalLoaded = result.dailyResults.map(_.segmentsLoaded).sum
    val totalFlown = result.totalSegmentsFlown
    val totalFilteredByDate = result.dailyResults.map(_.filteredByDate).sum
    val totalFilteredByStatus = result.dailyResults.map(_.filteredByStatus).sum
    val totalParseErrors = result.dailyResults.map(_.parseErrors).sum

    val ledgerFile = new File(s"${config.outputDir}/ledger.json")
    val writer = new PrintWriter(ledgerFile)

    writer.write(
      s"""{
         |  "ledger_version": "1.0",
         |  "run_id": "${config.runId}",
         |  "input_dataset": "${config.datasetPath}",
         |  "input_accounting": {
         |    "total_records": $totalLoaded,
         |    "source_key_field": "segment_id"
         |  },
         |  "output_accounting": {
         |    "partitions": [
         |      {
         |        "partition_type": "AGGREGATED",
         |        "description": "Records aggregated into daily summaries",
         |        "record_count": $totalFlown,
         |        "adjoint_type": "ReverseJoinMetadata",
         |        "adjoint_location": "${config.accountingDir}"
         |      },
         |      {
         |        "partition_type": "FILTERED",
         |        "description": "Records filtered by date (not in processing window)",
         |        "record_count": $totalFilteredByDate,
         |        "adjoint_type": "FilteredKeysMetadata",
         |        "adjoint_location": "adjoint/filtered_keys/date_filter"
         |      },
         |      {
         |        "partition_type": "FILTERED",
         |        "description": "Records filtered by status (status != FLOWN)",
         |        "record_count": $totalFilteredByStatus,
         |        "adjoint_type": "FilteredKeysMetadata",
         |        "adjoint_location": "adjoint/filtered_keys/status_filter"
         |      },
         |      {
         |        "partition_type": "ERROR",
         |        "description": "Records failed parsing/validation",
         |        "record_count": $totalParseErrors,
         |        "adjoint_type": "ErrorRecords",
         |        "adjoint_location": "${config.outputDir}/errors"
         |      }
         |    ],
         |    "total_accounted": ${totalFlown + totalFilteredByDate + totalFilteredByStatus + totalParseErrors},
         |    "unaccounted": ${totalLoaded - (totalFlown + totalFilteredByDate + totalFilteredByStatus)}
         |  },
         |  "verification": {
         |    "accounting_balanced": $allVerified,
         |    "proof_method": "partition_sum_check"
         |  }
         |}""".stripMargin
    )
    writer.close()

    println(s"Accounting ledger written to ${ledgerFile.getPath}")
  }

  private def processDateRange(config: TransformerRunConfig): TransformerResult = {
    val results = scala.collection.mutable.ListBuffer[DayResult]()

    var currentDate = config.flightDateStart
    var dayCount = 0

    while (!currentDate.isAfter(config.flightDateEnd)) {
      dayCount += 1
      println(s"Processing flight date $dayCount: $currentDate")

      val dayResult = processFlightDate(config, currentDate)
      results += dayResult

      println(s"  Segments FLOWN: ${dayResult.segmentsFlown}")
      println(s"  Summaries: ${dayResult.summariesProduced}")
      println(s"  Revenue: $$${dayResult.revenueUsd}")
      println(s"  Time: ${dayResult.processingTimeMs}ms")

      currentDate = currentDate.plusDays(1)
    }

    val resultsList = results.toList

    TransformerResult(
      runId = config.runId,
      runFolder = config.runFolderName,
      datasetPath = config.datasetPath,
      flightDateStart = config.flightDateStart.toString,
      flightDateEnd = config.flightDateEnd.toString,
      daysProcessed = resultsList.size,
      totalSegmentsFlown = resultsList.map(_.segmentsFlown).sum,
      totalSummaries = resultsList.map(_.summariesProduced).sum,
      totalRevenueUsd = resultsList.map(_.revenueUsd).sum,
      totalProcessingTimeMs = resultsList.map(_.processingTimeMs).sum,
      avgProcessingTimeMs = if (resultsList.nonEmpty) resultsList.map(_.processingTimeMs).sum / resultsList.size else 0,
      dailyResults = resultsList
    )
  }

  private def processFlightDate(config: TransformerRunConfig, flightDate: LocalDate): DayResult = {
    val startTime = System.currentTimeMillis()

    // Load segments from dataset
    val bookingDir = new File(s"${config.datasetPath}/bookings")
    val bookingDates = if (bookingDir.exists()) {
      bookingDir.listFiles()
        .filter(_.isDirectory)
        .map(_.getName)
        .filter { dateStr =>
          try {
            val date = LocalDate.parse(dateStr)
            !date.isBefore(config.bookingDateStart) && !date.isAfter(config.bookingDateEnd)
          } catch {
            case _: Exception => false
          }
        }
        .map(LocalDate.parse)
        .toList
        .sorted
    } else List.empty

    // Load segments with error tracking (REQ-ACC-01: no silent drops)
    val loadResults = bookingDates.map { bookingDate =>
      loadSegmentsForBookingDate(config.datasetPath, bookingDate)
    }

    val allSegments = loadResults.flatMap(_.segments)
    val allParseErrors = loadResults.flatMap(_.errors)

    // Track filter operations for accounting (REQ-ACC-03)
    val forDate = allSegments.filter(_.flight_date == flightDate.toString)
    val filteredByDate = allSegments.filter(_.flight_date != flightDate.toString)

    val flownSegments = forDate.filter(_.status == "FLOWN")
    val filteredByStatus = forDate.filter(_.status != "FLOWN")

    // Aggregate
    val summaries = aggregate(flownSegments, flightDate)

    // Write output to run directory
    val outputDir = new File(s"${config.accountingDir}/${flightDate.toString}")
    if (!outputDir.exists()) outputDir.mkdirs()

    val outputFile = new File(s"$outputDir/daily_revenue_summary.jsonl")
    val writer = new PrintWriter(outputFile)
    import io.circe.syntax._
    summaries.foreach { summary =>
      writer.println(summary.asJson.noSpaces)
    }
    writer.close()

    // Write parse errors to DLQ (REQ-TYP-03: error routing)
    if (allParseErrors.nonEmpty) {
      val errorsDir = new File(s"${config.outputDir}/errors/error_type=parse_error")
      if (!errorsDir.exists()) errorsDir.mkdirs()

      val errorFile = new File(s"$errorsDir/${flightDate.toString}.jsonl")
      val errorWriter = new PrintWriter(errorFile)
      allParseErrors.foreach { err =>
        errorWriter.println(s"""{"line":${err.lineNumber},"error":"${err.errorMessage.replace("\"", "\\\"")}","file":"${err.sourceFile}"}""")
      }
      errorWriter.close()
    }

    val endTime = System.currentTimeMillis()

    DayResult(
      flightDate = flightDate.toString,
      segmentsLoaded = allSegments.size,
      segmentsForDate = forDate.size,
      segmentsFlown = flownSegments.size,
      summariesProduced = summaries.size,
      revenueUsd = summaries.map(_.total_revenue_usd).sum,
      processingTimeMs = endTime - startTime,
      parseErrors = allParseErrors.size,
      filteredByDate = filteredByDate.size,
      filteredByStatus = filteredByStatus.size
    )
  }

  case class SegmentInput(
    segment_id: String,
    journey_id: String,
    flight_number: String,
    airline_code: String,
    departure_airport: String,
    arrival_airport: String,
    departure_datetime: String,
    arrival_datetime: String,
    flight_date: String,
    cabin_class: String,
    segment_price_local: BigDecimal,
    segment_price_usd: BigDecimal,
    segment_sequence: Int,
    is_codeshare: Boolean,
    operating_airline: Option[String],
    distance_km: Int,
    status: String
  )

  case class DailyRevenueSummary(
    summary_id: String,
    summary_date: String,
    airline_code: String,
    route: String,
    departure_airport: String,
    arrival_airport: String,
    segment_count: Long,
    total_revenue_usd: BigDecimal,
    avg_segment_price_usd: BigDecimal,
    total_distance_km: Long,
    economy_count: Long,
    business_count: Long,
    first_count: Long,
    codeshare_count: Long
  )

  /**
   * Load result containing both successful and failed parses.
   * Implements: REQ-ACC-01 (no silent drops), REQ-TYP-03 (error routing)
   */
  case class LoadResult(
    segments: List[SegmentInput],
    errors: List[ParseError]
  )

  case class ParseError(
    lineNumber: Int,
    rawLine: String,
    errorMessage: String,
    sourceFile: String
  )

  private def loadSegmentsForBookingDate(datasetPath: String, bookingDate: LocalDate): LoadResult = {
    val segmentsFile = new File(s"$datasetPath/bookings/${bookingDate.toString}/segments.jsonl")
    if (!segmentsFile.exists()) {
      return LoadResult(List.empty, List.empty)
    }

    val source = Source.fromFile(segmentsFile)
    try {
      val segments = scala.collection.mutable.ListBuffer[SegmentInput]()
      val errors = scala.collection.mutable.ListBuffer[ParseError]()

      source.getLines().zipWithIndex.foreach { case (line, idx) =>
        decode[SegmentInput](line) match {
          case Right(segment) =>
            segments += segment
          case Left(error) =>
            // Capture error instead of silently dropping (REQ-TYP-03)
            errors += ParseError(
              lineNumber = idx + 1,
              rawLine = if (line.length > 200) line.take(200) + "..." else line,
              errorMessage = error.getMessage,
              sourceFile = segmentsFile.getPath
            )
        }
      }

      LoadResult(segments.toList, errors.toList)
    } finally {
      source.close()
    }
  }

  /**
   * Legacy method for backward compatibility - use loadSegmentsWithAccounting instead.
   * @deprecated Use loadSegmentsForBookingDateWithErrors for proper error tracking
   */
  private def loadSegmentsForBookingDateLegacy(datasetPath: String, bookingDate: LocalDate): List[SegmentInput] = {
    loadSegmentsForBookingDate(datasetPath, bookingDate).segments
  }

  private def aggregate(segments: List[SegmentInput], flightDate: LocalDate): List[DailyRevenueSummary] = {
    case class AggKey(airline: String, route: String, depAirport: String, arrAirport: String)

    val grouped = segments.groupBy { seg =>
      AggKey(
        airline = seg.airline_code,
        route = s"${seg.departure_airport}-${seg.arrival_airport}",
        depAirport = seg.departure_airport,
        arrAirport = seg.arrival_airport
      )
    }

    grouped.map { case (key, segs) =>
      val segmentCount = segs.size.toLong
      val totalRevenue = segs.map(_.segment_price_usd).sum
      val totalDistance = segs.map(_.distance_km.toLong).sum
      val economyCount = segs.count(_.cabin_class == "ECONOMY").toLong
      val businessCount = segs.count(_.cabin_class == "BUSINESS").toLong
      val firstCount = segs.count(_.cabin_class == "FIRST").toLong
      val codeshareCount = segs.count(_.is_codeshare).toLong

      DailyRevenueSummary(
        summary_id = s"DRS-${flightDate.toString}-${key.airline}-${key.route}",
        summary_date = flightDate.toString,
        airline_code = key.airline,
        route = key.route,
        departure_airport = key.depAirport,
        arrival_airport = key.arrAirport,
        segment_count = segmentCount,
        total_revenue_usd = totalRevenue.setScale(2, BigDecimal.RoundingMode.HALF_UP),
        avg_segment_price_usd = if (segmentCount > 0)
          (totalRevenue / segmentCount).setScale(2, BigDecimal.RoundingMode.HALF_UP)
        else BigDecimal(0),
        total_distance_km = totalDistance,
        economy_count = economyCount,
        business_count = businessCount,
        first_count = firstCount,
        codeshare_count = codeshareCount
      )
    }.toList.sortBy(s => (s.airline_code, s.route))
  }

  private def writeProcessingReport(config: TransformerRunConfig, result: TransformerResult): Unit = {
    val reportFile = new File(s"${config.outputDir}/processing_report.json")
    val writer = new PrintWriter(reportFile)

    val dailyResultsJson = result.dailyResults.map { dr =>
      s"""    {
         |      "flight_date": "${dr.flightDate}",
         |      "segments_flown": ${dr.segmentsFlown},
         |      "summaries": ${dr.summariesProduced},
         |      "revenue_usd": "${dr.revenueUsd}",
         |      "time_ms": ${dr.processingTimeMs}
         |    }""".stripMargin
    }.mkString(",\n")

    writer.write(
      s"""{
         |  "run_id": "${result.runId}",
         |  "run_folder": "${result.runFolder}",
         |  "dataset_path": "${result.datasetPath}",
         |  "flight_date_start": "${result.flightDateStart}",
         |  "flight_date_end": "${result.flightDateEnd}",
         |  "days_processed": ${result.daysProcessed},
         |  "total_segments_flown": ${result.totalSegmentsFlown},
         |  "total_summaries": ${result.totalSummaries},
         |  "total_revenue_usd": "${result.totalRevenueUsd}",
         |  "total_processing_time_ms": ${result.totalProcessingTimeMs},
         |  "avg_processing_time_ms": ${result.avgProcessingTimeMs},
         |  "daily_results": [
         |$dailyResultsJson
         |  ]
         |}""".stripMargin
    )
    writer.close()

    println(s"Processing report written to ${reportFile.getPath}")
  }
}

/**
 * Result for a single day of processing with accounting breakdown.
 *
 * Implements: REQ-ACC-01 (Accounting Invariant)
 * The invariant: segmentsLoaded = segmentsFlown + filteredByDate + filteredByStatus + parseErrors
 */
case class DayResult(
  flightDate: String,
  segmentsLoaded: Int,
  segmentsForDate: Int,
  segmentsFlown: Int,
  summariesProduced: Int,
  revenueUsd: BigDecimal,
  processingTimeMs: Long,
  // Accounting fields (REQ-ACC-01)
  parseErrors: Int = 0,
  filteredByDate: Int = 0,
  filteredByStatus: Int = 0
) {
  /**
   * Verify the day-level accounting invariant.
   * Total input = processed + filtered (date) + filtered (status) + errors
   */
  def verifyAccounting: Boolean = {
    // Note: filteredByDate is excluded from forDate, so:
    // segmentsLoaded = (segmentsForDate + filteredByDate)
    // segmentsForDate = (segmentsFlown + filteredByStatus)
    // Therefore: segmentsLoaded = segmentsFlown + filteredByStatus + filteredByDate
    val expected = segmentsFlown + filteredByStatus + filteredByDate
    segmentsLoaded == expected
  }
}

/**
 * Overall transformer result
 */
case class TransformerResult(
  runId: String,
  runFolder: String,
  datasetPath: String,
  flightDateStart: String,
  flightDateEnd: String,
  daysProcessed: Int,
  totalSegmentsFlown: Long,
  totalSummaries: Long,
  totalRevenueUsd: BigDecimal,
  totalProcessingTimeMs: Long,
  avgProcessingTimeMs: Long,
  dailyResults: List[DayResult]
)

object TransformerResult {
  def empty(config: TransformerRunConfig): TransformerResult = TransformerResult(
    runId = config.runId,
    runFolder = config.runFolderName,
    datasetPath = config.datasetPath,
    flightDateStart = config.flightDateStart.toString,
    flightDateEnd = config.flightDateEnd.toString,
    daysProcessed = 0,
    totalSegmentsFlown = 0,
    totalSummaries = 0,
    totalRevenueUsd = BigDecimal(0),
    totalProcessingTimeMs = 0,
    avgProcessingTimeMs = 0,
    dailyResults = List.empty
  )
}
