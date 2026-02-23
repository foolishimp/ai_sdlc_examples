// Validates: REQ-TYP-01, REQ-TYP-02, REQ-TYP-07
// Tests for the CDME type system ADT.
package com.cdme.model

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import com.cdme.model.types.CdmeType
import com.cdme.model.types.CdmeType._

class CdmeTypeSpec extends AnyFlatSpec with Matchers {

  "CdmeType primitives" should "all be distinct" in {
    val primitives: Set[CdmeType] = Set(IntType, FloatType, StringType, BooleanType, DateType, TimestampType)
    primitives should have size 6
  }

  they should "be identified as primitive" in {
    CdmeType.isPrimitive(IntType) shouldBe true
    CdmeType.isPrimitive(FloatType) shouldBe true
    CdmeType.isPrimitive(StringType) shouldBe true
    CdmeType.isPrimitive(BooleanType) shouldBe true
    CdmeType.isPrimitive(DateType) shouldBe true
    CdmeType.isPrimitive(TimestampType) shouldBe true
  }

  "DecimalType" should "carry precision and scale" in {
    val d = DecimalType(18, 2)
    d.precision shouldBe 18
    d.scale shouldBe 2
    CdmeType.isPrimitive(d) shouldBe true
  }

  "OptionType" should "wrap an inner type" in {
    val opt = OptionType(IntType)
    opt.inner shouldBe IntType
    CdmeType.isPrimitive(opt) shouldBe false
  }

  "ListType" should "wrap an element type" in {
    val list = ListType(StringType)
    list.element shouldBe StringType
    CdmeType.isPrimitive(list) shouldBe false
  }

  "ProductType" should "carry named fields" in {
    val prod = ProductType(Map("x" -> IntType, "y" -> FloatType))
    prod.fields should have size 2
    prod.fields("x") shouldBe IntType
  }

  "SumType" should "carry named variants" in {
    val sum = SumType(Map("left" -> IntType, "right" -> StringType))
    sum.variants should have size 2
  }

  "RefinementType" should "carry base type and predicate" in {
    val r = RefinementType(IntType, "Positive", "x > 0")
    r.base shouldBe IntType
    r.predicateName shouldBe "Positive"
    r.predicateExpr shouldBe "x > 0"
  }

  "SemanticType" should "carry base type and semantic label" in {
    val s = SemanticType(FloatType, "Money")
    s.base shouldBe FloatType
    s.semanticLabel shouldBe "Money"
  }

  "Nested types" should "compose correctly" in {
    val nested = OptionType(ListType(SemanticType(IntType, "Count")))
    nested match {
      case OptionType(ListType(SemanticType(IntType, "Count"))) => succeed
      case _ => fail("Pattern match failed for nested type")
    }
  }

  "ProductType" should "support nested products" in {
    val inner = ProductType(Map("a" -> IntType))
    val outer = ProductType(Map("nested" -> inner, "b" -> StringType))
    outer.fields("nested") shouldBe inner
  }

  "Type equality" should "be structural" in {
    val t1 = OptionType(IntType)
    val t2 = OptionType(IntType)
    t1 shouldBe t2
    t1.hashCode shouldBe t2.hashCode
  }

  it should "distinguish different types" in {
    OptionType(IntType) should not be OptionType(FloatType)
    SemanticType(IntType, "Money") should not be SemanticType(IntType, "Count")
  }
}
