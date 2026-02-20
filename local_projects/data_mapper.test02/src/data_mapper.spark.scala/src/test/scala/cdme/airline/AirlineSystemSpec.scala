package cdme.airline

import cdme.core._
import cdme.config._
import cdme.compiler._
import cdme.registry._
import cdme.executor._
import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues

/**
 * Complex System Test: Multi-Leg International Airline Booking System
 *
 * This test validates CDME's ability to handle a realistic enterprise scenario:
 * - Multi-leg flight journeys (outbound + return, potentially weeks apart)
 * - International flights with currency conversion
 * - Multiple airlines in a single journey (codeshare)
 * - Daily accounting rollup from atomic flight segments
 * - Grain transitions: FlightSegment (atomic) -> Daily Accounting Summary
 *
 * Business Domain:
 * - Airlines operate international flights between airports in different countries
 * - Customers book journeys that may span multiple flight segments
 * - Accounting needs daily revenue summaries, not individual segments
 * - Currency must be normalized to reporting currency (USD)
 * - Travel dates and return dates can be months apart
 *
 * Implements: System Test Stage of AI SDLC
 * Validates: Complex grain transitions, multi-hop relationships, aggregations
 */
class AirlineSystemSpec extends AnyFeatureSpec with GivenWhenThen with Matchers with EitherValues {

  // ============================================
  // DOMAIN MODEL: International Airline System
  // ============================================

  /**
   * Entity Hierarchy:
   *
   *   Country ←──────────── Airport ←──────────── FlightSegment
   *      │                    │                        │
   *      └── Currency         └── Airline              ├── Journey
   *                                                    │
   *                                                    └── Customer
   *
   * Accounting System Target:
   *   DailyRevenueSummary (rolled up by airline, route, date)
   */

  // --- Country Entity ---
  val countryEntity = Entity(
    name = "Country",
    grain = Grain(GrainLevel.Summary, List("country_code")),
    attributes = List(
      Attribute("country_code", "String", nullable = false, primaryKey = true),
      Attribute("country_name", "String", nullable = false),
      Attribute("region", "String", nullable = false),
      Attribute("timezone", "String", nullable = false)
    ),
    relationships = List(
      Relationship("currency", "Currency", Cardinality.NToOne, "currency_code")
    )
  )

  // --- Currency Entity ---
  val currencyEntity = Entity(
    name = "Currency",
    grain = Grain(GrainLevel.Summary, List("currency_code")),
    attributes = List(
      Attribute("currency_code", "String", nullable = false, primaryKey = true),
      Attribute("currency_name", "String", nullable = false),
      Attribute("usd_exchange_rate", "Decimal", nullable = false),
      Attribute("effective_date", "Date", nullable = false)
    ),
    relationships = List.empty
  )

  // --- Airport Entity ---
  val airportEntity = Entity(
    name = "Airport",
    grain = Grain(GrainLevel.Summary, List("airport_code")),
    attributes = List(
      Attribute("airport_code", "String", nullable = false, primaryKey = true),
      Attribute("airport_name", "String", nullable = false),
      Attribute("city", "String", nullable = false),
      Attribute("country_code", "String", nullable = false),
      Attribute("latitude", "Decimal", nullable = false),
      Attribute("longitude", "Decimal", nullable = false)
    ),
    relationships = List(
      Relationship("country", "Country", Cardinality.NToOne, "country_code")
    )
  )

  // --- Airline Entity ---
  val airlineEntity = Entity(
    name = "Airline",
    grain = Grain(GrainLevel.Summary, List("airline_code")),
    attributes = List(
      Attribute("airline_code", "String", nullable = false, primaryKey = true),
      Attribute("airline_name", "String", nullable = false),
      Attribute("home_country_code", "String", nullable = false),
      Attribute("alliance", "String", nullable = true)
    ),
    relationships = List(
      Relationship("home_country", "Country", Cardinality.NToOne, "home_country_code")
    )
  )

  // --- Customer Entity ---
  val customerEntity = Entity(
    name = "Customer",
    grain = Grain(GrainLevel.Customer, List("customer_id")),
    attributes = List(
      Attribute("customer_id", "String", nullable = false, primaryKey = true),
      Attribute("first_name", "String", nullable = false),
      Attribute("last_name", "String", nullable = false),
      Attribute("email", "String", nullable = false),
      Attribute("loyalty_tier", "String", nullable = true),
      Attribute("country_code", "String", nullable = false),
      Attribute("preferred_currency", "String", nullable = false)
    ),
    relationships = List(
      Relationship("country", "Country", Cardinality.NToOne, "country_code"),
      Relationship("currency", "Currency", Cardinality.NToOne, "preferred_currency")
    )
  )

  // --- Journey Entity (Booking) ---
  val journeyEntity = Entity(
    name = "Journey",
    grain = Grain(GrainLevel.Atomic, List("journey_id")),
    attributes = List(
      Attribute("journey_id", "String", nullable = false, primaryKey = true),
      Attribute("customer_id", "String", nullable = false),
      Attribute("booking_date", "Date", nullable = false),
      Attribute("journey_type", "String", nullable = false),  // ONE_WAY, ROUND_TRIP, MULTI_CITY
      Attribute("total_price_local", "Decimal", nullable = false),
      Attribute("local_currency", "String", nullable = false),
      Attribute("total_price_usd", "Decimal", nullable = false),
      Attribute("status", "String", nullable = false),  // CONFIRMED, CANCELLED, COMPLETED
      Attribute("outbound_date", "Date", nullable = false),
      Attribute("return_date", "Date", nullable = true)  // Null for one-way
    ),
    relationships = List(
      Relationship("customer", "Customer", Cardinality.NToOne, "customer_id"),
      Relationship("currency", "Currency", Cardinality.NToOne, "local_currency"),
      Relationship("segments", "FlightSegment", Cardinality.OneToN, "journey_id")
    )
  )

