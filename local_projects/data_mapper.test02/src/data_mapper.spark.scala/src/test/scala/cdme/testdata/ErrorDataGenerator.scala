package cdme.testdata

import java.time.LocalDate
import java.io.{File, PrintWriter}
import scala.io.Source
import io.circe.parser._
import io.circe.syntax._
import io.circe.generic.auto._

/**
 * Error Data Generator
 *
 * Generates test data with configurable error injection.
 * Wraps the clean AirlineDataGenerator and applies ErrorInjector
 * to produce datasets with realistic data quality issues.
 *
 * Usage:
 *   val generator = new ErrorDataGenerator(
 *     errorRate = 0.10,
 *     seed = 42L
 *   )
 *   generator.generateWithErrors(
 *     startDate = LocalDate.of(2024, 1, 1),
 *     daysToGenerate = 10,
 *     journeysPerDay = 10000,
 *     outputDir = "test-data/airline-errors"
 *   )
 */
class ErrorDataGenerator(
  errorRate: Double = 0.10,
  seed: Long = System.currentTimeMillis()
) {

  private val baseGenerator = new AirlineDataGenerator(seed)
  private val errorInjector = new ErrorInjector(errorRate, seed + 1000)

  /**
   * Generate test data with error injection
   */
  def generateWithErrors(
    startDate: LocalDate,
    daysToGenerate: Int,
    journeysPerDay: Int,
    outputDir: String
  ): GenerationReport = {
    val dir = new File(outputDir)
    if (!dir.exists()) dir.mkdirs()

    // Write reference data (clean - no errors in reference data)
    writeReferenceData(outputDir)

    println(s"Generating $daysToGenerate days of data with ${(errorRate * 100).toInt}% error rate...")
    println(s"Total journeys: ${daysToGenerate * journeysPerDay}")

    var totalJourneys = 0L
    var totalSegments = 0L
    var totalCustomers = 0L
    val allErrors = scala.collection.mutable.ListBuffer[InjectedError]()
    val dailyStats = scala.collection.mutable.ListBuffer[DailyGenerationStats]()

    for (dayOffset <- 0 until daysToGenerate) {
      val bookingDate = startDate.plusDays(dayOffset)
      val dayDir = new File(s"$outputDir/bookings/${bookingDate.toString}")
      if (!dayDir.exists()) dayDir.mkdirs()

      println(s"  Generating day ${dayOffset + 1}/$daysToGenerate: $bookingDate")

      val journeys = scala.collection.mutable.ListBuffer[baseGenerator.Journey]()
      val segments = scala.collection.mutable.ListBuffer[baseGenerator.FlightSegment]()
      val customers = scala.collection.mutable.ListBuffer[baseGenerator.Customer]()

      for (i <- 1 to journeysPerDay) {
        val customerId = f"CUST-${bookingDate.toString}-$i%06d"
        val journeyId = f"JRN-${bookingDate.toString}-$i%06d"

        val customer = baseGenerator.generateCustomer(customerId)
        customers += customer

        val travelDate = bookingDate.plusDays(1 + new scala.util.Random(seed + i).nextInt(90))
        val (journey, journeySegments) = baseGenerator.generateJourney(journeyId, customer, bookingDate, travelDate)

        journeys += journey
        segments ++= journeySegments

        if (i % 10000 == 0) {
          println(s"    Processed $i/$journeysPerDay journeys...")
        }
      }

      // Apply error injection to segments (primary focus)
      val (corruptedSegments, segmentErrors, segmentStats) = processSegmentsWithErrors(segments.toList)

      // Apply error injection to journeys
      val (corruptedJourneys, journeyErrors, journeyStats) = processJourneysWithErrors(journeys.toList)

      // Apply error injection to customers
      val (corruptedCustomers, customerErrors, customerStats) = processCustomersWithErrors(customers.toList)

      // Write files
      writeCorruptedJsonLines(s"$dayDir/segments.jsonl", corruptedSegments)
      writeCorruptedJsonLines(s"$dayDir/journeys.jsonl", corruptedJourneys)
      writeCorruptedJsonLines(s"$dayDir/customers.jsonl", corruptedCustomers)

      // Write error manifest for the day
      val dayErrors = segmentErrors ++ journeyErrors ++ customerErrors
      allErrors ++= dayErrors
      writeErrorManifest(s"$dayDir/error_manifest.jsonl", dayErrors)

      totalJourneys += journeys.size
      totalSegments += segments.size
      totalCustomers += customers.size

      dailyStats += DailyGenerationStats(
        date = bookingDate.toString,
        journeyCount = journeys.size,
        segmentCount = segments.size,
        customerCount = customers.size,
        segmentErrors = segmentStats.errorRecords,
        journeyErrors = journeyStats.errorRecords,
        customerErrors = customerStats.errorRecords
      )

      println(s"    Written: ${journeys.size} journeys, ${segments.size} segments")
      println(s"    Errors: ${segmentStats.errorRecords} segment, ${journeyStats.errorRecords} journey, ${customerStats.errorRecords} customer")
    }

    // Calculate overall statistics
    val errorsByType = allErrors.groupBy(_.errorType).view.mapValues(_.size).toMap
    val errorsByField = allErrors.groupBy(_.fieldName).view.mapValues(_.size).toMap
    val errorsByEntity = allErrors.groupBy(_.entityType).view.mapValues(_.size).toMap

    val report = GenerationReport(
      startDate = startDate.toString,
      daysGenerated = daysToGenerate,
      journeysPerDay = journeysPerDay,
      totalJourneys = totalJourneys,
      totalSegments = totalSegments,
      totalCustomers = totalCustomers,
      errorRate = errorRate,
      totalErrors = allErrors.size,
      errorsByType = errorsByType,
      errorsByField = errorsByField,
      errorsByEntity = errorsByEntity,
      dailyStats = dailyStats.toList
    )

    // Write summary report
    writeGenerationReport(s"$outputDir/generation_report.json", report)

    println(s"\nGeneration complete!")
    println(s"  Total journeys: $totalJourneys")
    println(s"  Total segments: $totalSegments")
    println(s"  Total errors: ${allErrors.size}")
    println(s"  Error rate: ${(allErrors.size.toDouble / totalSegments * 100).formatted("%.2f")}%")
    println(s"  Output directory: $outputDir")

    report
  }

  private def processSegmentsWithErrors(
    segments: List[baseGenerator.FlightSegment]
  ): (List[String], List[InjectedError], ErrorStats) = {
    import io.circe.generic.auto._
    val jsonList = segments.map(_.asJson)
    val (corrupted, errors, stats) = errorInjector.processRecords(jsonList, "segment")
    (corrupted.map(_.noSpaces), errors, stats)
  }

  private def processJourneysWithErrors(
    journeys: List[baseGenerator.Journey]
  ): (List[String], List[InjectedError], ErrorStats) = {
    import io.circe.generic.auto._
    val jsonList = journeys.map(_.asJson)
    val (corrupted, errors, stats) = errorInjector.processRecords(jsonList, "journey")
    (corrupted.map(_.noSpaces), errors, stats)
  }

  private def processCustomersWithErrors(
    customers: List[baseGenerator.Customer]
  ): (List[String], List[InjectedError], ErrorStats) = {
    import io.circe.generic.auto._
    val jsonList = customers.map(_.asJson)
    val (corrupted, errors, stats) = errorInjector.processRecords(jsonList, "customer")
    (corrupted.map(_.noSpaces), errors, stats)
  }

  private def writeReferenceData(outputDir: String): Unit = {
    val refDir = new File(s"$outputDir/reference")
    if (!refDir.exists()) refDir.mkdirs()

    // Reuse base generator's reference data output models
    val airportData = baseGenerator.airports.map(a => baseGenerator.AirportOutput(
      a.code, a.name, a.city, a.countryCode, a.timezone, a.latitude, a.longitude, a.hub
    ))
    writeJsonLines(s"$refDir/airports.jsonl", airportData)

    val airlineData = baseGenerator.airlines.map(a => baseGenerator.AirlineOutput(
      a.code, a.name, a.homeCountry, a.alliance.getOrElse(""), a.hubs.mkString(",")
    ))
    writeJsonLines(s"$refDir/airlines.jsonl", airlineData)

    val countryData = baseGenerator.countries.map(c => baseGenerator.CountryOutput(
      c.code, c.name, c.region, c.timezone, c.currencyCode
    ))
    writeJsonLines(s"$refDir/countries.jsonl", countryData)

    val currencyData = baseGenerator.currencies.map(c => baseGenerator.CurrencyOutput(
      c.code, c.name, c.usdExchangeRate.toString
    ))
    writeJsonLines(s"$refDir/currencies.jsonl", currencyData)

    println(s"Reference data written to $refDir")
  }

  private def writeJsonLines[T: io.circe.Encoder](path: String, data: List[T]): Unit = {
    val writer = new PrintWriter(new File(path))
    data.foreach { item =>
      writer.println(item.asJson.noSpaces)
    }
    writer.close()
  }

  private def writeCorruptedJsonLines(path: String, data: List[String]): Unit = {
    val writer = new PrintWriter(new File(path))
    data.foreach(writer.println)
    writer.close()
  }

  private def writeErrorManifest(path: String, errors: List[InjectedError]): Unit = {
    val writer = new PrintWriter(new File(path))
    errors.foreach { error =>
      writer.println(error.asJson.noSpaces)
    }
    writer.close()
  }

  private def writeGenerationReport(path: String, report: GenerationReport): Unit = {
    val writer = new PrintWriter(new File(path))

    // Build JSON manually for nice formatting
    val dailyStatsJson = report.dailyStats.map { ds =>
      s"""    {
         |      "date": "${ds.date}",
         |      "journey_count": ${ds.journeyCount},
         |      "segment_count": ${ds.segmentCount},
         |      "customer_count": ${ds.customerCount},
         |      "segment_errors": ${ds.segmentErrors},
         |      "journey_errors": ${ds.journeyErrors},
         |      "customer_errors": ${ds.customerErrors}
         |    }""".stripMargin
    }.mkString(",\n")

    val errorsByTypeJson = report.errorsByType.map { case (k, v) =>
      s""""$k": $v"""
    }.mkString(", ")

    val errorsByFieldJson = report.errorsByField.map { case (k, v) =>
      s""""$k": $v"""
    }.mkString(", ")

    val errorsByEntityJson = report.errorsByEntity.map { case (k, v) =>
      s""""$k": $v"""
    }.mkString(", ")

    writer.write(
      s"""{
         |  "start_date": "${report.startDate}",
         |  "days_generated": ${report.daysGenerated},
         |  "journeys_per_day": ${report.journeysPerDay},
         |  "total_journeys": ${report.totalJourneys},
         |  "total_segments": ${report.totalSegments},
         |  "total_customers": ${report.totalCustomers},
         |  "error_rate": ${report.errorRate},
         |  "total_errors": ${report.totalErrors},
         |  "errors_by_type": { $errorsByTypeJson },
         |  "errors_by_field": { $errorsByFieldJson },
         |  "errors_by_entity": { $errorsByEntityJson },
         |  "daily_stats": [
         |$dailyStatsJson
         |  ]
         |}""".stripMargin
    )
    writer.close()

    println(s"Generation report written to $path")
  }
}

