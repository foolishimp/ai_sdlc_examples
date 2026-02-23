// Validates: REQ-ACC-01, REQ-ACC-02, REQ-ACC-04
// Tests for record accounting and the fundamental invariant.
package com.cdme.runtime

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import org.scalacheck.Gen

class RecordAccountingSpec extends AnyFlatSpec with Matchers with ScalaCheckPropertyChecks {

  "RecordAccounting.buildLedger" should "be balanced when counts add up" in {
    val ledger = RecordAccounting.buildLedger(
      runId = "run1",
      sourceKeyField = "tradeId",
      inputCount = 100,
      processedCount = 80,
      filteredCount = 15,
      erroredCount = 5,
      reverseJoinLocation = "/rj",
      filteredKeysLocation = "/fk",
      errorLocation = "/err"
    )
    ledger.balanced shouldBe true
    ledger.discrepancy shouldBe None
  }

  it should "detect imbalance when counts do not add up" in {
    val ledger = RecordAccounting.buildLedger(
      runId = "run2",
      sourceKeyField = "tradeId",
      inputCount = 100,
      processedCount = 80,
      filteredCount = 10,
      erroredCount = 5,
      reverseJoinLocation = "/rj",
      filteredKeysLocation = "/fk",
      errorLocation = "/err"
    )
    ledger.balanced shouldBe false
    ledger.discrepancy shouldBe defined
    ledger.discrepancy.get.difference shouldBe 5L
  }

  it should "handle zero input" in {
    val ledger = RecordAccounting.buildLedger(
      "run3", "key", 0, 0, 0, 0, "", "", ""
    )
    ledger.balanced shouldBe true
  }

  "RecordAccounting.verifyInvariant" should "pass for balanced ledger" in {
    val ledger = RecordAccounting.buildLedger(
      "run4", "key", 100, 50, 30, 20, "", "", ""
    )
    RecordAccounting.verifyInvariant(ledger) shouldBe Right(ledger)
  }

  it should "fail for unbalanced ledger" in {
    val ledger = RecordAccounting.buildLedger(
      "run5", "key", 100, 50, 30, 10, "", "", ""
    )
    RecordAccounting.verifyInvariant(ledger).isLeft shouldBe true
  }

  "Accounting invariant" should "hold for any valid partition of input" in {
    forAll(Gen.chooseNum(0L, 10000L)) { input =>
      forAll(
        Gen.chooseNum(0L, input),
        Gen.chooseNum(0L, input)
      ) { (processed, filtered) =>
        val errored = input - processed - filtered
        whenever(errored >= 0) {
          val ledger = RecordAccounting.buildLedger(
            "prop_test", "key", input, processed, filtered, errored, "", "", ""
          )
          ledger.balanced shouldBe true
          RecordAccounting.verifyInvariant(ledger) shouldBe Right(ledger)
        }
      }
    }
  }
}
