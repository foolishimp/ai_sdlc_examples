// Validates: REQ-F-LDM-004, REQ-F-LDM-006, REQ-F-LDM-007, REQ-F-TRV-002, REQ-BR-LDM-001
// Tests for grain safety, multi-level aggregation
package cdme.compiler

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import cdme.model.grain.{Grain, GrainOrder}
import cdme.model.category.CardinalityType
import cdme.model.error.ValidationError

class GrainCheckerSpec extends AnyFunSuite with Matchers {

  private val order = GrainOrder.standardTemporal

  // --- REQ-F-TRV-002: Grain Safety Enforcement ---

  test("REQ-F-TRV-002: same grain level is always safe regardless of cardinality") {
    for (card <- CardinalityType.values) {
      GrainChecker.checkTransition(Grain.Atomic, Grain.Atomic, card, order).isRight shouldBe true
      GrainChecker.checkTransition(Grain.Daily, Grain.Daily, card, order).isRight shouldBe true
    }
  }

  test("REQ-F-TRV-002: finer to coarser with ManyToOne (aggregation) is allowed") {
    val result = GrainChecker.checkTransition(
      Grain.Atomic, Grain.Daily, CardinalityType.ManyToOne, order
    )
    result.isRight shouldBe true
  }

  test("REQ-F-TRV-002: finer to coarser with OneToOne is rejected") {
    val result = GrainChecker.checkTransition(
      Grain.Atomic, Grain.Daily, CardinalityType.OneToOne, order
    )
    result.isLeft shouldBe true
    result.left.toOption.get shouldBe a[ValidationError.GrainViolation]
  }

  test("REQ-F-TRV-002: finer to coarser with OneToMany is rejected") {
    val result = GrainChecker.checkTransition(
      Grain.Atomic, Grain.Daily, CardinalityType.OneToMany, order
    )
    result.isLeft shouldBe true
  }

  test("REQ-F-TRV-002: coarser to finer with ManyToOne (reverse agg) is rejected") {
    val result = GrainChecker.checkTransition(
      Grain.Monthly, Grain.Daily, CardinalityType.ManyToOne, order
    )
    result.isLeft shouldBe true
    result.left.toOption.get shouldBe a[ValidationError.GrainViolation]
    result.left.toOption.get.message should include("Cannot aggregate from coarser")
  }

  test("REQ-F-TRV-002: coarser to finer with OneToMany (expansion) is allowed") {
    val result = GrainChecker.checkTransition(
      Grain.Monthly, Grain.Daily, CardinalityType.OneToMany, order
    )
    result.isRight shouldBe true
  }

  test("REQ-BR-LDM-001: incomparable grains are rejected") {
    val customGrain = Grain("Custom")
    val result = GrainChecker.checkTransition(
      Grain.Atomic, customGrain, CardinalityType.OneToOne, order
    )
    result.isLeft shouldBe true
    result.left.toOption.get.message should include("not comparable")
  }

  // --- REQ-F-LDM-007: Multi-Level Aggregation ---

  test("REQ-F-LDM-007: multi-level aggregation chain Atomic->Daily->Monthly is valid") {
    val transitions = List(
      (Grain.Atomic, Grain.Daily, CardinalityType.ManyToOne),
      (Grain.Daily, Grain.Monthly, CardinalityType.ManyToOne)
    )
    val errors = GrainChecker.validatePath(transitions, order)
    errors shouldBe empty
  }

  test("REQ-F-LDM-007: multi-level chain with invalid step collects the error") {
    val transitions = List(
      (Grain.Atomic, Grain.Daily, CardinalityType.ManyToOne),
      (Grain.Daily, Grain.Monthly, CardinalityType.OneToOne) // invalid: needs ManyToOne
    )
    val errors = GrainChecker.validatePath(transitions, order)
    errors should have size 1
    errors.head shouldBe a[ValidationError.GrainViolation]
  }

  test("REQ-F-LDM-007: Atomic -> Daily -> Monthly -> Yearly chain is valid") {
    val transitions = List(
      (Grain.Atomic, Grain.Daily, CardinalityType.ManyToOne),
      (Grain.Daily, Grain.Monthly, CardinalityType.ManyToOne),
      (Grain.Monthly, Grain.Yearly, CardinalityType.ManyToOne)
    )
    val errors = GrainChecker.validatePath(transitions, order)
    errors shouldBe empty
  }

  test("validatePath accumulates all errors in a chain") {
    val transitions = List(
      (Grain.Atomic, Grain.Daily, CardinalityType.OneToOne),  // error
      (Grain.Daily, Grain.Monthly, CardinalityType.OneToOne)  // error
    )
    val errors = GrainChecker.validatePath(transitions, order)
    errors should have size 2
  }

  test("validatePath with empty transitions returns no errors") {
    val errors = GrainChecker.validatePath(Nil, order)
    errors shouldBe empty
  }
}