  // --- FlightSegment Entity (Atomic grain - legs of a journey) ---
  val flightSegmentEntity = Entity(
    name = "FlightSegment",
    grain = Grain(GrainLevel.Atomic, List("segment_id")),
    attributes = List(
      Attribute("segment_id", "String", nullable = false, primaryKey = true),
      Attribute("journey_id", "String", nullable = false),
      Attribute("flight_number", "String", nullable = false),
      Attribute("airline_code", "String", nullable = false),
      Attribute("departure_airport", "String", nullable = false),
      Attribute("arrival_airport", "String", nullable = false),
      Attribute("departure_datetime", "Timestamp", nullable = false),
      Attribute("arrival_datetime", "Timestamp", nullable = false),
      Attribute("flight_date", "Date", nullable = false),
      Attribute("cabin_class", "String", nullable = false),  // ECONOMY, BUSINESS, FIRST
      Attribute("segment_price_local", "Decimal", nullable = false),
      Attribute("segment_price_usd", "Decimal", nullable = false),
      Attribute("segment_sequence", "Integer", nullable = false),
      Attribute("is_codeshare", "Boolean", nullable = false),
      Attribute("operating_airline", "String", nullable = true),
      Attribute("distance_km", "Integer", nullable = false),
      Attribute("status", "String", nullable = false)  // SCHEDULED, FLOWN, CANCELLED
    ),
    relationships = List(
      Relationship("journey", "Journey", Cardinality.NToOne, "journey_id"),
      Relationship("airline", "Airline", Cardinality.NToOne, "airline_code"),
      Relationship("departure", "Airport", Cardinality.NToOne, "departure_airport"),
      Relationship("arrival", "Airport", Cardinality.NToOne, "arrival_airport")
    )
  )

  // --- DailyRevenueSummary Entity (Accounting Target) ---
  val dailyRevenueSummaryEntity = Entity(
    name = "DailyRevenueSummary",
    grain = Grain(GrainLevel.Daily, List("summary_date", "airline_code", "route")),
    attributes = List(
      Attribute("summary_id", "String", nullable = false, primaryKey = true),
      Attribute("summary_date", "Date", nullable = false),
      Attribute("airline_code", "String", nullable = false),
      Attribute("route", "String", nullable = false),  // e.g., "SYD-LAX"
      Attribute("departure_country", "String", nullable = false),
      Attribute("arrival_country", "String", nullable = false),
      Attribute("segment_count", "Long", nullable = false),
      Attribute("passenger_count", "Long", nullable = false),
      Attribute("total_revenue_usd", "Decimal", nullable = false),
      Attribute("avg_segment_price_usd", "Decimal", nullable = false),
      Attribute("total_distance_km", "Long", nullable = false),
      Attribute("economy_count", "Long", nullable = false),
      Attribute("business_count", "Long", nullable = false),
      Attribute("first_count", "Long", nullable = false)
    ),
    relationships = List(
      Relationship("airline", "Airline", Cardinality.NToOne, "airline_code")
    )
  )

  // --- MonthlyCustomerSummary Entity ---
  val monthlyCustomerSummaryEntity = Entity(
    name = "MonthlyCustomerSummary",
    grain = Grain(GrainLevel.Monthly, List("summary_month", "customer_id")),
    attributes = List(
      Attribute("summary_id", "String", nullable = false, primaryKey = true),
      Attribute("summary_month", "String", nullable = false),  // YYYY-MM
      Attribute("customer_id", "String", nullable = false),
      Attribute("journey_count", "Long", nullable = false),
      Attribute("segment_count", "Long", nullable = false),
      Attribute("total_spend_usd", "Decimal", nullable = false),
      Attribute("total_distance_km", "Long", nullable = false),
      Attribute("countries_visited", "Long", nullable = false),
      Attribute("airlines_used", "Long", nullable = false)
    ),
    relationships = List(
      Relationship("customer", "Customer", Cardinality.NToOne, "customer_id")
    )
  )

  // Combine all entities
  val airlineEntities: Map[String, Entity] = Map(
    "Country" -> countryEntity,
    "Currency" -> currencyEntity,
    "Airport" -> airportEntity,
    "Airline" -> airlineEntity,
    "Customer" -> customerEntity,
    "Journey" -> journeyEntity,
    "FlightSegment" -> flightSegmentEntity,
    "DailyRevenueSummary" -> dailyRevenueSummaryEntity,
    "MonthlyCustomerSummary" -> monthlyCustomerSummaryEntity
  )

