// Validates: INT-006 (Speculative)
// Tests for Frobenius algebra laws.
package com.cdme.adjoint

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import com.cdme.adjoint.frobenius._

class FrobeniusSpec extends AnyFlatSpec with Matchers with ScalaCheckPropertyChecks {

  "SumFrobeniusLong" should "have identity 0" in {
    SumFrobeniusLong.unit shouldBe 0L
  }

  it should "multiply (add) correctly" in {
    SumFrobeniusLong.multiply(3L, 5L) shouldBe 8L
  }

  it should "comultiply correctly" in {
    SumFrobeniusLong.comultiply(10L) shouldBe (10L, 0L)
  }

  it should "be commutative" in {
    forAll { (a: Long, b: Long) =>
      whenever(
        Math.addExact(a, b) == a + b
      ) {
        SumFrobeniusLong.isCommutative(a, b) shouldBe true
      }
    }
  }

  it should "satisfy the Frobenius law" in {
    SumFrobeniusLong.frobeniusLawHolds(5L) shouldBe true
    SumFrobeniusLong.frobeniusLawHolds(0L) shouldBe true
  }

  "CountFrobenius" should "have identity 0" in {
    CountFrobenius.unit shouldBe 0L
  }

  it should "multiply (add) correctly" in {
    CountFrobenius.multiply(10L, 20L) shouldBe 30L
  }

  it should "be commutative" in {
    CountFrobenius.isCommutative(3L, 7L) shouldBe true
  }
}
