package com.cdme.compiler

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import com.cdme.model.Grain

// Validates: REQ-TRV-02 (Grain Safety — AC-CDME-002)
class GrainCheckerSpec extends AnyFlatSpec with Matchers {

  "GrainChecker" should "allow same-grain operations" in {
    GrainChecker.check(Grain.Atomic, Grain.Atomic, hasAggregation = false) shouldBe Right(())
    GrainChecker.check(Grain.Daily, Grain.Daily, hasAggregation = false) shouldBe Right(())
  }

  it should "reject cross-grain without aggregation (AC-CDME-002)" in {
    val result = GrainChecker.check(Grain.Atomic, Grain.Daily, hasAggregation = false)
    result.isLeft shouldBe true
    result.left.get shouldBe a[CompilationError.GrainViolation]
  }

  it should "allow cross-grain WITH explicit aggregation" in {
    GrainChecker.check(Grain.Atomic, Grain.Daily, hasAggregation = true) shouldBe Right(())
  }

  it should "reject disaggregation (coarse to fine)" in {
    val result = GrainChecker.check(Grain.Monthly, Grain.Daily, hasAggregation = false)
    result.isLeft shouldBe true
  }

  "GrainChecker.checkMultiple" should "pass for compatible grains" in {
    val result = GrainChecker.checkMultiple(List(
      ("field1", Grain.Daily),
      ("field2", Grain.Daily),
      ("field3", Grain.Daily)
    ))
    result.isRight shouldBe true
  }

  it should "reject incompatible grains in projection" in {
    val result = GrainChecker.checkMultiple(List(
      ("field1", Grain.Atomic),
      ("field2", Grain.Daily)
    ))
    result.isLeft shouldBe true
    result.left.get shouldBe a[CompilationError.GrainViolation]
  }
}
