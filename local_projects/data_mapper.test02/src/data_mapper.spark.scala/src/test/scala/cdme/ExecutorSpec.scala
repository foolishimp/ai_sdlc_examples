package cdme

import cdme.core._
import cdme.compiler._
import cdme.executor._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues

/**
 * Executor unit tests following TDD.
 * Tests the core logic without requiring Spark initialization (Java 25 incompatible).
 *
 * Implements: REQ-INT-01 (Morphism Execution), REQ-TRV-01 (Context Lifting)
 */
class ExecutorSpec extends AnyFlatSpec with Matchers with EitherValues {

  // ============================================
  // Unit Tests for Morphism Logic
  // ============================================

  "MorphismOp" should "correctly represent filter morphism" in {
    val filterMorphism = MorphismOp(
      name = "filter_completed",
      morphismType = "FILTER",
      predicate = Some("status = 'COMPLETED'")
    )

    filterMorphism.name shouldBe "filter_completed"
    filterMorphism.morphismType shouldBe "FILTER"
    filterMorphism.predicate shouldBe Some("status = 'COMPLETED'")
  }

  it should "correctly represent aggregate morphism" in {
    val aggMorphism = MorphismOp(
      name = "sum_amounts",
      morphismType = "AGGREGATE",
      predicate = None
    )

    aggMorphism.morphismType shouldBe "AGGREGATE"
    aggMorphism.predicate shouldBe None
  }

  // ============================================
  // Unit Tests for Projection Logic
  // ============================================

  "ProjectionOp" should "correctly represent simple projection" in {
    val proj = ProjectionOp(
      name = "order_id",
      source = "order_id",
      aggregation = None
    )

    proj.name shouldBe "order_id"
    proj.source shouldBe "order_id"
    proj.aggregation shouldBe None
  }

  it should "correctly represent aggregation projection" in {
    val proj = ProjectionOp(
      name = "total_amount",
      source = "amount",
      aggregation = Some("SUM")
    )

    proj.name shouldBe "total_amount"
    proj.source shouldBe "amount"
    proj.aggregation shouldBe Some("SUM")
  }

  it should "detect if aggregation is present" in {
    val projections = List(
      ProjectionOp("customer_id", "customer_id", None),
      ProjectionOp("total_amount", "amount", Some("SUM"))
    )

    val hasAggregations = projections.exists(_.aggregation.isDefined)
    hasAggregations shouldBe true
  }

  it should "detect if no aggregation is present" in {
    val projections = List(
      ProjectionOp("order_id", "order_id", None),
      ProjectionOp("status", "status", None)
    )

    val hasAggregations = projections.exists(_.aggregation.isDefined)
    hasAggregations shouldBe false
  }

  // ============================================
  // Unit Tests for ExecutionPlan
  // ============================================

  "ExecutionPlan" should "correctly identify grain transition" in {
    val plan = ExecutionPlan(
      mappingName = "order_summary",
      sourceEntity = "Order",
      targetEntity = "CustomerOrderSummary",
      sourceGrain = Grain(GrainLevel.Atomic, List("order_id")),
      targetGrain = Grain(GrainLevel.Customer, List("customer_id")),
      morphisms = List.empty,
      projections = List.empty
    )

    plan.sourceGrain.level shouldBe GrainLevel.Atomic
    plan.targetGrain.level shouldBe GrainLevel.Customer
    (plan.sourceGrain.level.level < plan.targetGrain.level.level) shouldBe true
  }

  it should "correctly store morphism sequence" in {
    val plan = ExecutionPlan(
      mappingName = "filtered_orders",
      sourceEntity = "Order",
      targetEntity = "FilteredOrder",
      sourceGrain = Grain(GrainLevel.Atomic, List("order_id")),
      targetGrain = Grain(GrainLevel.Atomic, List("order_id")),
      morphisms = List(
        MorphismOp("filter1", "FILTER", Some("status = 'ACTIVE'")),
        MorphismOp("filter2", "FILTER", Some("amount > 100"))
      ),
      projections = List.empty
    )

    plan.morphisms.length shouldBe 2
    plan.morphisms.head.name shouldBe "filter1"
    plan.morphisms(1).name shouldBe "filter2"
  }