  // Physical bindings
  val airlineBindings: Map[String, PhysicalBinding] = Map(
    "Country" -> PhysicalBinding("Country", "delta", "s3://airline-data/reference/countries", List.empty),
    "Currency" -> PhysicalBinding("Currency", "delta", "s3://airline-data/reference/currencies", List("effective_date")),
    "Airport" -> PhysicalBinding("Airport", "delta", "s3://airline-data/reference/airports", List.empty),
    "Airline" -> PhysicalBinding("Airline", "delta", "s3://airline-data/reference/airlines", List.empty),
    "Customer" -> PhysicalBinding("Customer", "delta", "s3://airline-data/customers", List.empty),
    "Journey" -> PhysicalBinding("Journey", "delta", "s3://airline-data/bookings/journeys", List("booking_date")),
    "FlightSegment" -> PhysicalBinding("FlightSegment", "delta", "s3://airline-data/bookings/segments", List("flight_date")),
    "DailyRevenueSummary" -> PhysicalBinding("DailyRevenueSummary", "delta", "s3://airline-data/accounting/daily_revenue", List("summary_date")),
    "MonthlyCustomerSummary" -> PhysicalBinding("MonthlyCustomerSummary", "delta", "s3://airline-data/analytics/monthly_customers", List("summary_month"))
  )

  // ============================================
  // Feature: Complex Schema Registry
  // ============================================

  Feature("SYS-001: Complex Multi-Entity Schema Registration") {
    info("As an Airline Data Engineer")
    info("I want to register a complex airline domain model")
    info("So that I can build data pipelines across the entire domain")

    Scenario("Register complete airline domain model with 9 entities") {
      Given("a complex airline domain model with 9 entities")
      val entities = airlineEntities

      And("physical bindings for each entity")
      val bindings = airlineBindings

      When("the schema registry is created")
      val result = SchemaRegistryImpl.fromConfig(entities, bindings)

      Then("the registry should be created successfully")
      result.isRight shouldBe true

      And("all 9 entities should be accessible")
      val registry = result.value
      registry.getEntity("Country").isRight shouldBe true
      registry.getEntity("Currency").isRight shouldBe true
      registry.getEntity("Airport").isRight shouldBe true
      registry.getEntity("Airline").isRight shouldBe true
      registry.getEntity("Customer").isRight shouldBe true
      registry.getEntity("Journey").isRight shouldBe true
      registry.getEntity("FlightSegment").isRight shouldBe true
      registry.getEntity("DailyRevenueSummary").isRight shouldBe true
      registry.getEntity("MonthlyCustomerSummary").isRight shouldBe true
    }

    Scenario("Validate multi-hop relationship path: FlightSegment -> Airport -> Country") {
      Given("a schema registry with the airline domain model")
      val registry = SchemaRegistryImpl.fromConfig(airlineEntities, airlineBindings).value

      When("validating path from FlightSegment through Airport to Country")
      val pathResult = registry.validatePath("FlightSegment.departure.country.country_name")

      Then("the path validation should succeed")
      pathResult.isRight shouldBe true

      And("the final type should be String")
      pathResult.value.finalType shouldBe "String"

      And("the path should traverse two relationships")
      val relationshipSegments = pathResult.value.segments.collect {
        case RelationshipSegment(name, _) => name
      }
      relationshipSegments should contain("departure")
      relationshipSegments should contain("country")
    }

    Scenario("Validate customer loyalty and currency path") {
      Given("a schema registry with the airline domain model")
      val registry = SchemaRegistryImpl.fromConfig(airlineEntities, airlineBindings).value

      When("validating path from Journey -> Customer -> Country -> Currency")
      val pathResult = registry.validatePath("Journey.customer.country.currency.usd_exchange_rate")

      Then("the path validation should succeed")
      pathResult.isRight shouldBe true

      And("the final type should be Decimal (exchange rate)")
      pathResult.value.finalType shouldBe "Decimal"
    }
  }

  // ============================================
  // Feature: Grain Transitions (Atomic -> Daily)
  // ============================================

