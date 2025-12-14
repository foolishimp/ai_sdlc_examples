package cdme

import cdme.core._
import cdme.executor._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues

/**
 * ErrorDomain unit tests following TDD.
 * Tests accumulator-based error collection logic without requiring Spark initialization.
 *
 * Implements: REQ-TYP-03 (Error Domain Semantics), REQ-TYP-03-A (Batch Failure Threshold),
 *             REQ-ERR-01 (Minimal Error Object), RIC-ERR-01 (Circuit Breakers)
 * Validates: REQ-ERROR-01 (Error object content)
 */
class ErrorDomainSpec extends AnyFlatSpec with Matchers with EitherValues {

  // ============================================
  // Unit Tests for ErrorConfig
  // ============================================

  "ErrorConfig" should "hold threshold configuration values" in {
    // Validates: REQ-TYP-03-A
    val config = ErrorConfig(
      absoluteThreshold = 1000,
      percentageThreshold = 0.05,
      bufferLimit = 100,
      dlqPath = "/path/to/dlq"
    )

    config.absoluteThreshold shouldBe 1000
    config.percentageThreshold shouldBe 0.05
    config.bufferLimit shouldBe 100
    config.dlqPath shouldBe "/path/to/dlq"
  }

  it should "validate percentage threshold is between 0 and 1" in {
    // Validates: REQ-TYP-03-A
    val validConfig = ErrorConfig(
      absoluteThreshold = 1000,
      percentageThreshold = 0.5,
      bufferLimit = 100,
      dlqPath = "/path/to/dlq"
    )

    validConfig.percentageThreshold should (be >= 0.0 and be <= 1.0)
  }

  it should "support zero absolute threshold for percentage-only mode" in {
    // Validates: REQ-TYP-03-A
    val config = ErrorConfig(
      absoluteThreshold = 0,
      percentageThreshold = 0.05,
      bufferLimit = 100,
      dlqPath = "/path/to/dlq"
    )

    config.absoluteThreshold shouldBe 0
  }

  // ============================================
  // Unit Tests for ErrorObject
  // ============================================

  "ErrorObject" should "be serializable for Spark accumulators" in {
    // Validates: REQ-ERR-01 (Minimal Error Object Content)
    val errorObj = ErrorObject(
      sourceKey = "order_123",
      morphismPath = "Order -> validate_amount",
      errorType = "refinement_error",
      expected = "amount > 0",
      actual = "-100",
      context = Map("field" -> "amount", "row_id" -> "123")
    )

    // Verify ErrorObject is serializable
    errorObj shouldBe a[Serializable]
    errorObj.sourceKey shouldBe "order_123"
    errorObj.morphismPath shouldBe "Order -> validate_amount"
    errorObj.errorType shouldBe "refinement_error"
    errorObj.expected shouldBe "amount > 0"
    errorObj.actual shouldBe "-100"
    errorObj.context shouldBe Map("field" -> "amount", "row_id" -> "123")
  }

  it should "contain all required fields per REQ-ERROR-01" in {
    // Validates: REQ-ERROR-01
    // Required fields: type, offending value(s), source entity+epoch, morphism path
    val errorObj = ErrorObject(
      sourceKey = "customer_456",
      morphismPath = "Customer -> validate_email",
      errorType = "type_cast_error",
      expected = "valid_email",
      actual = "not-an-email",
      context = Map("epoch" -> "2025-12-15", "entity" -> "Customer")
    )

    // Verify all required fields are present
    errorObj.sourceKey should not be empty
    errorObj.morphismPath should not be empty
    errorObj.errorType should not be empty
    errorObj.expected should not be empty
    errorObj.actual should not be empty
    errorObj.context should contain key "epoch"
    errorObj.context should contain key "entity"
  }

  it should "support empty context when not needed" in {
    val errorObj = ErrorObject(
      sourceKey = "trade_789",
      morphismPath = "Trade -> calculate_pnl",
      errorType = "validation_error",
      expected = "non-null",
      actual = "null",
      context = Map.empty
    )

    errorObj.context shouldBe empty
  }

  // ============================================
  // Unit Tests for ThresholdResult
  // ============================================

  "ThresholdResult.Continue" should "indicate processing can continue" in {
    // Validates: REQ-TYP-03-A
    val result = ThresholdResult.Continue

    result shouldBe ThresholdResult.Continue
    result match {
      case ThresholdResult.Continue => succeed
      case _ => fail("Expected Continue result")
    }
  }

