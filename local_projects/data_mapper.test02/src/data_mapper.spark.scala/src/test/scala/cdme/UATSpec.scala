package cdme

import cdme.core._
import cdme.config._
import cdme.compiler._
import cdme.registry._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues

/**
 * User Acceptance Tests (UAT) for CDME.
 * Tests the full pipeline from user perspective without Spark initialization.
 *
 * Implements: UAT Stage of AI SDLC
 * Validates: REQ-INT-01, REQ-TRV-02, REQ-CFG-*, REQ-ERR-*
 */
class UATSpec extends AnyFlatSpec with Matchers with EitherValues {

  // ============================================
  // Test Fixtures
  // ============================================

  val sampleOrderEntity = Entity(
    name = "Order",
    grain = Grain(GrainLevel.Atomic, List("order_id")),
    attributes = List(
      Attribute("order_id", "String", nullable = false, primaryKey = true),
      Attribute("customer_id", "String", nullable = false, primaryKey = false),
      Attribute("amount", "Decimal", nullable = false, primaryKey = false),
      Attribute("status", "String", nullable = false, primaryKey = false)
    ),
    relationships = List(
      Relationship("customer", "Customer", Cardinality.NToOne, "customer_id")
    )
  )

  val sampleCustomerEntity = Entity(
    name = "Customer",
    grain = Grain(GrainLevel.Customer, List("customer_id")),
    attributes = List(
      Attribute("customer_id", "String", nullable = false, primaryKey = true),
      Attribute("name", "String", nullable = false, primaryKey = false),
      Attribute("tier", "String", nullable = true, primaryKey = false)
    ),
    relationships = List.empty
  )

  val sampleOrderSummaryEntity = Entity(
    name = "CustomerOrderSummary",
    grain = Grain(GrainLevel.Customer, List("customer_id")),
    attributes = List(
      Attribute("customer_id", "String", nullable = false, primaryKey = true),
      Attribute("total_amount", "Decimal", nullable = false, primaryKey = false),
      Attribute("order_count", "Long", nullable = false, primaryKey = false)
    ),
    relationships = List.empty
  )

  val sampleEntities: Map[String, Entity] = Map(
    "Order" -> sampleOrderEntity,
    "Customer" -> sampleCustomerEntity,
    "CustomerOrderSummary" -> sampleOrderSummaryEntity
  )

  val sampleBindings: Map[String, PhysicalBinding] = Map(
    "Order" -> PhysicalBinding("Order", "delta", "s3://data/orders", List.empty),
    "Customer" -> PhysicalBinding("Customer", "delta", "s3://data/customers", List.empty),
    "CustomerOrderSummary" -> PhysicalBinding("CustomerOrderSummary", "delta", "s3://data/summaries", List.empty)
  )

  // ============================================
  // UAT-001: User can set up schema registry
  // ============================================

  "UAT-001: Schema Registry Setup" should "allow user to register entities" in {
    val result = SchemaRegistryImpl.fromConfig(sampleEntities, sampleBindings)

    result.isRight shouldBe true
    val registry = result.value
    registry.getEntity("Order").isRight shouldBe true
    registry.getEntity("Customer").isRight shouldBe true
  }

  it should "reject invalid entity references" in {
    val result = SchemaRegistryImpl.fromConfig(sampleEntities, sampleBindings)
    val registry = result.value

    registry.getEntity("NonExistent").isLeft shouldBe true
  }

  // ============================================
  // UAT-002: User can validate relationship paths
  // ============================================

  "UAT-002: Path Validation" should "validate correct relationship paths" in {
    val result = SchemaRegistryImpl.fromConfig(sampleEntities, sampleBindings)
    val registry = result.value

    val pathResult = registry.validatePath("Order.customer.name")
    pathResult.isRight shouldBe true
    pathResult.value.finalType shouldBe "String"
  }

  it should "reject invalid relationship paths" in {
    val result = SchemaRegistryImpl.fromConfig(sampleEntities, sampleBindings)
    val registry = result.value

    val pathResult = registry.validatePath("Order.invalid_rel.field")
    pathResult.isLeft shouldBe true
  }

  it should "reject invalid attribute paths" in {
    val result = SchemaRegistryImpl.fromConfig(sampleEntities, sampleBindings)
    val registry = result.value

    val pathResult = registry.validatePath("Order.customer.invalid_attr")
    pathResult.isLeft shouldBe true
  }

  // ============================================
  // UAT-003: User can compile simple mapping
  // ============================================

