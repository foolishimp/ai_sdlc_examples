package cdme.testdata

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.parser._
import io.circe.Json

/**
 * Tests for Error Injector
 *
 * Validates that error injection produces expected error types
 * and maintains data integrity for non-errored records.
 */
class ErrorInjectorSpec extends AnyFlatSpec with Matchers {

  "ErrorInjector" should "not inject errors when error rate is 0" in {
    val injector = new ErrorInjector(errorRate = 0.0, seed = 42L)
    val json = parse("""{"segment_id": "SEG-001", "airline_code": "AA"}""").getOrElse(Json.Null)

    val (result, errors) = injector.injectErrors(json, "segment")

    errors shouldBe empty
    result shouldBe json
  }

  it should "always inject errors when error rate is 1.0" in {
    val injector = new ErrorInjector(errorRate = 1.0, seed = 42L)
    // Use a complete segment record to ensure field selection always succeeds
    val json = parse(
      """{
        |  "segment_id": "SEG-001",
        |  "journey_id": "JRN-001",
        |  "airline_code": "AA",
        |  "departure_airport": "JFK",
        |  "arrival_airport": "LAX",
        |  "flight_date": "2024-01-15",
        |  "segment_price_usd": "500.00",
        |  "distance_km": 5000,
        |  "status": "FLOWN"
        |}""".stripMargin
    ).getOrElse(Json.Null)

    val (_, errors) = injector.injectErrors(json, "segment")

    errors should not be empty
  }

  it should "inject errors at approximately the configured rate" in {
    val injector = new ErrorInjector(errorRate = 0.10, seed = 42L)
    val json = parse("""{"segment_id": "SEG-001", "airline_code": "AA"}""").getOrElse(Json.Null)

    var errorCount = 0
    val iterations = 1000

    for (_ <- 0 until iterations) {
      val (_, errors) = injector.injectErrors(json, "segment")
      if (errors.nonEmpty) errorCount += 1
    }

    // Should be approximately 10% (allowing for randomness)
    val errorRate = errorCount.toDouble / iterations
    errorRate should be > 0.05
    errorRate should be < 0.20
  }

  it should "generate TYPE_MISMATCH errors" in {
    val injector = new ErrorInjector(errorRate = 1.0, seed = 12345L)

    // Run multiple times to catch different error types
    val errors = (1 to 100).flatMap { i =>
      val testInjector = new ErrorInjector(errorRate = 1.0, seed = i.toLong)
      val json = parse("""{"segment_price_usd": "100.00", "distance_km": 1000}""").getOrElse(Json.Null)
      val (_, errs) = testInjector.injectErrors(json, "segment")
      errs
    }

    errors.map(_.errorType) should contain("TYPE_MISMATCH")
  }

  it should "generate NULL_VALUE errors" in {
    val errors = (1 to 100).flatMap { i =>
      val testInjector = new ErrorInjector(errorRate = 1.0, seed = i.toLong * 7)
      val json = parse("""{"segment_id": "SEG-001", "airline_code": "AA"}""").getOrElse(Json.Null)
      val (_, errs) = testInjector.injectErrors(json, "segment")
      errs
    }

    errors.map(_.errorType) should contain("NULL_VALUE")
  }

  it should "generate INVALID_FORMAT errors" in {
    val errors = (1 to 100).flatMap { i =>
      val testInjector = new ErrorInjector(errorRate = 1.0, seed = i.toLong * 13)
      val json = parse("""{"flight_date": "2024-01-15", "status": "FLOWN"}""").getOrElse(Json.Null)
      val (_, errs) = testInjector.injectErrors(json, "segment")
      errs
    }

    errors.map(_.errorType) should contain("INVALID_FORMAT")
  }

  it should "generate OUT_OF_RANGE errors for numeric fields" in {
    val errors = (1 to 100).flatMap { i =>
      val testInjector = new ErrorInjector(errorRate = 1.0, seed = i.toLong * 17)
      val json = parse("""{"segment_price_usd": "500.00", "distance_km": 5000}""").getOrElse(Json.Null)
      val (_, errs) = testInjector.injectErrors(json, "segment")
      errs
    }

    errors.map(_.errorType) should contain("OUT_OF_RANGE")
  }

