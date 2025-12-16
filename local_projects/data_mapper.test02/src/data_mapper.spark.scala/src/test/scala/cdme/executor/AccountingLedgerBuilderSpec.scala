package cdme.executor

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues

/**
 * Tests for AccountingLedgerBuilder
 *
 * Validates: REQ-ACC-01, REQ-ACC-02, REQ-ACC-03, REQ-ACC-04
 *
 * Implements: TDD approach (RED → GREEN → REFACTOR)
 */
class AccountingLedgerBuilderSpec extends AnyFlatSpec with Matchers with EitherValues {

  "AccountingLedgerBuilder" should "verify balanced accounting" in {
    val builder = new AccountingLedgerBuilder("run-001", "input.json", "id")
    builder.setInputKeys(Set("A", "B", "C"))
    builder.addPartition("OUTPUT", Set("A", "B"), "adjoint/output")
    builder.addPartition("ERROR", Set("C"), "adjoint/errors")

    val result = builder.verify()

    result.isRight shouldBe true
    result.value.verification.balanced shouldBe true
  }

  it should "detect missing keys" in {
    val builder = new AccountingLedgerBuilder("run-002", "input.json", "id")
    builder.setInputKeys(Set("A", "B", "C"))
    builder.addPartition("OUTPUT", Set("A", "B"), "adjoint/output")

    val result = builder.verify()

    result.isLeft shouldBe true
    result.left.value.missingKeys shouldBe Set("C")
  }

  it should "detect collisions" in {
    val builder = new AccountingLedgerBuilder("run-003", "input.json", "id")
    builder.setInputKeys(Set("A", "B"))
    builder.addPartition("OUTPUT", Set("A"), "adjoint/output")
    builder.addPartition("ERROR", Set("A", "B"), "adjoint/errors")

    val result = builder.verify()

    result.isLeft shouldBe true
    result.left.value.collisions should contain key "A"
  }

  it should "serialize to JSON" in {
    val builder = new AccountingLedgerBuilder("run-004", "input.json", "id")
    builder.setInputKeys(Set("A"))
    builder.addPartition("OUTPUT", Set("A"), "adjoint/output")

    val ledger = builder.verify().value
    val json = AccountingLedger.toJson(ledger)

    json should include ("\"ledgerVersion\"")
    json should include ("\"runId\"")
  }
}
