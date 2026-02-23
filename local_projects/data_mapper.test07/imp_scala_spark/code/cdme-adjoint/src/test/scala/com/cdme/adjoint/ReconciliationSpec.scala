// Validates: REQ-ADJ-08, REQ-ADJ-09
// Tests for reconciliation engine and impact analysis.
package com.cdme.adjoint

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import com.cdme.model.adjoint.AdjointClassification
import com.cdme.model.error.CdmeError

class ReconciliationSpec extends AnyFlatSpec with Matchers {

  // Identity adjoint for testing
  val identityAdjoint: Adjoint[Int, Int] = new Adjoint[Int, Int] {
    def forward(a: Int): Either[CdmeError, Int] = Right(a)
    def backward(b: Int): Either[CdmeError, Set[Int]] = Right(Set(b))
    def classification: AdjointClassification = AdjointClassification.Isomorphism
  }

  // Lossy adjoint: forward loses information
  val lossyAdjoint: Adjoint[Int, Int] = new Adjoint[Int, Int] {
    def forward(a: Int): Either[CdmeError, Int] = Right(a % 10) // loses tens digit
    def backward(b: Int): Either[CdmeError, Set[Int]] = Right(Set(b)) // can't recover
    def classification: AdjointClassification = AdjointClassification.Lossy
  }

  "ReconciliationEngine" should "show exact match for identity adjoint" in {
    val result = ReconciliationEngine.reconcile(identityAdjoint, Set(1, 2, 3))
    result.isRight shouldBe true
    val recon = result.toOption.get
    recon.isExact shouldBe true
    recon.matched shouldBe Set(1, 2, 3)
    recon.missing shouldBe empty
    recon.extra shouldBe empty
  }

  it should "detect missing records for lossy adjoint" in {
    val result = ReconciliationEngine.reconcile(lossyAdjoint, Set(15, 25))
    result.isRight shouldBe true
    val recon = result.toOption.get
    // 15 % 10 = 5, backward(5) = {5} -- 15 is missing
    recon.missing should contain(15)
    recon.missing should contain(25)
  }

  it should "handle empty input" in {
    val result = ReconciliationEngine.reconcile(identityAdjoint, Set.empty[Int])
    result.isRight shouldBe true
    result.toOption.get.isExact shouldBe true
  }

  "ImpactAnalyzer" should "trace contributing sources" in {
    val backward: Int => Either[CdmeError, Set[Int]] = b => Right(Set(b, b + 10))
    val result = ImpactAnalyzer.analyzeImpact(Set(1, 2), backward, List("m1"))
    result.isRight shouldBe true
    val impact = result.toOption.get
    impact.contributingSources should contain allOf (1, 2, 11, 12)
    impact.pathsTraversed shouldBe List("m1")
  }

  it should "handle empty target subset" in {
    val backward: Int => Either[CdmeError, Set[Int]] = _ => Right(Set.empty)
    val result = ImpactAnalyzer.analyzeImpact(Set.empty[Int], backward, Nil)
    result.isRight shouldBe true
    result.toOption.get.contributingSources shouldBe empty
  }

  it should "propagate backward errors" in {
    val backward: Int => Either[CdmeError, Set[Int]] = _ =>
      Left(com.cdme.model.error.ValidationError("backward failed"))
    val result = ImpactAnalyzer.analyzeImpact(Set(1), backward, Nil)
    result.isLeft shouldBe true
  }
}
