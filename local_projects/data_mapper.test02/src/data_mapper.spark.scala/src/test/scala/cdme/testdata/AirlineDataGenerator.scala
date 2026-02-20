package cdme.testdata

import java.time.{LocalDate, LocalDateTime, LocalTime, ZoneOffset}
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import scala.util.Random
import scala.collection.mutable.ListBuffer
import java.io.{File, PrintWriter}
import io.circe.syntax._
import io.circe.generic.auto._
import io.circe.{Encoder, Json}

/**
 * Airline Test Data Generator
 *
 * Generates realistic multi-leg international travel data for system testing.
 * Creates 100k journeys per day over a 10-day period (1M total journeys, ~3-5M segments).
 *
 * Features:
 * - Multi-leg journeys (1-5 legs)
 * - International routes across 50+ airports
 * - Multiple airlines with codeshare agreements
 * - Currency conversion (10+ currencies)
 * - Realistic pricing by cabin class and distance
 * - Round-trip and multi-city journeys
 * - Travel spanning days, weeks, or months
 *
 * Usage:
 *   val generator = new AirlineDataGenerator(seed = 42L)
 *   generator.generateMonth(
 *     startDate = LocalDate.of(2024, 1, 1),
 *     daysToGenerate = 10,
 *     journeysPerDay = 100000,
 *     outputDir = "test-data/airline"
 *   )
 */
class AirlineDataGenerator(seed: Long = System.currentTimeMillis()) {

  private val random = new Random(seed)

  // ============================================
  // Reference Data
  // ============================================

  case class AirportRef(
    code: String,
    name: String,
    city: String,
    countryCode: String,
    timezone: String,
    latitude: Double,
    longitude: Double,
    hub: Boolean = false
  )

  case class AirlineRef(
    code: String,
    name: String,
    homeCountry: String,
    alliance: Option[String],
    hubs: List[String]
  )

  case class CountryRef(
    code: String,
    name: String,
    region: String,
    timezone: String,
    currencyCode: String
  )

  case class CurrencyRef(
    code: String,
    name: String,
    usdExchangeRate: BigDecimal
  )