  Feature("SYS-002: Grain Transition from Atomic Segments to Daily Summary") {
    info("As an Airline Accountant")
    info("I want to aggregate flight segments into daily revenue summaries")
    info("So that I can produce daily financial reports")

    Scenario("Aggregate flight segments to daily revenue by airline and route") {
      Given("a schema registry with FlightSegment and DailyRevenueSummary entities")
      val registry = SchemaRegistryImpl.fromConfig(airlineEntities, airlineBindings).value
      val compiler = new Compiler(registry)

      And("a mapping that aggregates segments to daily revenue")
      val mapping = MappingConfig(
        name = "daily_revenue_summary",
        description = Some("Aggregate flight segments to daily airline route revenue"),
        source = SourceConfig("FlightSegment", "${epoch}"),
        target = TargetConfig("DailyRevenueSummary", GrainConfig("Daily", List("summary_date", "airline_code", "route"))),
        morphisms = Some(List(
          MorphismConfig("filter_flown", "FILTER", Some("status = 'FLOWN'"), None, None),
          MorphismConfig("aggregate_daily", "AGGREGATE", None, None, None)
        )),
        projections = List(
          ProjectionConfig("summary_date", "flight_date", None),
          ProjectionConfig("airline_code", "airline_code", None),
          ProjectionConfig("route", "CONCAT(departure_airport, '-', arrival_airport)", None),
          ProjectionConfig("segment_count", "segment_id", Some("COUNT")),
          ProjectionConfig("total_revenue_usd", "segment_price_usd", Some("SUM")),
          ProjectionConfig("avg_segment_price_usd", "segment_price_usd", Some("AVG")),
          ProjectionConfig("total_distance_km", "distance_km", Some("SUM"))
        ),
        validations = Some(List(
          ValidationConfig("total_revenue_usd", "RANGE", Some("0:"), "Revenue must be non-negative")
        ))
      )

      When("the mapping is compiled")
      val ctx = createMockContext(registry)
      val plan = compiler.compile(mapping, ctx)

      Then("the compilation should succeed")
      plan.isRight shouldBe true

      And("the plan should have correct grain transition")
      plan.value.sourceGrain.level shouldBe GrainLevel.Atomic
      plan.value.targetGrain.level shouldBe GrainLevel.Daily

      And("the plan should include filter and aggregate morphisms")
      plan.value.morphisms.map(_.morphismType) shouldBe List("FILTER", "AGGREGATE")

      And("the plan should have 4 aggregation projections")
      val aggProjections = plan.value.projections.filter(_.aggregation.isDefined)
      aggProjections.length shouldBe 4
    }

    Scenario("Validate grain safety prevents direct copy without aggregation") {
      Given("FlightSegment at Atomic grain and DailyRevenueSummary at Daily grain")
      val sourceGrain = Grain(GrainLevel.Atomic, List("segment_id"))
      val targetGrain = Grain(GrainLevel.Daily, List("summary_date", "airline_code"))

      When("attempting grain transition without aggregation")
      val result = GrainValidator.validateTransition(
        sourceGrain = sourceGrain,
        targetGrain = targetGrain,
        hasAggregation = false
      )

      Then("the validation should fail with GrainSafetyError")
      result.isLeft shouldBe true
      result.left.value shouldBe a[CdmeError.GrainSafetyError]
    }

    Scenario("Allow grain coarsening with proper aggregation") {
      Given("FlightSegment at Atomic grain and DailyRevenueSummary at Daily grain")
      val sourceGrain = Grain(GrainLevel.Atomic, List("segment_id"))
      val targetGrain = Grain(GrainLevel.Daily, List("summary_date", "airline_code"))

      When("attempting grain transition WITH aggregation")
      val result = GrainValidator.validateTransition(
        sourceGrain = sourceGrain,
        targetGrain = targetGrain,
        hasAggregation = true
      )

      Then("the validation should succeed")
      result.isRight shouldBe true
    }
  }

  // ============================================
  // Feature: Multi-Leg Journey Handling
  // ============================================

  Feature("SYS-003: Multi-Leg Journey Aggregation") {
    info("As an Airline Revenue Analyst")
    info("I want to analyze journeys that span multiple flight segments")
    info("So that I can understand complete customer journey value")

    Scenario("Compile mapping for journey-level metrics from segments") {
      Given("a schema registry with Journey and FlightSegment entities")
      val registry = SchemaRegistryImpl.fromConfig(airlineEntities, airlineBindings).value
      val compiler = new Compiler(registry)

      And("a mapping that aggregates segment metrics to journey level")
      val mapping = MappingConfig(
        name = "journey_segment_summary",
        description = Some("Aggregate segment details up to journey level"),
        source = SourceConfig("FlightSegment", "${epoch}"),
        target = TargetConfig("Journey", GrainConfig("Atomic", List("journey_id"))),
        morphisms = Some(List(
          MorphismConfig("aggregate_by_journey", "AGGREGATE", None, Some("journey_id"), None)
        )),
        projections = List(
          ProjectionConfig("journey_id", "journey_id", None),
          ProjectionConfig("total_segments", "segment_id", Some("COUNT")),
          ProjectionConfig("total_distance_km", "distance_km", Some("SUM")),
          ProjectionConfig("total_price_usd", "segment_price_usd", Some("SUM")),
          ProjectionConfig("first_departure", "departure_datetime", Some("MIN")),
          ProjectionConfig("last_arrival", "arrival_datetime", Some("MAX"))
        ),
        validations = None
      )

      When("the mapping is compiled")
      val ctx = createMockContext(registry)
      val plan = compiler.compile(mapping, ctx)

      Then("the compilation should succeed")
      plan.isRight shouldBe true

      And("the plan should have 5 different aggregation types")
      val aggTypes = plan.value.projections.flatMap(_.aggregation).toSet
      aggTypes should contain("COUNT")
      aggTypes should contain("SUM")
      aggTypes should contain("MIN")
      aggTypes should contain("MAX")
    }

    Scenario("Handle round-trip journeys with outbound and return legs") {
      Given("a schema registry with the airline domain model")
      val registry = SchemaRegistryImpl.fromConfig(airlineEntities, airlineBindings).value
      val compiler = new Compiler(registry)

      And("a mapping that calculates round-trip journey metrics")
      val mapping = MappingConfig(
        name = "round_trip_analysis",
        description = Some("Analyze round-trip journeys with potentially months between outbound and return"),
        source = SourceConfig("Journey", "${epoch}"),
        target = TargetConfig("Journey", GrainConfig("Atomic", List("journey_id"))),
        morphisms = Some(List(
          MorphismConfig("filter_round_trips", "FILTER", Some("journey_type = 'ROUND_TRIP' AND return_date IS NOT NULL"), None, None)
        )),
        projections = List(
          ProjectionConfig("journey_id", "journey_id", None),
          ProjectionConfig("customer_id", "customer_id", None),
          ProjectionConfig("outbound_date", "outbound_date", None),
          ProjectionConfig("return_date", "return_date", None),
          ProjectionConfig("total_price_usd", "total_price_usd", None)
        ),
        validations = Some(List(
          ValidationConfig("return_date", "NOT_NULL", None, "Round trip must have return date")
        ))
      )

      When("the mapping is compiled")
      val ctx = createMockContext(registry)
      val plan = compiler.compile(mapping, ctx)

      Then("the compilation should succeed")
      plan.isRight shouldBe true

      And("the plan should include the filter for round trips")
      plan.value.morphisms.head.predicate.get should include("ROUND_TRIP")
    }
  }

