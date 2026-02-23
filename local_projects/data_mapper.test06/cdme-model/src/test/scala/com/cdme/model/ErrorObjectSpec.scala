package com.cdme.model

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

// Validates: REQ-TYP-03, REQ-ERROR-01
class ErrorObjectSpec extends AnyFlatSpec with Matchers {

  "ErrorObject" should "contain all required metadata fields (REQ-ERROR-01)" in {
    val error = ErrorObject(
      failureType = "TYPE_VIOLATION",
      offendingValues = Map("expected" -> "Int", "actual" -> "abc"),
      sourceEntity = "Trade",
      sourceEpoch = "2025-01-01",
      morphismPath = List("Trade", "amount"),
      constraintViolated = "REQ-TYP-06",
      timestamp = java.time.Instant.now()
    )

    error.failureType should not be empty
    error.offendingValues should not be empty
    error.sourceEntity should not be empty
    error.sourceEpoch should not be empty
    error.morphismPath should not be empty
    error.constraintViolated should not be empty
  }

  "ErrorObject.typeViolation" should "create properly structured error" in {
    val error = ErrorObject.typeViolation(
      expected = SemanticType.IntType,
      actual = "abc",
      entity = "Trade",
      epoch = "2025-01-01",
      path = List("Trade", "amount")
    )
    error.failureType shouldBe "TYPE_VIOLATION"
    error.sourceEntity shouldBe "Trade"
  }

  "ErrorObject.refinementViolation" should "create properly structured error" in {
    val error = ErrorObject.refinementViolation(
      predicateName = "positive",
      value = "-5",
      entity = "Account",
      epoch = "2025-01-01",
      path = List("Account", "balance")
    )
    error.failureType shouldBe "REFINEMENT_VIOLATION"
    error.offendingValues should contain key "predicate"
    error.offendingValues("predicate") shouldBe "positive"
  }
}
