package cdme

import cdme.core._
import cdme.config._
import cdme.compiler._
import cdme.registry._
import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues

/**
 * User Acceptance Tests (UAT) for CDME using BDD style.
 * Tests validate business requirements from user perspective.
 *
 * Implements: UAT Stage of AI SDLC
 * Validates: REQ-INT-01, REQ-TRV-02, REQ-CFG-*, REQ-ERR-*
 *
 * BDD Format: Given/When/Then
 */
class UATSpec extends AnyFeatureSpec with GivenWhenThen with Matchers with EitherValues {

  // ============================================
  // Test Fixtures - Sample Domain Model
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
  // Feature: Schema Registry Management
  // ============================================

  Feature("UAT-001: Schema Registry Setup") {
    info("As a Data Engineer")
    info("I want to register entity schemas in the registry")
    info("So that I can validate data mappings against known schemas")

    Scenario("User registers valid entities with physical bindings") {
      Given("a set of entity definitions with attributes and relationships")
      val entities = sampleEntities

      And("physical bindings for each entity")
      val bindings = sampleBindings

      When("the user creates a schema registry")
      val result = SchemaRegistryImpl.fromConfig(entities, bindings)

      Then("the registry should be created successfully")
      result.isRight shouldBe true

      And("all entities should be accessible")
      val registry = result.value
      registry.getEntity("Order").isRight shouldBe true
      registry.getEntity("Customer").isRight shouldBe true
      registry.getEntity("CustomerOrderSummary").isRight shouldBe true
    }

    Scenario("User queries for non-existent entity") {
      Given("a schema registry with registered entities")
      val registry = SchemaRegistryImpl.fromConfig(sampleEntities, sampleBindings).value

      When("the user queries for an entity that doesn't exist")
      val result = registry.getEntity("NonExistentEntity")

      Then("the registry should return an error")
      result.isLeft shouldBe true
    }
  }

  // ============================================
  // Feature: Path Validation
  // ============================================

  Feature("UAT-002: Relationship Path Validation") {
    info("As a Data Engineer")
    info("I want to validate attribute paths including relationship traversals")
    info("So that I can catch invalid references at compile time")

    Scenario("User validates a correct relationship path") {
      Given("a schema registry with Order -> Customer relationship")
      val registry = SchemaRegistryImpl.fromConfig(sampleEntities, sampleBindings).value

      When("the user validates path 'Order.customer.name'")
      val pathResult = registry.validatePath("Order.customer.name")

      Then("the validation should succeed")
      pathResult.isRight shouldBe true

      And("the final type should be String")
      pathResult.value.finalType shouldBe "String"

      And("the path should include the relationship traversal")
      val relationshipSegments = pathResult.value.segments.collect {
        case RelationshipSegment(name, _) => name
      }
      relationshipSegments should contain("customer")
    }

    Scenario("User validates path with invalid relationship") {
      Given("a schema registry with defined relationships")
      val registry = SchemaRegistryImpl.fromConfig(sampleEntities, sampleBindings).value

      When("the user validates a path with non-existent relationship")
      val result = registry.validatePath("Order.invalid_relationship.field")

      Then("the validation should fail")
      result.isLeft shouldBe true
    }

    Scenario("User validates path with invalid attribute") {
      Given("a schema registry with defined entities")
      val registry = SchemaRegistryImpl.fromConfig(sampleEntities, sampleBindings).value

      When("the user validates a path with non-existent attribute")
      val result = registry.validatePath("Order.customer.invalid_attribute")

      Then("the validation should fail")
      result.isLeft shouldBe true
    }
  }

  // ============================================
  // Feature: Mapping Compilation
  // ============================================