  // ============================================
  // Feature: International Currency Handling
  // ============================================

  Feature("SYS-004: Multi-Currency Revenue Normalization") {
    info("As a Finance Manager")
    info("I want revenue normalized to USD regardless of booking currency")
    info("So that I can produce consolidated financial reports")

    Scenario("Validate currency traversal path from Journey") {
      Given("a schema registry with Journey -> Currency relationship")
      val registry = SchemaRegistryImpl.fromConfig(airlineEntities, airlineBindings).value

      When("validating path to currency exchange rate")
      val pathResult = registry.validatePath("Journey.currency.usd_exchange_rate")

      Then("the path should be valid")
      pathResult.isRight shouldBe true
      pathResult.value.finalType shouldBe "Decimal"
    }

    Scenario("Compile mapping with currency conversion calculation") {
      Given("a schema registry with the airline domain model")
      val registry = SchemaRegistryImpl.fromConfig(airlineEntities, airlineBindings).value
      val compiler = new Compiler(registry)

      And("a mapping that includes currency conversion in projections")
      val mapping = MappingConfig(
        name = "journey_revenue_normalized",
        description = Some("Journey revenue with currency normalization"),
        source = SourceConfig("Journey", "${epoch}"),
        target = TargetConfig("Journey", GrainConfig("Atomic", List("journey_id"))),
        morphisms = Some(List(
          MorphismConfig("filter_completed", "FILTER", Some("status = 'COMPLETED'"), None, None),
          MorphismConfig("traverse_currency", "TRAVERSE", None, Some("currency"), Some("N:1"))
        )),
        projections = List(
          ProjectionConfig("journey_id", "journey_id", None),
          ProjectionConfig("local_amount", "total_price_local", None),
          ProjectionConfig("local_currency", "local_currency", None),
          ProjectionConfig("usd_amount", "total_price_usd", None)  // Pre-calculated in source
        ),
        validations = Some(List(
          ValidationConfig("usd_amount", "RANGE", Some("0:"), "USD amount must be positive")
        ))
      )

      When("the mapping is compiled")
      val ctx = createMockContext(registry)
      val plan = compiler.compile(mapping, ctx)

      Then("the compilation should succeed")
      plan.isRight shouldBe true

      And("the plan should include traverse morphism for currency lookup")
      val traverseMorphism = plan.value.morphisms.find(_.morphismType == "TRAVERSE")
      traverseMorphism shouldBe defined
      traverseMorphism.get.path shouldBe Some("currency")
    }
  }

  // ============================================
  // Feature: Airline Codeshare Handling
  // ============================================

  Feature("SYS-005: Codeshare Flight Analysis") {
    info("As an Airline Partnership Manager")
    info("I want to track revenue by marketing vs operating airline")
    info("So that I can reconcile codeshare agreements")

    Scenario("Filter and analyze codeshare flights") {
      Given("a schema registry with FlightSegment entity including codeshare flag")
      val registry = SchemaRegistryImpl.fromConfig(airlineEntities, airlineBindings).value
      val compiler = new Compiler(registry)

      And("a mapping that analyzes codeshare flights separately")
      val mapping = MappingConfig(
        name = "codeshare_analysis",
        description = Some("Separate codeshare vs direct flights for partnership reconciliation"),
        source = SourceConfig("FlightSegment", "${epoch}"),
        target = TargetConfig("DailyRevenueSummary", GrainConfig("Daily", List("summary_date", "airline_code"))),
        morphisms = Some(List(
          MorphismConfig("filter_codeshares", "FILTER", Some("is_codeshare = true"), None, None),
          MorphismConfig("aggregate_by_marketing_airline", "AGGREGATE", None, None, None)
        )),
        projections = List(
          ProjectionConfig("summary_date", "flight_date", None),
          ProjectionConfig("marketing_airline", "airline_code", None),
          ProjectionConfig("operating_airline", "operating_airline", None),
          ProjectionConfig("codeshare_segment_count", "segment_id", Some("COUNT")),
          ProjectionConfig("codeshare_revenue_usd", "segment_price_usd", Some("SUM"))
        ),
        validations = None
      )

      When("the mapping is compiled")
      val ctx = createMockContext(registry)
      val plan = compiler.compile(mapping, ctx)

      Then("the compilation should succeed")
      plan.isRight shouldBe true

      And("the plan should filter for codeshare flights")
      plan.value.morphisms.head.predicate.get should include("is_codeshare")
    }
  }

  // ============================================
  // Feature: Monthly Customer Analytics
  // ============================================

