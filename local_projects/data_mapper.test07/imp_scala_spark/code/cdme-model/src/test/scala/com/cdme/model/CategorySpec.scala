// Validates: REQ-LDM-01
// Tests for Category: identity laws, entity/morphism CRUD, graph operations.
package com.cdme.model

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import com.cdme.model.grain.{Grain, GrainHierarchy}
import com.cdme.model.types.CdmeType

class CategorySpec extends AnyFlatSpec with Matchers {

  val grainHierarchy: GrainHierarchy = GrainHierarchy.default

  val tradeEntity: Entity = Entity(
    id = "trade",
    name = "Trade",
    attributes = Map("tradeId" -> CdmeType.StringType, "amount" -> CdmeType.FloatType),
    grain = Grain.Atomic,
    identityMorphismId = "id_trade",
    accessControl = AccessControl.open
  )

  val positionEntity: Entity = Entity(
    id = "position",
    name = "Position",
    attributes = Map("positionId" -> CdmeType.StringType, "total" -> CdmeType.FloatType),
    grain = Grain.Daily,
    identityMorphismId = "id_position",
    accessControl = AccessControl.open
  )

  val idTradeMorphism: Morphism = Morphism(
    id = "id_trade",
    name = "Identity(Trade)",
    domain = "trade",
    codomain = "trade",
    cardinality = Cardinality.OneToOne,
    morphismKind = MorphismKind.Structural,
    adjoint = None,
    accessControl = AccessControl.open
  )

  val idPositionMorphism: Morphism = Morphism(
    id = "id_position",
    name = "Identity(Position)",
    domain = "position",
    codomain = "position",
    cardinality = Cardinality.OneToOne,
    morphismKind = MorphismKind.Structural,
    adjoint = None,
    accessControl = AccessControl.open
  )

  val tradeToPosition: Morphism = Morphism(
    id = "trade_to_position",
    name = "TradeToPosition",
    domain = "trade",
    codomain = "position",
    cardinality = Cardinality.NToOne,
    morphismKind = MorphismKind.Algebraic,
    adjoint = None,
    accessControl = AccessControl.open
  )

  val category: Category = Category(
    name = "TestLDM",
    entities = Map("trade" -> tradeEntity, "position" -> positionEntity),
    morphisms = Map(
      "id_trade" -> idTradeMorphism,
      "id_position" -> idPositionMorphism,
      "trade_to_position" -> tradeToPosition
    ),
    grainHierarchy = grainHierarchy
  )

  "Category" should "look up entities by ID" in {
    category.entity("trade") shouldBe Some(tradeEntity)
    category.entity("nonexistent") shouldBe None
  }

  it should "look up morphisms by ID" in {
    category.morphism("trade_to_position") shouldBe Some(tradeToPosition)
    category.morphism("nonexistent") shouldBe None
  }

  it should "find outgoing morphisms for an entity" in {
    val outgoing = category.outgoing("trade")
    outgoing should contain(idTradeMorphism)
    outgoing should contain(tradeToPosition)
    outgoing should have size 2
  }

  it should "find incoming morphisms for an entity" in {
    val incoming = category.incoming("position")
    incoming should contain(tradeToPosition)
    incoming should contain(idPositionMorphism)
  }

  it should "verify identity morphisms exist for all entities" in {
    category.hasIdentityMorphisms shouldBe true
  }

  it should "detect missing identity morphisms" in {
    val brokenCategory = category.copy(
      morphisms = category.morphisms - "id_trade"
    )
    brokenCategory.hasIdentityMorphisms shouldBe false
  }

  it should "support empty categories" in {
    val empty = Category("Empty", Map.empty, Map.empty, grainHierarchy)
    empty.entities shouldBe empty
    empty.morphisms shouldBe empty
    empty.hasIdentityMorphisms shouldBe true // vacuously true
  }

  it should "support multigraph (multiple edges between same entities)" in {
    val secondMorphism = tradeToPosition.copy(
      id = "trade_to_position_v2",
      name = "TradeToPositionV2"
    )
    val multiCategory = category.copy(
      morphisms = category.morphisms + ("trade_to_position_v2" -> secondMorphism)
    )
    multiCategory.outgoing("trade") should have size 3
  }
}