  Feature("UAT-003: Simple Mapping Compilation") {
    info("As a Data Engineer")
    info("I want to compile mapping definitions into execution plans")
    info("So that the system can execute data transformations")

    Scenario("User compiles a simple entity-to-entity mapping") {
      Given("a schema registry with Order entity")
      val registry = SchemaRegistryImpl.fromConfig(sampleEntities, sampleBindings).value
      val compiler = new Compiler(registry)

      And("a simple mapping that copies Order fields")
      val mapping = MappingConfig(
        name = "simple_orders",
        description = Some("Copy order fields"),
        source = SourceConfig("Order", "${epoch}"),
        target = TargetConfig("Order", GrainConfig("Atomic", List("order_id"))),
        morphisms = None,
        projections = List(
          ProjectionConfig("order_id", "order_id", None),
          ProjectionConfig("amount", "amount", None)
        ),
        validations = None
      )

      When("the user compiles the mapping")
      val ctx = createMockContext(registry)
      val plan = compiler.compile(mapping, ctx)

      Then("the compilation should succeed")
      plan.isRight shouldBe true

      And("the execution plan should have correct mapping name")
      plan.value.mappingName shouldBe "simple_orders"

      And("the plan should include both projections")
      plan.value.projections.length shouldBe 2
    }
  }

  // ============================================
  // Feature: Grain Safety Enforcement
  // ============================================

  Feature("UAT-004: Grain Safety Enforcement") {
    info("As a Data Engineer")
    info("I want the system to prevent grain coarsening without aggregation")
    info("So that I don't accidentally lose data through improper transformations")

    Scenario("User attempts grain coarsening without aggregation") {
      Given("a source at Atomic grain (order_id)")
      val sourceGrain = Grain(GrainLevel.Atomic, List("order_id"))

      And("a target at Customer grain (customer_id)")
      val targetGrain = Grain(GrainLevel.Customer, List("customer_id"))

      When("the user attempts this transition without aggregation")
      val result = GrainValidator.validateTransition(
        sourceGrain = sourceGrain,
        targetGrain = targetGrain,
        hasAggregation = false
      )

      Then("the validation should fail with a GrainSafetyError")
      result.isLeft shouldBe true
      result.left.value shouldBe a[CdmeError.GrainSafetyError]
    }

    Scenario("User performs grain coarsening with proper aggregation") {
      Given("a source at Atomic grain (order_id)")
      val sourceGrain = Grain(GrainLevel.Atomic, List("order_id"))

      And("a target at Customer grain (customer_id)")
      val targetGrain = Grain(GrainLevel.Customer, List("customer_id"))

      When("the user includes aggregation in the mapping")
      val result = GrainValidator.validateTransition(
        sourceGrain = sourceGrain,
        targetGrain = targetGrain,
        hasAggregation = true
      )

      Then("the validation should succeed")
      result.isRight shouldBe true
    }

    Scenario("User performs same-grain transformation") {
      Given("a source and target at the same Atomic grain")
      val sourceGrain = Grain(GrainLevel.Atomic, List("order_id"))
      val targetGrain = Grain(GrainLevel.Atomic, List("order_id"))

      When("the user creates a mapping without aggregation")
      val result = GrainValidator.validateTransition(
        sourceGrain = sourceGrain,
        targetGrain = targetGrain,
        hasAggregation = false
      )

      Then("the validation should succeed")
      result.isRight shouldBe true
    }
  }

  // ============================================
  // Feature: Filter Morphism
  // ============================================

