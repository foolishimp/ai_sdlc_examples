package com.cdme.compiler

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import com.cdme.model.SemanticType

// Validates: REQ-TYP-05, REQ-TYP-06
class TypeUnifierSpec extends AnyFlatSpec with Matchers {

  "TypeUnifier.unify" should "succeed for identical types" in {
    TypeUnifier.unify(SemanticType.IntType, SemanticType.IntType) shouldBe Right(SemanticType.IntType)
  }

  it should "succeed for compatible types" in {
    TypeUnifier.unify(
      SemanticType.IntType,
      SemanticType.OptionType(SemanticType.IntType)
    ) shouldBe Right(SemanticType.OptionType(SemanticType.IntType))
  }

  it should "reject incompatible types (no implicit cast — REQ-TYP-05)" in {
    val result = TypeUnifier.unify(SemanticType.IntType, SemanticType.StringType)
    result.isLeft shouldBe true
    result.left.get shouldBe a[CompilationError.TypeMismatch]
  }

  "TypeUnifier.requireExplicitCast" should "succeed for same types" in {
    TypeUnifier.requireExplicitCast(SemanticType.IntType, SemanticType.IntType, "test") shouldBe Right(())
  }

  it should "reject implicit casts (REQ-TYP-05)" in {
    val result = TypeUnifier.requireExplicitCast(SemanticType.IntType, SemanticType.StringType, "test")
    result.isLeft shouldBe true
    result.left.get shouldBe a[CompilationError.ImplicitCast]
  }
}
