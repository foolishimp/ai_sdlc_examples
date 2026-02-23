package com.cdme.model

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

// Validates: REQ-TYP-01, REQ-TYP-02, REQ-TYP-06, REQ-TYP-07
class SemanticTypeSpec extends AnyFlatSpec with Matchers {

  "SemanticType.isCompatible" should "return true for identical types" in {
    SemanticType.isCompatible(SemanticType.IntType, SemanticType.IntType) shouldBe true
    SemanticType.isCompatible(SemanticType.StringType, SemanticType.StringType) shouldBe true
    SemanticType.isCompatible(SemanticType.BooleanType, SemanticType.BooleanType) shouldBe true
  }

  it should "return false for different primitive types (no implicit cast — REQ-TYP-05)" in {
    SemanticType.isCompatible(SemanticType.IntType, SemanticType.StringType) shouldBe false
    SemanticType.isCompatible(SemanticType.FloatType, SemanticType.IntType) shouldBe false
    SemanticType.isCompatible(SemanticType.DateType, SemanticType.TimestampType) shouldBe false
  }

  it should "allow T to be compatible with Option[T]" in {
    SemanticType.isCompatible(SemanticType.IntType, SemanticType.OptionType(SemanticType.IntType)) shouldBe true
  }

  it should "handle nested option types" in {
    SemanticType.isCompatible(
      SemanticType.OptionType(SemanticType.IntType),
      SemanticType.OptionType(SemanticType.IntType)
    ) shouldBe true
  }

  it should "check product type field compatibility" in {
    val product1 = SemanticType.ProductType(Map("a" -> SemanticType.IntType, "b" -> SemanticType.StringType))
    val product2 = SemanticType.ProductType(Map("a" -> SemanticType.IntType))
    SemanticType.isCompatible(product1, product2) shouldBe true  // product1 has all fields of product2
  }

  it should "unwrap domain types for compatibility (REQ-TYP-07)" in {
    val money = SemanticType.DomainType("Money", SemanticType.DoubleType)
    SemanticType.isCompatible(money, SemanticType.DoubleType) shouldBe true
    SemanticType.isCompatible(SemanticType.DoubleType, money) shouldBe true
  }

  it should "reject incompatible domain types" in {
    val money = SemanticType.DomainType("Money", SemanticType.DoubleType)
    SemanticType.isCompatible(money, SemanticType.StringType) shouldBe false
  }

  it should "handle list type compatibility" in {
    SemanticType.isCompatible(
      SemanticType.ListType(SemanticType.IntType),
      SemanticType.ListType(SemanticType.IntType)
    ) shouldBe true
    SemanticType.isCompatible(
      SemanticType.ListType(SemanticType.IntType),
      SemanticType.ListType(SemanticType.StringType)
    ) shouldBe false
  }

  "RefinementType" should "be constructable with base type and predicate" in {
    val posInt = SemanticType.RefinementType(SemanticType.IntType, "positive")
    posInt.base shouldBe SemanticType.IntType
    posInt.predicateName shouldBe "positive"
  }
}
