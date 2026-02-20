// Validates: REQ-F-API-001, REQ-F-LDM-001, REQ-F-LDM-002, REQ-F-LDM-004, REQ-F-LDM-005
// Tests for fluent API, validation
package cdme.api

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import cdme.model.category.{EntityId, AttributeId, MorphismId}
import cdme.model.grain.Grain
import cdme.model.types.{PrimitiveType, SemanticType, SemanticTag}
import cdme.model.access.AccessRule
import cdme.model.error.ValidationError

class LdmBuilderSpec extends AnyFunSuite with Matchers {

  // --- REQ-F-API-001: Fluent LDM Definition ---

  test("REQ-F-API-001: build entity with grain and attributes via fluent API") {
    val result = LdmBuilder("Trade")
      .grain(Grain.Atomic)
      .attribute("id", PrimitiveType.IntType)
      .attribute("amount", SemanticType(PrimitiveType.DecimalType, SemanticTag("Money")))
      .attribute("tradeDate", PrimitiveType.DateType)
      .build()

    result.isRight shouldBe true
    val entity = result.toOption.get
    entity.name shouldBe "Trade"
    entity.grain shouldBe Grain.Atomic
    entity.attributeCount shouldBe 3
  }

  test("REQ-F-API-001: entity factory helper") {
    val builder = LdmBuilder.entity("Trade")
    builder.entityName shouldBe "Trade"
  }

  test("REQ-F-LDM-004: entity without grain is rejected") {
    val result = LdmBuilder("Trade")
      .attribute("id", PrimitiveType.IntType)
      .build()

    result.isLeft shouldBe true
    result.left.toOption.get shouldBe a[ValidationError.General]
    result.left.toOption.get.message should include("grain")
  }

  test("REQ-F-LDM-005: entity with typed attributes") {
    val result = LdmBuilder("Counterparty")
      .grain(Grain.Atomic)
      .attribute("id", PrimitiveType.IntType)
      .attribute("name", PrimitiveType.StringType)
      .attribute("active", PrimitiveType.BooleanType)
      .build()

    result.isRight shouldBe true
    val entity = result.toOption.get
    entity.attributeType(AttributeId("id")) shouldBe Some(PrimitiveType.IntType)
    entity.attributeType(AttributeId("name")) shouldBe Some(PrimitiveType.StringType)
    entity.attributeType(AttributeId("active")) shouldBe Some(PrimitiveType.BooleanType)
  }

  test("REQ-F-API-001: entity ID is derived from name (lowercase, underscored)") {
    val result = LdmBuilder("Trade Counterparty")
      .grain(Grain.Atomic)
      .build()

    result.isRight shouldBe true
    result.toOption.get.id shouldBe EntityId("trade_counterparty")
  }

  test("entity builder supports access rules") {
    val result = LdmBuilder("Secret")
      .grain(Grain.Atomic)
      .attribute("data", PrimitiveType.StringType)
      .withAccessRule(AccessRule(MorphismId("m1"), Set("admin")))
      .build()

    result.isRight shouldBe true
    result.toOption.get.accessRules should have size 1
  }

  // --- CategoryBuilder ---

  test("CategoryBuilder: build empty category") {
    val builder = CategoryBuilder("TestCategory")
    val result = builder.build()
    result.isRight shouldBe true
    result.toOption.get.name shouldBe "TestCategory"
    result.toOption.get.entityCount shouldBe 0
  }

  test("CategoryBuilder: add entities and morphisms") {
    val trade = LdmBuilder("Trade")
      .grain(Grain.Atomic)
      .attribute("id", PrimitiveType.IntType)
      .build().toOption.get

    val cp = LdmBuilder("Counterparty")
      .grain(Grain.Atomic)
      .attribute("id", PrimitiveType.IntType)
      .build().toOption.get

    val result = for {
      b1 <- CategoryBuilder("TradingBook").addEntity(trade)
      b2 <- b1.addEntity(cp)
    } yield b2.build()

    result.isRight shouldBe true
    val catResult = result.toOption.get
    catResult.isRight shouldBe true
    catResult.toOption.get.entityCount shouldBe 2
  }

  test("CategoryBuilder: rejects duplicate entity") {
    val trade = LdmBuilder("Trade")
      .grain(Grain.Atomic)
      .build().toOption.get

    val result = for {
      b1 <- CategoryBuilder("test").addEntity(trade)
      b2 <- b1.addEntity(trade)
    } yield b2

    result.isLeft shouldBe true
  }

  test("CategoryBuilder: validates category on build") {
    val builder = CategoryBuilder("test")
    val result = builder.build()
    // Empty category should be valid
    result.isRight shouldBe true
  }
}