  "ThresholdResult.Halt" should "contain reason and error count" in {
    // Validates: REQ-TYP-03-A
    val result = ThresholdResult.Halt(
      reason = ThresholdReason.AbsoluteCount,
      errorCount = 1500,
      threshold = 1000.0
    )

    result match {
      case ThresholdResult.Halt(reason, count, threshold) =>
        reason shouldBe ThresholdReason.AbsoluteCount
        count shouldBe 1500
        threshold shouldBe 1000.0
      case _ => fail("Expected Halt result")
    }
  }

  it should "support percentage reason" in {
    // Validates: REQ-TYP-03-A
    val result = ThresholdResult.Halt(
      reason = ThresholdReason.PercentageRate,
      errorCount = 75,
      threshold = 0.05
    )

    result match {
      case ThresholdResult.Halt(reason, count, threshold) =>
        reason shouldBe ThresholdReason.PercentageRate
        count shouldBe 75
        threshold shouldBe 0.05
      case _ => fail("Expected Halt result")
    }
  }

  // ============================================
  // Unit Tests for MockErrorDomain (No Spark)
  // ============================================

  "MockErrorDomain" should "initialize with empty error count" in {
    // Validates: REQ-TYP-03
    val domain = new MockErrorDomain(ErrorConfig(
      absoluteThreshold = 1000,
      percentageThreshold = 0.05,
      bufferLimit = 100,
      dlqPath = "/test/dlq"
    ))

    domain.getErrorCount() shouldBe 0
    domain.getErrors() shouldBe empty
  }

  it should "increment error count when routing errors" in {
    // Validates: REQ-TYP-03
    val domain = new MockErrorDomain(ErrorConfig(
      absoluteThreshold = 1000,
      percentageThreshold = 0.05,
      bufferLimit = 100,
      dlqPath = "/test/dlq"
    ))

    val error1 = ErrorObject("key1", "path1", "type1", "exp1", "act1", Map.empty)
    val error2 = ErrorObject("key2", "path2", "type2", "exp2", "act2", Map.empty)

    domain.routeError(error1)
    domain.getErrorCount() shouldBe 1

    domain.routeError(error2)
    domain.getErrorCount() shouldBe 2
  }

  it should "add errors to buffer respecting buffer limit" in {
    // Validates: REQ-TYP-03
    val domain = new MockErrorDomain(ErrorConfig(
      absoluteThreshold = 1000,
      percentageThreshold = 0.05,
      bufferLimit = 3,
      dlqPath = "/test/dlq"
    ))

    val error1 = ErrorObject("key1", "path1", "type1", "exp1", "act1", Map.empty)
    val error2 = ErrorObject("key2", "path2", "type2", "exp2", "act2", Map.empty)
    val error3 = ErrorObject("key3", "path3", "type3", "exp3", "act3", Map.empty)
    val error4 = ErrorObject("key4", "path4", "type4", "exp4", "act4", Map.empty)

    domain.routeError(error1)
    domain.routeError(error2)
    domain.routeError(error3)
    domain.routeError(error4) // Should not be buffered (exceeds limit)

    domain.getErrorCount() shouldBe 4
    domain.getErrors() should have size 3 // Buffer limited to 3
    domain.getErrors() should contain (error1)
    domain.getErrors() should contain (error2)
    domain.getErrors() should contain (error3)
    domain.getErrors() should not contain (error4)
  }

  it should "return Continue when under absolute threshold" in {
    // Validates: REQ-TYP-03-A
    val domain = new MockErrorDomain(ErrorConfig(
      absoluteThreshold = 100,
      percentageThreshold = 0.5,
      bufferLimit = 100,
      dlqPath = "/test/dlq"
    ))

    // Add 50 errors (under absolute threshold of 100)
    (1 to 50).foreach { i =>
      domain.routeError(ErrorObject(s"key$i", "path", "type", "exp", "act", Map.empty))
    }

    val result = domain.checkThreshold(totalRows = 1000)
    result shouldBe ThresholdResult.Continue
  }