  // Major international airports (50+)
  val airports: List[AirportRef] = List(
    // North America
    AirportRef("JFK", "John F Kennedy International", "New York", "US", "America/New_York", 40.6413, -73.7781, hub = true),
    AirportRef("LAX", "Los Angeles International", "Los Angeles", "US", "America/Los_Angeles", 33.9416, -118.4085, hub = true),
    AirportRef("ORD", "O'Hare International", "Chicago", "US", "America/Chicago", 41.9742, -87.9073, hub = true),
    AirportRef("DFW", "Dallas Fort Worth", "Dallas", "US", "America/Chicago", 32.8998, -97.0403, hub = true),
    AirportRef("MIA", "Miami International", "Miami", "US", "America/New_York", 25.7959, -80.2870),
    AirportRef("SFO", "San Francisco International", "San Francisco", "US", "America/Los_Angeles", 37.6213, -122.3790, hub = true),
    AirportRef("YYZ", "Toronto Pearson", "Toronto", "CA", "America/Toronto", 43.6777, -79.6248, hub = true),
    AirportRef("YVR", "Vancouver International", "Vancouver", "CA", "America/Vancouver", 49.1967, -123.1815),
    AirportRef("MEX", "Mexico City International", "Mexico City", "MX", "America/Mexico_City", 19.4363, -99.0721),

    // Europe
    AirportRef("LHR", "Heathrow", "London", "GB", "Europe/London", 51.4700, -0.4543, hub = true),
    AirportRef("CDG", "Charles de Gaulle", "Paris", "FR", "Europe/Paris", 49.0097, 2.5479, hub = true),
    AirportRef("FRA", "Frankfurt", "Frankfurt", "DE", "Europe/Berlin", 50.0379, 8.5622, hub = true),
    AirportRef("AMS", "Schiphol", "Amsterdam", "NL", "Europe/Amsterdam", 52.3105, 4.7683, hub = true),
    AirportRef("MAD", "Madrid Barajas", "Madrid", "ES", "Europe/Madrid", 40.4983, -3.5676),
    AirportRef("FCO", "Fiumicino", "Rome", "IT", "Europe/Rome", 41.8003, 12.2389),
    AirportRef("MUC", "Munich", "Munich", "DE", "Europe/Berlin", 48.3537, 11.7750),
    AirportRef("ZRH", "Zurich", "Zurich", "CH", "Europe/Zurich", 47.4647, 8.5492),
    AirportRef("VIE", "Vienna", "Vienna", "AT", "Europe/Vienna", 48.1103, 16.5697),
    AirportRef("CPH", "Copenhagen", "Copenhagen", "DK", "Europe/Copenhagen", 55.6180, 12.6508),
    AirportRef("ARN", "Stockholm Arlanda", "Stockholm", "SE", "Europe/Stockholm", 59.6519, 17.9186),
    AirportRef("DUB", "Dublin", "Dublin", "IE", "Europe/Dublin", 53.4264, -6.2499),
    AirportRef("LIS", "Lisbon", "Lisbon", "PT", "Europe/Lisbon", 38.7756, -9.1354),
    AirportRef("ATH", "Athens", "Athens", "GR", "Europe/Athens", 37.9364, 23.9445),
    AirportRef("IST", "Istanbul", "Istanbul", "TR", "Europe/Istanbul", 41.2753, 28.7519, hub = true),

    // Asia Pacific
    AirportRef("SIN", "Changi", "Singapore", "SG", "Asia/Singapore", 1.3644, 103.9915, hub = true),
    AirportRef("HKG", "Hong Kong International", "Hong Kong", "HK", "Asia/Hong_Kong", 22.3080, 113.9185, hub = true),
    AirportRef("NRT", "Narita", "Tokyo", "JP", "Asia/Tokyo", 35.7647, 140.3864, hub = true),
    AirportRef("HND", "Haneda", "Tokyo", "JP", "Asia/Tokyo", 35.5494, 139.7798),
    AirportRef("ICN", "Incheon", "Seoul", "KR", "Asia/Seoul", 37.4602, 126.4407, hub = true),
    AirportRef("PEK", "Beijing Capital", "Beijing", "CN", "Asia/Shanghai", 40.0799, 116.6031, hub = true),
    AirportRef("PVG", "Shanghai Pudong", "Shanghai", "CN", "Asia/Shanghai", 31.1443, 121.8083),
    AirportRef("BKK", "Suvarnabhumi", "Bangkok", "TH", "Asia/Bangkok", 13.6900, 100.7501),
    AirportRef("KUL", "Kuala Lumpur", "Kuala Lumpur", "MY", "Asia/Kuala_Lumpur", 2.7456, 101.7072),
    AirportRef("DEL", "Indira Gandhi", "Delhi", "IN", "Asia/Kolkata", 28.5562, 77.1000, hub = true),
    AirportRef("BOM", "Chhatrapati Shivaji", "Mumbai", "IN", "Asia/Kolkata", 19.0896, 72.8656),
    AirportRef("CGK", "Soekarno-Hatta", "Jakarta", "ID", "Asia/Jakarta", -6.1256, 106.6559),
    AirportRef("MNL", "Ninoy Aquino", "Manila", "PH", "Asia/Manila", 14.5086, 121.0194),

    // Middle East
    AirportRef("DXB", "Dubai International", "Dubai", "AE", "Asia/Dubai", 25.2532, 55.3657, hub = true),
    AirportRef("DOH", "Hamad International", "Doha", "QA", "Asia/Qatar", 25.2609, 51.6138, hub = true),
    AirportRef("AUH", "Abu Dhabi", "Abu Dhabi", "AE", "Asia/Dubai", 24.4331, 54.6511),
    AirportRef("TLV", "Ben Gurion", "Tel Aviv", "IL", "Asia/Jerusalem", 32.0055, 34.8854),

    // Oceania
    AirportRef("SYD", "Sydney Kingsford Smith", "Sydney", "AU", "Australia/Sydney", -33.9399, 151.1753, hub = true),
    AirportRef("MEL", "Melbourne Tullamarine", "Melbourne", "AU", "Australia/Melbourne", -37.6690, 144.8410),
    AirportRef("BNE", "Brisbane", "Brisbane", "AU", "Australia/Brisbane", -27.3942, 153.1218),
    AirportRef("AKL", "Auckland", "Auckland", "NZ", "Pacific/Auckland", -37.0082, 174.7850),

    // South America
    AirportRef("GRU", "Guarulhos", "Sao Paulo", "BR", "America/Sao_Paulo", -23.4356, -46.4731, hub = true),
    AirportRef("EZE", "Ezeiza", "Buenos Aires", "AR", "America/Argentina/Buenos_Aires", -34.8222, -58.5358),
    AirportRef("SCL", "Santiago", "Santiago", "CL", "America/Santiago", -33.3930, -70.7858),
    AirportRef("BOG", "El Dorado", "Bogota", "CO", "America/Bogota", 4.7016, -74.1469),
    AirportRef("LIM", "Jorge Chavez", "Lima", "PE", "America/Lima", -12.0219, -77.1143),

    // Africa
    AirportRef("JNB", "O.R. Tambo", "Johannesburg", "ZA", "Africa/Johannesburg", -26.1367, 28.2411, hub = true),
    AirportRef("CPT", "Cape Town", "Cape Town", "ZA", "Africa/Johannesburg", -33.9715, 18.6021),
    AirportRef("CAI", "Cairo", "Cairo", "EG", "Africa/Cairo", 30.1219, 31.4056),
    AirportRef("NBO", "Jomo Kenyatta", "Nairobi", "KE", "Africa/Nairobi", -1.3192, 36.9278),
    AirportRef("CMN", "Mohammed V", "Casablanca", "MA", "Africa/Casablanca", 33.3675, -7.5898)
  )

