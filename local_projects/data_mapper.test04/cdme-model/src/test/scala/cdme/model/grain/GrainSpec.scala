// Validates: REQ-F-LDM-004, REQ-F-LDM-006, REQ-F-LDM-007, REQ-F-TRV-002, REQ-F-INT-003, REQ-BR-LDM-001
// Tests for grain partial order, grain safety violations, monoidal aggregation
package cdme.model.grain

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import org.scalacheck.Gen
import cdme.model.error.ValidationError
import cdme.model.grain.Monoid._

class GrainSpec extends AnyFunSuite with Matchers with ScalaCheckPropertyChecks {

  private val standardOrder = GrainOrder.standardTemporal

  // --- REQ-F-LDM-004: Grain Metadata ---

  test("REQ-F-LDM-004: grain levels are created from string identifiers") {
    val g = Grain("Daily")
    g.level shouldBe "Daily"
    g.show shouldBe "Grain(Daily)"
  }

  test("REQ-F-LDM-004: built-in grain levels exist") {
    Grain.Atomic.level shouldBe "Atomic"
    Grain.Daily.level shouldBe "Daily"
    Grain.Monthly.level shouldBe "Monthly"
    Grain.Quarterly.level shouldBe "Quarterly"
    Grain.Yearly.level shouldBe "Yearly"
  }

  // --- Partial Order Properties ---

  test("GrainOrder: standard temporal ordering has 5 levels") {
    standardOrder.orderedLevels.size shouldBe 5
  }

  test("GrainOrder: Atomic is finer than all other levels") {
    standardOrder.isFinerThan(Grain.Atomic, Grain.Daily) shouldBe true
    standardOrder.isFinerThan(Grain.Atomic, Grain.Monthly) shouldBe true
    standardOrder.isFinerThan(Grain.Atomic, Grain.Quarterly) shouldBe true
    standardOrder.isFinerThan(Grain.Atomic, Grain.Yearly) shouldBe true
  }

  test("GrainOrder: Yearly is coarser than all other levels") {
    standardOrder.isFinerThan(Grain.Yearly, Grain.Atomic) shouldBe false
    standardOrder.isCoarserOrEqual(Grain.Yearly, Grain.Atomic) shouldBe true
    standardOrder.isCoarserOrEqual(Grain.Yearly, Grain.Daily) shouldBe true
    standardOrder.isCoarserOrEqual(Grain.Yearly, Grain.Monthly) shouldBe true
    standardOrder.isCoarserOrEqual(Grain.Yearly, Grain.Quarterly) shouldBe true
  }

  test("GrainOrder: isFinerThan is irreflexive - no grain is finer than itself") {
    for (grain <- standardOrder.orderedLevels) {
      standardOrder.isFinerThan(grain, grain) shouldBe false
    }
  }

  test("GrainOrder: isCoarserOrEqual is reflexive") {
    for (grain <- standardOrder.orderedLevels) {
      standardOrder.isCoarserOrEqual(grain, grain) shouldBe true
    }
  }

  test("GrainOrder: areSameLevel is reflexive") {
    for (grain <- standardOrder.orderedLevels) {
      standardOrder.areSameLevel(grain, grain) shouldBe true
    }
  }

  test("GrainOrder: areSameLevel rejects different levels") {
    standardOrder.areSameLevel(Grain.Atomic, Grain.Daily) shouldBe false
  }

  test("GrainOrder: unknown grains are not comparable") {
    val unknown = Grain("Custom")
    standardOrder.areComparable(Grain.Atomic, unknown) shouldBe false
    standardOrder.isFinerThan(Grain.Atomic, unknown) shouldBe false
  }

  test("GrainOrder: transitivity - if a < b and b < c then a < c") {
    val grains = standardOrder.orderedLevels
    for {
      i <- grains.indices
      j <- i + 1 until grains.size
      k <- j + 1 until grains.size
    } {
      val a = grains(i)
      val b = grains(j)
      val c = grains(k)
      standardOrder.isFinerThan(a, b) shouldBe true
      standardOrder.isFinerThan(b, c) shouldBe true
      standardOrder.isFinerThan(a, c) shouldBe true
    }
  }

  // --- Aggregation Validation ---

