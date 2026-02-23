// Validates: REQ-LDM-03, REQ-LDM-05
// Tests for path compilation and morphism chain verification.
package com.cdme.compiler

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import com.cdme.model._
import com.cdme.model.grain.{Grain, GrainHierarchy}
import com.cdme.model.types.CdmeType
import com.cdme.model.error._

class PathCompilerSpec extends AnyFlatSpec with Matchers {

  val entityA: Entity = Entity("A", "EntityA", Map("x" -> CdmeType.IntType), Grain.Atomic, "id_A", AccessControl.open)
  val entityB: Entity = Entity("B", "EntityB", Map("y" -> CdmeType.IntType), Grain.Atomic, "id_B", AccessControl.open)
  val entityC: Entity = Entity("C", "EntityC", Map("z" -> CdmeType.IntType), Grain.Atomic, "id_C", AccessControl.open)

  val mAB: Morphism = Morphism("mAB", "AtoB", "A", "B", Cardinality.OneToOne, MorphismKind.Structural, None, AccessControl.open)
  val mBC: Morphism = Morphism("mBC", "BtoC", "B", "C", Cardinality.OneToOne, MorphismKind.Structural, None, AccessControl.open)
  val mRestricted: Morphism = Morphism("mR", "Restricted", "A", "B", Cardinality.OneToOne, MorphismKind.Structural, None,
    AccessControl(allowedRoles = Set("admin"), deniedRoles = Set.empty))

  val category: Category = Category(
    name = "TestPath",
    entities = Map("A" -> entityA, "B" -> entityB, "C" -> entityC),
    morphisms = Map("mAB" -> mAB, "mBC" -> mBC, "mR" -> mRestricted,
      "id_A" -> Morphism("id_A", "Id(A)", "A", "A", Cardinality.OneToOne, MorphismKind.Structural, None, AccessControl.open),
      "id_B" -> Morphism("id_B", "Id(B)", "B", "B", Cardinality.OneToOne, MorphismKind.Structural, None, AccessControl.open),
      "id_C" -> Morphism("id_C", "Id(C)", "C", "C", Cardinality.OneToOne, MorphismKind.Structural, None, AccessControl.open)
    ),
    grainHierarchy = GrainHierarchy.default
  )

  val systemPrincipal: Principal = Principal.system
  val userPrincipal: Principal = Principal("user1", Set("user"))

  "verifyMorphismChain" should "accept valid chain A->B->C" in {
    val result = PathCompiler.verifyMorphismChain(List("mAB", "mBC"), category)
    result.isRight shouldBe true
    result.toOption.get should have size 2
  }

  it should "reject chain with codomain/domain mismatch" in {
    // mBC: B->C, mAB: A->B — reversed order means codomain(mBC)=C != domain(mAB)=A
    val result = PathCompiler.verifyMorphismChain(List("mBC", "mAB"), category)
    result.isLeft shouldBe true
    result.left.getOrElse(null) shouldBe a[CompositionError]
  }

  it should "reject nonexistent morphism" in {
    val result = PathCompiler.verifyMorphismChain(List("nonexistent"), category)
    result.isLeft shouldBe true
  }

  it should "accept single morphism chain" in {
    val result = PathCompiler.verifyMorphismChain(List("mAB"), category)
    result.isRight shouldBe true
    result.toOption.get should have size 1
  }

  it should "accept empty chain" in {
    val result = PathCompiler.verifyMorphismChain(Nil, category)
    result.isRight shouldBe true
    result.toOption.get shouldBe empty
  }

  "verifyPath" should "reject empty path" in {
    val result = PathCompiler.verifyPath(DotPath(Nil), category, systemPrincipal)
    result.isLeft shouldBe true
  }

  it should "reject path with nonexistent morphism" in {
    val result = PathCompiler.verifyPath(DotPath.parse("phantom"), category, systemPrincipal)
    result.isLeft shouldBe true
  }

  "DotPath" should "parse dot-separated strings" in {
    val path = DotPath.parse("Entity.Relationship.Attribute")
    path.segments shouldBe List("Entity", "Relationship", "Attribute")
  }

  it should "render back to string" in {
    DotPath(List("a", "b", "c")).asString shouldBe "a.b.c"
  }
}
