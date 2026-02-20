// Validates: REQ-F-API-001, REQ-F-ADJ-001, REQ-F-LDM-002
// Tests for fluent morphism builder, adjoint construction
package cdme.api

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import cdme.model.category.{MorphismId, EntityId, CardinalityType, ContainmentType}
import cdme.model.adjoint.{AdjointMorphism, BackwardResult}
import cdme.model.access.AccessRule
import cdme.model.error.ValidationError

class MorphismBuilderSpec extends AnyFunSuite with Matchers:

  // --- REQ-F-API-001: Fluent Morphism Definition ---

  test("REQ-F-API-001: build morphism with all required fields") {
    val result = MorphismBuilder[String, String]("tradeToCP")
      .from("Trade")
      .to("Counterparty")
      .cardinality(CardinalityType.ManyToOne)
      .forward(s => Right(s.toUpperCase))
      .backward(s => BackwardResult(Vector(s.toLowerCase)))
      .build()

    result.isRight shouldBe true
    val m = result.toOption.get
    m.name shouldBe "tradeToCP"
    m.domain shouldBe EntityId("trade")
    m.codomain shouldBe EntityId("counterparty")
    m.cardinality shouldBe CardinalityType.ManyToOne
  }

  test("REQ-F-API-001: morphism builder rejects missing domain") {
    val result = MorphismBuilder[String, String]("test")
      .to("Target")
      .cardinality(CardinalityType.OneToOne)
      .forward(s => Right(s))
      .backward(s => BackwardResult(Vector(s)))
      .build()

    result.isLeft shouldBe true
    result.left.toOption.get.message should include("domain")
  }

  test("REQ-F-API-001: morphism builder rejects missing codomain") {
    val result = MorphismBuilder[String, String]("test")
      .from("Source")
      .cardinality(CardinalityType.OneToOne)
      .forward(s => Right(s))
      .backward(s => BackwardResult(Vector(s)))
      .build()

    result.isLeft shouldBe true
    result.left.toOption.get.message should include("codomain")
  }

  test("REQ-F-LDM-002: morphism builder rejects missing cardinality") {
    val result = MorphismBuilder[String, String]("test")
      .from("Source")
      .to("Target")
      .forward(s => Right(s))
      .backward(s => BackwardResult(Vector(s)))
      .build()

    result.isLeft shouldBe true
    result.left.toOption.get.message should include("cardinality")
  }

  test("REQ-F-ADJ-001: morphism builder rejects missing forward function") {
    val result = MorphismBuilder[String, String]("test")
      .from("Source")
      .to("Target")
      .cardinality(CardinalityType.OneToOne)
      .backward(s => BackwardResult(Vector(s)))
      .build()

    result.isLeft shouldBe true
    result.left.toOption.get.message should include("forward")
  }

  test("REQ-F-ADJ-001: morphism builder rejects missing backward function") {
    val result = MorphismBuilder[String, String]("test")
      .from("Source")
      .to("Target")
      .cardinality(CardinalityType.OneToOne)
      .forward(s => Right(s))
      .build()

    result.isLeft shouldBe true
    result.left.toOption.get.message should include("backward")
  }

  // --- Cardinality to Containment mapping ---

  test("OneToOne cardinality maps to Isomorphic containment") {
    val result = MorphismBuilder[String, String]("test")
      .from("A").to("B")
      .cardinality(CardinalityType.OneToOne)
      .forward(s => Right(s))
      .backward(s => BackwardResult(Vector(s)))
      .build()

    result.toOption.get.containment shouldBe ContainmentType.Isomorphic
  }

  test("ManyToOne cardinality maps to Preimage containment") {
    val result = MorphismBuilder[String, String]("test")
      .from("A").to("B")
      .cardinality(CardinalityType.ManyToOne)
      .forward(s => Right(s))
      .backward(s => BackwardResult(Vector(s)))
      .build()

    result.toOption.get.containment shouldBe ContainmentType.Preimage
  }

  test("OneToMany cardinality maps to Expansion containment") {
    val result = MorphismBuilder[String, String]("test")
      .from("A").to("B")
      .cardinality(CardinalityType.OneToMany)
      .forward(s => Right(s))
      .backward(s => BackwardResult(Vector(s)))
      .build()

    result.toOption.get.containment shouldBe ContainmentType.Expansion
  }

  // --- Built morphism is functional ---

  test("built morphism executes forward correctly") {
    val m = MorphismBuilder[Int, Int]("double")
      .from("A").to("B")
      .cardinality(CardinalityType.OneToOne)
      .forward(x => Right(x * 2))
      .backward(x => BackwardResult(Vector(x / 2)))
      .build()
      .toOption.get

    m.forward(5) shouldBe Right(10)
  }

  test("built morphism executes backward correctly") {
    val m = MorphismBuilder[Int, Int]("double")
      .from("A").to("B")
      .cardinality(CardinalityType.OneToOne)
      .forward(x => Right(x * 2))
      .backward(x => BackwardResult(Vector(x / 2)))
      .build()
      .toOption.get

    m.backward(10).records should contain(5)
  }

  // --- Access rules ---

  test("morphism builder supports access rules") {
    val result = MorphismBuilder[String, String]("restricted")
      .from("A").to("B")
      .cardinality(CardinalityType.OneToOne)
      .forward(s => Right(s))
      .backward(s => BackwardResult(Vector(s)))
      .withAccessRule(AccessRule(MorphismId("restricted"), Set("admin")))
      .build()

    result.isRight shouldBe true
    result.toOption.get.accessRules should have size 1
  }

  // --- Entity ID derivation ---

  test("domain and codomain entity IDs are derived consistently") {
    val m = MorphismBuilder[String, String]("test")
      .from("Trade Entity")
      .to("Counterparty Entity")
      .cardinality(CardinalityType.OneToOne)
      .forward(s => Right(s))
      .backward(s => BackwardResult(Vector(s)))
      .build()
      .toOption.get

    m.domain shouldBe EntityId("trade_entity")
    m.codomain shouldBe EntityId("counterparty_entity")
  }
