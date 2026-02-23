// Validates: REQ-TRV-02, REQ-INT-05
// Tests for grain safety checking.
package com.cdme.compiler

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import com.cdme.model._
import com.cdme.model.grain.{Grain, GrainHierarchy}
import com.cdme.model.types.CdmeType

class GrainCheckerSpec extends AnyFlatSpec with Matchers {

  def mkEntity(id: String, grain: Grain): Entity = Entity(
    id = id, name = id, attributes = Map("value" -> CdmeType.IntType),
    grain = grain, identityMorphismId = s"id_$id", accessControl = AccessControl.open
  )

  def mkMorphism(id: String, from: String, to: String, kind: MorphismKind): Morphism = Morphism(
    id = id, name = id, domain = from, codomain = to,
    cardinality = Cardinality.NToOne, morphismKind = kind,
    adjoint = None, accessControl = AccessControl.open
  )

  val category: Category = Category(
    name = "TestGrain",
    entities = Map(
      "atomic_e" -> mkEntity("atomic_e", Grain.Atomic),
      "daily_e"  -> mkEntity("daily_e", Grain.Daily),
      "monthly_e" -> mkEntity("monthly_e", Grain.Monthly)
    ),
    morphisms = Map(
      "agg_atomic_daily" -> mkMorphism("agg_atomic_daily", "atomic_e", "daily_e", MorphismKind.Algebraic),
      "bad_structural"   -> mkMorphism("bad_structural", "atomic_e", "daily_e", MorphismKind.Structural),
      "same_grain"       -> mkMorphism("same_grain", "daily_e", "daily_e", MorphismKind.Structural),
      "refine_down"      -> mkMorphism("refine_down", "monthly_e", "daily_e", MorphismKind.Structural)
    ),
    grainHierarchy = GrainHierarchy.default
  )

  "GrainChecker" should "accept algebraic morphism for coarsening" in {
    GrainChecker.checkGrainSafety(List("agg_atomic_daily"), category) shouldBe Right(())
  }

  it should "reject non-algebraic morphism for coarsening" in {
    val result = GrainChecker.checkGrainSafety(List("bad_structural"), category)
    result.isLeft shouldBe true
    result.left.getOrElse(Nil) should have size 1
  }

  it should "accept same-grain morphism" in {
    GrainChecker.checkGrainSafety(List("same_grain"), category) shouldBe Right(())
  }

  it should "reject refinement (coarser to finer)" in {
    val result = GrainChecker.checkGrainSafety(List("refine_down"), category)
    result.isLeft shouldBe true
  }

  it should "handle empty morphism list" in {
    GrainChecker.checkGrainSafety(Nil, category) shouldBe Right(())
  }

  it should "accumulate multiple violations" in {
    val result = GrainChecker.checkGrainSafety(List("bad_structural", "refine_down"), category)
    result.isLeft shouldBe true
    result.left.getOrElse(Nil).size should be >= 2
  }

  "validateAggregationPath" should "find valid path from Atomic to Monthly" in {
    val result = GrainChecker.validateAggregationPath(
      Grain.Atomic, Grain.Monthly, GrainHierarchy.default
    )
    result.isRight shouldBe true
    result.toOption.get should have size 3
  }

  it should "reject reverse aggregation path" in {
    val result = GrainChecker.validateAggregationPath(
      Grain.Monthly, Grain.Atomic, GrainHierarchy.default
    )
    result.isLeft shouldBe true
  }

  it should "find direct path from Daily to Monthly" in {
    val result = GrainChecker.validateAggregationPath(
      Grain.Daily, Grain.Monthly, GrainHierarchy.default
    )
    result shouldBe Right(List(Grain.Daily, Grain.Monthly))
  }
}