  test("REQ-F-LDM-007: validateAggregation allows finer-to-coarser") {
    standardOrder.validateAggregation(Grain.Atomic, Grain.Daily).isRight shouldBe true
    standardOrder.validateAggregation(Grain.Daily, Grain.Monthly).isRight shouldBe true
    standardOrder.validateAggregation(Grain.Monthly, Grain.Yearly).isRight shouldBe true
  }

  test("REQ-F-LDM-007: validateAggregation allows same-level") {
    standardOrder.validateAggregation(Grain.Daily, Grain.Daily).isRight shouldBe true
  }

  test("REQ-F-LDM-007: validateAggregation rejects coarser-to-finer") {
    val result = standardOrder.validateAggregation(Grain.Monthly, Grain.Daily)
    result.isLeft shouldBe true
    result.left.toOption.get shouldBe a[ValidationError.GrainViolation]
  }

  test("REQ-F-LDM-007: validateAggregation rejects incomparable grains") {
    val unknown = Grain("Custom")
    val result = standardOrder.validateAggregation(Grain.Atomic, unknown)
    result.isLeft shouldBe true
  }

  // --- REQ-F-LDM-006: Monoid Laws (property-based) ---

  test("REQ-F-LDM-006: SumMonoid[Int] - associativity") {
    val m = Monoid.SumMonoidInt
    forAll { (a: Int, b: Int, c: Int) =>
      whenever(
        a.toLong + b.toLong + c.toLong < Int.MaxValue &&
        a.toLong + b.toLong + c.toLong > Int.MinValue
      ) {
        m.combine(m.combine(a, b), c) shouldBe m.combine(a, m.combine(b, c))
      }
    }
  }

  test("REQ-F-LDM-006: SumMonoid[Int] - left identity") {
    val m = Monoid.SumMonoidInt
    forAll { (a: Int) =>
      m.combine(m.empty, a) shouldBe a
    }
  }

  test("REQ-F-LDM-006: SumMonoid[Int] - right identity") {
    val m = Monoid.SumMonoidInt
    forAll { (a: Int) =>
      m.combine(a, m.empty) shouldBe a
    }
  }

  test("REQ-F-LDM-006: SumMonoid[Long] - associativity") {
    val m = Monoid.SumMonoidLong
    val boundedLong = Gen.choose(Long.MinValue / 4, Long.MaxValue / 4)
    forAll(boundedLong, boundedLong, boundedLong) { (a: Long, b: Long, c: Long) =>
      m.combine(m.combine(a, b), c) shouldBe m.combine(a, m.combine(b, c))
    }
  }

  test("REQ-F-LDM-006: SumMonoid[BigDecimal] - identity and associativity") {
    val m = Monoid.SumMonoidBigDecimal
    m.combine(m.empty, BigDecimal(42)) shouldBe BigDecimal(42)
    m.combine(BigDecimal(42), m.empty) shouldBe BigDecimal(42)
    m.combine(m.combine(BigDecimal(1), BigDecimal(2)), BigDecimal(3)) shouldBe
      m.combine(BigDecimal(1), m.combine(BigDecimal(2), BigDecimal(3)))
  }

  test("REQ-F-LDM-006: ConcatMonoid - associativity") {
    val m = Monoid.ConcatMonoid
    forAll { (a: String, b: String, c: String) =>
      m.combine(m.combine(a, b), c) shouldBe m.combine(a, m.combine(b, c))
    }
  }

  test("REQ-F-LDM-006: ConcatMonoid - identity") {
    val m = Monoid.ConcatMonoid
    m.combine(m.empty, "hello") shouldBe "hello"
    m.combine("hello", m.empty) shouldBe "hello"
  }

  test("REQ-F-LDM-006: aggregation over empty set yields identity element") {
    val m = Monoid.SumMonoidInt
    val empty: Iterable[Int] = Iterable.empty
    empty.combineAll(m) shouldBe 0
  }

  test("REQ-F-LDM-006: custom monoid creation") {
    val maxMonoid = Monoid.custom[Int](Int.MinValue, math.max, "MaxMonoid")
    maxMonoid.empty shouldBe Int.MinValue
    maxMonoid.combine(3, 7) shouldBe 7
    maxMonoid.combine(10, 2) shouldBe 10
    maxMonoid.name shouldBe "MaxMonoid"
  }

  test("REQ-F-LDM-006: combineAll aggregates a collection") {
    val values = List(1, 2, 3, 4, 5)
    values.combineAll(Monoid.SumMonoidInt) shouldBe 15
  }
}