  // ============================================
  // Unit Tests for Error Handling Logic
  // ============================================

  "CdmeError" should "correctly create compilation error" in {
    val error = CdmeError.CompilationError("Invalid predicate")

    error.errorType shouldBe "compilation_error"
    error.morphismPath shouldBe "compilation"  // default value
  }

  it should "correctly create grain safety error" in {
    val error = CdmeError.GrainSafetyError(
      sourceKey = "order_id",
      morphismPath = "aggregate_by_customer",
      sourceGrain = "Atomic",
      targetGrain = "Customer",
      violation = "Cannot coarsen without aggregation"
    )

    error.errorType shouldBe "grain_safety_error"
    error.sourceGrain shouldBe "Atomic"
    error.targetGrain shouldBe "Customer"
  }

  // ============================================
  // Unit Tests for Aggregation Types
  // ============================================

  "Aggregation types" should "support SUM" in {
    val proj = ProjectionOp("total", "amount", Some("SUM"))
    proj.aggregation shouldBe Some("SUM")
  }

  it should "support COUNT" in {
    val proj = ProjectionOp("order_count", "order_id", Some("COUNT"))
    proj.aggregation shouldBe Some("COUNT")
  }

  it should "support AVG" in {
    val proj = ProjectionOp("avg_amount", "amount", Some("AVG"))
    proj.aggregation shouldBe Some("AVG")
  }

  it should "support MIN" in {
    val proj = ProjectionOp("min_amount", "amount", Some("MIN"))
    proj.aggregation shouldBe Some("MIN")
  }

  it should "support MAX" in {
    val proj = ProjectionOp("max_amount", "amount", Some("MAX"))
    proj.aggregation shouldBe Some("MAX")
  }

  // ============================================
  // Unit Tests for Filter Morphism Validation
  // ============================================

  "Filter morphism" should "require predicate" in {
    val filterWithoutPredicate = MorphismOp(
      name = "bad_filter",
      morphismType = "FILTER",
      predicate = None
    )

    // Validation logic: filter requires predicate
    filterWithoutPredicate.predicate.isEmpty shouldBe true
  }

  it should "accept valid predicate" in {
    val filterWithPredicate = MorphismOp(
      name = "good_filter",
      morphismType = "FILTER",
      predicate = Some("status = 'COMPLETED'")
    )

    filterWithPredicate.predicate.isDefined shouldBe true
  }

  // ============================================
  // Unit Tests for Execution Result Structure
  // ============================================

  "ExecutionResult structure" should "contain all required fields" in {
    // This is a compile-time check - if this compiles, the structure is correct
    // We can't instantiate without DataFrame, but we can verify the case class exists

    // Verify the class has expected members via reflection
    val resultClass = classOf[ExecutionResult]
    val fieldNames = resultClass.getDeclaredFields.map(_.getName)

    fieldNames should contain("mappingName")
    fieldNames should contain("sourceEntity")
    fieldNames should contain("sourceRowCount")
    fieldNames should contain("outputRowCount")
  }

  // ============================================
  // Unit Tests for Error Threshold Checking
  // Implements: REQ-ERR-02 (Error Threshold Configuration)
  // ============================================

  "ErrorThresholdChecker" should "pass when error rate is below threshold" in {
    val checker = ErrorThresholdChecker(errorThreshold = 0.05)
    val result = checker.check(totalRecords = 1000, errorCount = 40)

    result.isRight shouldBe true
    result.value shouldBe ThresholdCheckResult(
      passed = true,
      errorRate = 0.04,
      threshold = 0.05,
      totalRecords = 1000,
      errorCount = 40
    )
  }

  it should "fail when error rate exceeds threshold" in {
    val checker = ErrorThresholdChecker(errorThreshold = 0.05)
    val result = checker.check(totalRecords = 1000, errorCount = 60)

    result.isLeft shouldBe true
    result.left.value shouldBe a[CdmeError.ThresholdExceededError]
  }

