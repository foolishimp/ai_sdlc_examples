package cdme.testdata

import java.time.LocalDate
import java.io.{File, PrintWriter}
import scala.io.Source
import io.circe.parser._
import io.circe.generic.auto._

/**
 * Daily Accounting Runner
 *
 * Simulates running the CDME mapper to process daily flight segments
 * into accounting summaries. This runner:
 *
 * 1. Reads flight segments for a specific flight_date
 * 2. Applies filter morphism (status = 'FLOWN')
 * 3. Aggregates by date/airline/route
 * 4. Produces DailyRevenueSummary records
 *
 * Usage:
 *   val runner = new DailyAccountingRunner("test-data/airline")
 *   runner.processDay(LocalDate.of(2024, 1, 15))
 *   runner.processDateRange(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31))
 */
class DailyAccountingRunner(dataDir: String) {

  // Input segment model (matches generator output)
  case class FlightSegmentInput(
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

  // Output summary model
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
    codeshare_count: Long,
    cancelled_count: Long
  )

  // Aggregation key
  case class AggKey(date: String, airline: String, route: String, depAirport: String, arrAirport: String)

  // Aggregation accumulator
  case class AggAccum(
    segmentCount: Long = 0,
    totalRevenue: BigDecimal = BigDecimal(0),
    totalDistance: Long = 0,
    economyCount: Long = 0,
    businessCount: Long = 0,
    firstCount: Long = 0,
    codeshareCount: Long = 0,
    cancelledCount: Long = 0
  ) {
    def +(segment: FlightSegmentInput): AggAccum = {
      val cabinIncrement = segment.cabin_class match {
        case "ECONOMY" => (1L, 0L, 0L)
        case "BUSINESS" => (0L, 1L, 0L)
        case "FIRST" => (0L, 0L, 1L)
        case _ => (0L, 0L, 0L)
      }

      AggAccum(
        segmentCount = segmentCount + 1,
        totalRevenue = totalRevenue + segment.segment_price_usd,
        totalDistance = totalDistance + segment.distance_km,
        economyCount = economyCount + cabinIncrement._1,
        businessCount = businessCount + cabinIncrement._2,
        firstCount = firstCount + cabinIncrement._3,
        codeshareCount = codeshareCount + (if (segment.is_codeshare) 1 else 0),
        cancelledCount = cancelledCount + (if (segment.status == "CANCELLED") 1 else 0)
      )
    }

    def combine(other: AggAccum): AggAccum = AggAccum(
      segmentCount = segmentCount + other.segmentCount,
      totalRevenue = totalRevenue + other.totalRevenue,
      totalDistance = totalDistance + other.totalDistance,
      economyCount = economyCount + other.economyCount,
      businessCount = businessCount + other.businessCount,
      firstCount = firstCount + other.firstCount,
      codeshareCount = codeshareCount + other.codeshareCount,
      cancelledCount = cancelledCount + other.cancelledCount
    )
  }

  /**
   * Load all segments from a booking date directory
   */
  def loadSegmentsForBookingDate(bookingDate: LocalDate): List[FlightSegmentInput] = {
    val segmentsFile = new File(s"$dataDir/bookings/${bookingDate.toString}/segments.jsonl")
    if (!segmentsFile.exists()) {
      println(s"  Warning: No segments file for booking date $bookingDate")
      return List.empty
    }

    val source = Source.fromFile(segmentsFile)
    try {
      source.getLines().flatMap { line =>
        decode[FlightSegmentInput](line).toOption
      }.toList
    } finally {
      source.close()
    }
  }

  /**
   * Filter segments by flight_date (actual travel date)
   */
  def filterByFlightDate(segments: List[FlightSegmentInput], flightDate: LocalDate): List[FlightSegmentInput] = {
    val dateStr = flightDate.toString
    segments.filter(_.flight_date == dateStr)
  }

  /**
   * Apply FLOWN filter (accounting only processes completed flights)
   */
  def filterFlown(segments: List[FlightSegmentInput]): List[FlightSegmentInput] = {
    segments.filter(_.status == "FLOWN")
  }

