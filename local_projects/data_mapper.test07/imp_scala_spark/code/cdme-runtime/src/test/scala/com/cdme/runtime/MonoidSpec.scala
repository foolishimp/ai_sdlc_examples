// Validates: REQ-LDM-04, REQ-LDM-04-A
// Tests for monoid laws: associativity, identity, empty fold.
package com.cdme.runtime

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import com.cdme.runtime.monoid.CdmeMonoid

class MonoidSpec extends AnyFlatSpec with Matchers with ScalaCheckPropertyChecks {

  "SumLong monoid" should "be associative" in {
    forAll { (a: Long, b: Long, c: Long) =>
      whenever(
        Math.addExact(a, b) == a + b &&
        Math.addExact(b, c) == b + c &&
        Math.addExact(a + b, c) == a + b + c
      ) {
        CdmeMonoid.sumLong.combine(CdmeMonoid.sumLong.combine(a, b), c) shouldBe
          CdmeMonoid.sumLong.combine(a, CdmeMonoid.sumLong.combine(b, c))
      }
    }
  }

  it should "have identity 0" in {
    forAll { (a: Long) =>
      CdmeMonoid.sumLong.combine(a, CdmeMonoid.sumLong.identity) shouldBe a
      CdmeMonoid.sumLong.combine(CdmeMonoid.sumLong.identity, a) shouldBe a
    }
  }

  "ProductLong monoid" should "have identity 1" in {
    forAll { (a: Long) =>
      CdmeMonoid.productLong.combine(a, CdmeMonoid.productLong.identity) shouldBe a
      CdmeMonoid.productLong.combine(CdmeMonoid.productLong.identity, a) shouldBe a
    }
  }

  "MinLong monoid" should "return the minimum" in {
    CdmeMonoid.minLong.combine(3L, 5L) shouldBe 3L
    CdmeMonoid.minLong.combine(10L, 2L) shouldBe 2L
  }

  it should "have identity Long.MaxValue" in {
    forAll { (a: Long) =>
      CdmeMonoid.minLong.combine(a, CdmeMonoid.minLong.identity) shouldBe a
    }
  }

  "MaxLong monoid" should "return the maximum" in {
    CdmeMonoid.maxLong.combine(3L, 5L) shouldBe 5L
    CdmeMonoid.maxLong.combine(10L, 2L) shouldBe 10L
  }

  it should "have identity Long.MinValue" in {
    forAll { (a: Long) =>
      CdmeMonoid.maxLong.combine(a, CdmeMonoid.maxLong.identity) shouldBe a
    }
  }

  "ConcatString monoid" should "be associative" in {
    forAll { (a: String, b: String, c: String) =>
      CdmeMonoid.concatString.combine(CdmeMonoid.concatString.combine(a, b), c) shouldBe
        CdmeMonoid.concatString.combine(a, CdmeMonoid.concatString.combine(b, c))
    }
  }

  it should "have empty string as identity" in {
    forAll { (a: String) =>
      CdmeMonoid.concatString.combine(a, CdmeMonoid.concatString.identity) shouldBe a
      CdmeMonoid.concatString.combine(CdmeMonoid.concatString.identity, a) shouldBe a
    }
  }

  "And monoid" should "have identity true" in {
    CdmeMonoid.andBoolean.identity shouldBe true
    CdmeMonoid.andBoolean.combine(true, true) shouldBe true
    CdmeMonoid.andBoolean.combine(true, false) shouldBe false
  }

  "Or monoid" should "have identity false" in {
    CdmeMonoid.orBoolean.identity shouldBe false
    CdmeMonoid.orBoolean.combine(false, true) shouldBe true
    CdmeMonoid.orBoolean.combine(false, false) shouldBe false
  }

  "List monoid" should "concatenate lists" in {
    val m = CdmeMonoid.listMonoid[Int]
    m.combine(List(1, 2), List(3, 4)) shouldBe List(1, 2, 3, 4)
    m.combine(Nil, List(1)) shouldBe List(1)
    m.combine(List(1), Nil) shouldBe List(1)
  }

  "Set monoid" should "union sets" in {
    val m = CdmeMonoid.setMonoid[Int]
    m.combine(Set(1, 2), Set(2, 3)) shouldBe Set(1, 2, 3)
  }

  "fold on empty collection" should "return identity (REQ-LDM-04-A)" in {
    implicit val m: CdmeMonoid[Long] = CdmeMonoid.sumLong
    CdmeMonoid.fold(Seq.empty[Long]) shouldBe 0L
  }

  "fold on non-empty collection" should "combine all elements" in {
    implicit val m: CdmeMonoid[Long] = CdmeMonoid.sumLong
    CdmeMonoid.fold(Seq(1L, 2L, 3L)) shouldBe 6L
  }
}
