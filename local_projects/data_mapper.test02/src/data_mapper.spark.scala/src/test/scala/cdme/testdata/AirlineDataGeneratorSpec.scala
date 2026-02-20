package cdme.testdata

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.time.LocalDate
import java.io.File
import scala.util.{Try, Success, Failure}

/**
 * Tests for Airline Data Generator
 *
 * Validates that the generator produces realistic, consistent test data.
 */
class AirlineDataGeneratorSpec extends AnyFlatSpec with Matchers {

  val generator = new AirlineDataGenerator(seed = 42L)

  "AirlineDataGenerator" should "have reference data for 50+ airports" in {
    generator.airports.size should be >= 50
  }

  it should "have reference data for major airlines" in {
    generator.airlines.size should be >= 20
  }

  it should "have reference data for 40+ countries" in {
    generator.countries.size should be >= 40
  }

  it should "have reference data for 20+ currencies" in {
    generator.currencies.size should be >= 20
  }

  it should "calculate distance correctly between airports" in {
    val syd = generator.airportByCode("SYD")
    val lax = generator.airportByCode("LAX")
    val distance = generator.calculateDistance(syd, lax)

    // Sydney to LA is approximately 12,000 km
    distance should be > 11000
    distance should be < 13000
  }

  it should "calculate shorter distance for nearby airports" in {
    val lhr = generator.airportByCode("LHR")
    val cdg = generator.airportByCode("CDG")
    val distance = generator.calculateDistance(lhr, cdg)

    // London to Paris is approximately 350 km
    distance should be > 300
    distance should be < 450
  }

  it should "generate a valid customer" in {
    val customer = generator.generateCustomer("TEST-001")

    customer.customer_id shouldBe "TEST-001"
    customer.first_name should not be empty
    customer.last_name should not be empty
    customer.email should include("@")
    generator.countryByCode.contains(customer.country_code) shouldBe true
    generator.currencyByCode.contains(customer.preferred_currency) shouldBe true
  }

  it should "generate a journey with segments" in {
    val customer = generator.generateCustomer("CUST-001")
    val bookingDate = LocalDate.of(2024, 1, 1)
    val travelDate = LocalDate.of(2024, 1, 15)

    val (journey, segments) = generator.generateJourney("JRN-001", customer, bookingDate, travelDate)

    journey.journey_id shouldBe "JRN-001"
    journey.customer_id shouldBe customer.customer_id
    journey.booking_date shouldBe bookingDate.toString
    journey.outbound_date shouldBe travelDate.toString

    segments should not be empty
    segments.size should be >= 1
    segments.size should be <= 10 // Max 5 outbound + 5 return for round-trip

    // All segments should belong to the journey
    segments.foreach { seg =>
      seg.journey_id shouldBe "JRN-001"
      seg.segment_id should startWith("SEG-JRN-001")
    }
  }

  it should "generate round-trip journeys with return segments" in {
    // Run multiple times to find a round-trip (probabilistic)
    val roundTrips = (1 to 20).flatMap { i =>
      val customer = generator.generateCustomer(s"CUST-RT-$i")
      val (journey, segments) = generator.generateJourney(
        s"JRN-RT-$i", customer,
        LocalDate.of(2024, 1, 1),
        LocalDate.of(2024, 1, 15)
      )
      if (journey.journey_type == "ROUND_TRIP") Some((journey, segments)) else None
    }

    roundTrips should not be empty
    val (roundTrip, segments) = roundTrips.head

    roundTrip.journey_type shouldBe "ROUND_TRIP"
    roundTrip.return_date shouldBe defined
    segments.size should be >= 2 // At least 1 outbound + 1 return
  }

  it should "generate segments with valid prices" in {
    val customer = generator.generateCustomer("CUST-PRICE-001")
    val (_, segments) = generator.generateJourney(
      "JRN-PRICE-001", customer,
      LocalDate.of(2024, 1, 1),
      LocalDate.of(2024, 1, 15)
    )

    segments.foreach { seg =>
      seg.segment_price_usd should be > BigDecimal(0)
      seg.segment_price_usd should be < BigDecimal(20000) // Max reasonable price
      seg.distance_km should be > 0
    }
  }

  it should "generate segments with valid cabin classes" in {
    val validCabins = Set("ECONOMY", "BUSINESS", "FIRST")

    (1 to 10).foreach { i =>
      val customer = generator.generateCustomer(s"CUST-CABIN-$i")
      val (_, segments) = generator.generateJourney(
        s"JRN-CABIN-$i", customer,
        LocalDate.of(2024, 1, 1),
        LocalDate.of(2024, 1, 15)
      )

      segments.foreach { seg =>
        validCabins should contain(seg.cabin_class)
      }
    }
  }

  it should "generate some codeshare flights" in {
    val allSegments = (1 to 50).flatMap { i =>
      val customer = generator.generateCustomer(s"CUST-CS-$i")
      val (_, segments) = generator.generateJourney(
        s"JRN-CS-$i", customer,
        LocalDate.of(2024, 1, 1),
        LocalDate.of(2024, 1, 15)
      )
      segments
    }

    val codeshares = allSegments.filter(_.is_codeshare)
    codeshares should not be empty // Should have some codeshares
    codeshares.size should be < allSegments.size // Not all should be codeshares
  }

  it should "produce deterministic output with same seed" in {
    val gen1 = new AirlineDataGenerator(seed = 12345L)
    val gen2 = new AirlineDataGenerator(seed = 12345L)

    val customer1 = gen1.generateCustomer("CUST-SEED-001")
    val customer2 = gen2.generateCustomer("CUST-SEED-001")

    customer1.first_name shouldBe customer2.first_name
    customer1.last_name shouldBe customer2.last_name
    customer1.country_code shouldBe customer2.country_code
  }

  it should "select airlines with hub preference" in {
    val fra = generator.airportByCode("FRA") // Lufthansa hub
    val muc = generator.airportByCode("MUC") // Also Lufthansa

    // Run multiple times and count Lufthansa selections
    val selections = (1 to 100).map { _ =>
      val (airline, _, _) = generator.selectAirline(fra, muc)
      airline.code
    }

    val lhCount = selections.count(_ == "LH")
    // Lufthansa should be selected more often due to hub preference
    lhCount should be > 20 // At least 20% should be LH
  }

  "Small data generation" should "complete without errors" in {
    val outputDir = "target/test-data/airline-small"
    val dir = new File(outputDir)

    // Clean up previous run
    if (dir.exists()) {
      dir.listFiles().foreach { f =>
        if (f.isDirectory) f.listFiles().foreach(_.delete())
        f.delete()
      }
    }

    val smallGenerator = new AirlineDataGenerator(seed = 999L)

    // Generate just 100 journeys for 1 day
    noException should be thrownBy {
      smallGenerator.generateMonth(
        startDate = LocalDate.of(2024, 1, 1),
        daysToGenerate = 1,
        journeysPerDay = 100,
        outputDir = outputDir
      )
    }

    // Verify output files exist
    new File(s"$outputDir/reference/airports.jsonl").exists() shouldBe true
    new File(s"$outputDir/reference/airlines.jsonl").exists() shouldBe true
    new File(s"$outputDir/bookings/2024-01-01/journeys.jsonl").exists() shouldBe true
    new File(s"$outputDir/bookings/2024-01-01/segments.jsonl").exists() shouldBe true
    new File(s"$outputDir/bookings/2024-01-01/customers.jsonl").exists() shouldBe true
  }
}