/**
 * Statistics for a single day of generation
 */
case class DailyGenerationStats(
  date: String,
  journeyCount: Int,
  segmentCount: Int,
  customerCount: Int,
  segmentErrors: Int,
  journeyErrors: Int,
  customerErrors: Int
)

/**
 * Overall generation report
 */
case class GenerationReport(
  startDate: String,
  daysGenerated: Int,
  journeysPerDay: Int,
  totalJourneys: Long,
  totalSegments: Long,
  totalCustomers: Long,
  errorRate: Double,
  totalErrors: Int,
  errorsByType: Map[String, Int],
  errorsByField: Map[String, Int],
  errorsByEntity: Map[String, Int],
  dailyStats: List[DailyGenerationStats]
)

/**
 * Command-line entry point for error data generation
 */
object ErrorDataGenerator {

  def main(args: Array[String]): Unit = {
    val startDate = if (args.length > 0) LocalDate.parse(args(0)) else LocalDate.of(2024, 1, 1)
    val days = if (args.length > 1) args(1).toInt else 10
    val journeysPerDay = if (args.length > 2) args(2).toInt else 10000
    val outputDir = if (args.length > 3) args(3) else "test-data/airline-errors"
    val errorRate = if (args.length > 4) args(4).toDouble else 0.10
    val seed = if (args.length > 5) args(5).toLong else 42L

    println(s"Error Data Generator")
    println(s"====================")
    println(s"Start date: $startDate")
    println(s"Days: $days")
    println(s"Journeys/day: $journeysPerDay")
    println(s"Error rate: ${(errorRate * 100).toInt}%")
    println(s"Output: $outputDir")
    println(s"Seed: $seed")
    println()

    val generator = new ErrorDataGenerator(errorRate, seed)
    generator.generateWithErrors(startDate, days, journeysPerDay, outputDir)
  }
}