  val airportByCode: Map[String, AirportRef] = airports.map(a => a.code -> a).toMap
  val hubAirports: List[AirportRef] = airports.filter(_.hub)

  // Major international airlines
  val airlines: List[AirlineRef] = List(
    AirlineRef("AA", "American Airlines", "US", Some("oneworld"), List("DFW", "MIA", "ORD", "JFK")),
    AirlineRef("UA", "United Airlines", "US", Some("Star Alliance"), List("ORD", "SFO", "IAH", "EWR")),
    AirlineRef("DL", "Delta Air Lines", "US", Some("SkyTeam"), List("ATL", "DTW", "JFK", "LAX")),
    AirlineRef("BA", "British Airways", "GB", Some("oneworld"), List("LHR")),
    AirlineRef("LH", "Lufthansa", "DE", Some("Star Alliance"), List("FRA", "MUC")),
    AirlineRef("AF", "Air France", "FR", Some("SkyTeam"), List("CDG")),
    AirlineRef("KL", "KLM", "NL", Some("SkyTeam"), List("AMS")),
    AirlineRef("EK", "Emirates", "AE", None, List("DXB")),
    AirlineRef("QR", "Qatar Airways", "QA", Some("oneworld"), List("DOH")),
    AirlineRef("EY", "Etihad Airways", "AE", None, List("AUH")),
    AirlineRef("SQ", "Singapore Airlines", "SG", Some("Star Alliance"), List("SIN")),
    AirlineRef("CX", "Cathay Pacific", "HK", Some("oneworld"), List("HKG")),
    AirlineRef("QF", "Qantas", "AU", Some("oneworld"), List("SYD", "MEL")),
    AirlineRef("NH", "All Nippon Airways", "JP", Some("Star Alliance"), List("NRT", "HND")),
    AirlineRef("JL", "Japan Airlines", "JP", Some("oneworld"), List("NRT", "HND")),
    AirlineRef("KE", "Korean Air", "KR", Some("SkyTeam"), List("ICN")),
    AirlineRef("CA", "Air China", "CN", Some("Star Alliance"), List("PEK")),
    AirlineRef("MU", "China Eastern", "CN", Some("SkyTeam"), List("PVG")),
    AirlineRef("TK", "Turkish Airlines", "TR", Some("Star Alliance"), List("IST")),
    AirlineRef("AC", "Air Canada", "CA", Some("Star Alliance"), List("YYZ", "YVR")),
    AirlineRef("LX", "Swiss", "CH", Some("Star Alliance"), List("ZRH")),
    AirlineRef("AZ", "ITA Airways", "IT", Some("SkyTeam"), List("FCO")),
    AirlineRef("IB", "Iberia", "ES", Some("oneworld"), List("MAD")),
    AirlineRef("SA", "South African Airways", "ZA", Some("Star Alliance"), List("JNB")),
    AirlineRef("LA", "LATAM", "CL", Some("oneworld"), List("SCL", "GRU"))
  )

  val airlineByCode: Map[String, AirlineRef] = airlines.map(a => a.code -> a).toMap