  it should "pass when error rate equals threshold (boundary)" in {
    val checker = ErrorThresholdChecker(errorThreshold = 0.05)
    val result = checker.check(totalRecords = 1000, errorCount = 50)

    result.isRight shouldBe true
    result.value.passed shouldBe true
  }

  it should "handle zero tolerance (0% threshold)" in {
    val checker = ErrorThresholdChecker(errorThreshold = 0.0)
    val result = checker.check(totalRecords = 1000, errorCount = 1)

    result.isLeft shouldBe true
    val error = result.left.value.asInstanceOf[CdmeError.ThresholdExceededError]
    error.threshold shouldBe 0.0
    error.actualRate shouldBe 0.001
  }

  it should "pass with zero errors on zero tolerance" in {
    val checker = ErrorThresholdChecker(errorThreshold = 0.0)
    val result = checker.check(totalRecords = 1000, errorCount = 0)

    result.isRight shouldBe true
    result.value.errorRate shouldBe 0.0
  }

  it should "handle empty dataset (zero records)" in {
    val checker = ErrorThresholdChecker(errorThreshold = 0.05)
    val result = checker.check(totalRecords = 0, errorCount = 0)

    result.isRight shouldBe true
    result.value.passed shouldBe true
    result.value.errorRate shouldBe 0.0
  }

  it should "handle 100% threshold (accept all errors)" in {
    val checker = ErrorThresholdChecker(errorThreshold = 1.0)
    val result = checker.check(totalRecords = 100, errorCount = 100)

    result.isRight shouldBe true
    result.value.passed shouldBe true
  }

  "ThresholdExceededError" should "have correct error type" in {
    val error = CdmeError.ThresholdExceededError(
      sourceKey = "batch_001",
      morphismPath = "validate_records",
      threshold = 0.05,
      actualRate = 0.08,
      totalRecords = 1000,
      errorCount = 80
    )

    error.errorType shouldBe "threshold_exceeded_error"
    error.threshold shouldBe 0.05
    error.actualRate shouldBe 0.08
  }

  // ============================================
  // Integration Test Placeholders (require Spark)
  // ============================================

  "Executor integration" should "be tested with compatible Java version" in {
    // These tests require Java 17 or earlier due to Spark 3.5 limitations
    // Mark as pending until proper test environment is configured
    pending
  }

  // ============================================
  // Unit Tests for Algebra Integration
  // Implements: REQ-ADJ-01 (Monoid-based aggregations)
  // ============================================

  "Aggregator" should "create aggregator from monoid" in {
    import cdme.core.Aggregator
    import cdme.core.MonoidInstances._
    import cats.implicits._

    case class Order(amount: BigDecimal)

    // Create aggregator for summing order amounts
    val sumAgg = Aggregator.fromMonoid[Order, BigDecimal](_.amount)

    sumAgg.zero shouldBe BigDecimal(0)
    sumAgg.combine(BigDecimal(100), Order(BigDecimal(50))) shouldBe BigDecimal(150)
    sumAgg.merge(BigDecimal(100), BigDecimal(200)) shouldBe BigDecimal(300)
    sumAgg.finish(BigDecimal(500)) shouldBe BigDecimal(500)
  }

  it should "support count aggregation via Long monoid" in {
    import cdme.core.Aggregator
    import cdme.core.MonoidInstances._

    case class Order(id: String)

    val countAgg = Aggregator.fromMonoid[Order, Long](_ => 1L)

    countAgg.zero shouldBe 0L
    countAgg.combine(5L, Order("order1")) shouldBe 6L
    countAgg.merge(10L, 20L) shouldBe 30L
  }

