// Validates: REQ-TYP-06, REQ-TYP-05
// Tests for the type unification algorithm.
package com.cdme.compiler

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import com.cdme.compiler.types.TypeUnifier
import com.cdme.model.types.{CdmeType, SubtypeRegistry}
import com.cdme.model.types.CdmeType._

class TypeUnifierSpec extends AnyFlatSpec with Matchers with ScalaCheckPropertyChecks {

  val emptyRegistry: SubtypeRegistry = SubtypeRegistry.empty

  "TypeUnifier" should "unify identical primitive types" in {
    TypeUnifier.unify(IntType, IntType, emptyRegistry) shouldBe Right(IntType)
    TypeUnifier.unify(StringType, StringType, emptyRegistry) shouldBe Right(StringType)
    TypeUnifier.unify(FloatType, FloatType, emptyRegistry) shouldBe Right(FloatType)
  }

  it should "reject different primitive types (no implicit coercion)" in {
    TypeUnifier.unify(IntType, FloatType, emptyRegistry).isLeft shouldBe true
    TypeUnifier.unify(StringType, IntType, emptyRegistry).isLeft shouldBe true
    TypeUnifier.unify(DateType, TimestampType, emptyRegistry).isLeft shouldBe true
  }

  it should "unify identical OptionTypes" in {
    TypeUnifier.unify(OptionType(IntType), OptionType(IntType), emptyRegistry) shouldBe Right(OptionType(IntType))
  }

  it should "reject incompatible OptionTypes" in {
    TypeUnifier.unify(OptionType(IntType), OptionType(StringType), emptyRegistry).isLeft shouldBe true
  }

  it should "unify identical ListTypes" in {
    TypeUnifier.unify(ListType(StringType), ListType(StringType), emptyRegistry) shouldBe Right(ListType(StringType))
  }

  it should "reject incompatible ListTypes" in {
    TypeUnifier.unify(ListType(IntType), ListType(StringType), emptyRegistry).isLeft shouldBe true
  }

  it should "unify identical ProductTypes" in {
    val prod = ProductType(Map("x" -> IntType, "y" -> FloatType))
    TypeUnifier.unify(prod, prod, emptyRegistry) shouldBe Right(prod)
  }

  it should "reject ProductTypes with missing fields" in {
    val prodA = ProductType(Map("x" -> IntType))
    val prodB = ProductType(Map("x" -> IntType, "y" -> FloatType))
    TypeUnifier.unify(prodA, prodB, emptyRegistry).isLeft shouldBe true
  }

  it should "reject ProductTypes with incompatible field types" in {
    val prodA = ProductType(Map("x" -> IntType))
    val prodB = ProductType(Map("x" -> StringType))
    TypeUnifier.unify(prodA, prodB, emptyRegistry).isLeft shouldBe true
  }

  it should "unify identical SemanticTypes" in {
    val sem = SemanticType(FloatType, "Money")
    TypeUnifier.unify(sem, sem, emptyRegistry) shouldBe Right(sem)
  }

  it should "reject SemanticTypes with different labels" in {
    val semA = SemanticType(FloatType, "Money")
    val semB = SemanticType(FloatType, "Percent")
    TypeUnifier.unify(semA, semB, emptyRegistry).isLeft shouldBe true
  }

  it should "use subtype registry for subtype relationship" in {
    val registry = SubtypeRegistry(Map(FloatType -> Set(IntType)))
    // IntType is declared as subtype of FloatType
    TypeUnifier.unify(IntType, FloatType, registry) shouldBe Right(FloatType)
  }

  it should "not allow unregistered subtype relationships" in {
    TypeUnifier.unify(IntType, FloatType, emptyRegistry).isLeft shouldBe true
  }

  it should "unify nested OptionType(ListType(...))" in {
    val nested = OptionType(ListType(IntType))
    TypeUnifier.unify(nested, nested, emptyRegistry) shouldBe Right(nested)
  }

  "isCompatible" should "return true for compatible types" in {
    TypeUnifier.isCompatible(IntType, IntType, emptyRegistry) shouldBe true
  }

  it should "return false for incompatible types" in {
    TypeUnifier.isCompatible(IntType, StringType, emptyRegistry) shouldBe false
  }

  "Self-unification" should "succeed for all primitive types" in {
    val primitives = List(IntType, FloatType, StringType, BooleanType, DateType, TimestampType)
    primitives.foreach { t =>
      TypeUnifier.unify(t, t, emptyRegistry) shouldBe Right(t)
    }
  }
}
