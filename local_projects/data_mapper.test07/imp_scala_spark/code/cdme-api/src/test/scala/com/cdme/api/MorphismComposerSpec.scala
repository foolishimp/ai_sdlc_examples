// Validates: REQ-LDM-03, REQ-INT-04
// Tests for morphism composition and mapping definition building.
package com.cdme.api

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import com.cdme.compiler.MappingExpression

class MorphismComposerSpec extends AnyFlatSpec with Matchers {

  "MorphismComposer" should "build a valid mapping definition" in {
    val result = MorphismComposer("TestMapping", "trade", "position")
      .via("trade_to_position")
      .mapField("posId", "trade.positionId")
      .mapField("total", "trade.amount")
      .build()

    result.isRight shouldBe true
    val mapping = result.toOption.get
    mapping.name shouldBe "TestMapping"
    mapping.sourceEntity shouldBe "trade"
    mapping.targetEntity shouldBe "position"
    mapping.morphismPath shouldBe List("trade_to_position")
    mapping.fieldMappings should have size 2
  }

  it should "support multiple morphisms in chain" in {
    val result = MorphismComposer("Chain", "a", "c")
      .via("ab")
      .via("bc")
      .mapField("z", "a.x")
      .build()

    result.isRight shouldBe true
    result.toOption.get.morphismPath shouldBe List("ab", "bc")
  }

  it should "support constant fields" in {
    val result = MorphismComposer("Const", "a", "b")
      .constField("version", "1.0")
      .build()

    result.isRight shouldBe true
    val mapping = result.toOption.get
    mapping.fieldMappings.head.expression match {
      case MappingExpression.Constant(v) => v shouldBe "1.0"
      case other => fail(s"Expected Constant, got $other")
    }
  }

  it should "support synthesis fields" in {
    val result = MorphismComposer("Synth", "a", "b")
      .synthField("fullName", "concat(firstName, ' ', lastName)")
      .build()

    result.isRight shouldBe true
  }

  it should "support conditional fields" in {
    val result = MorphismComposer("Cond", "a", "b")
      .conditionalField("status",
        "amount > 0",
        MappingExpression.Constant("positive"),
        MappingExpression.Constant("non-positive")
      )
      .build()

    result.isRight shouldBe true
  }

  it should "support fallback fields" in {
    val result = MorphismComposer("Fallback", "a", "b")
      .fallbackField("value", List(
        MappingExpression.SourcePath(com.cdme.compiler.DotPath.parse("primary")),
        MappingExpression.SourcePath(com.cdme.compiler.DotPath.parse("secondary")),
        MappingExpression.Constant("default")
      ))
      .build()

    result.isRight shouldBe true
  }

  it should "reject empty source entity" in {
    val result = MorphismComposer("Bad", "", "b")
      .build()

    result.isLeft shouldBe true
  }

  it should "reject empty target entity" in {
    val result = MorphismComposer("Bad", "a", "")
      .build()

    result.isLeft shouldBe true
  }
}