  it should "support average aggregation via Avg monoid" in {
    import cdme.core.MonoidInstances._
    import cats.implicits._

    val avg1 = Avg(BigDecimal(100), 2)
    val avg2 = Avg(BigDecimal(200), 3)

    val combined = avgMonoid.combine(avg1, avg2)

    combined.sum shouldBe BigDecimal(300)
    combined.count shouldBe 5
    combined.average shouldBe Some(BigDecimal(60))
  }

  it should "support min aggregation" in {
    import cdme.core.MonoidInstances._

    val minBigDecimal = minMonoid[BigDecimal]

    minBigDecimal.combine(Some(BigDecimal(100)), Some(BigDecimal(50))) shouldBe Some(BigDecimal(50))
    minBigDecimal.combine(Some(BigDecimal(100)), None) shouldBe Some(BigDecimal(100))
    minBigDecimal.combine(None, None) shouldBe None
  }

  it should "support max aggregation" in {
    import cdme.core.MonoidInstances._

    val maxBigDecimal = maxMonoid[BigDecimal]

    maxBigDecimal.combine(Some(BigDecimal(100)), Some(BigDecimal(50))) shouldBe Some(BigDecimal(100))
    maxBigDecimal.combine(Some(BigDecimal(100)), None) shouldBe Some(BigDecimal(100))
    maxBigDecimal.combine(None, None) shouldBe None
  }

  "AggregationOp" should "specify aggregation function and extract path" in {
    // Test the new AggregationOp case class that will wire Aggregator to Executor
    val aggOp = AggregationOp(
      name = "total_amount",
      sourcePath = "amount",
      aggregationType = "SUM",
      groupByKeys = List("customer_id")
    )

    aggOp.name shouldBe "total_amount"
    aggOp.sourcePath shouldBe "amount"
    aggOp.aggregationType shouldBe "SUM"
    aggOp.groupByKeys shouldBe List("customer_id")
  }

  it should "support different aggregation types" in {
    val sumOp = AggregationOp("total", "amount", "SUM", List("customer_id"))
    val countOp = AggregationOp("count", "order_id", "COUNT", List("customer_id"))
    val avgOp = AggregationOp("avg_amount", "amount", "AVG", List("customer_id"))
    val minOp = AggregationOp("min_amount", "amount", "MIN", List("customer_id"))
    val maxOp = AggregationOp("max_amount", "amount", "MAX", List("customer_id"))

    sumOp.aggregationType shouldBe "SUM"
    countOp.aggregationType shouldBe "COUNT"
    avgOp.aggregationType shouldBe "AVG"
    minOp.aggregationType shouldBe "MIN"
    maxOp.aggregationType shouldBe "MAX"
  }

  "AggregatorFactory" should "create appropriate Aggregator for SUM" in {
    import cdme.executor.AggregatorFactory
    import cdme.core.MonoidInstances._

    val aggregator = AggregatorFactory.create[BigDecimal]("SUM")

    aggregator.zero shouldBe BigDecimal(0)
    aggregator.combine(BigDecimal(100), BigDecimal(50)) shouldBe BigDecimal(150)
  }

  it should "create appropriate Aggregator for COUNT" in {
    import cdme.executor.AggregatorFactory
    import cdme.core.MonoidInstances._

    val aggregator = AggregatorFactory.create[Long]("COUNT")

    aggregator.zero shouldBe 0L
    aggregator.combine(5L, 1L) shouldBe 6L
  }

  it should "create appropriate Aggregator for AVG" in {
    import cdme.executor.AggregatorFactory
    import cdme.core.MonoidInstances._

    val aggregator = AggregatorFactory.create[Avg]("AVG")

    val result = aggregator.combine(Avg(BigDecimal(100), 2), Avg(BigDecimal(50), 1))

    result.sum shouldBe BigDecimal(150)
    result.count shouldBe 3
  }

  it should "fail for unknown aggregation type" in {
    import cdme.executor.AggregatorFactory

    val thrown = intercept[IllegalArgumentException] {
      AggregatorFactory.create[BigDecimal]("UNKNOWN")
    }

    thrown.getMessage should include("Unsupported aggregation type")
  }

