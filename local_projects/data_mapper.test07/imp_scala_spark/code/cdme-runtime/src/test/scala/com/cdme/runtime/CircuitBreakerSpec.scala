// Validates: RIC-ERR-01, REQ-TYP-03-A
// Tests for the circuit breaker.
package com.cdme.runtime

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CircuitBreakerSpec extends AnyFlatSpec with Matchers {

  "CircuitBreaker" should "start in Monitoring state" in {
    val cb = CircuitBreaker.create(100, 5.0)
    cb.state shouldBe CircuitBreakerState.Monitoring(0, 0)
  }

  it should "track successes without tripping" in {
    var cb = CircuitBreaker.create(10, 50.0)
    for (_ <- 1 to 10) {
      cb = cb.record(success = true).toOption.get
    }
    cb.state shouldBe CircuitBreakerState.Cleared
  }

  it should "trip when error rate exceeds threshold" in {
    var cb = CircuitBreaker.create(10, 5.0)
    // 10 failures out of 10 = 100% > 5%
    var result: Either[_, _] = Right(cb)
    for (_ <- 1 to 10) {
      result = result.flatMap { case c: CircuitBreaker => c.record(success = false) }
    }
    result.isLeft shouldBe true
  }

  it should "not trip when errors are below threshold" in {
    var cb = CircuitBreaker.create(100, 10.0)
    // 5 errors out of 100 = 5% < 10%
    for (_ <- 1 to 5) {
      cb = cb.record(success = false).toOption.get
    }
    for (_ <- 1 to 95) {
      cb = cb.record(success = true).toOption.get
    }
    cb.state shouldBe CircuitBreakerState.Cleared
  }

  it should "clear after window completes without tripping" in {
    var cb = CircuitBreaker.create(5, 50.0)
    for (_ <- 1 to 5) {
      cb = cb.record(success = true).toOption.get
    }
    cb.state shouldBe CircuitBreakerState.Cleared
  }

  it should "remain cleared after clearing" in {
    var cb = CircuitBreaker.create(2, 50.0)
    cb = cb.record(success = true).toOption.get
    cb = cb.record(success = true).toOption.get
    cb.state shouldBe CircuitBreakerState.Cleared
    // Additional records after clearing should be no-ops
    cb = cb.record(success = false).toOption.get
    cb.state shouldBe CircuitBreakerState.Cleared
  }

  it should "trip early if threshold is already exceeded" in {
    val cb = CircuitBreaker.create(10, 5.0)
    // First record is an error: 1/10 = 10% is the best case, already > 5%
    val result = cb.record(success = false)
    result.isLeft shouldBe true
  }
}
