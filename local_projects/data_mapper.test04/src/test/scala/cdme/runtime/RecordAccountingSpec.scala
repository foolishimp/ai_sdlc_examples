// Validates: REQ-F-ACC-001, REQ-F-ACC-002, REQ-F-ACC-003, REQ-F-ACC-004, REQ-BR-ERR-001
// Tests for accounting invariant: |input| = |processed| + |filtered| + |errored|
package cdme.runtime

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import org.scalacheck.Gen

class RecordAccountingSpec extends AnyFunSuite with Matchers with ScalaCheckPropertyChecks:

  // --- REQ-F-ACC-001: Accounting Invariant ---

  test("REQ-F-ACC-001: balanced ledger holds invariant") {
    val ledger = AccountingLedger(
      runId = "run-001",
      inputCount = 1000,
      sourceKeyField = "trade_id",
      processedCount = 900,
      filteredCount = 50,
      erroredCount = 50
    )
    ledger.invariantHolds shouldBe true
    ledger.discrepancy shouldBe 0L
    ledger.totalAccounted shouldBe 1000L
  }

  test("REQ-F-ACC-001: unbalanced ledger violates invariant") {
    val ledger = AccountingLedger(
      runId = "run-002",
      inputCount = 1000,
      sourceKeyField = "trade_id",
      processedCount = 800,
      filteredCount = 50,
      erroredCount = 50
    )
    ledger.invariantHolds shouldBe false
    ledger.discrepancy shouldBe 100L
  }

  test("REQ-F-ACC-001: property - invariant holds iff discrepancy is zero") {
    val genCounts = for
      input <- Gen.posNum[Long]
      processed <- Gen.choose(0L, input)
      filtered <- Gen.choose(0L, input - processed)
    yield (input, processed, filtered, input - processed - filtered)

    forAll(genCounts) { case (input, processed, filtered, errored) =>
      val ledger = AccountingLedger(
        runId = "prop-test",
        inputCount = input,
        sourceKeyField = "id",
        processedCount = processed,
        filteredCount = filtered,
        erroredCount = errored
      )
      ledger.invariantHolds shouldBe true
      ledger.discrepancy shouldBe 0L
    }
  }

  test("REQ-F-ACC-001: zero input count with zero outputs holds invariant") {
    val ledger = AccountingLedger(
      runId = "empty-run",
      inputCount = 0,
      sourceKeyField = "id",
      processedCount = 0,
      filteredCount = 0,
      erroredCount = 0
    )
    ledger.invariantHolds shouldBe true
  }

  // --- REQ-F-ACC-002: Accounting Ledger ---

  test("REQ-F-ACC-002: ledger contains required fields") {
    val ledger = AccountingLedger(
      runId = "run-003",
      inputCount = 500,
      sourceKeyField = "record_key",
      processedCount = 400,
      filteredCount = 50,
      erroredCount = 50,
      adjointMetadataLocations = Map(
        "reverseJoin" -> java.net.URI.create("file:///data/rjt.parquet")
      )
    )
    ledger.runId shouldBe "run-003"
    ledger.sourceKeyField shouldBe "record_key"
    ledger.adjointMetadataLocations should contain key "reverseJoin"
  }

  test("REQ-F-ACC-002: factory method creates zeroed ledger") {
    val ledger = AccountingLedger.create("run-004", 1000, "id")
    ledger.inputCount shouldBe 1000L
    ledger.processedCount shouldBe 0L
    ledger.filteredCount shouldBe 0L
    ledger.erroredCount shouldBe 0L
    ledger.verificationStatus shouldBe VerificationStatus.Pending
  }

  // --- REQ-F-ACC-003: Completion Gate ---

  test("REQ-F-ACC-003: completion gate verifies balanced ledger") {
    val ledger = AccountingLedger(
      runId = "run-005",
      inputCount = 100,
      sourceKeyField = "id",
      processedCount = 80,
      filteredCount = 10,
      erroredCount = 10
    )
    val result = CompletionGate.verify(ledger)
    result.isRight shouldBe true
    val verified = result.toOption.get
    verified.ledger.verificationStatus shouldBe VerificationStatus.Verified
  }

  test("REQ-F-ACC-003: completion gate rejects unbalanced ledger") {
    val ledger = AccountingLedger(
      runId = "run-006",
      inputCount = 100,
      sourceKeyField = "id",
      processedCount = 70,
      filteredCount = 10,
      erroredCount = 10 // missing 10 records
    )
    val result = CompletionGate.verify(ledger)
    result.isLeft shouldBe true
    val failure = result.left.toOption.get
    failure.message should include("invariant violated")
    failure.message should include("discrepancy")
    failure.ledger.verificationStatus shouldBe a[VerificationStatus.Failed]
  }

  test("REQ-F-ACC-003: completion gate failure includes discrepancy count") {
    val ledger = AccountingLedger(
      runId = "run-007",
      inputCount = 1000,
      sourceKeyField = "id",
      processedCount = 500,
      filteredCount = 200,
      erroredCount = 100 // discrepancy: 200
    )
    val result = CompletionGate.verify(ledger)
    result.isLeft shouldBe true
    result.left.toOption.get.message should include("200")
  }

  // --- VerificationStatus ---

  test("VerificationStatus enum covers all states") {
    val pending = VerificationStatus.Pending
    val verified = VerificationStatus.Verified
    val failed = VerificationStatus.Failed("test reason")

    pending shouldBe VerificationStatus.Pending
    verified shouldBe VerificationStatus.Verified
    failed shouldBe a[VerificationStatus.Failed]
  }

  // --- CircuitBreakerResult in Ledger ---

  test("REQ-F-ACC-002: ledger can include circuit breaker result") {
    val ledger = AccountingLedger(
      runId = "run-008",
      inputCount = 1000,
      sourceKeyField = "id",
      processedCount = 1000,
      filteredCount = 0,
      erroredCount = 0,
      circuitBreakerResult = Some(CircuitBreakerResult.Passed(10000, 0.01))
    )
    ledger.circuitBreakerResult shouldBe defined
    ledger.circuitBreakerResult.get shouldBe a[CircuitBreakerResult.Passed]
  }

  // --- REQ-BR-ERR-001: No Silent Data Loss ---

  test("REQ-BR-ERR-001: property - every record must appear in exactly one partition") {
    val genPartition = for
      total <- Gen.choose(1L, 10000L)
      processed <- Gen.choose(0L, total)
      filtered <- Gen.choose(0L, total - processed)
    yield (total, processed, filtered, total - processed - filtered)

    forAll(genPartition) { case (total, processed, filtered, errored) =>
      val ledger = AccountingLedger("prop", total, "key", processed, filtered, errored)
      // Mutual exclusivity check: processed + filtered + errored = total
      ledger.totalAccounted shouldBe total
      // Completeness check: no unaccounted records
      ledger.discrepancy shouldBe 0L
      ledger.invariantHolds shouldBe true
    }
  }