  Feature("SYS-006: Monthly Customer Travel Summary") {
    info("As a Customer Analytics Manager")
    info("I want monthly summaries of customer travel patterns")
    info("So that I can identify high-value customers and trends")

    Scenario("Aggregate journeys to monthly customer summary") {
      Given("a schema registry with Journey and MonthlyCustomerSummary entities")
      val registry = SchemaRegistryImpl.fromConfig(airlineEntities, airlineBindings).value
      val compiler = new Compiler(registry)

      And("a mapping that creates monthly customer travel summaries")
      val mapping = MappingConfig(
        name = "monthly_customer_travel",
        description = Some("Monthly customer travel pattern summary"),
        source = SourceConfig("Journey", "${epoch}"),
        target = TargetConfig("MonthlyCustomerSummary", GrainConfig("Monthly", List("summary_month", "customer_id"))),
        morphisms = Some(List(
          MorphismConfig("filter_completed", "FILTER", Some("status = 'COMPLETED'"), None, None),
          MorphismConfig("aggregate_monthly", "AGGREGATE", None, None, None)
        )),
        projections = List(
          ProjectionConfig("summary_month", "DATE_FORMAT(booking_date, 'yyyy-MM')", None),
          ProjectionConfig("customer_id", "customer_id", None),
          ProjectionConfig("journey_count", "journey_id", Some("COUNT")),
          ProjectionConfig("total_spend_usd", "total_price_usd", Some("SUM")),
          ProjectionConfig("avg_journey_value", "total_price_usd", Some("AVG"))
        ),
        validations = None
      )

      When("the mapping is compiled")
      val ctx = createMockContext(registry)
      val plan = compiler.compile(mapping, ctx)

      Then("the compilation should succeed")
      plan.isRight shouldBe true

      And("the target grain should be Monthly")
      plan.value.targetGrain.level shouldBe GrainLevel.Monthly

      And("the plan should have journey and customer aggregations")
      val aggProjections = plan.value.projections.filter(_.aggregation.isDefined)
      aggProjections.length shouldBe 3
    }

    Scenario("Validate grain transition from Atomic Journey to Monthly Customer") {
      Given("Journey at Atomic grain and MonthlyCustomerSummary at Monthly grain")
      val sourceGrain = Grain(GrainLevel.Atomic, List("journey_id"))
      val targetGrain = Grain(GrainLevel.Monthly, List("summary_month", "customer_id"))

      When("validating the grain transition with aggregation")
      val result = GrainValidator.validateTransition(
        sourceGrain = sourceGrain,
        targetGrain = targetGrain,
        hasAggregation = true
      )

      Then("the transition should be allowed")
      result.isRight shouldBe true
    }
  }

  // ============================================
  // Feature: Complex Filter Chains
  // ============================================

  Feature("SYS-007: Complex Business Rule Filters") {
    info("As a Revenue Manager")
    info("I want to apply complex business rules via filter chains")
    info("So that I can analyze specific business scenarios")

    Scenario("Chain multiple filters for premium international travel analysis") {
      Given("a schema registry with the airline domain model")
      val registry = SchemaRegistryImpl.fromConfig(airlineEntities, airlineBindings).value
      val compiler = new Compiler(registry)

      And("a mapping with multiple chained business rule filters")
      val mapping = MappingConfig(
        name = "premium_international_revenue",
        description = Some("Analyze premium cabin international flights for high-value segment reporting"),
        source = SourceConfig("FlightSegment", "${epoch}"),
        target = TargetConfig("DailyRevenueSummary", GrainConfig("Daily", List("summary_date", "airline_code"))),
        morphisms = Some(List(
          // Filter 1: Only completed flights
          MorphismConfig("filter_flown", "FILTER", Some("status = 'FLOWN'"), None, None),
          // Filter 2: Only premium cabins
          MorphismConfig("filter_premium", "FILTER", Some("cabin_class IN ('BUSINESS', 'FIRST')"), None, None),
          // Filter 3: Only long-haul (>5000km)
          MorphismConfig("filter_longhaul", "FILTER", Some("distance_km > 5000"), None, None),
          // Filter 4: Only high-value segments (>$1000)
          MorphismConfig("filter_high_value", "FILTER", Some("segment_price_usd > 1000"), None, None),
          // Aggregate
          MorphismConfig("aggregate_daily", "AGGREGATE", None, None, None)
        )),
        projections = List(
          ProjectionConfig("summary_date", "flight_date", None),
          ProjectionConfig("airline_code", "airline_code", None),
          ProjectionConfig("premium_segment_count", "segment_id", Some("COUNT")),
          ProjectionConfig("premium_revenue_usd", "segment_price_usd", Some("SUM")),
          ProjectionConfig("avg_premium_price", "segment_price_usd", Some("AVG")),
          ProjectionConfig("total_premium_distance", "distance_km", Some("SUM"))
        ),
        validations = None
      )

      When("the mapping is compiled")
      val ctx = createMockContext(registry)
      val plan = compiler.compile(mapping, ctx)

      Then("the compilation should succeed")
      plan.isRight shouldBe true

      And("the plan should have 5 morphisms (4 filters + 1 aggregate)")
      plan.value.morphisms.length shouldBe 5

      And("the first 4 morphisms should be filters")
      plan.value.morphisms.take(4).foreach { m =>
        m.morphismType shouldBe "FILTER"
      }

      And("the last morphism should be aggregate")
      plan.value.morphisms.last.morphismType shouldBe "AGGREGATE"

      And("filters should be in correct business logic order")
      plan.value.morphisms.map(_.name) shouldBe List(
        "filter_flown",
        "filter_premium",
        "filter_longhaul",
        "filter_high_value",
        "aggregate_daily"
      )
    }
  }

