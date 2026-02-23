// Validates: REQ-ADJ-01, REQ-ADJ-02, REQ-ADJ-07
// Tests for adjoint computation, containment laws, and composition.
package com.cdme.adjoint

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import com.cdme.model.adjoint.AdjointClassification
import com.cdme.model.error.CdmeError

class AdjointComputerSpec extends AnyFlatSpec with Matchers with ScalaCheckPropertyChecks {

  // Isomorphism: f(x) = x * 2, f^-(y) = y / 2
  val doubleAdjoint: Adjoint[Int, Int] = new Adjoint[Int, Int] {
    def forward(a: Int): Either[CdmeError, Int] = Right(a * 2)
    def backward(b: Int): Either[CdmeError, Set[Int]] = Right(Set(b / 2))
    def classification: AdjointClassification = AdjointClassification.Isomorphism
  }

  // Projection: f(x) = x / 10 (integer division), f^-(y) = {y*10..y*10+9}
  val divTenAdjoint: Adjoint[Int, Int] = new Adjoint[Int, Int] {
    def forward(a: Int): Either[CdmeError, Int] = Right(a / 10)
    def backward(b: Int): Either[CdmeError, Set[Int]] = Right((b * 10 to b * 10 + 9).toSet)
    def classification: AdjointClassification = AdjointClassification.Projection
  }

  "Isomorphism adjoint" should "satisfy containment law" in {
    val result = doubleAdjoint.validateContainmentLaw(Set(1, 2, 3, 4, 5))
    result.isRight shouldBe true
    result.toOption.get.isValid shouldBe true
  }

  it should "have exact round-trip for even inputs" in {
    val result = for {
      fwd <- doubleAdjoint.forward(5)
      bwd <- doubleAdjoint.backward(fwd)
    } yield bwd
    result shouldBe Right(Set(5))
  }

  "Projection adjoint" should "satisfy containment law (backward supset)" in {
    val result = divTenAdjoint.validateContainmentLaw(Set(15, 25, 35))
    result.isRight shouldBe true
    result.toOption.get.isValid shouldBe true
  }

  it should "return larger set on backward" in {
    val result = for {
      fwd <- divTenAdjoint.forward(15)
      bwd <- divTenAdjoint.backward(fwd)
    } yield bwd
    result.isRight shouldBe true
    result.toOption.get should contain(15)
    result.toOption.get.size should be > 1
  }

  "AdjointComposer" should "compose two adjoints" in {
    val composed = AdjointComposer.compose(doubleAdjoint, divTenAdjoint)
    // Forward: divTen(double(5)) = divTen(10) = 1
    composed.forward(5) shouldBe Right(1)
  }

  it should "compose backward in contravariant order" in {
    val composed = AdjointComposer.compose(doubleAdjoint, divTenAdjoint)
    // Backward: double^-(divTen^-(1)) = double^-({10..19}) = {5..9}
    val result = composed.backward(1)
    result.isRight shouldBe true
    result.toOption.get should contain(5)
  }

  it should "classify composition correctly" in {
    val composed = AdjointComposer.compose(doubleAdjoint, divTenAdjoint)
    composed.classification shouldBe AdjointClassification.Projection
  }

  "AdjointComposer.classifyComposition" should "preserve Isomorphism . Isomorphism" in {
    AdjointComposer.classifyComposition(
      AdjointClassification.Isomorphism,
      AdjointClassification.Isomorphism
    ) shouldBe AdjointClassification.Isomorphism
  }

  it should "degrade to Lossy for Embedding . Projection" in {
    AdjointComposer.classifyComposition(
      AdjointClassification.Embedding,
      AdjointClassification.Projection
    ) shouldBe AdjointClassification.Lossy
  }

  "GaloisValidator" should "validate lower closure" in {
    val result = GaloisValidator.validateLowerClosure(doubleAdjoint, Set(1, 2, 3))
    result.isRight shouldBe true
    result.toOption.get.lowerClosureHolds shouldBe true
  }

  it should "validate isomorphism" in {
    val result = GaloisValidator.validateIsomorphism(doubleAdjoint, Set(1, 2, 3))
    result shouldBe Right(true)
  }

  it should "reject non-isomorphism for validateIsomorphism" in {
    val result = GaloisValidator.validateIsomorphism(divTenAdjoint, Set(15))
    result.isLeft shouldBe true
  }
}