  /**
   * Aggregate segments to daily revenue summaries
   */
  def aggregate(segments: List[FlightSegmentInput]): List[DailyRevenueSummary] = {
    val grouped = segments.groupBy { seg =>
      AggKey(
        date = seg.flight_date,
        airline = seg.airline_code,
        route = s"${seg.departure_airport}-${seg.arrival_airport}",
        depAirport = seg.departure_airport,
        arrAirport = seg.arrival_airport
      )
    }

    grouped.map { case (key, segs) =>
      val accum = segs.foldLeft(AggAccum())(_ + _)

      DailyRevenueSummary(
        summary_id = s"DRS-${key.date}-${key.airline}-${key.route}",
        summary_date = key.date,
        airline_code = key.airline,
        route = key.route,
        departure_airport = key.depAirport,
        arrival_airport = key.arrAirport,
        segment_count = accum.segmentCount,
        total_revenue_usd = accum.totalRevenue.setScale(2, BigDecimal.RoundingMode.HALF_UP),
        avg_segment_price_usd = if (accum.segmentCount > 0)
          (accum.totalRevenue / accum.segmentCount).setScale(2, BigDecimal.RoundingMode.HALF_UP)
        else BigDecimal(0),
        total_distance_km = accum.totalDistance,
        economy_count = accum.economyCount,
        business_count = accum.businessCount,
        first_count = accum.firstCount,
        codeshare_count = accum.codeshareCount,
        cancelled_count = accum.cancelledCount
      )
    }.toList.sortBy(s => (s.summary_date, s.airline_code, s.route))
  }

  /**
   * Process a single flight date across all booking dates
   *
   * Since travel can be 1-90 days after booking, we need to scan
   * multiple booking date directories to find all segments for a flight date.
   */
  def processFlightDate(flightDate: LocalDate, bookingDateRange: (LocalDate, LocalDate)): ProcessingResult = {
    val startTime = System.currentTimeMillis()

    // Scan all booking dates that could have segments for this flight date
    val bookingDir = new File(s"$dataDir/bookings")
    val bookingDates = if (bookingDir.exists()) {
      bookingDir.listFiles()
        .filter(_.isDirectory)
        .map(_.getName)
        .filter { dateStr =>
          try {
            val date = LocalDate.parse(dateStr)
            !date.isBefore(bookingDateRange._1) && !date.isAfter(bookingDateRange._2)
          } catch {
            case _: Exception => false
          }
        }
        .map(LocalDate.parse)
        .toList
        .sorted
    } else List.empty

    println(s"  Scanning ${bookingDates.size} booking date directories...")

    // Load and filter segments
    var totalLoaded = 0L
    var totalForDate = 0L
    var totalFlown = 0L

    val allSegments = bookingDates.flatMap { bookingDate =>
      val segments = loadSegmentsForBookingDate(bookingDate)
      totalLoaded += segments.size

      val forDate = filterByFlightDate(segments, flightDate)
      totalForDate += forDate.size

      val flown = filterFlown(forDate)
      totalFlown += flown.size

      flown
    }

    // Aggregate
    val summaries = aggregate(allSegments)

    // Write output
    val outputDir = new File(s"$dataDir/accounting/${flightDate.toString}")
    if (!outputDir.exists()) outputDir.mkdirs()

    val outputFile = new File(s"$outputDir/daily_revenue_summary.jsonl")
    val writer = new PrintWriter(outputFile)
    import io.circe.syntax._
    summaries.foreach { summary =>
      writer.println(summary.asJson.noSpaces)
    }
    writer.close()

    val endTime = System.currentTimeMillis()
    val duration = endTime - startTime

    ProcessingResult(
      flightDate = flightDate,
      segmentsLoaded = totalLoaded,
      segmentsForDate = totalForDate,
      segmentsFlown = totalFlown,
      summariesProduced = summaries.size,
      totalRevenueUsd = summaries.map(_.total_revenue_usd).sum,
      processingTimeMs = duration,
      outputPath = outputFile.getPath
    )
  }