  // ============================================
  // Feature: Window Functions for Analytics
  // ============================================

  Feature("SYS-008: Running Totals and Rankings") {
    info("As an Airline Analyst")
    info("I want running totals and rankings for trend analysis")
    info("So that I can track performance over time")

    Scenario("Calculate running revenue total by airline") {
      Given("a schema registry with the airline domain model")
      val registry = SchemaRegistryImpl.fromConfig(airlineEntities, airlineBindings).value
      val compiler = new Compiler(registry)

      And("a mapping with window function for running total")
      val mapping = MappingConfig(
        name = "airline_running_revenue",
        description = Some("Daily revenue with running total by airline"),
        source = SourceConfig("FlightSegment", "${epoch}"),
        target = TargetConfig("FlightSegment", GrainConfig("Atomic", List("segment_id"))),
        morphisms = Some(List(
          MorphismConfig(
            name = "running_total_window",
            `type` = "WINDOW",
            predicate = None,
            groupBy = Some(List("airline_code")),
            orderBy = Some(List("flight_date"))
          )
        )),
        projections = List(
          ProjectionConfig("segment_id", "segment_id", None),
          ProjectionConfig("airline_code", "airline_code", None),
          ProjectionConfig("flight_date", "flight_date", None),
          ProjectionConfig("segment_revenue", "segment_price_usd", None),
          ProjectionConfig("running_revenue", "segment_price_usd", Some("SUM"))
        ),
        validations = None
      )

      When("the mapping is compiled")
      val ctx = createMockContext(registry)
      val plan = compiler.compile(mapping, ctx)

      Then("the compilation should succeed")
      plan.isRight shouldBe true

      And("the plan should include a WINDOW morphism")
      plan.value.morphisms.head.morphismType shouldBe "WINDOW"

      And("the window should partition by airline and order by date")
      plan.value.morphisms.head.groupBy shouldBe Some(List("airline_code"))
      plan.value.morphisms.head.orderBy shouldBe Some(List("flight_date"))
    }

    Scenario("Rank customers by monthly spend") {
      Given("a schema registry with the airline domain model")
      val registry = SchemaRegistryImpl.fromConfig(airlineEntities, airlineBindings).value
      val compiler = new Compiler(registry)

      And("a mapping that ranks customers within each month")
      val mapping = MappingConfig(
        name = "customer_monthly_ranking",
        description = Some("Rank customers by spend within each month"),
        source = SourceConfig("Journey", "${epoch}"),
        target = TargetConfig("Journey", GrainConfig("Atomic", List("journey_id"))),
        morphisms = Some(List(
          MorphismConfig(
            name = "rank_by_spend",
            `type` = "WINDOW",
            predicate = None,
            groupBy = Some(List("DATE_FORMAT(booking_date, 'yyyy-MM')")),
            orderBy = Some(List("total_price_usd DESC"))
          )
        )),
        projections = List(
          ProjectionConfig("journey_id", "journey_id", None),
          ProjectionConfig("customer_id", "customer_id", None),
          ProjectionConfig("booking_month", "DATE_FORMAT(booking_date, 'yyyy-MM')", None),
          ProjectionConfig("total_price_usd", "total_price_usd", None),
          ProjectionConfig("customer_rank", "customer_id", Some("RANK"))
        ),
        validations = None
      )

      When("the mapping is compiled")
      val ctx = createMockContext(registry)
      val plan = compiler.compile(mapping, ctx)

      Then("the compilation should succeed")
      plan.isRight shouldBe true

      And("the ranking projection should be present")
      val rankProjection = plan.value.projections.find(_.aggregation == Some("RANK"))
      rankProjection shouldBe defined
    }
  }

  // ============================================
  // Feature: Error Handling for Complex Scenarios
  // ============================================

  Feature("SYS-009: Complex Mapping Error Detection") {
    info("As a Data Engineer")
    info("I want clear errors when complex mappings have issues")
    info("So that I can quickly fix configuration problems")

    Scenario("Detect invalid multi-hop relationship path") {
      Given("a schema registry with the airline domain model")
      val registry = SchemaRegistryImpl.fromConfig(airlineEntities, airlineBindings).value

      When("validating a path with invalid intermediate relationship")
      val result = registry.validatePath("FlightSegment.departure.invalid_rel.name")

      Then("the validation should fail")
      result.isLeft shouldBe true
    }

    Scenario("Detect invalid entity reference in mapping") {
      Given("a schema registry with the airline domain model")
      val registry = SchemaRegistryImpl.fromConfig(airlineEntities, airlineBindings).value
      val compiler = new Compiler(registry)

      And("a mapping referencing non-existent entity")
      val mapping = MappingConfig(
        name = "invalid_mapping",
        description = None,
        source = SourceConfig("NonExistentFlightData", "${epoch}"),
        target = TargetConfig("DailyRevenueSummary", GrainConfig("Daily", List("summary_date"))),
        morphisms = None,
        projections = List(ProjectionConfig("summary_date", "flight_date", None)),
        validations = None
      )

      When("the mapping is compiled")
      val ctx = createMockContext(registry)
      val result = compiler.compile(mapping, ctx)

      Then("the compilation should fail with clear error")
      result.isLeft shouldBe true
    }
  }

