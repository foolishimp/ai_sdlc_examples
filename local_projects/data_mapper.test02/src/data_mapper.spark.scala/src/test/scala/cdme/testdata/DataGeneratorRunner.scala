package cdme.testdata

import java.time.LocalDate
import java.io.{File, PrintWriter}
import io.circe.syntax._
import io.circe.generic.auto._

/**
 * Dataset Generator Runner
 *
 * Generates stable, reusable datasets (no timestamps).
 * Supports both clean and error-injected data generation.
 *
 * Key distinction:
 * - DATASETS (this runner): Stable, reusable source data
 *   - test-data/datasets/airline-clean/
 *   - test-data/datasets/airline-errors-10pct/
 *
 * - RUNS (AccountingRunner): Timestamped transformer output
 *   - test-data/runs/MMDD_HHmmss_<runId>/
 *
 * Usage:
 *   # Clean dataset
 *   sbt "Test / runMain cdme.testdata.DataGeneratorRunner airline-clean"
 *
 *   # Error injection dataset (10% errors)
 *   sbt "Test / runMain cdme.testdata.DataGeneratorRunner airline-errors-10pct 2024-01-01 10 10000 0.10"
 *
 *   # Full parameters
 *   sbt "Test / runMain cdme.testdata.DataGeneratorRunner my-dataset 2024-01-01 10 10000 0.10 42 test-data/datasets"
 */
object DataGeneratorRunner {

  def main(args: Array[String]): Unit = {
    if (args.isEmpty || args(0) == "--help" || args(0) == "-h") {
      DatasetConfig.printUsage()
      return
    }

    val config = DatasetConfig.fromArgs(args)
    run(config)
  }

  /**
   * Run data generation with the given configuration
   */
  def run(config: DatasetConfig): GenerationReport = {
    println("=" * 60)
    println("Dataset Generator")
    println("=" * 60)
    println(config.summary)
    println()

    // Initialize output directory
    val outputDir = config.initialize()
    println(s"Output directory: $outputDir")
    println()

    // Create generators
    val baseGenerator = new AirlineDataGenerator(config.seed)
    val errorInjector = if (config.hasErrors) {
      Some(new ErrorInjector(config.errorRate, config.effectiveErrorSeed))
    } else None

    // Generate data
    val report = generateData(config, baseGenerator, errorInjector, outputDir)

    println()
    println("=" * 60)
    println("Dataset Generation Complete")
    println("=" * 60)
    println(s"Dataset ID: ${config.datasetId}")
    println(s"Output: $outputDir")
    println(s"Journeys: ${report.totalJourneys}")
    println(s"Segments: ${report.totalSegments}")
    if (config.hasErrors) {
      println(s"Errors: ${report.totalErrors} (${(report.totalErrors.toDouble / report.totalSegments * 100).formatted("%.1f")}%)")
    }

    report
  }

