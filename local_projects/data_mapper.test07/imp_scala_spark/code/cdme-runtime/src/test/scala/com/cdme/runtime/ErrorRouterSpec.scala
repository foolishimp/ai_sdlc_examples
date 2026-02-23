// Validates: REQ-TYP-03, RIC-ERR-01
// Tests for error bifurcation and circuit breaker checking.
package com.cdme.runtime

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import com.cdme.model.error._

class ErrorRouterSpec extends AnyFlatSpec with Matchers {

  "ErrorRouter.bifurcate" should "separate valid and error records" in {
    val records = Seq(1, 2, 3, 4, 5)
    val (valid, errors) = ErrorRouter.bifurcate[Int](
      records,
      _.toString,
      r => if (r % 2 == 0) Right(r) else Left(ValidationError(s"Odd: $r"))
    )
    valid shouldBe Seq(2, 4)
    errors should have size 3
  }

  it should "handle all-valid records" in {
    val records = Seq(2, 4, 6)
    val (valid, errors) = ErrorRouter.bifurcate[Int](
      records,
      _.toString,
      r => Right(r)
    )
    valid shouldBe Seq(2, 4, 6)
    errors shouldBe empty
  }

  it should "handle all-error records" in {
    val records = Seq(1, 3, 5)
    val (valid, errors) = ErrorRouter.bifurcate[Int](
      records,
      _.toString,
      r => Left(ValidationError(s"Bad: $r"))
    )
    valid shouldBe empty
    errors should have size 3
  }

  it should "handle empty input" in {
    val (valid, errors) = ErrorRouter.bifurcate[Int](
      Seq.empty,
      _.toString,
      r => Right(r)
    )
    valid shouldBe empty
    errors shouldBe empty
  }

  it should "capture morphism ID in error records" in {
    val records = Seq(1)
    val (_, errors) = ErrorRouter.bifurcate[Int](
      records,
      _.toString,
      _ => Left(ValidationError("bad")),
      morphismId = Some("morph_1")
    )
    errors.head.morphismId shouldBe Some("morph_1")
  }

  "ErrorRouter.checkCircuitBreaker" should "pass with NoThreshold" in {
    ErrorRouter.checkCircuitBreaker(100, 100, FailureThreshold.NoThreshold) shouldBe Right(())
  }

  it should "pass when under absolute threshold" in {
    ErrorRouter.checkCircuitBreaker(5, 1000, FailureThreshold.AbsoluteCount(10)) shouldBe Right(())
  }

  it should "trip when exceeding absolute threshold" in {
    val result = ErrorRouter.checkCircuitBreaker(11, 1000, FailureThreshold.AbsoluteCount(10))
    result.isLeft shouldBe true
  }

  it should "pass when under percentage threshold" in {
    ErrorRouter.checkCircuitBreaker(4, 100, FailureThreshold.Percentage(5.0)) shouldBe Right(())
  }

  it should "trip when exceeding percentage threshold" in {
    val result = ErrorRouter.checkCircuitBreaker(6, 100, FailureThreshold.Percentage(5.0))
    result.isLeft shouldBe true
  }

  it should "handle zero total count" in {
    ErrorRouter.checkCircuitBreaker(0, 0, FailureThreshold.Percentage(5.0)) shouldBe Right(())
  }
}