  /**
   * Process a range of flight dates
   */
  def processDateRange(
    startFlightDate: LocalDate,
    endFlightDate: LocalDate,
    bookingDateRange: (LocalDate, LocalDate)
  ): List[ProcessingResult] = {
    val results = scala.collection.mutable.ListBuffer[ProcessingResult]()

    var currentDate = startFlightDate
    while (!currentDate.isAfter(endFlightDate)) {
      println(s"\nProcessing flight date: $currentDate")
      val result = processFlightDate(currentDate, bookingDateRange)
      results += result

      println(s"  Segments loaded: ${result.segmentsLoaded}")
      println(s"  Segments for date: ${result.segmentsForDate}")
      println(s"  Segments FLOWN: ${result.segmentsFlown}")
      println(s"  Summaries produced: ${result.summariesProduced}")
      println(s"  Total revenue: $$${result.totalRevenueUsd}")
      println(s"  Processing time: ${result.processingTimeMs}ms")

      currentDate = currentDate.plusDays(1)
    }

    // Write overall summary
    writeSummaryReport(results.toList)

    results.toList
  }

  /**
   * Write a summary report of all processing
   */
  private def writeSummaryReport(results: List[ProcessingResult]): Unit = {
    val reportDir = new File(s"$dataDir/accounting")
    if (!reportDir.exists()) reportDir.mkdirs()

    val reportFile = new File(s"$reportDir/processing_report.json")
    val writer = new PrintWriter(reportFile)

    val dailyResults = results.map { r =>
      s"""    {
         |      "flight_date": "${r.flightDate.toString}",
         |      "segments_flown": ${r.segmentsFlown},
         |      "summaries": ${r.summariesProduced},
         |      "revenue_usd": "${r.totalRevenueUsd.toString}",
         |      "time_ms": ${r.processingTimeMs}
         |    }""".stripMargin
    }.mkString(",\n")

    writer.write(
      s"""{
         |  "processing_date": "${LocalDate.now().toString}",
         |  "days_processed": ${results.size},
         |  "total_segments_processed": ${results.map(_.segmentsFlown).sum},
         |  "total_summaries_produced": ${results.map(_.summariesProduced).sum},
         |  "total_revenue_usd": "${results.map(_.totalRevenueUsd).sum.toString}",
         |  "total_processing_time_ms": ${results.map(_.processingTimeMs).sum},
         |  "avg_processing_time_ms": ${if (results.nonEmpty) results.map(_.processingTimeMs).sum / results.size else 0},
         |  "daily_results": [
         |$dailyResults
         |  ]
         |}""".stripMargin
    )
    writer.close()

    println(s"\nProcessing report written to: ${reportFile.getPath}")
  }

  case class ProcessingResult(
    flightDate: LocalDate,
    segmentsLoaded: Long,
    segmentsForDate: Long,
    segmentsFlown: Long,
    summariesProduced: Int,
    totalRevenueUsd: BigDecimal,
    processingTimeMs: Long,
    outputPath: String
  )
}

/**
 * Command-line entry point for daily accounting processing
 */
object DailyAccountingRunner {

  def main(args: Array[String]): Unit = {
    val dataDir = if (args.length > 0) args(0) else "test-data/airline"
    val startFlightDate = if (args.length > 1) LocalDate.parse(args(1)) else LocalDate.of(2024, 1, 15)
    val endFlightDate = if (args.length > 2) LocalDate.parse(args(2)) else startFlightDate.plusDays(30)
    val bookingStart = if (args.length > 3) LocalDate.parse(args(3)) else LocalDate.of(2024, 1, 1)
    val bookingEnd = if (args.length > 4) LocalDate.parse(args(4)) else LocalDate.of(2024, 1, 10)

    println(s"Daily Accounting Runner")
    println(s"=======================")
    println(s"Data directory: $dataDir")
    println(s"Flight date range: $startFlightDate to $endFlightDate")
    println(s"Booking date range: $bookingStart to $bookingEnd")
    println()

    val runner = new DailyAccountingRunner(dataDir)
    val results = runner.processDateRange(startFlightDate, endFlightDate, (bookingStart, bookingEnd))

    println(s"\n${"=" * 50}")
    println(s"SUMMARY")
    println(s"${"=" * 50}")
    println(s"Days processed: ${results.size}")
    println(s"Total segments (FLOWN): ${results.map(_.segmentsFlown).sum}")
    println(s"Total summaries: ${results.map(_.summariesProduced).sum}")
    println(s"Total revenue: $$${results.map(_.totalRevenueUsd).sum}")
    println(s"Total time: ${results.map(_.processingTimeMs).sum}ms")
  }
}