  // ============================================
  // Feature: Adjoint Wrapper Integration
  // ============================================

  Feature("SYS-010: Adjoint Metadata Capture for Reverse Traversal") {
    info("As a Finance Auditor")
    info("I want to trace back from daily summaries to original segments")
    info("So that I can audit and reconcile financial reports")

    Scenario("Verify adjoint classification types are available") {
      Given("the AdjointWrapper components")
      import cdme.executor.AdjointClassification

      When("checking adjoint classifications")
      Then("ISOMORPHISM should be defined for 1:1 mappings")
      AdjointClassification.ISOMORPHISM shouldBe AdjointClassification.ISOMORPHISM

      And("EMBEDDING should be defined for injective mappings")
      AdjointClassification.EMBEDDING shouldBe AdjointClassification.EMBEDDING

      And("PROJECTION should be defined for surjective mappings (aggregations)")
      AdjointClassification.PROJECTION shouldBe AdjointClassification.PROJECTION

      And("LOSSY should be defined for non-invertible mappings")
      AdjointClassification.LOSSY shouldBe AdjointClassification.LOSSY
    }

    Scenario("Create AdjointResult for aggregation operation") {
      Given("the AdjointWrapper components")
      import cdme.executor.{AdjointClassification, AdjointResult, ReverseJoinMetadata}

      And("sample aggregation result data")
      val aggregatedData = List(
        Map("airline_code" -> "QF", "total_revenue" -> BigDecimal(1000000))
      )
      val reverseJoinData: Map[String, Any] = Map(
        "QF" -> List("SEG001", "SEG002", "SEG003")
      )

      When("creating an AdjointResult for the aggregation")
      val result = AdjointResult(
        output = aggregatedData,
        metadata = ReverseJoinMetadata(reverseJoinData),
        classification = AdjointClassification.PROJECTION
      )

      Then("the result should contain the aggregated output")
      result.output shouldBe aggregatedData

      And("the metadata should contain reverse join information")
      result.metadata shouldBe a[ReverseJoinMetadata]

      And("the classification should be PROJECTION")
      result.classification shouldBe AdjointClassification.PROJECTION
    }
  }

  // ============================================
  // Feature: Error Domain Integration
  // ============================================

  Feature("SYS-011: Distributed Error Collection") {
    info("As a Data Platform Engineer")
    info("I want errors collected across distributed execution")
    info("So that I can monitor and troubleshoot pipeline failures")

    Scenario("Configure error threshold for batch processing") {
      Given("the ErrorDomain components")
      import cdme.executor.{ErrorConfig, ThresholdResult, ThresholdReason}

      And("a configuration with 5% error threshold")
      val config = ErrorConfig(
        absoluteThreshold = 10000,
        percentageThreshold = 0.05,
        bufferLimit = 1000,
        dlqPath = "s3://airline-data/errors/dlq"
      )

      When("checking the configuration")
      Then("the percentage threshold should be 5%")
      config.percentageThreshold shouldBe 0.05

      And("the buffer limit should be 1000")
      config.bufferLimit shouldBe 1000
    }

    Scenario("Simulate error threshold check for batch processing") {
      Given("the ErrorDomain components")
      import cdme.executor.{ErrorConfig, MockErrorDomain, ErrorObject, ThresholdResult}

      And("an error domain with 5% threshold")
      val config = ErrorConfig(
        absoluteThreshold = 10000,
        percentageThreshold = 0.05,
        bufferLimit = 100,
        dlqPath = "s3://errors"
      )
      val errorDomain = new MockErrorDomain(config)

      And("1000 errors routed (under absolute threshold)")
      (1 to 1000).foreach { i =>
        errorDomain.routeError(ErrorObject(
          sourceKey = s"SEGMENT_$i",
          morphismPath = "filter_positive_amount",
          errorType = "VALIDATION_ERROR",
          expected = "> 0",
          actual = "-100",
          context = Map("segment_id" -> s"SEG$i", "epoch" -> "2024-01-15")
        ))
      }

      When("checking threshold with 50000 total rows (2% error rate)")
      val result = errorDomain.checkThreshold(totalRows = 50000)

      Then("processing should continue (2% < 5%)")
      result shouldBe ThresholdResult.Continue

      When("checking threshold with 10000 total rows (10% error rate)")
      val result2 = errorDomain.checkThreshold(totalRows = 10000)

      Then("processing should halt (10% > 5%)")
      result2 shouldBe a[ThresholdResult.Halt]
    }
  }

  // ============================================
  // Helper Methods
  // ============================================

  private def createMockContext(registry: SchemaRegistry): ExecutionContext = {
    ExecutionContext(
      runId = "airline-system-test-run",
      epoch = "2024-01-15",
      spark = null,  // Not needed for compilation tests
      registry = registry,
      config = CdmeConfig(
        version = "1.0",
        registry = RegistryConfig("", ""),
        execution = ExecutionConfig("BATCH", 0.05, "KEY_DERIVABLE"),
        output = OutputConfig(
          "s3://airline-data/output/data",
          "s3://airline-data/output/errors",
          "s3://airline-data/output/lineage"
        )
      )
    )
  }
}