  // Countries
  val countries: List[CountryRef] = List(
    CountryRef("US", "United States", "North America", "America/New_York", "USD"),
    CountryRef("CA", "Canada", "North America", "America/Toronto", "CAD"),
    CountryRef("MX", "Mexico", "North America", "America/Mexico_City", "MXN"),
    CountryRef("GB", "United Kingdom", "Europe", "Europe/London", "GBP"),
    CountryRef("FR", "France", "Europe", "Europe/Paris", "EUR"),
    CountryRef("DE", "Germany", "Europe", "Europe/Berlin", "EUR"),
    CountryRef("NL", "Netherlands", "Europe", "Europe/Amsterdam", "EUR"),
    CountryRef("ES", "Spain", "Europe", "Europe/Madrid", "EUR"),
    CountryRef("IT", "Italy", "Europe", "Europe/Rome", "EUR"),
    CountryRef("CH", "Switzerland", "Europe", "Europe/Zurich", "CHF"),
    CountryRef("AT", "Austria", "Europe", "Europe/Vienna", "EUR"),
    CountryRef("SE", "Sweden", "Europe", "Europe/Stockholm", "SEK"),
    CountryRef("DK", "Denmark", "Europe", "Europe/Copenhagen", "DKK"),
    CountryRef("IE", "Ireland", "Europe", "Europe/Dublin", "EUR"),
    CountryRef("PT", "Portugal", "Europe", "Europe/Lisbon", "EUR"),
    CountryRef("GR", "Greece", "Europe", "Europe/Athens", "EUR"),
    CountryRef("TR", "Turkey", "Europe", "Europe/Istanbul", "TRY"),
    CountryRef("SG", "Singapore", "Asia", "Asia/Singapore", "SGD"),
    CountryRef("HK", "Hong Kong", "Asia", "Asia/Hong_Kong", "HKD"),
    CountryRef("JP", "Japan", "Asia", "Asia/Tokyo", "JPY"),
    CountryRef("KR", "South Korea", "Asia", "Asia/Seoul", "KRW"),
    CountryRef("CN", "China", "Asia", "Asia/Shanghai", "CNY"),
    CountryRef("TH", "Thailand", "Asia", "Asia/Bangkok", "THB"),
    CountryRef("MY", "Malaysia", "Asia", "Asia/Kuala_Lumpur", "MYR"),
    CountryRef("IN", "India", "Asia", "Asia/Kolkata", "INR"),
    CountryRef("ID", "Indonesia", "Asia", "Asia/Jakarta", "IDR"),
    CountryRef("PH", "Philippines", "Asia", "Asia/Manila", "PHP"),
    CountryRef("AE", "United Arab Emirates", "Middle East", "Asia/Dubai", "AED"),
    CountryRef("QA", "Qatar", "Middle East", "Asia/Qatar", "QAR"),
    CountryRef("IL", "Israel", "Middle East", "Asia/Jerusalem", "ILS"),
    CountryRef("AU", "Australia", "Oceania", "Australia/Sydney", "AUD"),
    CountryRef("NZ", "New Zealand", "Oceania", "Pacific/Auckland", "NZD"),
    CountryRef("BR", "Brazil", "South America", "America/Sao_Paulo", "BRL"),
    CountryRef("AR", "Argentina", "South America", "America/Argentina/Buenos_Aires", "ARS"),
    CountryRef("CL", "Chile", "South America", "America/Santiago", "CLP"),
    CountryRef("CO", "Colombia", "South America", "America/Bogota", "COP"),
    CountryRef("PE", "Peru", "South America", "America/Lima", "PEN"),
    CountryRef("ZA", "South Africa", "Africa", "Africa/Johannesburg", "ZAR"),
    CountryRef("EG", "Egypt", "Africa", "Africa/Cairo", "EGP"),
    CountryRef("KE", "Kenya", "Africa", "Africa/Nairobi", "KES"),
    CountryRef("MA", "Morocco", "Africa", "Africa/Casablanca", "MAD")
  )

  val countryByCode: Map[String, CountryRef] = countries.map(c => c.code -> c).toMap

  // Currencies with USD exchange rates (approximate)
  val currencies: List[CurrencyRef] = List(
    CurrencyRef("USD", "US Dollar", BigDecimal(1.0)),
    CurrencyRef("EUR", "Euro", BigDecimal(1.08)),
    CurrencyRef("GBP", "British Pound", BigDecimal(1.27)),
    CurrencyRef("JPY", "Japanese Yen", BigDecimal(0.0067)),
    CurrencyRef("AUD", "Australian Dollar", BigDecimal(0.65)),
    CurrencyRef("CAD", "Canadian Dollar", BigDecimal(0.74)),
    CurrencyRef("CHF", "Swiss Franc", BigDecimal(1.13)),
    CurrencyRef("CNY", "Chinese Yuan", BigDecimal(0.14)),
    CurrencyRef("HKD", "Hong Kong Dollar", BigDecimal(0.13)),
    CurrencyRef("SGD", "Singapore Dollar", BigDecimal(0.74)),
    CurrencyRef("INR", "Indian Rupee", BigDecimal(0.012)),
    CurrencyRef("MXN", "Mexican Peso", BigDecimal(0.058)),
    CurrencyRef("BRL", "Brazilian Real", BigDecimal(0.20)),
    CurrencyRef("KRW", "South Korean Won", BigDecimal(0.00075)),
    CurrencyRef("AED", "UAE Dirham", BigDecimal(0.27)),
    CurrencyRef("THB", "Thai Baht", BigDecimal(0.028)),
    CurrencyRef("SEK", "Swedish Krona", BigDecimal(0.095)),
    CurrencyRef("DKK", "Danish Krone", BigDecimal(0.145)),
    CurrencyRef("NZD", "New Zealand Dollar", BigDecimal(0.61)),
    CurrencyRef("ZAR", "South African Rand", BigDecimal(0.053)),
    CurrencyRef("TRY", "Turkish Lira", BigDecimal(0.031)),
    CurrencyRef("MYR", "Malaysian Ringgit", BigDecimal(0.21)),
    CurrencyRef("QAR", "Qatari Riyal", BigDecimal(0.27)),
    CurrencyRef("ILS", "Israeli Shekel", BigDecimal(0.27))
  )

  val currencyByCode: Map[String, CurrencyRef] = currencies.map(c => c.code -> c).toMap

  // ============================================
  // Output Data Models
  // ============================================