  it should "return Halt when absolute threshold exceeded" in {
    // Validates: REQ-TYP-03-A (Circuit Breaker)
    val domain = new MockErrorDomain(ErrorConfig(
      absoluteThreshold = 100,
      percentageThreshold = 0.5,
      bufferLimit = 200,
      dlqPath = "/test/dlq"
    ))

    // Add 101 errors (exceeds absolute threshold of 100)
    (1 to 101).foreach { i =>
      domain.routeError(ErrorObject(s"key$i", "path", "type", "exp", "act", Map.empty))
    }

    val result = domain.checkThreshold(totalRows = 10000)
    result match {
      case ThresholdResult.Halt(reason, count, threshold) =>
        reason shouldBe ThresholdReason.AbsoluteCount
        count shouldBe 101
        threshold shouldBe 100.0
      case _ => fail("Expected Halt result")
    }
  }

  it should "return Halt when percentage threshold exceeded" in {
    // Validates: REQ-TYP-03-A (Circuit Breaker)
    val domain = new MockErrorDomain(ErrorConfig(
      absoluteThreshold = 10000,
      percentageThreshold = 0.05,
      bufferLimit = 100,
      dlqPath = "/test/dlq"
    ))

    // Add 60 errors out of 1000 rows = 6% error rate (exceeds 5% threshold)
    (1 to 60).foreach { i =>
      domain.routeError(ErrorObject(s"key$i", "path", "type", "exp", "act", Map.empty))
    }

    val result = domain.checkThreshold(totalRows = 1000)
    result match {
      case ThresholdResult.Halt(reason, count, threshold) =>
        reason shouldBe ThresholdReason.PercentageRate
        count shouldBe 60
        threshold shouldBe 0.05
      case _ => fail("Expected Halt result")
    }
  }

  it should "check absolute threshold before percentage threshold" in {
    // Validates: REQ-TYP-03-A
    val domain = new MockErrorDomain(ErrorConfig(
      absoluteThreshold = 50,
      percentageThreshold = 0.10,
      bufferLimit = 100,
      dlqPath = "/test/dlq"
    ))

    // Add 60 errors out of 1000 rows
    // Error rate: 6% (under 10% percentage threshold)
    // But 60 > 50 absolute threshold
    (1 to 60).foreach { i =>
      domain.routeError(ErrorObject(s"key$i", "path", "type", "exp", "act", Map.empty))
    }

    val result = domain.checkThreshold(totalRows = 1000)
    result match {
      case ThresholdResult.Halt(reason, _, _) =>
        reason shouldBe ThresholdReason.AbsoluteCount // Should fail on absolute first
      case _ => fail("Expected Halt result")
    }
  }

  it should "handle zero total rows gracefully" in {
    // Validates: REQ-TYP-03-A
    val domain = new MockErrorDomain(ErrorConfig(
      absoluteThreshold = 100,
      percentageThreshold = 0.05,
      bufferLimit = 100,
      dlqPath = "/test/dlq"
    ))

    val result = domain.checkThreshold(totalRows = 0)
    result shouldBe ThresholdResult.Continue
  }

  it should "reset accumulators for new batch" in {
    // Validates: REQ-TYP-03
    val domain = new MockErrorDomain(ErrorConfig(
      absoluteThreshold = 1000,
      percentageThreshold = 0.05,
      bufferLimit = 100,
      dlqPath = "/test/dlq"
    ))

    // Add some errors
    domain.routeError(ErrorObject("key1", "path1", "type1", "exp1", "act1", Map.empty))
    domain.routeError(ErrorObject("key2", "path2", "type2", "exp2", "act2", Map.empty))

    domain.getErrorCount() shouldBe 2
    domain.getErrors() should have size 2

    // Reset for new batch
    domain.reset()

    domain.getErrorCount() shouldBe 0
    domain.getErrors() shouldBe empty
  }

  it should "return all buffered errors" in {
    // Validates: REQ-TYP-03
    val domain = new MockErrorDomain(ErrorConfig(
      absoluteThreshold = 1000,
      percentageThreshold = 0.05,
      bufferLimit = 100,
      dlqPath = "/test/dlq"
    ))

    val error1 = ErrorObject("key1", "path1", "type1", "exp1", "act1", Map("ctx1" -> "val1"))
    val error2 = ErrorObject("key2", "path2", "type2", "exp2", "act2", Map("ctx2" -> "val2"))
    val error3 = ErrorObject("key3", "path3", "type3", "exp3", "act3", Map("ctx3" -> "val3"))

    domain.routeError(error1)
    domain.routeError(error2)
    domain.routeError(error3)

    val errors = domain.getErrors()
    errors should have size 3
    errors should contain (error1)
    errors should contain (error2)
    errors should contain (error3)
  }
}