  it should "provide helper method to check if aggregation type is supported" in {
    import cdme.executor.AggregatorFactory

    AggregatorFactory.isSupported("SUM") shouldBe true
    AggregatorFactory.isSupported("COUNT") shouldBe true
    AggregatorFactory.isSupported("AVG") shouldBe true
    AggregatorFactory.isSupported("MIN") shouldBe true
    AggregatorFactory.isSupported("MAX") shouldBe true
    AggregatorFactory.isSupported("UNKNOWN") shouldBe false
    AggregatorFactory.isSupported("sum") shouldBe true  // Case-insensitive
  }

  it should "provide list of supported aggregation types" in {
    import cdme.executor.AggregatorFactory

    val types = AggregatorFactory.supportedTypes

    types should contain("SUM")
    types should contain("COUNT")
    types should contain("AVG")
    types should contain("MIN")
    types should contain("MAX")
    types.length shouldBe 5
  }

  // ============================================
  // Unit Tests for Dataset[T] Type Safety
  // Implements: REQ-TYP-01, REQ-TYP-02 (ADR-006)
  // ============================================

  "TypedRow" should "represent mapped data with type safety" in {
    // Validates: REQ-TYP-01 (Type-Safe Morphism Composition)
    import cdme.executor.TypedRow

    val row = TypedRow(
      fields = Map(
        "order_id" -> "ORDER001",
        "customer_id" -> "CUST001",
        "amount" -> BigDecimal(100.50)
      )
    )

    row.fields("order_id") shouldBe "ORDER001"
    row.fields("customer_id") shouldBe "CUST001"
    row.fields("amount") shouldBe BigDecimal(100.50)
  }

  it should "support type-safe field access via get method" in {
    import cdme.executor.TypedRow

    val row = TypedRow(
      fields = Map(
        "order_id" -> "ORDER001",
        "amount" -> BigDecimal(100.50)
      )
    )

    row.get[String]("order_id") shouldBe Some("ORDER001")
    row.get[BigDecimal]("amount") shouldBe Some(BigDecimal(100.50))
    row.get[String]("missing") shouldBe None
  }

  it should "fail type-safe access for wrong type" in {
    import cdme.executor.TypedRow

    val row = TypedRow(
      fields = Map("amount" -> BigDecimal(100.50))
    )

    // Should fail casting BigDecimal to String
    row.get[String]("amount") shouldBe None
  }

  "TypedExecutor" should "have typed variant of applyProjections" in {
    // This test validates the API exists - full integration test requires Spark
    // Validates: REQ-TYP-01 (Dataset[T] for type safety)

    // Verify TypedExecutor class exists with expected method signature
    val executorClass = classOf[cdme.executor.TypedExecutor]
    val methods = executorClass.getDeclaredMethods.map(_.getName)

    methods should contain("applyProjectionsTyped")
  }

  it should "have typed execution method returning Dataset[TypedRow]" in {
    // Validates: REQ-TYP-01
    val executorClass = classOf[cdme.executor.TypedExecutor]
    val methods = executorClass.getDeclaredMethods.map(_.getName)

    methods should contain("executeTyped")
  }

  "TypedRow encoder" should "be defined in companion object" in {
    // Validates: ADR-006 requirement for auto-derived encoders
    // This test verifies the encoder method exists (compile-time check)
    // Actual encoder instantiation requires SparkSession (tested in integration tests)

    import cdme.executor.TypedRow

    // Verify encoder method exists via reflection
    val companionClass = TypedRow.getClass
    val methods = companionClass.getDeclaredMethods.map(_.getName)

    methods should contain("typedRowEncoder")
  }

  "ExecutionResult" should "have typed variant with Dataset[TypedRow]" in {
    // Validates: REQ-TYP-01 (Type-safe results)
    val resultClass = classOf[cdme.executor.TypedExecutionResult]
    val fieldNames = resultClass.getDeclaredFields.map(_.getName)

    fieldNames should contain("data")
    fieldNames should contain("mappingName")
    fieldNames should contain("sourceEntity")
  }
}
