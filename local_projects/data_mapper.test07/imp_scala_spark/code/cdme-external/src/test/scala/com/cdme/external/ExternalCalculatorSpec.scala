// Validates: REQ-INT-08
// Tests for external calculator registry and invocation.
package com.cdme.external

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import com.cdme.model.types.CdmeType

class ExternalCalculatorSpec extends AnyFlatSpec with Matchers {

  val doubleCalc: ExternalCalculator = ExternalCalculator(
    id = "double",
    name = "Double Calculator",
    version = "1.0.0",
    domainType = CdmeType.IntType,
    codomainType = CdmeType.IntType,
    determinismAssertion = true,
    metadata = Map("author" -> "test"),
    function = Some((x: Any) => x.asInstanceOf[Int] * 2)
  )

  val noFuncCalc: ExternalCalculator = ExternalCalculator(
    id = "nofunc",
    name = "No Function Calculator",
    version = "1.0.0",
    domainType = CdmeType.StringType,
    codomainType = CdmeType.StringType,
    determinismAssertion = true,
    metadata = Map.empty,
    function = None
  )

  "ExternalCalculatorRegistry" should "start empty" in {
    ExternalCalculatorRegistry.empty.registeredIds shouldBe empty
  }

  it should "register a calculator" in {
    val result = ExternalCalculatorRegistry.empty.register(doubleCalc)
    result.isRight shouldBe true
    result.toOption.get.registeredIds should contain("double")
  }

  it should "reject duplicate registration" in {
    val registry = ExternalCalculatorRegistry.empty.register(doubleCalc).toOption.get
    val result = registry.register(doubleCalc)
    result.isLeft shouldBe true
  }

  it should "look up registered calculator" in {
    val registry = ExternalCalculatorRegistry.empty.register(doubleCalc).toOption.get
    registry.lookup("double").isRight shouldBe true
  }

  it should "return error for unknown calculator" in {
    ExternalCalculatorRegistry.empty.lookup("unknown").isLeft shouldBe true
  }

  it should "invoke a registered calculator" in {
    val registry = ExternalCalculatorRegistry.empty.register(doubleCalc).toOption.get
    val result = registry.invoke("double", 5)
    result shouldBe Right(10)
  }

  it should "handle calculator without function" in {
    val registry = ExternalCalculatorRegistry.empty.register(noFuncCalc).toOption.get
    registry.invoke("nofunc", "test").isLeft shouldBe true
  }

  it should "handle calculator invocation failure" in {
    val failCalc = doubleCalc.copy(
      id = "fail",
      function = Some((_: Any) => throw new RuntimeException("boom"))
    )
    val registry = ExternalCalculatorRegistry.empty.register(failCalc).toOption.get
    registry.invoke("fail", 5).isLeft shouldBe true
  }

  "ExternalMorphismAdapter" should "create a morphism from calculator" in {
    val morphism = ExternalMorphismAdapter.asMorphism(doubleCalc, "input", "output")
    morphism.id shouldBe "ext_double"
    morphism.domain shouldBe "input"
    morphism.codomain shouldBe "output"
    morphism.morphismKind shouldBe com.cdme.model.MorphismKind.External
  }

  it should "create adjoint spec when backward is available" in {
    ExternalMorphismAdapter.asAdjointSpec(doubleCalc, hasBackward = true) shouldBe defined
    ExternalMorphismAdapter.asAdjointSpec(doubleCalc, hasBackward = false) shouldBe None
  }

  "DeterminismContract" should "carry assertion metadata" in {
    val contract = DeterminismContract(
      calculatorId = "double",
      isDeterministic = true,
      assertedBy = "test-user",
      assertedAt = java.time.Instant.now()
    )
    contract.isDeterministic shouldBe true
    contract.assertedBy shouldBe "test-user"
  }
}
