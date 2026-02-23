// Validates: REQ-TRV-03, REQ-SHF-01
// Tests for epoch and temporal consistency checking.
package com.cdme.compiler

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ContextCheckerSpec extends AnyFlatSpec with Matchers {

  "ContextChecker" should "allow same-epoch joins" in {
    val result = ContextChecker.validateFiberCompatibility("epoch1", "epoch1", None)
    result shouldBe Right(())
  }

  it should "reject cross-epoch joins without temporal semantics" in {
    val result = ContextChecker.validateFiberCompatibility("epoch1", "epoch2", None)
    result.isLeft shouldBe true
  }

  it should "allow cross-epoch joins with AsOf semantics" in {
    val result = ContextChecker.validateFiberCompatibility(
      "epoch1", "epoch2", Some(ContextChecker.TemporalSemantics.AsOf)
    )
    result shouldBe Right(())
  }

  it should "allow cross-epoch joins with Latest semantics" in {
    val result = ContextChecker.validateFiberCompatibility(
      "epoch1", "epoch2", Some(ContextChecker.TemporalSemantics.Latest)
    )
    result shouldBe Right(())
  }

  it should "allow cross-epoch joins with Exact semantics" in {
    val result = ContextChecker.validateFiberCompatibility(
      "epoch1", "epoch2", Some(ContextChecker.TemporalSemantics.Exact)
    )
    result shouldBe Right(())
  }

  it should "provide clear error message for cross-epoch violation" in {
    val result = ContextChecker.validateFiberCompatibility("2024-01", "2024-02", None)
    result.isLeft shouldBe true
    result.left.getOrElse(null).message should include("temporal semantics")
  }
}
