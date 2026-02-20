// Validates: REQ-DATA-QUAL-001, REQ-DATA-QUAL-002
// Tests for sampling, threshold detection, structural error distinction
package cdme.runtime

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import cdme.compiler.CircuitBreakerConfig

class CircuitBreakerSpec extends AnyFunSuite with Matchers {

  private val defaultConfig = CircuitBreakerConfig(
    sampleSize = 10000,
    structuralErrorThreshold = 0.05
  )

  // --- REQ-DATA-QUAL-002: Circuit Breaker ---

  test("REQ-DATA-QUAL-002: low error rate passes circuit breaker") {
    val sampleResults = SampleResults(
      sampleSize = 10000,
      errorCount = 100, // 1% error rate
      errorDetails = List("type mismatch on field 'amount'")
    )
    val result = CircuitBreaker.evaluate(sampleResults, defaultConfig)
    result.isRight shouldBe true
    val passed = result.toOption.get.asInstanceOf[CircuitBreakerResult.Passed]
    passed.sampleSize shouldBe 10000
    passed.errorRate shouldBe 0.01
  }

  test("REQ-DATA-QUAL-002: high error rate trips circuit breaker") {
    val sampleResults = SampleResults(
      sampleSize = 10000,
      errorCount = 1000, // 10% error rate > 5% threshold
      errorDetails = List("missing column 'trade_date'", "null key field")
    )
    val result = CircuitBreaker.evaluate(sampleResults, defaultConfig)
    result.isLeft shouldBe true
    val err = result.left.toOption.get
    err.errorRate shouldBe 0.10
    err.threshold shouldBe 0.05
    err.message should include("Circuit breaker tripped")
    err.message should include("structural")
  }

  test("REQ-DATA-QUAL-002: error rate exactly at threshold passes") {
    val sampleResults = SampleResults(
      sampleSize = 10000,
      errorCount = 500 // 5% = threshold (not exceeded, since > is used)
    )
    val result = CircuitBreaker.evaluate(sampleResults, defaultConfig)
    result.isRight shouldBe true
  }

  test("REQ-DATA-QUAL-002: error rate just above threshold trips") {
    val sampleResults = SampleResults(
      sampleSize = 10000,
      errorCount = 501 // 5.01% > 5% threshold
    )
    val result = CircuitBreaker.evaluate(sampleResults, defaultConfig)
    result.isLeft shouldBe true
  }

  test("REQ-DATA-QUAL-002: zero errors passes circuit breaker") {
    val sampleResults = SampleResults(sampleSize = 10000, errorCount = 0)
    val result = CircuitBreaker.evaluate(sampleResults, defaultConfig)
    result.isRight shouldBe true
    result.toOption.get.asInstanceOf[CircuitBreakerResult.Passed].errorRate shouldBe 0.0
  }

  test("REQ-DATA-QUAL-002: empty sample passes circuit breaker") {
    val sampleResults = SampleResults(sampleSize = 0, errorCount = 0)
    val result = CircuitBreaker.evaluate(sampleResults, defaultConfig)
    result.isRight shouldBe true
  }

  test("REQ-DATA-QUAL-002: circuit breaker message includes sample error details") {
    val sampleResults = SampleResults(
      sampleSize = 100,
      errorCount = 10, // 10% > 5%
      errorDetails = List("missing 'trade_id'", "type mismatch on 'amount'", "null date")
    )
    val result = CircuitBreaker.evaluate(sampleResults, defaultConfig)
    result.isLeft shouldBe true
    result.left.toOption.get.message should include("missing 'trade_id'")
  }

  // --- SampleResults ---

  test("SampleResults: errorRate calculation") {
    SampleResults(100, 5).errorRate shouldBe 0.05
    SampleResults(100, 0).errorRate shouldBe 0.0
    SampleResults(0, 0).errorRate shouldBe 0.0
  }

  // --- StructuralError ---

  test("StructuralError contains error rate and threshold") {
    val err = StructuralError(
      message = "Config error detected",
      errorRate = 0.15,
      threshold = 0.05
    )
    err.errorRate shouldBe 0.15
    err.threshold shouldBe 0.05
    err.message shouldBe "Config error detected"
  }

  // --- Configurable threshold ---

  test("custom threshold can be more lenient") {
    val lenientConfig = CircuitBreakerConfig(sampleSize = 1000, structuralErrorThreshold = 0.20)
    val sampleResults = SampleResults(1000, 150) // 15% < 20%
    CircuitBreaker.evaluate(sampleResults, lenientConfig).isRight shouldBe true
  }

  test("custom threshold can be more strict") {
    val strictConfig = CircuitBreakerConfig(sampleSize = 1000, structuralErrorThreshold = 0.01)
    val sampleResults = SampleResults(1000, 20) // 2% > 1%
    CircuitBreaker.evaluate(sampleResults, strictConfig).isLeft shouldBe true
  }

  // --- CircuitBreakerResult variants ---

  test("CircuitBreakerResult.Passed carries sample metadata") {
    val passed = CircuitBreakerResult.Passed(10000, 0.02)
    passed.sampleSize shouldBe 10000
    passed.errorRate shouldBe 0.02
  }

  test("CircuitBreakerResult.Tripped carries threshold info") {
    val tripped = CircuitBreakerResult.Tripped(10000, 0.10, 0.05)
    tripped.sampleSize shouldBe 10000
    tripped.errorRate shouldBe 0.10
    tripped.threshold shouldBe 0.05
  }
}
