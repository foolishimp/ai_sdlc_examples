package com.cdme.compiler

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import com.cdme.model._

// Validates: REQ-LDM-03, REQ-AI-01, REQ-TRV-02, REQ-TYP-06, REQ-LDM-05
class TopologicalCompilerSpec extends AnyFlatSpec with Matchers {

  val compiler = new TopologicalCompiler()

  val tradeEntity = Entity("Trade", Grain.Atomic, Map(
    "tradeId" -> SemanticType.StringType,
    "amount" -> SemanticType.DoubleType
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
    "trade_to_position", "Trade", "Position",
    Cardinality.NToOne, MorphismType.Structural,
    SemanticType.StringType, SemanticType.StringType
  )
  val positionToRisk = Morphism(
    "position_to_risk", "Position", "Risk",
    Cardinality.OneToOne, MorphismType.Structural,
    SemanticType.StringType, SemanticType.StringType
  )
  val restrictedMorphism = Morphism(
    "restricted_access", "Trade", "Position",
    Cardinality.NToOne, MorphismType.Structural,
    SemanticType.StringType, SemanticType.StringType,
    MorphismMetadata(rbacRoles = Set("admin"))
  )

  val category = Category(
    "FinanceModel",
    Map("Trade" -> tradeEntity, "Position" -> positionEntity, "Risk" -> riskEntity),
    Map(
      "trade_to_position" -> tradeToPosition,
      "position_to_risk" -> positionToRisk,
      "restricted_access" -> restrictedMorphism
    )
  )

  val adminPrincipal = Principal("admin-user", Set("admin"))
  val readOnlyPrincipal = Principal("reader", Set("reader"))

  // ── REQ-AI-01: Hallucination Prevention ──

  "TopologicalCompiler" should "reject hallucinated morphisms (AC-CDME-005)" in {
    val result = compiler.validatePath("Trade.nonexistent_relationship", category, adminPrincipal)
    result.isLeft shouldBe true
    result.left.get.exists(_.isInstanceOf[CompilationError.HallucinatedMorphism]) shouldBe true
  }

  it should "reject hallucinated entities" in {
    val result = compiler.validatePath("FakeEntity.something", category, adminPrincipal)
    result.isLeft shouldBe true
    result.left.get.exists(_.isInstanceOf[CompilationError.HallucinatedEntity]) shouldBe true
  }

  // ── REQ-LDM-03: Composition Validity ──

  it should "validate valid paths (AC-CDME-001)" in {
    val result = compiler.validatePath("Trade.trade_to_position", category, adminPrincipal)
    result.isRight shouldBe true
  }

  it should "validate multi-step paths" in {
    val result = compiler.validatePath("Position.position_to_risk", category, adminPrincipal)
    result.isRight shouldBe true
  }

  // ── REQ-LDM-05: Access Control ──

  it should "deny access to restricted morphisms for unauthorized users" in {
    val result = compiler.validatePath("Trade.restricted_access", category, readOnlyPrincipal)
    result.isLeft shouldBe true
    result.left.get.exists(_.isInstanceOf[CompilationError.AccessDenied]) shouldBe true
  }

  it should "allow access to restricted morphisms for authorized users" in {
    val result = compiler.validatePath("Trade.restricted_access", category, adminPrincipal)
    result.isRight shouldBe true
  }

  // ── REQ-INT-06: Versioned Lookups ──

  it should "reject mappings with unversioned lookups" in {
    val mapping = MappingDefinition(
      "test_mapping", "1.0", "FinanceModel", "Position",
      fieldMappings = Nil,
      lookups = Map("ref" -> LookupBinding("ref_table", "", LookupBindingType.DataBacked))
    )
    val result = compiler.compile(mapping, category, adminPrincipal)
    result.isLeft shouldBe true
    result.left.get.exists(_.isInstanceOf[CompilationError.MissingLookupVersion]) shouldBe true
  }

  // ── REQ-TRV-06: Cost Budget ──

  it should "reject mappings exceeding cost budget" in {
    val mapping = MappingDefinition(
      "test_mapping", "1.0", "FinanceModel", "Position",
      fieldMappings = List(FieldMapping("positionId", MappingExpression.Path("Trade.trade_to_position"), SemanticType.StringType)),
      costBudget = Some(CostBudget(maxOutputRows = 1, maxJoinDepth = 1, maxIntermediateSize = 1))
    )
    val result = compiler.compile(mapping, category, adminPrincipal)
    result.isLeft shouldBe true
    result.left.get.exists(_.isInstanceOf[CompilationError.BudgetExceeded]) shouldBe true
  }
}
