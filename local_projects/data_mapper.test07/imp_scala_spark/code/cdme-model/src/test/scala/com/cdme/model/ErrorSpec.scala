// Validates: REQ-TYP-03, REQ-ERROR-01
// Tests for the CdmeError sealed hierarchy.
package com.cdme.model

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import com.cdme.model.error._
import com.cdme.model.types.CdmeType
import com.cdme.model.grain.Grain

class ErrorSpec extends AnyFlatSpec with Matchers {

  "TypeUnificationError" should "carry expected and actual types" in {
    val err = TypeUnificationError(
      expected = CdmeType.IntType,
      actual = CdmeType.StringType,
      morphismPath = List("m1", "m2"),
      sourceEntity = Some("trade")
    )
    err.code shouldBe "TYPE_UNIFICATION"
    err.expected shouldBe CdmeType.IntType
    err.actual shouldBe CdmeType.StringType
    err.morphismPath shouldBe List("m1", "m2")
    err.sourceEntity shouldBe Some("trade")
  }

  "GrainViolationError" should "carry source and target grains" in {
    val err = GrainViolationError(Grain.Atomic, Grain.Monthly, List("m1"))
    err.code shouldBe "GRAIN_VIOLATION"
    err.sourceGrain shouldBe Grain.Atomic
    err.targetGrain shouldBe Grain.Monthly
  }

  "CircuitBreakerTripped" should "report error rate" in {
    val err = CircuitBreakerTripped(
      errorCount = 500,
      totalCount = 1000,
      thresholdPercent = 5.0
    )
    err.code shouldBe "CIRCUIT_BREAKER_TRIPPED"
    err.message should include("500")
    err.message should include("1000")
  }

  "AccountingInvariantError" should "report the discrepancy" in {
    val err = AccountingInvariantError(
      inputCount = 100,
      processedCount = 80,
      filteredCount = 10,
      erroredCount = 5
    )
    err.code shouldBe "ACCOUNTING_INVARIANT"
    err.message should include("100")
    err.message should include("80")
  }

  "All error types" should "implement CdmeError trait" in {
    val errors: List[CdmeError] = List(
      TypeUnificationError(CdmeType.IntType, CdmeType.FloatType, Nil),
      RefinementViolationError("Positive", "x > 0", "-1", Nil),
      GrainViolationError(Grain.Atomic, Grain.Daily, Nil),
      PathNotFoundError("a.b.c", "morphism not found"),
      CompositionError("m1", "m2", "codomain mismatch"),
      AccessDeniedError("m1", "user1", Nil),
      BudgetExceededError(10000, 5000),
      CircuitBreakerTripped(100, 200, 5.0),
      BatchThresholdExceeded(100, 1000),
      BoundaryMisalignError("epoch1", "epoch2", "different slicing"),
      AccountingInvariantError(100, 80, 10, 5),
      ExternalCalculatorError("calc1", "timeout"),
      ContractBreachError("c1", "conservation", "0.95", "1.0"),
      LateArrivalError("epoch1", "data arrived after close"),
      ValidationError("generic error"),
      ConfigError("bad config")
    )
    errors.foreach { e =>
      e.code should not be empty
      e.message should not be empty
    }
    errors should have size 16
  }

  "Error defaults" should "use None for optional fields" in {
    val err = PathNotFoundError("a.b", "not found")
    err.sourceEntity shouldBe None
    err.sourceEpoch shouldBe None
    err.morphismPath shouldBe empty
  }
}