  "UAT-003: Simple Mapping Compilation" should "compile valid mapping" in {
    val result = SchemaRegistryImpl.fromConfig(sampleEntities, sampleBindings)
    val registry = result.value
    val compiler = new Compiler(registry)

    val mapping = MappingConfig(
      name = "simple_orders",
      description = Some("Copy orders"),
      source = SourceConfig("Order", "${epoch}"),
      target = TargetConfig("Order", GrainConfig("Atomic", List("order_id"))),
      morphisms = None,
      projections = List(
        ProjectionConfig("order_id", "order_id", None),
        ProjectionConfig("amount", "amount", None)
      ),
      validations = None
    )

    val ctx = createMockContext(registry)
    val plan = compiler.compile(mapping, ctx)

    plan.isRight shouldBe true
    plan.value.mappingName shouldBe "simple_orders"
    plan.value.projections.length shouldBe 2
  }

  // ============================================
  // UAT-004: User cannot bypass grain safety
  // ============================================

  "UAT-004: Grain Safety Enforcement" should "reject coarsening without aggregation" in {
    // User tries to go from Atomic (order_id) to Customer (customer_id) without aggregation
    val validationResult = GrainValidator.validateTransition(
      sourceGrain = Grain(GrainLevel.Atomic, List("order_id")),
      targetGrain = Grain(GrainLevel.Customer, List("customer_id")),
      hasAggregation = false
    )

    validationResult.isLeft shouldBe true
    val error = validationResult.left.value
    error shouldBe a[CdmeError.GrainSafetyError]
  }

  it should "allow coarsening with proper aggregation" in {
    val validationResult = GrainValidator.validateTransition(
      sourceGrain = Grain(GrainLevel.Atomic, List("order_id")),
      targetGrain = Grain(GrainLevel.Customer, List("customer_id")),
      hasAggregation = true
    )

    validationResult.isRight shouldBe true
  }

  it should "allow same-grain transformations" in {
    val validationResult = GrainValidator.validateTransition(
      sourceGrain = Grain(GrainLevel.Atomic, List("order_id")),
      targetGrain = Grain(GrainLevel.Atomic, List("order_id")),
      hasAggregation = false
    )

    validationResult.isRight shouldBe true
  }

  // ============================================
  // UAT-005: User can define filter morphisms
  // ============================================

  "UAT-005: Filter Morphism Definition" should "allow valid filter with predicate" in {
    val result = SchemaRegistryImpl.fromConfig(sampleEntities, sampleBindings)
    val registry = result.value
    val compiler = new Compiler(registry)

    val mapping = MappingConfig(
      name = "filtered_orders",
      description = Some("Filter completed orders"),
      source = SourceConfig("Order", "${epoch}"),
      target = TargetConfig("Order", GrainConfig("Atomic", List("order_id"))),
      morphisms = Some(List(
        MorphismConfig("filter_completed", "FILTER", Some("status = 'COMPLETED'"), None, None)
      )),
      projections = List(
        ProjectionConfig("order_id", "order_id", None),
        ProjectionConfig("amount", "amount", None)
      ),
      validations = None
    )

    val ctx = createMockContext(registry)
    val plan = compiler.compile(mapping, ctx)

    plan.isRight shouldBe true
    plan.value.morphisms.length shouldBe 1
    plan.value.morphisms.head.morphismType shouldBe "FILTER"
    plan.value.morphisms.head.predicate shouldBe Some("status = 'COMPLETED'")
  }

  // ============================================
  // UAT-006: User can define aggregation mapping
  // ============================================

  "UAT-006: Aggregation Mapping" should "compile mapping with aggregations" in {
    val result = SchemaRegistryImpl.fromConfig(sampleEntities, sampleBindings)
    val registry = result.value
    val compiler = new Compiler(registry)

    val mapping = MappingConfig(
      name = "order_summary",
      description = Some("Summarize orders by customer"),
      source = SourceConfig("Order", "${epoch}"),
      target = TargetConfig("CustomerOrderSummary", GrainConfig("Customer", List("customer_id"))),
      morphisms = Some(List(
        MorphismConfig("aggregate_by_customer", "AGGREGATE", None, None, None)
      )),
      projections = List(
        ProjectionConfig("customer_id", "customer_id", None),
        ProjectionConfig("total_amount", "amount", Some("SUM")),
        ProjectionConfig("order_count", "order_id", Some("COUNT"))
      ),
      validations = None
    )

    val ctx = createMockContext(registry)
    val plan = compiler.compile(mapping, ctx)

    plan.isRight shouldBe true
    plan.value.projections.count(_.aggregation.isDefined) shouldBe 2
  }

