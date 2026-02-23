package com.cdme.adjoint

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

// Validates: REQ-ADJ-08, REQ-ADJ-09
class ReconciliationSpec extends AnyFlatSpec with Matchers {

  "ReconciliationEngine" should "show exact reconciliation for isomorphisms" in {
    val adjoint = Adjoint.isomorphism[Int, Int](_ * 2, _ / 2)
    val result = ReconciliationEngine.reconcile(adjoint, Set(2, 4, 6))
    result.isExact shouldBe true
    result.missing shouldBe empty
  }

  it should "detect missing records for lossy adjoints" in {
    // A lossy adjoint: forward loses precision, backward doesn't recover
    val adjoint = Adjoint.lossy[Int, Int](_ / 2, _ * 2)
    val result = ReconciliationEngine.reconcile(adjoint, Set(3, 5, 7))
    // 3/2=1, 5/2=2, 7/2=3 → backward: 1*2=2, 2*2=4, 3*2=6
    // Original: {3,5,7}, RoundTripped: {2,4,6} → missing: {3,5,7}
    result.isExact shouldBe false
  }

  "ImpactAnalyzer" should "compute impact via reverse-join table" in {
    val rjt = ReverseJoinTable.fromPairs(List(
      "agg1" -> "src1", "agg1" -> "src2",
      "agg2" -> "src3"
    ))
    val impact = ImpactAnalyzer.analyzeImpactViaReverseJoin(rjt, Set("agg1"))
    impact shouldBe Set("src1", "src2")
  }

  it should "compute impact via adjoint backward" in {
    val adjoint = Adjoint.isomorphism[Int, Int](_ + 10, _ - 10)
    val impact = ImpactAnalyzer.analyzeImpact(adjoint, Set(15, 25))
    impact shouldBe Set(5, 15)
  }
}