  case class Customer(
    customer_id: String,
    first_name: String,
    last_name: String,
    email: String,
    loyalty_tier: Option[String],
    country_code: String,
    preferred_currency: String
  )

  case class Journey(
    journey_id: String,
    customer_id: String,
    booking_date: String,
    journey_type: String,
    total_price_local: BigDecimal,
    local_currency: String,
    total_price_usd: BigDecimal,
    status: String,
    outbound_date: String,
    return_date: Option[String]
  )

  case class FlightSegment(
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

  // ============================================
  // Data Generation Logic
  // ============================================

  private val firstNames = List(
    "James", "Mary", "John", "Patricia", "Robert", "Jennifer", "Michael", "Linda",
    "William", "Elizabeth", "David", "Barbara", "Richard", "Susan", "Joseph", "Jessica",
    "Thomas", "Sarah", "Charles", "Karen", "Christopher", "Nancy", "Daniel", "Lisa",
    "Wei", "Yuki", "Mohammed", "Fatima", "Hans", "Sophie", "Carlos", "Maria",
    "Raj", "Priya", "Ahmed", "Aisha", "Chen", "Mei", "Kim", "Min-ji",
    "Pierre", "Marie", "Giovanni", "Giulia", "Hiroshi", "Sakura", "Sven", "Anna"
  )

  private val lastNames = List(
    "Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis",
    "Rodriguez", "Martinez", "Hernandez", "Lopez", "Gonzalez", "Wilson", "Anderson",
    "Thomas", "Taylor", "Moore", "Jackson", "Martin", "Lee", "Perez", "Thompson",
    "Wang", "Li", "Zhang", "Chen", "Liu", "Yang", "Huang", "Wu",
    "Kim", "Park", "Tanaka", "Suzuki", "Sato", "Mueller", "Schmidt", "Schneider",
    "Patel", "Singh", "Kumar", "Sharma", "Ali", "Khan", "Hassan", "Ahmed"
  )

  private val loyaltyTiers = List("BRONZE", "SILVER", "GOLD", "PLATINUM", "DIAMOND")
  private val cabinClasses = List("ECONOMY", "ECONOMY", "ECONOMY", "ECONOMY", "BUSINESS", "BUSINESS", "FIRST")
  private val journeyTypes = List("ONE_WAY", "ROUND_TRIP", "ROUND_TRIP", "ROUND_TRIP", "MULTI_CITY")

  /**
   * Calculate great circle distance between two airports in km
   */
  def calculateDistance(from: AirportRef, to: AirportRef): Int = {
    val R = 6371 // Earth radius in km
    val lat1 = Math.toRadians(from.latitude)
    val lat2 = Math.toRadians(to.latitude)
    val dLat = Math.toRadians(to.latitude - from.latitude)
    val dLon = Math.toRadians(to.longitude - from.longitude)

    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(lat1) * Math.cos(lat2) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

    (R * c).toInt
  }

  /**
   * Calculate flight duration based on distance (approximate)
   */
  def calculateFlightDuration(distanceKm: Int): Int = {
    // Average speed ~800 km/h plus 30 min for takeoff/landing
    val hours = distanceKm / 800.0 + 0.5
    (hours * 60).toInt // Return minutes
  }

  /**
   * Calculate base price based on distance and cabin class
   */
  def calculateBasePrice(distanceKm: Int, cabinClass: String): BigDecimal = {
    val basePerKm = cabinClass match {
      case "ECONOMY" => BigDecimal(0.08)
      case "BUSINESS" => BigDecimal(0.25)
      case "FIRST" => BigDecimal(0.50)
    }
    val base = distanceKm * basePerKm
    // Add randomness (±30%)
    val variance = 0.7 + random.nextDouble() * 0.6
    (base * variance).setScale(2, BigDecimal.RoundingMode.HALF_UP)
  }

  /**
   * Select a random airline that can fly the route (based on hubs)
   */
  def selectAirline(from: AirportRef, to: AirportRef): (AirlineRef, Boolean, Option[String]) = {
    // Prefer airlines with hub at departure or arrival
    val hubAirlines = airlines.filter(a =>
      a.hubs.contains(from.code) || a.hubs.contains(to.code)
    )

    val selectedAirline = if (hubAirlines.nonEmpty && random.nextDouble() < 0.7) {
      hubAirlines(random.nextInt(hubAirlines.size))
    } else {
      airlines(random.nextInt(airlines.size))
    }

    // 15% chance of codeshare
    val isCodeshare = random.nextDouble() < 0.15
    val operatingAirline = if (isCodeshare) {
      val sameAlliance = airlines.filter(a =>
        a.alliance == selectedAirline.alliance && a.code != selectedAirline.code
      )
      if (sameAlliance.nonEmpty) Some(sameAlliance(random.nextInt(sameAlliance.size)).code)
      else None
    } else None

    (selectedAirline, isCodeshare, operatingAirline)
  }

  /**
   * Generate a realistic route (list of airports) for a journey
   */
  def generateRoute(numLegs: Int, departureCountry: String): List[AirportRef] = {
    val startAirports = airports.filter(_.countryCode == departureCountry)
    val start = if (startAirports.nonEmpty) {
      startAirports(random.nextInt(startAirports.size))
    } else {
      airports(random.nextInt(airports.size))
    }

    val route = ListBuffer[AirportRef](start)
    var current = start

    for (_ <- 1 to numLegs) {
      // Prefer hub connections for multi-leg journeys
      val candidates = if (random.nextDouble() < 0.6) {
        hubAirports.filter(_.code != current.code)
      } else {
        airports.filter(_.code != current.code)
      }

      val next = candidates(random.nextInt(candidates.size))
      route += next
      current = next
    }

    route.toList
  }

  /**
   * Generate a customer
   */
  def generateCustomer(customerId: String): Customer = {
    val country = countries(random.nextInt(countries.size))
    val loyaltyTier = if (random.nextDouble() < 0.6) {
      Some(loyaltyTiers(random.nextInt(loyaltyTiers.size)))
    } else None

    Customer(
      customer_id = customerId,
      first_name = firstNames(random.nextInt(firstNames.size)),
      last_name = lastNames(random.nextInt(lastNames.size)),
      email = s"customer_$customerId@email.com",
      loyalty_tier = loyaltyTier,
      country_code = country.code,
      preferred_currency = country.currencyCode
    )
  }

  /**
   * Generate a complete journey with segments
   */
  def generateJourney(
    journeyId: String,
    customer: Customer,
    bookingDate: LocalDate,
    travelDate: LocalDate
  ): (Journey, List[FlightSegment]) = {

    // Determine journey type and number of legs
    val journeyType = journeyTypes(random.nextInt(journeyTypes.size))
    val numOutboundLegs = journeyType match {
      case "ONE_WAY" => 1 + random.nextInt(3) // 1-3 legs
      case "ROUND_TRIP" => 1 + random.nextInt(3) // 1-3 legs each way
      case "MULTI_CITY" => 2 + random.nextInt(4) // 2-5 legs total
    }

    val cabinClass = cabinClasses(random.nextInt(cabinClasses.size))
    val outboundRoute = generateRoute(numOutboundLegs, customer.country_code)

    val segments = ListBuffer[FlightSegment]()
    var currentDate = travelDate
    var currentTime = LocalTime.of(6 + random.nextInt(14), random.nextInt(60)) // 6am - 8pm
    var totalPriceUsd = BigDecimal(0)
    var segmentSeq = 1

    // Generate outbound segments
    for (i <- 0 until outboundRoute.size - 1) {
      val from = outboundRoute(i)
      val to = outboundRoute(i + 1)
      val distance = calculateDistance(from, to)
      val duration = calculateFlightDuration(distance)
      val (airline, isCodeshare, operatingAirline) = selectAirline(from, to)

      val departureDateTime = LocalDateTime.of(currentDate, currentTime)
      val arrivalDateTime = departureDateTime.plusMinutes(duration)

      val priceUsd = calculateBasePrice(distance, cabinClass)
      totalPriceUsd += priceUsd

      // Determine segment status (95% flown for past dates, 5% cancelled)
      val status = if (currentDate.isBefore(LocalDate.now()) || currentDate.isEqual(LocalDate.now())) {
        if (random.nextDouble() < 0.95) "FLOWN" else "CANCELLED"
      } else "SCHEDULED"

      segments += FlightSegment(
        segment_id = s"SEG-$journeyId-$segmentSeq",
        journey_id = journeyId,
        flight_number = s"${airline.code}${100 + random.nextInt(900)}",
        airline_code = airline.code,
        departure_airport = from.code,
        arrival_airport = to.code,
        departure_datetime = departureDateTime.toString,
        arrival_datetime = arrivalDateTime.toString,
        flight_date = currentDate.toString,
        cabin_class = cabinClass,
        segment_price_local = priceUsd, // Simplified: using USD as local
        segment_price_usd = priceUsd,
        segment_sequence = segmentSeq,
        is_codeshare = isCodeshare,
        operating_airline = operatingAirline,
        distance_km = distance,
        status = status
      )

      segmentSeq += 1

      // Add connection time (1-4 hours) or next day
      if (arrivalDateTime.getHour >= 22 || random.nextDouble() < 0.2) {
        currentDate = arrivalDateTime.toLocalDate.plusDays(1)
        currentTime = LocalTime.of(8 + random.nextInt(10), random.nextInt(60))
      } else {
        currentDate = arrivalDateTime.toLocalDate
        currentTime = arrivalDateTime.toLocalTime.plusHours(1 + random.nextInt(3))
      }
    }

    // Generate return segments for round-trip
    val returnDate: Option[String] = if (journeyType == "ROUND_TRIP") {
      // Return 1-30 days after last outbound segment
      val returnStartDate = currentDate.plusDays(1 + random.nextInt(30))
      val returnRoute = outboundRoute.reverse

      currentDate = returnStartDate
      currentTime = LocalTime.of(6 + random.nextInt(14), random.nextInt(60))

      for (i <- 0 until returnRoute.size - 1) {
        val from = returnRoute(i)
        val to = returnRoute(i + 1)
        val distance = calculateDistance(from, to)
        val duration = calculateFlightDuration(distance)
        val (airline, isCodeshare, operatingAirline) = selectAirline(from, to)

        val departureDateTime = LocalDateTime.of(currentDate, currentTime)
        val arrivalDateTime = departureDateTime.plusMinutes(duration)

        val priceUsd = calculateBasePrice(distance, cabinClass)
        totalPriceUsd += priceUsd

        val status = if (currentDate.isBefore(LocalDate.now()) || currentDate.isEqual(LocalDate.now())) {
          if (random.nextDouble() < 0.95) "FLOWN" else "CANCELLED"
        } else "SCHEDULED"

        segments += FlightSegment(
          segment_id = s"SEG-$journeyId-$segmentSeq",
          journey_id = journeyId,
          flight_number = s"${airline.code}${100 + random.nextInt(900)}",
          airline_code = airline.code,
          departure_airport = from.code,
          arrival_airport = to.code,
          departure_datetime = departureDateTime.toString,
          arrival_datetime = arrivalDateTime.toString,
          flight_date = currentDate.toString,
          cabin_class = cabinClass,
          segment_price_local = priceUsd,
          segment_price_usd = priceUsd,
          segment_sequence = segmentSeq,
          is_codeshare = isCodeshare,
          operating_airline = operatingAirline,
          distance_km = distance,
          status = status
        )

        segmentSeq += 1

        if (arrivalDateTime.getHour >= 22 || random.nextDouble() < 0.2) {
          currentDate = arrivalDateTime.toLocalDate.plusDays(1)
          currentTime = LocalTime.of(8 + random.nextInt(10), random.nextInt(60))
        } else {
          currentDate = arrivalDateTime.toLocalDate
          currentTime = arrivalDateTime.toLocalTime.plusHours(1 + random.nextInt(3))
        }
      }

      Some(returnStartDate.toString)
    } else None

    // Determine journey status
    val journeyStatus = {
      val segmentStatuses = segments.map(_.status).toList
      if (segmentStatuses.forall(_ == "FLOWN")) "COMPLETED"
      else if (segmentStatuses.exists(_ == "CANCELLED")) "CANCELLED"
      else "CONFIRMED"
    }

    // Get customer currency for local price
    val customerCurrency = currencyByCode.getOrElse(customer.preferred_currency, currencies.head)
    val totalPriceLocal = (totalPriceUsd / customerCurrency.usdExchangeRate)
      .setScale(2, BigDecimal.RoundingMode.HALF_UP)

    val journey = Journey(
      journey_id = journeyId,
      customer_id = customer.customer_id,
      booking_date = bookingDate.toString,
      journey_type = journeyType,
      total_price_local = totalPriceLocal,
      local_currency = customer.preferred_currency,
      total_price_usd = totalPriceUsd.setScale(2, BigDecimal.RoundingMode.HALF_UP),
      status = journeyStatus,
      outbound_date = travelDate.toString,
      return_date = returnDate
    )

    (journey, segments.toList)
  }

  /**
   * Generate test data for a period
   *
   * @param startDate First booking date
   * @param daysToGenerate Number of days to generate
   * @param journeysPerDay Number of journeys per day
   * @param outputDir Output directory for JSON/CSV files
   */
  def generateMonth(
    startDate: LocalDate,
    daysToGenerate: Int,
    journeysPerDay: Int,
    outputDir: String
  ): Unit = {
    val dir = new File(outputDir)
    if (!dir.exists()) dir.mkdirs()

    // Write reference data (once)
    writeReferenceData(outputDir)

    println(s"Generating $daysToGenerate days of data, $journeysPerDay journeys/day...")
    println(s"Total journeys: ${daysToGenerate * journeysPerDay}")

    var totalSegments = 0L
    var totalJourneys = 0L

    for (dayOffset <- 0 until daysToGenerate) {
      val bookingDate = startDate.plusDays(dayOffset)
      val dayDir = new File(s"$outputDir/bookings/${bookingDate.toString}")
      if (!dayDir.exists()) dayDir.mkdirs()

      val journeys = ListBuffer[Journey]()
      val segments = ListBuffer[FlightSegment]()
      val customers = ListBuffer[Customer]()

      println(s"  Generating day ${dayOffset + 1}/$daysToGenerate: $bookingDate")

      for (i <- 1 to journeysPerDay) {
        val customerId = f"CUST-${bookingDate.toString}-$i%06d"
        val journeyId = f"JRN-${bookingDate.toString}-$i%06d"

        val customer = generateCustomer(customerId)
        customers += customer

        // Travel date is 1-90 days after booking
        val travelDate = bookingDate.plusDays(1 + random.nextInt(90))

        val (journey, journeySegments) = generateJourney(journeyId, customer, bookingDate, travelDate)
        journeys += journey
        segments ++= journeySegments

        if (i % 10000 == 0) {
          println(s"    Processed $i/$journeysPerDay journeys...")
        }
      }

      // Write daily files
      writeJsonLines(s"$dayDir/customers.jsonl", customers.toList)
      writeJsonLines(s"$dayDir/journeys.jsonl", journeys.toList)
      writeJsonLines(s"$dayDir/segments.jsonl", segments.toList)

      totalJourneys += journeys.size
      totalSegments += segments.size

      println(s"    Written: ${journeys.size} journeys, ${segments.size} segments")
    }

    // Write summary
    val summaryWriter = new PrintWriter(new File(s"$outputDir/generation_summary.json"))
    summaryWriter.write(
      s"""{
         |  "start_date": "${startDate.toString}",
         |  "days_generated": $daysToGenerate,
         |  "journeys_per_day": $journeysPerDay,
         |  "total_journeys": $totalJourneys,
         |  "total_segments": $totalSegments,
         |  "avg_segments_per_journey": ${totalSegments.toDouble / totalJourneys}
         |}""".stripMargin
    )
    summaryWriter.close()

    println(s"\nGeneration complete!")
    println(s"  Total journeys: $totalJourneys")
    println(s"  Total segments: $totalSegments")
    println(s"  Avg segments/journey: ${totalSegments.toDouble / totalJourneys}")
    println(s"  Output directory: $outputDir")
  }

  // Case classes for reference data serialization
  case class AirportOutput(
    airport_code: String,
    airport_name: String,
    city: String,
    country_code: String,
    timezone: String,
    latitude: Double,
    longitude: Double,
    is_hub: Boolean
  )

  case class AirlineOutput(
    airline_code: String,
    airline_name: String,
    home_country: String,
    alliance: String,
    hubs: String
  )

  case class CountryOutput(
    country_code: String,
    country_name: String,
    region: String,
    timezone: String,
    currency_code: String
  )

  case class CurrencyOutput(
    currency_code: String,
    currency_name: String,
    usd_exchange_rate: String
  )

  /**
   * Write reference data files
   */
  private def writeReferenceData(outputDir: String): Unit = {
    val refDir = new File(s"$outputDir/reference")
    if (!refDir.exists()) refDir.mkdirs()

    // Airports
    val airportData = airports.map(a => AirportOutput(
      a.code, a.name, a.city, a.countryCode, a.timezone, a.latitude, a.longitude, a.hub
    ))
    writeJsonLines(s"$refDir/airports.jsonl", airportData)

    // Airlines
    val airlineData = airlines.map(a => AirlineOutput(
      a.code, a.name, a.homeCountry, a.alliance.getOrElse(""), a.hubs.mkString(",")
    ))
    writeJsonLines(s"$refDir/airlines.jsonl", airlineData)

    // Countries
    val countryData = countries.map(c => CountryOutput(
      c.code, c.name, c.region, c.timezone, c.currencyCode
    ))
    writeJsonLines(s"$refDir/countries.jsonl", countryData)

    // Currencies
    val currencyData = currencies.map(c => CurrencyOutput(
      c.code, c.name, c.usdExchangeRate.toString
    ))
    writeJsonLines(s"$refDir/currencies.jsonl", currencyData)

    println(s"Reference data written to $refDir")
  }

  /**
   * Write data as JSON Lines format
   */
  private def writeJsonLines[T: Encoder](path: String, data: List[T]): Unit = {
    val writer = new PrintWriter(new File(path))
    data.foreach { item =>
      writer.println(item.asJson.noSpaces)
    }
    writer.close()
  }
}

/**
 * Command-line entry point for data generation
 */
object AirlineDataGenerator {

  def main(args: Array[String]): Unit = {
    val startDate = if (args.length > 0) LocalDate.parse(args(0)) else LocalDate.of(2024, 1, 1)
    val days = if (args.length > 1) args(1).toInt else 10
    val journeysPerDay = if (args.length > 2) args(2).toInt else 100000
    val outputDir = if (args.length > 3) args(3) else "test-data/airline"
    val seed = if (args.length > 4) args(4).toLong else 42L

    println(s"Airline Test Data Generator")
    println(s"===========================")
    println(s"Start date: $startDate")
    println(s"Days: $days")
    println(s"Journeys/day: $journeysPerDay")
    println(s"Output: $outputDir")
    println(s"Seed: $seed")
    println()

    val generator = new AirlineDataGenerator(seed)
    generator.generateMonth(startDate, days, journeysPerDay, outputDir)
  }
}
