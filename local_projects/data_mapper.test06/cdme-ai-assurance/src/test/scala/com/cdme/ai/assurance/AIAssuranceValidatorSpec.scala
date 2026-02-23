package com.cdme.ai.assurance

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import com.cdme.model._
import com.cdme.compiler._

// Validates: REQ-AI-01 (AC-CDME-005), REQ-AI-02
class AIAssuranceValidatorSpec extends AnyFlatSpec with Matchers {

  val category = Category(
    "TestModel",
    Map(
      "Trade" -> Entity("Trade", Grain.Atomic, Map("id" -> SemanticType.StringType)),
      "Position" -> Entity("Position", Grain.Daily, Map("id" -> SemanticType.StringType))
    ),
    Map(
      "trade_to_position" -> Morphism("trade_to_position", "Trade", "Position",
        Cardinality.NToOne, MorphismType.Structural,
        SemanticType.StringType, SemanticType.StringType)
    )
  )

  val compiler = new TopologicalCompiler()
  val validator = new AIAssuranceValidator(compiler)
  val principal = Principal("ai-agent", Set("admin"))

  "AIAssuranceValidator" should "accept valid AI-generated mappings" in {
    val mapping = MappingDefinition(
      "ai_mapping", "1.0", "TestModel", "Position",
      fieldMappings = List(
        FieldMapping("id", MappingExpression.Path("Trade.trade_to_position"), SemanticType.StringType)
      )
    )
    val result = validator.validate(mapping, category, principal, MappingSource.AI)
    result.isRight shouldBe true
    result.toOption.get.validationPassed shouldBe true
  }

  it should "reject AI-generated hallucinated mappings (AC-CDME-005)" in {
    val mapping = MappingDefinition(
      "hallucinated_mapping", "1.0", "TestModel", "Position",
      fieldMappings = List(
        FieldMapping("id", MappingExpression.Path("Trade.nonexistent_relationship"), SemanticType.StringType)
      )
    )
    val result = validator.validate(mapping, category, principal, MappingSource.AI)
    result.isLeft shouldBe true
    result.left.get.exists(_.isInstanceOf[AssuranceViolation.HallucinatedMorphism]) shouldBe true
  }

  it should "produce assurance certificate for valid mappings (REQ-AI-02)" in {
    val mapping = MappingDefinition(
      "certified_mapping", "1.0", "TestModel", "Position",
      fieldMappings = List(
        FieldMapping("id", MappingExpression.Path("Trade.trade_to_position"), SemanticType.StringType)
      )
    )
    val result = validator.validate(mapping, category, principal, MappingSource.AI)
    result.isRight shouldBe true
    val cert = result.toOption.get
    cert.mappingName shouldBe "certified_mapping"
    cert.source shouldBe MappingSource.AI
    cert.validationPassed shouldBe true
  }
}