  Feature("UAT-005: Filter Morphism Definition") {
    info("As a Data Engineer")
    info("I want to define filter morphisms with SQL predicates")
    info("So that I can filter data based on business rules")

    Scenario("User creates a mapping with filter morphism") {
      Given("a schema registry with Order entity")
      val registry = SchemaRegistryImpl.fromConfig(sampleEntities, sampleBindings).value
      val compiler = new Compiler(registry)

      And("a mapping with a filter for completed orders")
      val mapping = MappingConfig(
        name = "completed_orders",
        description = Some("Filter completed orders only"),
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

      When("the user compiles the mapping")
      val ctx = createMockContext(registry)
      val plan = compiler.compile(mapping, ctx)

      Then("the compilation should succeed")
      plan.isRight shouldBe true

      And("the plan should include the filter morphism")
      plan.value.morphisms.length shouldBe 1
      plan.value.morphisms.head.morphismType shouldBe "FILTER"

      And("the filter should have the correct predicate")
      plan.value.morphisms.head.predicate shouldBe Some("status = 'COMPLETED'")
    }
  }

  // ============================================
  // Feature: Aggregation Mapping
  // ============================================

  Feature("UAT-006: Aggregation Mapping") {
    info("As a Data Engineer")
    info("I want to create aggregation mappings with SUM, COUNT, etc.")
    info("So that I can summarize data at coarser grains")

    Scenario("User creates order summary with aggregations") {
      Given("a schema registry with Order and CustomerOrderSummary entities")
      val registry = SchemaRegistryImpl.fromConfig(sampleEntities, sampleBindings).value
      val compiler = new Compiler(registry)

      And("a mapping that aggregates orders by customer")
      val mapping = MappingConfig(
        name = "customer_order_summary",
        description = Some("Aggregate orders by customer"),
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

      When("the user compiles the mapping")
      val ctx = createMockContext(registry)
      val plan = compiler.compile(mapping, ctx)

      Then("the compilation should succeed")
      plan.isRight shouldBe true

      And("the plan should have aggregation projections")
      val aggProjections = plan.value.projections.filter(_.aggregation.isDefined)
      aggProjections.length shouldBe 2

      And("SUM aggregation should be present")
      aggProjections.exists(_.aggregation == Some("SUM")) shouldBe true

      And("COUNT aggregation should be present")
      aggProjections.exists(_.aggregation == Some("COUNT")) shouldBe true
    }
  }

  // ============================================
  // Feature: Error Messages
  // ============================================

  Feature("UAT-007: Clear Error Messages") {
    info("As a Data Engineer")
    info("I want to receive clear error messages when something goes wrong")
    info("So that I can quickly identify and fix issues")

    Scenario("User references non-existent source entity") {
      Given("a schema registry with known entities")
      val registry = SchemaRegistryImpl.fromConfig(sampleEntities, sampleBindings).value
      val compiler = new Compiler(registry)

      And("a mapping referencing a non-existent entity")
      val mapping = MappingConfig(
        name = "invalid_source",
        description = None,
        source = SourceConfig("NonExistentEntity", "${epoch}"),
        target = TargetConfig("Order", GrainConfig("Atomic", List("order_id"))),
        morphisms = None,
        projections = List(ProjectionConfig("order_id", "order_id", None)),
        validations = None
      )

      When("the user attempts to compile the mapping")
      val ctx = createMockContext(registry)
      val result = compiler.compile(mapping, ctx)

      Then("the compilation should fail")
      result.isLeft shouldBe true

      And("the error should have a descriptive error type")
      result.left.value.errorType should include("error")
    }

    Scenario("User uses invalid attribute path in projection") {
      Given("a schema registry with Order entity")
      val registry = SchemaRegistryImpl.fromConfig(sampleEntities, sampleBindings).value
      val compiler = new Compiler(registry)

      And("a mapping with an invalid path in projections")
      val mapping = MappingConfig(
        name = "invalid_path",
        description = None,
        source = SourceConfig("Order", "${epoch}"),
        target = TargetConfig("Order", GrainConfig("Atomic", List("order_id"))),
        morphisms = None,
        projections = List(
          ProjectionConfig("bad_field", "nonexistent.invalid.path", None)
        ),
        validations = None
      )

      When("the user attempts to compile the mapping")
      val ctx = createMockContext(registry)
      val result = compiler.compile(mapping, ctx)

      Then("the compilation should fail")
      result.isLeft shouldBe true
    }
  }

  // ============================================
  // Feature: Multiple Morphisms
  // ============================================

  Feature("UAT-008: Chained Morphisms") {
    info("As a Data Engineer")
    info("I want to chain multiple morphisms together")
    info("So that I can build complex transformation pipelines")

    Scenario("User creates pipeline with filter-filter-aggregate chain") {
      Given("a schema registry with Order and CustomerOrderSummary entities")
      val registry = SchemaRegistryImpl.fromConfig(sampleEntities, sampleBindings).value
      val compiler = new Compiler(registry)

      And("a mapping with multiple chained morphisms")
      val mapping = MappingConfig(
        name = "filtered_summary",
        description = Some("Filter completed high-value orders then aggregate"),
        source = SourceConfig("Order", "${epoch}"),
        target = TargetConfig("CustomerOrderSummary", GrainConfig("Customer", List("customer_id"))),
        morphisms = Some(List(
          MorphismConfig("filter_completed", "FILTER", Some("status = 'COMPLETED'"), None, None),
          MorphismConfig("filter_high_value", "FILTER", Some("amount > 100"), None, None),
          MorphismConfig("aggregate_by_customer", "AGGREGATE", None, None, None)
        )),
        projections = List(
          ProjectionConfig("customer_id", "customer_id", None),
          ProjectionConfig("total_amount", "amount", Some("SUM"))
        ),
        validations = None
      )

      When("the user compiles the mapping")
      val ctx = createMockContext(registry)
      val plan = compiler.compile(mapping, ctx)

      Then("the compilation should succeed")
      plan.isRight shouldBe true

      And("the plan should have 3 morphisms in order")
      plan.value.morphisms.length shouldBe 3
      plan.value.morphisms.map(_.morphismType) shouldBe List("FILTER", "FILTER", "AGGREGATE")

      And("each morphism should have correct name")
      plan.value.morphisms.map(_.name) shouldBe List(
        "filter_completed",
        "filter_high_value",
        "aggregate_by_customer"
      )
    }
  }

  // ============================================
  // Feature: Relationship Traversal
  // ============================================

  Feature("UAT-009: Relationship Traversal in Projections") {
    info("As a Data Engineer")
    info("I want to include attributes from related entities in projections")
    info("So that I can denormalize data through relationship traversal")

    Scenario("User projects attribute from related entity") {
      Given("a schema registry with Order -> Customer relationship")
      val registry = SchemaRegistryImpl.fromConfig(sampleEntities, sampleBindings).value

      When("the user validates a path traversing the relationship")
      val pathResult = registry.validatePath("Order.customer.tier")

      Then("the path validation should succeed")
      pathResult.isRight shouldBe true

      And("the path should resolve to the Customer entity's attribute type")
      pathResult.value.finalType shouldBe "String"

      And("the traversal should include the customer relationship")
      val relationshipNames = pathResult.value.segments.collect {
        case RelationshipSegment(name, _) => name
      }
      relationshipNames should contain("customer")
    }
  }

  // ============================================
  // Feature: Error Type Consistency
  // ============================================

  Feature("UAT-010: Consistent Error Typing") {
    info("As a Data Engineer")
    info("I want errors to have consistent, machine-readable types")
    info("So that I can build automation around error handling")

    Scenario("Compilation error has correct error type") {
      Given("a compilation error")
      val error = CdmeError.CompilationError("Invalid configuration")

      When("the error type is retrieved")
      val errorType = error.errorType

      Then("it should be in snake_case format")
      errorType shouldBe "compilation_error"
    }

    Scenario("Grain safety error has correct error type") {
      Given("a grain safety error")
      val error = CdmeError.GrainSafetyError(
        sourceKey = "test",
        morphismPath = "test_path",
        sourceGrain = "Atomic",
        targetGrain = "Customer",
        violation = "test violation"
      )

      When("the error type is retrieved")
      val errorType = error.errorType

      Then("it should be in snake_case format")
      errorType shouldBe "grain_safety_error"
    }
  }

  // ============================================
  // Helper Methods
  // ============================================

  private def createMockContext(registry: SchemaRegistry): ExecutionContext = {
    ExecutionContext(
      runId = "uat-test-run",
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
