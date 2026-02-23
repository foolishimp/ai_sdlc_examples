package com.cdme.model

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

// Validates: REQ-LDM-04, REQ-LDM-04-A
class MonoidSpec extends AnyFlatSpec with Matchers with ScalaCheckPropertyChecks {

  "Monoid[Int] (sum)" should "satisfy identity law" in {
    forAll { (a: Int) =>
      Monoid.sumInt.combine(Monoid.sumInt.empty, a) shouldBe a
      Monoid.sumInt.combine(a, Monoid.sumInt.empty) shouldBe a
    }
  }

  it should "satisfy associativity law" in {
    forAll { (a: Int, b: Int, c: Int) =>
      Monoid.sumInt.combine(Monoid.sumInt.combine(a, b), c) shouldBe
        Monoid.sumInt.combine(a, Monoid.sumInt.combine(b, c))
    }
  }

  it should "return identity for empty aggregation (REQ-LDM-04-A)" in {
    Monoid.sumInt.empty shouldBe 0
  }

  "Monoid[Double] (sum)" should "satisfy identity law" in {
    Monoid.sumDouble.combine(Monoid.sumDouble.empty, 42.0) shouldBe 42.0
    Monoid.sumDouble.combine(42.0, Monoid.sumDouble.empty) shouldBe 42.0
  }

  "Monoid[Double] (product)" should "satisfy identity law" in {
    Monoid.productDouble.combine(Monoid.productDouble.empty, 42.0) shouldBe 42.0
    Monoid.productDouble.combine(42.0, Monoid.productDouble.empty) shouldBe 42.0
  }

  it should "return 1.0 as identity for empty aggregation" in {
    Monoid.productDouble.empty shouldBe 1.0
  }

  "Monoid[String] (concat)" should "satisfy identity law" in {
    Monoid.concatString.combine(Monoid.concatString.empty, "hello") shouldBe "hello"
    Monoid.concatString.combine("hello", Monoid.concatString.empty) shouldBe "hello"
  }

  it should "satisfy associativity law" in {
    forAll { (a: String, b: String, c: String) =>
      Monoid.concatString.combine(Monoid.concatString.combine(a, b), c) shouldBe
        Monoid.concatString.combine(a, Monoid.concatString.combine(b, c))
    }
  }

  "Monoid registry" should "contain all named monoids" in {
    Monoid.registry should contain key "sum_int"
    Monoid.registry should contain key "sum_double"
    Monoid.registry should contain key "product_double"
    Monoid.registry should contain key "concat_string"
  }

  "Monoid[List]" should "satisfy identity law" in {
    val m = Monoid.listMonoid[Int]
    m.combine(m.empty, List(1, 2, 3)) shouldBe List(1, 2, 3)
    m.combine(List(1, 2, 3), m.empty) shouldBe List(1, 2, 3)
  }

  it should "satisfy associativity law" in {
    val m = Monoid.listMonoid[Int]
    m.combine(m.combine(List(1), List(2)), List(3)) shouldBe
      m.combine(List(1), m.combine(List(2), List(3)))
  }
}