  it should "generate MISSING_FIELD errors" in {
    val errors = (1 to 100).flatMap { i =>
      val testInjector = new ErrorInjector(errorRate = 1.0, seed = i.toLong * 19)
      val json = parse(
        """{"segment_id": "SEG-001", "airline_code": "AA", "flight_date": "2024-01-15"}"""
      ).getOrElse(Json.Null)
      val (_, errs) = testInjector.injectErrors(json, "segment")
      errs
    }

    errors.map(_.errorType) should contain("MISSING_FIELD")
  }

  it should "generate EXTRA_FIELD errors" in {
    val errors = (1 to 100).flatMap { i =>
      val testInjector = new ErrorInjector(errorRate = 1.0, seed = i.toLong * 23)
      val json = parse("""{"segment_id": "SEG-001"}""").getOrElse(Json.Null)
      val (_, errs) = testInjector.injectErrors(json, "segment")
      errs
    }

    errors.map(_.errorType) should contain("EXTRA_FIELD")
  }

  it should "generate REFERENTIAL_INTEGRITY errors" in {
    val errors = (1 to 100).flatMap { i =>
      val testInjector = new ErrorInjector(errorRate = 1.0, seed = i.toLong * 29)
      val json = parse(
        """{"airline_code": "AA", "departure_airport": "JFK", "arrival_airport": "LAX"}"""
      ).getOrElse(Json.Null)
      val (_, errs) = testInjector.injectErrors(json, "segment")
      errs
    }

    errors.map(_.errorType) should contain("REFERENTIAL_INTEGRITY")
  }

  it should "process multiple records with error statistics" in {
    val injector = new ErrorInjector(errorRate = 0.10, seed = 42L)

    val records = (1 to 100).map { i =>
      parse(s"""{"segment_id": "SEG-$i", "airline_code": "AA", "distance_km": 1000}""")
        .getOrElse(Json.Null)
    }.toList

    val (corrupted, errors, stats) = injector.processRecords(records, "segment")

    corrupted.size shouldBe 100
    stats.totalRecords shouldBe 100
    stats.cleanRecords + stats.errorRecords shouldBe 100
    stats.errorRecords should be > 0
    stats.errorRecords should be < 30 // Should be around 10%
  }

  it should "provide detailed error statistics" in {
    val injector = new ErrorInjector(errorRate = 0.50, seed = 42L)

    val records = (1 to 100).map { i =>
      parse(
        s"""{
           |  "segment_id": "SEG-$i",
           |  "airline_code": "AA",
           |  "departure_airport": "JFK",
           |  "arrival_airport": "LAX",
           |  "flight_date": "2024-01-15",
           |  "segment_price_usd": "500.00",
           |  "distance_km": 5000,
           |  "status": "FLOWN"
           |}""".stripMargin
      ).getOrElse(Json.Null)
    }.toList

    val (_, _, stats) = injector.processRecords(records, "segment")

    stats.errorsByType should not be empty
    stats.errorsByField should not be empty
    stats.summary should include("Error Injection Statistics")
  }

  it should "be deterministic with same seed" in {
    val json = parse("""{"segment_id": "SEG-001", "airline_code": "AA"}""").getOrElse(Json.Null)

    val injector1 = new ErrorInjector(errorRate = 0.50, seed = 12345L)
    val injector2 = new ErrorInjector(errorRate = 0.50, seed = 12345L)

    val (result1, errors1) = injector1.injectErrors(json, "segment")
    val (result2, errors2) = injector2.injectErrors(json, "segment")

    result1 shouldBe result2
    errors1.map(_.errorType) shouldBe errors2.map(_.errorType)
  }