  // ============================================
  // UAT-007: User receives clear error messages
  // ============================================

  "UAT-007: Error Messages" should "provide clear error for missing entity" in {
    val result = SchemaRegistryImpl.fromConfig(sampleEntities, sampleBindings)
    val registry = result.value
    val compiler = new Compiler(registry)

    val mapping = MappingConfig(
      name = "invalid_mapping",
      description = None,
      source = SourceConfig("NonExistentEntity", "${epoch}"),
      target = TargetConfig("Order", GrainConfig("Atomic", List("order_id"))),
      morphisms = None,
      projections = List(
        ProjectionConfig("order_id", "order_id", None)
      ),
      validations = None
    )

    val ctx = createMockContext(registry)
    val plan = compiler.compile(mapping, ctx)

    plan.isLeft shouldBe true
    val error = plan.left.value
    error.errorType should include("error")
  }

  it should "provide clear error for invalid path" in {
    val result = SchemaRegistryImpl.fromConfig(sampleEntities, sampleBindings)
    val registry = result.value
    val compiler = new Compiler(registry)

    val mapping = MappingConfig(
      name = "invalid_path_mapping",
      description = None,
      source = SourceConfig("Order", "${epoch}"),
      target = TargetConfig("Order", GrainConfig("Atomic", List("order_id"))),
      morphisms = None,
      projections = List(
        ProjectionConfig("invalid", "invalid.path.here", None)
      ),
      validations = None
    )

    val ctx = createMockContext(registry)
    val plan = compiler.compile(mapping, ctx)

    plan.isLeft shouldBe true
  }

  // ============================================
  // UAT-008: User can define multiple morphisms
  // ============================================

  "UAT-008: Multiple Morphisms" should "support chained morphisms" in {
    val result = SchemaRegistryImpl.fromConfig(sampleEntities, sampleBindings)
    val registry = result.value
    val compiler = new Compiler(registry)

    val mapping = MappingConfig(
      name = "filtered_summary",
      description = Some("Filter then aggregate"),
      source = SourceConfig("Order", "${epoch}"),
      target = TargetConfig("CustomerOrderSummary", GrainConfig("Customer", List("customer_id"))),
      morphisms = Some(List(
        MorphismConfig("filter_completed", "FILTER", Some("status = 'COMPLETED'"), None, None),
        MorphismConfig("filter_amount", "FILTER", Some("amount > 100"), None, None),
        MorphismConfig("aggregate_by_customer", "AGGREGATE", None, None, None)
      )),
      projections = List(
        ProjectionConfig("customer_id", "customer_id", None),
        ProjectionConfig("total_amount", "amount", Some("SUM"))
      ),
      validations = None
    )

    val ctx = createMockContext(registry)
    val plan = compiler.compile(mapping, ctx)

    plan.isRight shouldBe true
    plan.value.morphisms.length shouldBe 3
    plan.value.morphisms.map(_.morphismType) shouldBe List("FILTER", "FILTER", "AGGREGATE")
  }

  // ============================================
  // UAT-009: User can reference relationships
  // ============================================

  "UAT-009: Relationship References" should "allow projections from related entities" in {
    val result = SchemaRegistryImpl.fromConfig(sampleEntities, sampleBindings)
    val registry = result.value

    // Validate that relationship path resolves correctly
    val pathResult = registry.validatePath("Order.customer.tier")
    pathResult.isRight shouldBe true

    // Check that the path includes relationship traversal
    val relationshipSegments = pathResult.value.segments.collect { case RelationshipSegment(name, _) => name }
    relationshipSegments should contain("customer")

    // Verify final type is correct (from Customer entity)
    pathResult.value.finalType shouldBe "String"
  }

  // ============================================
  // Helper Methods
  // ============================================

  private def createMockContext(registry: SchemaRegistry): ExecutionContext = {
    // Create a minimal context for compilation (no Spark needed for compilation)
    ExecutionContext(
      runId = "test-run-001",
      epoch = "2024-12-10",
      spark = null,  // Not needed for compilation
      registry = registry,
      config = CdmeConfig(
        version = "1.0",
        registry = RegistryConfig("", ""),
        execution = ExecutionConfig("BATCH", 0.05, "BASIC"),
        output = OutputConfig("output/data", "output/errors", "output/lineage")
      )
    )
  }
}