  private def generateData(
    config: DatasetConfig,
    baseGenerator: AirlineDataGenerator,
    errorInjector: Option[ErrorInjector],
    outputDir: String
  ): GenerationReport = {
    // Write reference data
    writeReferenceData(baseGenerator, outputDir)

    println(s"Generating ${config.daysToGenerate} days of data, ${config.journeysPerDay} journeys/day...")
    if (config.hasErrors) {
      println(s"Error injection enabled: ${(config.errorRate * 100).toInt}%")
    }
    println()

    var totalJourneys = 0L
    var totalSegments = 0L
    var totalCustomers = 0L
    val allErrors = scala.collection.mutable.ListBuffer[InjectedError]()
    val dailyStats = scala.collection.mutable.ListBuffer[DailyGenerationStats]()

    for (dayOffset <- 0 until config.daysToGenerate) {
      val bookingDate = config.startDate.plusDays(dayOffset)
      val dayDir = new File(s"$outputDir/bookings/${bookingDate.toString}")
      if (!dayDir.exists()) dayDir.mkdirs()

      println(s"  Day ${dayOffset + 1}/${config.daysToGenerate}: $bookingDate")

      val journeys = scala.collection.mutable.ListBuffer[baseGenerator.Journey]()
      val segments = scala.collection.mutable.ListBuffer[baseGenerator.FlightSegment]()
      val customers = scala.collection.mutable.ListBuffer[baseGenerator.Customer]()

      for (i <- 1 to config.journeysPerDay) {
        val customerId = f"CUST-${bookingDate.toString}-$i%06d"
        val journeyId = f"JRN-${bookingDate.toString}-$i%06d"

        val customer = baseGenerator.generateCustomer(customerId)
        customers += customer

        val travelDate = bookingDate.plusDays(1 + new scala.util.Random(config.seed + dayOffset * 100000 + i).nextInt(90))
        val (journey, journeySegments) = baseGenerator.generateJourney(journeyId, customer, bookingDate, travelDate)

        journeys += journey
        segments ++= journeySegments

        if (i % 10000 == 0) {
          println(s"    Processed $i/${config.journeysPerDay} journeys...")
        }
      }

      // Apply error injection if enabled
      val (segmentOutput, journeyOutput, customerOutput, dayErrors) = errorInjector match {
        case Some(injector) =>
          val (corruptedSegments, segmentErrors, _) = processWithErrors(injector, segments.toList, "segment")
          val (corruptedJourneys, journeyErrors, _) = processWithErrors(injector, journeys.toList, "journey")
          val (corruptedCustomers, customerErrors, _) = processWithErrors(injector, customers.toList, "customer")
          val errors = segmentErrors ++ journeyErrors ++ customerErrors
          (corruptedSegments, corruptedJourneys, corruptedCustomers, errors)

        case None =>
          val segOut = segments.toList.map(_.asJson.noSpaces)
          val jrnOut = journeys.toList.map(_.asJson.noSpaces)
          val custOut = customers.toList.map(_.asJson.noSpaces)
          (segOut, jrnOut, custOut, List.empty[InjectedError])
      }

      // Write daily files
      writeJsonLines(s"$dayDir/segments.jsonl", segmentOutput)
      writeJsonLines(s"$dayDir/journeys.jsonl", journeyOutput)
      writeJsonLines(s"$dayDir/customers.jsonl", customerOutput)

      // Write error manifest if errors present
      if (dayErrors.nonEmpty) {
        writeErrorManifest(s"$dayDir/error_manifest.jsonl", dayErrors)
      }

      allErrors ++= dayErrors
      totalJourneys += journeys.size
      totalSegments += segments.size
      totalCustomers += customers.size

      val segmentErrorCount = dayErrors.count(_.entityType == "segment")
      val journeyErrorCount = dayErrors.count(_.entityType == "journey")
      val customerErrorCount = dayErrors.count(_.entityType == "customer")

      dailyStats += DailyGenerationStats(
        date = bookingDate.toString,
        journeyCount = journeys.size,
        segmentCount = segments.size,
        customerCount = customers.size,
        segmentErrors = segmentErrorCount,
        journeyErrors = journeyErrorCount,
        customerErrors = customerErrorCount
      )

      val errorInfo = if (config.hasErrors) {
        s" | Errors: $segmentErrorCount seg, $journeyErrorCount jrn, $customerErrorCount cust"
      } else ""
      println(s"    Written: ${journeys.size} journeys, ${segments.size} segments$errorInfo")
    }

    // Build report
    val report = GenerationReport(
      startDate = config.startDate.toString,
      daysGenerated = config.daysToGenerate,
      journeysPerDay = config.journeysPerDay,
      totalJourneys = totalJourneys,
      totalSegments = totalSegments,
      totalCustomers = totalCustomers,
      errorRate = config.errorRate,
      totalErrors = allErrors.size,
      errorsByType = allErrors.groupBy(_.errorType).view.mapValues(_.size).toMap,
      errorsByField = allErrors.groupBy(_.fieldName).view.mapValues(_.size).toMap,
      errorsByEntity = allErrors.groupBy(_.entityType).view.mapValues(_.size).toMap,
      dailyStats = dailyStats.toList
    )

    // Write report
    writeGenerationReport(s"$outputDir/generation_report.json", report, config)

    report
  }

  private def processWithErrors[T: io.circe.Encoder](
    injector: ErrorInjector,
    records: List[T],
    entityType: String
  ): (List[String], List[InjectedError], ErrorStats) = {
    val jsonList = records.map(_.asJson)
    val (corrupted, errors, stats) = injector.processRecords(jsonList, entityType)
    (corrupted.map(_.noSpaces), errors, stats)
  }

  private def writeReferenceData(generator: AirlineDataGenerator, outputDir: String): Unit = {
    val refDir = new File(s"$outputDir/reference")
    if (!refDir.exists()) refDir.mkdirs()

    val airportData = generator.airports.map(a => generator.AirportOutput(
      a.code, a.name, a.city, a.countryCode, a.timezone, a.latitude, a.longitude, a.hub
    ))
    writeJsonLinesTyped(s"$refDir/airports.jsonl", airportData)

    val airlineData = generator.airlines.map(a => generator.AirlineOutput(
      a.code, a.name, a.homeCountry, a.alliance.getOrElse(""), a.hubs.mkString(",")
    ))
    writeJsonLinesTyped(s"$refDir/airlines.jsonl", airlineData)

    val countryData = generator.countries.map(c => generator.CountryOutput(
      c.code, c.name, c.region, c.timezone, c.currencyCode
    ))
    writeJsonLinesTyped(s"$refDir/countries.jsonl", countryData)

    val currencyData = generator.currencies.map(c => generator.CurrencyOutput(
      c.code, c.name, c.usdExchangeRate.toString
    ))
    writeJsonLinesTyped(s"$refDir/currencies.jsonl", currencyData)

    println(s"Reference data written to $refDir")
  }

  private def writeJsonLinesTyped[T: io.circe.Encoder](path: String, data: List[T]): Unit = {
    val writer = new PrintWriter(new File(path))
    data.foreach { item =>
      writer.println(item.asJson.noSpaces)
    }
    writer.close()
  }

  private def writeJsonLines(path: String, data: List[String]): Unit = {
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

  private def writeGenerationReport(path: String, report: GenerationReport, config: DatasetConfig): Unit = {
    val writer = new PrintWriter(new File(path))

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
         |  "dataset_id": "${config.datasetId}",
         |  "start_date": "${report.startDate}",
         |  "days_generated": ${report.daysGenerated},
         |  "journeys_per_day": ${report.journeysPerDay},
         |  "total_journeys": ${report.totalJourneys},
         |  "total_segments": ${report.totalSegments},
         |  "total_customers": ${report.totalCustomers},
         |  "seed": ${config.seed},
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
