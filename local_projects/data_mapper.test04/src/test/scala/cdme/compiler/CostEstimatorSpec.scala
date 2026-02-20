// Validates: REQ-F-TRV-007
// Tests for cardinality budget, explosion detection
package cdme.compiler

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import cdme.model.category.CardinalityType
import cdme.model.error.ValidationError

class CostEstimatorSpec extends AnyFunSuite with Matchers:

  // --- REQ-F-TRV-007: Cardinality Cost Estimation ---

  test("REQ-F-TRV-007: OneToOne step preserves cardinality") {
    val report = CostEstimator.estimate(
      inputCardinality = 1000L,
      steps = List(CardinalityType.OneToOne)
    )
    report.stepEstimates shouldBe List(1000L)
    report.totalEstimate shouldBe 1000L
  }

  test("REQ-F-TRV-007: ManyToOne step preserves cardinality") {
    val report = CostEstimator.estimate(
      inputCardinality = 1000L,
      steps = List(CardinalityType.ManyToOne)
    )
    report.stepEstimates shouldBe List(1000L)
    report.totalEstimate shouldBe 1000L
  }

  test("REQ-F-TRV-007: OneToMany step increases cardinality by default fan-out (10x)") {
    val report = CostEstimator.estimate(
      inputCardinality = 1000L,
      steps = List(CardinalityType.OneToMany)
    )
    report.stepEstimates shouldBe List(10000L)
    report.totalEstimate shouldBe 10000L
  }

  test("REQ-F-TRV-007: ManyToMany step increases cardinality by default fan-out (100x)") {
    val report = CostEstimator.estimate(
      inputCardinality = 1000L,
      steps = List(CardinalityType.ManyToMany)
    )
    report.stepEstimates shouldBe List(100000L)
    report.totalEstimate shouldBe 100000L
  }

  test("REQ-F-TRV-007: multi-step estimation is cumulative") {
    val report = CostEstimator.estimate(
      inputCardinality = 100L,
      steps = List(
        CardinalityType.OneToOne,   // 100 * 1 = 100
        CardinalityType.OneToMany,  // 100 * 10 = 1000
        CardinalityType.ManyToOne   // 1000 * 1 = 1000
      )
    )
    report.stepEstimates shouldBe List(100L, 1000L, 1000L)
    report.totalEstimate shouldBe 1000L
  }

  test("REQ-F-TRV-007: declared statistics override defaults") {
    val report = CostEstimator.estimate(
      inputCardinality = 100L,
      steps = List(CardinalityType.OneToMany),
      declaredStats = Map(0 -> 50L) // 50x fan-out instead of 10x
    )
    report.stepEstimates shouldBe List(5000L)
    report.totalEstimate shouldBe 5000L
  }

  // --- Budget Checking ---

  test("REQ-F-TRV-007: within-budget report passes check") {
    val report = CostEstimator.estimate(1000L, List(CardinalityType.OneToOne))
    val budget = CardinalityBudget(
      maxOutputRows = 10000L,
      maxJoinFanOut = 100L,
      maxIntermediateSize = 100000L
    )
    val result = CostEstimator.checkBudget(report, budget)
    result.isRight shouldBe true
  }

  test("REQ-F-TRV-007: exceeding maxOutputRows is rejected") {
    val report = CostEstimator.estimate(1000L, List(CardinalityType.OneToMany))
    // totalEstimate = 10000, but budget only allows 5000
    val budget = CardinalityBudget(
      maxOutputRows = 5000L,
      maxJoinFanOut = 100L,
      maxIntermediateSize = 100000L
    )
    val result = CostEstimator.checkBudget(report, budget)
    result.isLeft shouldBe true
    result.left.toOption.get shouldBe a[ValidationError.BudgetExceeded]
  }

  test("REQ-F-TRV-007: exceeding maxIntermediateSize is rejected at the explosion step") {
    val report = CostEstimator.estimate(
      1000L,
      List(CardinalityType.OneToMany, CardinalityType.OneToMany)
    )
    // Step 0: 10000, Step 1: 100000
    val budget = CardinalityBudget(
      maxOutputRows = 200000L,
      maxJoinFanOut = 100L,
      maxIntermediateSize = 50000L // exceeded at step 1
    )
    val result = CostEstimator.checkBudget(report, budget)
    result.isLeft shouldBe true
    val err = result.left.toOption.get.asInstanceOf[ValidationError.BudgetExceeded]
    err.message should include("step")
  }

  test("REQ-F-TRV-007: empty step list within budget") {
    val report = CostEstimator.estimate(100L, Nil)
    val budget = CardinalityBudget(1000L, 100L, 10000L)
    CostEstimator.checkBudget(report, budget).isRight shouldBe true
  }

  test("CostReport tracks step estimates") {
    val report = CostReport(
      stepEstimates = List(100L, 1000L, 10000L),
      totalEstimate = 10000L,
      explosionPoint = None
    )
    report.stepEstimates should have size 3
    report.explosionPoint shouldBe None
  }
