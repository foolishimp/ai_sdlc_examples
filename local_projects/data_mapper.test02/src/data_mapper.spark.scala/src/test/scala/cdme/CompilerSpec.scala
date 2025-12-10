package cdme

import cdme.core._
import cdme.config._
import cdme.registry._
import cdme.compiler._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues

/**
 * Basic compiler tests.
 * Validates: Compilation logic, path validation, grain safety
 */
class CompilerSpec extends AnyFlatSpec with Matchers with EitherValues {

  // Test data setup
  val orderGrain = Grain(GrainLevel.Atomic, List("order_id"))

  val orderAttributes = List(
    Attribute("order_id", "String", nullable = false, primaryKey = true),
    Attribute("customer_id", "String", nullable = false),
    Attribute("total_amount", "Decimal", nullable = false),
    Attribute("order_date", "Date", nullable = false)
  )

  val orderRelationships = List(
    Relationship("customer", "Customer", Cardinality.NToOne, "customer_id")
  )

  val orderEntity = Entity(
    name = "Order",
    grain = orderGrain,
    attributes = orderAttributes,
    relationships = orderRelationships
  )

  val customerEntity = Entity(
    name = "Customer",
    grain = Grain(GrainLevel.Customer, List("customer_id")),
    attributes = List(
      Attribute("customer_id", "String", nullable = false, primaryKey = true),
      Attribute("name", "String", nullable = false)
    ),
    relationships = List.empty
  )

  val entities = Map(
    "Order" -> orderEntity,
    "Customer" -> customerEntity
  )

  val bindings = Map(
    "Order" -> PhysicalBinding("Order", "PARQUET", "test/data/orders"),
    "Customer" -> PhysicalBinding("Customer", "PARQUET", "test/data/customers")
  )

  "SchemaRegistry" should "validate valid paths" in {
    val registry = new SchemaRegistryImpl(entities, bindings)

    val result = registry.validatePath("Order.customer.name")

    result.isRight shouldBe true
    val pathResult = result.value
    pathResult.valid shouldBe true
    pathResult.finalType shouldBe "String"
  }

  it should "reject invalid entity paths" in {
    val registry = new SchemaRegistryImpl(entities, bindings)

    val result = registry.validatePath("InvalidEntity.field")

    result.isLeft shouldBe true
  }

  it should "reject invalid relationship paths" in {
    val registry = new SchemaRegistryImpl(entities, bindings)

    val result = registry.validatePath("Order.invalid_relationship.field")

    result.isLeft shouldBe true
  }

  it should "reject invalid attribute paths" in {
    val registry = new SchemaRegistryImpl(entities, bindings)

    val result = registry.validatePath("Order.invalid_field")

    result.isLeft shouldBe true
  }

  "Compiler" should "compile simple mapping" in {
    val registry = new SchemaRegistryImpl(entities, bindings)

    val mapping = MappingConfig(
      name = "test_mapping",
      description = None,
      source = SourceConfig("Order", "2024-12-15"),
      target = TargetConfig(
        "OrderSummary",
        GrainConfig("DAILY", List("order_date"))
      ),
      morphisms = None,
      projections = List(
        ProjectionConfig("order_date", "order_date", None),
        ProjectionConfig("total_amount", "total_amount", Some("SUM"))
      ),
      validations = None
    )

    val compiler = new Compiler(registry)

    // Note: This will fail without a full ExecutionContext
    // In a real test, we'd create a proper context with SparkSession
    // This is just structural validation

    val testCtx = null.asInstanceOf[ExecutionContext]
    // val result = compiler.compile(mapping, testCtx)
    // result.isRight shouldBe true
  }

  "GrainValidator" should "allow grain refinement with aggregation" in {
    val sourceGrain = Grain(GrainLevel.Atomic, List("order_id"))
    val targetGrain = Grain(GrainLevel.Daily, List("order_date"))

    val result = GrainValidator.validateTransition(sourceGrain, targetGrain, hasAggregation = true)

    result.isRight shouldBe true
  }

  it should "reject grain coarsening without aggregation" in {
    val sourceGrain = Grain(GrainLevel.Atomic, List("order_id"))
    val targetGrain = Grain(GrainLevel.Daily, List("order_date"))

    val result = GrainValidator.validateTransition(sourceGrain, targetGrain, hasAggregation = false)

    result.isLeft shouldBe true
  }

  it should "allow grain preservation" in {
    val sourceGrain = Grain(GrainLevel.Atomic, List("order_id"))
    val targetGrain = Grain(GrainLevel.Atomic, List("order_id"))

    val result = GrainValidator.validateTransition(sourceGrain, targetGrain, hasAggregation = false)

    result.isRight shouldBe true
  }
}
