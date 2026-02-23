package com.cdme.runtime

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

// Validates: REQ-ACC-01, REQ-ACC-04
class AccountingLedgerSpec extends AnyFlatSpec with Matchers {

  "AccountingLedger" should "be balanced when input = processed + filtered + errored (AC-CDME-003)" in {
    val ledger = AccountingLedger(
      runId = "run-001",
      inputCount = 100,
      processedCount = 80,
      filteredCount = 15,
      erroredCount = 5
    )
    ledger.isBalanced shouldBe true
  }

  it should "detect imbalanced ledger" in {
    val ledger = AccountingLedger(
      runId = "run-001",
      inputCount = 100,
      processedCount = 80,
      filteredCount = 15,
      erroredCount = 10  // 80 + 15 + 10 = 105 != 100
    )
    ledger.isBalanced shouldBe false
  }

  it should "verify successfully when balanced (REQ-ACC-04)" in {
    val ledger = AccountingLedger(
      runId = "run-001",
      inputCount = 50,
      processedCount = 40,
      filteredCount = 5,
      erroredCount = 5
    )
    val verified = ledger.verify()
    verified.verified shouldBe true
  }

  it should "fail verification when imbalanced" in {
    val ledger = AccountingLedger(
      runId = "run-001",
      inputCount = 50,
      processedCount = 40,
      filteredCount = 5,
      erroredCount = 10
    )
    an[IllegalArgumentException] should be thrownBy ledger.verify()
  }

  it should "track per-morphism accounting" in {
    val ledger = AccountingLedger(
      runId = "run-001",
      inputCount = 100,
      processedCount = 90,
      filteredCount = 5,
      erroredCount = 5,
      morphismAccounting = Map(
        "step1" -> MorphismAccounting("step1", 100, 95, 5, 0),
        "step2" -> MorphismAccounting("step2", 95, 90, 0, 5)
      )
    )
    ledger.morphismAccounting should have size 2
    ledger.isBalanced shouldBe true
  }
}
