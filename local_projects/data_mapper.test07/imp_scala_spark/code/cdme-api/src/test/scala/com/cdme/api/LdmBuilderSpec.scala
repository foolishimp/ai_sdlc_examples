// Validates: REQ-INT-04, REQ-LDM-01
// Tests for the LDM builder API.
package com.cdme.api

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import com.cdme.model._
import com.cdme.model.grain.Grain
import com.cdme.model.types.CdmeType

class LdmBuilderSpec extends AnyFlatSpec with Matchers {

  "LdmBuilder" should "build a valid category with entities and morphisms" in {
    val result = LdmBuilder("TestLDM")
      .addEntity("trade", "Trade", Map("tradeId" -> CdmeType.StringType), Grain.Atomic)
      .addEntity("position", "Position", Map("posId" -> CdmeType.StringType), Grain.Daily)
      .addMorphism("agg", "Aggregate", "trade", "position", Cardinality.NToOne, MorphismKind.Algebraic)
      .build()

    result.isRight shouldBe true
    val cat = result.toOption.get
    cat.entities should have size 2
    cat.morphisms.size should be >= 3 // 2 identities + 1 aggregation
    cat.name shouldBe "TestLDM"
  }

  it should "create identity morphisms automatically" in {
    val result = LdmBuilder("Test")
      .addEntity("e1", "E1", Map("a" -> CdmeType.IntType), Grain.Atomic)
      .build()

    result.isRight shouldBe true
    val cat = result.toOption.get
    cat.morphism("id_e1") shouldBe defined
    cat.morphism("id_e1").get.domain shouldBe "e1"
    cat.morphism("id_e1").get.codomain shouldBe "e1"
  }

  it should "support chained builder pattern" in {
    val builder = LdmBuilder("Chain")
      .addEntity("a", "A", Map.empty, Grain.Atomic)
      .addEntity("b", "B", Map.empty, Grain.Atomic)
      .addMorphism("ab", "AtoB", "a", "b", Cardinality.OneToOne, MorphismKind.Structural)

    builder.entities should have size 2
    builder.morphisms.size should be >= 3
  }

  it should "accept custom access control" in {
    val result = LdmBuilder("Secure")
      .addEntity("secret", "Secret", Map("data" -> CdmeType.StringType), Grain.Atomic,
        AccessControl(allowedRoles = Set("admin"), deniedRoles = Set.empty))
      .build()

    result.isRight shouldBe true
    val entity = result.toOption.get.entity("secret").get
    entity.accessControl.isAccessible(Set("admin")) shouldBe true
    entity.accessControl.isAccessible(Set("user")) shouldBe false
  }

  it should "reject dangling morphism references" in {
    val builder = LdmBuilder("Bad")
      .addMorphism("bad", "BadMorphism", "nonexistent_source", "nonexistent_target",
        Cardinality.OneToOne, MorphismKind.Structural)

    val result = builder.build()
    result.isLeft shouldBe true
  }

  it should "build empty category" in {
    val result = LdmBuilder("Empty").build()
    result.isRight shouldBe true
    result.toOption.get.entities shouldBe empty
  }

  it should "set custom grain hierarchy" in {
    import com.cdme.model.grain.{GrainHierarchy, Grain}
    val customHierarchy = GrainHierarchy(
      List(Grain.Atomic, Grain.Daily),
      Map((Grain.Atomic, Grain.Daily) -> List(Grain.Atomic, Grain.Daily))
    )
    val result = LdmBuilder("Custom")
      .withGrainHierarchy(customHierarchy)
      .build()

    result.isRight shouldBe true
    result.toOption.get.grainHierarchy.grains should have size 2
  }

  it should "support adding Entity objects directly" in {
    val entity = Entity("e1", "E1", Map("x" -> CdmeType.IntType), Grain.Atomic, "id_e1", AccessControl.open)
    val idMorphism = Morphism("id_e1", "Id(E1)", "e1", "e1", Cardinality.OneToOne, MorphismKind.Structural, None, AccessControl.open)
    val result = LdmBuilder("Direct")
      .withEntity(entity)
      .withMorphism(idMorphism)
      .build()

    result.isRight shouldBe true
  }
}