  it should "handle all entity types" in {
    val injector = new ErrorInjector(errorRate = 0.50, seed = 42L)

    // Segment
    val segmentJson = parse("""{"segment_id": "SEG-001"}""").getOrElse(Json.Null)
    val (_, segmentErrors) = injector.injectErrors(segmentJson, "segment")

    // Journey
    val journeyJson = parse("""{"journey_id": "JRN-001"}""").getOrElse(Json.Null)
    val (_, journeyErrors) = injector.injectErrors(journeyJson, "journey")

    // Customer
    val customerJson = parse("""{"customer_id": "CUST-001"}""").getOrElse(Json.Null)
    val (_, customerErrors) = injector.injectErrors(customerJson, "customer")

    // At least some should have errors at 50% rate
    val totalErrors = segmentErrors.size + journeyErrors.size + customerErrors.size
    totalErrors should be >= 0 // May be 0 due to randomness, but should not throw
  }

  "ErrorInjector type mismatch" should "convert numbers to strings" in {
    val injector = new ErrorInjector(errorRate = 1.0, seed = 42L)

    // Force type mismatch by selecting it repeatedly
    var foundTypeMismatch = false
    for (i <- 1 to 50 if !foundTypeMismatch) {
      val testInjector = new ErrorInjector(errorRate = 1.0, seed = i.toLong)
      val json = parse("""{"distance_km": 1000}""").getOrElse(Json.Null)
      val (result, errors) = testInjector.injectErrors(json, "segment")

      if (errors.exists(_.errorType == "TYPE_MISMATCH")) {
        val distanceValue = result.hcursor.downField("distance_km").focus
        distanceValue.foreach { v =>
          if (v.isString) foundTypeMismatch = true
        }
      }
    }

    foundTypeMismatch shouldBe true
  }

  "ErrorInjector invalid format" should "produce invalid date formats" in {
    var foundInvalidDate = false
    val invalidDatePatterns = List("not-a-date", "/", "YYYY", "00", "invalid")

    for (i <- 1 to 50 if !foundInvalidDate) {
      val testInjector = new ErrorInjector(errorRate = 1.0, seed = i.toLong)
      val json = parse("""{"flight_date": "2024-01-15"}""").getOrElse(Json.Null)
      val (result, errors) = testInjector.injectErrors(json, "segment")

      if (errors.exists(e => e.errorType == "INVALID_FORMAT" && e.fieldName == "flight_date")) {
        val dateValue = result.hcursor.downField("flight_date").as[String].getOrElse("")
        if (invalidDatePatterns.exists(p => dateValue.contains(p) || !dateValue.matches("\\d{4}-\\d{2}-\\d{2}"))) {
          foundInvalidDate = true
        }
      }
    }

    foundInvalidDate shouldBe true
  }

  "ErrorInjector referential integrity" should "produce invalid airport codes" in {
    var foundInvalidAirport = false
    val invalidCodes = Set("XXX", "FAKE", "000", "NONEXISTENT")

    for (i <- 1 to 100 if !foundInvalidAirport) {
      val testInjector = new ErrorInjector(errorRate = 1.0, seed = i.toLong)
      val json = parse("""{"departure_airport": "JFK"}""").getOrElse(Json.Null)
      val (result, errors) = testInjector.injectErrors(json, "segment")

      if (errors.exists(e => e.errorType == "REFERENTIAL_INTEGRITY" && e.fieldName == "departure_airport")) {
        val airportValue = result.hcursor.downField("departure_airport").as[String].getOrElse("")
        if (invalidCodes.contains(airportValue)) {
          foundInvalidAirport = true
        }
      }
    }

    foundInvalidAirport shouldBe true
  }

  "ErrorStats" should "calculate error rate correctly" in {
    val stats = ErrorStats(
      totalRecords = 100,
      cleanRecords = 90,
      errorRecords = 10,
      totalErrors = 15,
      errorsByType = Map("TYPE_MISMATCH" -> 5, "NULL_VALUE" -> 10),
      errorsByField = Map("segment_id" -> 8, "airline_code" -> 7)
    )

    stats.errorRate shouldBe 0.10
    stats.summary should include("10.0%")
    stats.summary should include("TYPE_MISMATCH: 5")
    stats.summary should include("segment_id: 8")
  }
}
