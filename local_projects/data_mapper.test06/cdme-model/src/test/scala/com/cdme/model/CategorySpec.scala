package com.cdme.model

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

// Validates: REQ-LDM-01, REQ-LDM-02, REQ-LDM-03
class CategorySpec extends AnyFlatSpec with Matchers {

  val tradeEntity = Entity("Trade", Grain.Atomic, Map(
    "tradeId" -> SemanticType.StringType,
    "amount" -> SemanticType.DoubleType,
    "currency" -> SemanticType.StringType
  ))

  val positionEntity = Entity("Position", Grain.Daily, Map(
    "positionId" -> SemanticType.StringType,
    "totalAmount" -> SemanticType.DoubleType
  ))

  val riskEntity = Entity("Risk", Grain.Daily, Map(
    "riskId" -> SemanticType.StringType,
    "var" -> SemanticType.DoubleType
  ))

  val tradeToPosition = Morphism(
    name = "trade_to_position",
    source = "Trade",
    target = "Position",
    cardinality = Cardinality.NToOne,
    morphismType = MorphismType.Structural,
    domainType = SemanticType.StringType,
    codomainType = SemanticType.StringType
  )

  val positionToRisk = Morphism(
    name = "position_to_risk",
    source = "Position",
    target = "Risk",
    cardinality = Cardinality.OneToOne,
    morphismType = MorphismType.Structural,
    domainType = SemanticType.StringType,
    codomainType = SemanticType.StringType
  )

  val category = Category(
    name = "FinanceModel",
    objects = Map("Trade" -> tradeEntity, "Position" -> positionEntity, "Risk" -> riskEntity),
    morphisms = Map("trade_to_position" -> tradeToPosition, "position_to_risk" -> positionToRisk)
  )

  "Category" should "contain all entities as objects" in {
    category.hasEntity("Trade") shouldBe true
    category.hasEntity("Position") shouldBe true
    category.hasEntity("Risk") shouldBe true
    category.hasEntity("NonExistent") shouldBe false
  }

  it should "contain all morphisms" in {
    category.hasMorphism("trade_to_position") shouldBe true
    category.hasMorphism("position_to_risk") shouldBe true
    category.hasMorphism("nonexistent") shouldBe false
  }

  it should "find morphisms from a source entity" in {
    category.morphismsFrom("Trade") should have size 1
    category.morphismsFrom("Position") should have size 1
    category.morphismsFrom("Risk") should have size 0
  }

  it should "find morphisms to a target entity" in {
    category.morphismsTo("Position") should have size 1
    category.morphismsTo("Risk") should have size 1
    category.morphismsTo("Trade") should have size 0
  }

  it should "find morphisms between two entities" in {
    category.morphismsBetween("Trade", "Position") should have size 1
    category.morphismsBetween("Position", "Risk") should have size 1
    category.morphismsBetween("Trade", "Risk") should have size 0
  }
}
