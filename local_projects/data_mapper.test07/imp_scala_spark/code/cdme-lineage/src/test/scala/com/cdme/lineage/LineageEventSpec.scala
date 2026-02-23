// Validates: REQ-INT-03, REQ-TRV-04
// Tests for lineage event construction.
package com.cdme.lineage

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import com.cdme.runtime._
import com.cdme.compiler._
import java.time.Instant

class LineageEventSpec extends AnyFlatSpec with Matchers {

  val manifest: RunManifest = RunManifest(
    runId = "run-001",
    configHash = "abc123",
    codeHash = "def456",
    designHash = "ghi789",
    timestamp = Instant.now(),
    inputChecksums = Map.empty,
    outputChecksums = Map.empty
  )

  val epoch: Epoch = Epoch("ep1", Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-01-02T00:00:00Z"))

  val context: ExecutionContext = ExecutionContext(
    runId = "run-001",
    epoch = epoch,
    lookupVersions = Map("rates" -> "v1.0"),
    lineageMode = LineageMode.Full,
    failureThreshold = FailureThreshold.NoThreshold,
    dryRun = false,
    runManifest = manifest
  )

  val plan: ValidatedPlan = ValidatedPlan(
    executionDag = ExecutionDag(Map.empty, Map.empty, Set.empty, Set.empty),
    costEstimate = CostEstimate(1000L, 2, 256000L, withinBudget = true, Map.empty),
    lineageClassification = Map.empty,
    adjointComposition = Map.empty,
    warnings = Nil
  )

  "OpenLineageEmitter.emitStart" should "produce a Start event" in {
    val result = OpenLineageEmitter.emitStart(context, plan, "test-job", Nil, Nil)
    result.isRight shouldBe true
    val event = result.toOption.get
    event.eventType shouldBe LineageEventType.Start
    event.runId shouldBe "run-001"
    event.jobName shouldBe "test-job"
    event.facets.costEstimate shouldBe defined
    event.facets.runManifest.configHash shouldBe "abc123"
  }

  "OpenLineageEmitter.emitComplete" should "produce a Complete event with ledger" in {
    val ledger = RecordAccounting.buildLedger("run-001", "key", 100, 80, 10, 10, "", "", "")
    val result = OpenLineageEmitter.emitComplete(context, ledger, "test-job", Nil, Nil, Nil)
    result.isRight shouldBe true
    val event = result.toOption.get
    event.eventType shouldBe LineageEventType.Complete
    event.facets.accountingLedger shouldBe defined
  }

  "OpenLineageEmitter.emitFail" should "produce a Fail event" in {
    val error = com.cdme.model.error.ValidationError("test error")
    val result = OpenLineageEmitter.emitFail(context, error, "test-job", Nil)
    result.isRight shouldBe true
    result.toOption.get.eventType shouldBe LineageEventType.Fail
  }

  "LineageEventType" should "have three variants" in {
    val types: Set[LineageEventType] = Set(LineageEventType.Start, LineageEventType.Complete, LineageEventType.Fail)
    types should have size 3
  }

  "CdmeLineageEvent" should "carry lookup versions in facets" in {
    val result = OpenLineageEmitter.emitStart(context, plan, "job", Nil, Nil)
    val event = result.toOption.get
    event.facets.lookupVersionsUsed shouldBe Map("rates" -> "v1.0")
  }

  "FidelityCertificate" should "compute a deterministic hash" in {
    val cert = FidelityCertificate(
      certificateId = "cert-001",
      previousCertificateHash = None,
      sourceDomainHash = "src_hash",
      targetDomainHash = "tgt_hash",
      contractVersion = "1.0",
      invariantResults = List(InvariantResult("conservation", passed = true, "1.0", "1.0", "ok")),
      timestamp = Instant.parse("2024-01-01T00:00:00Z"),
      runId = "run-001"
    )
    val hash1 = cert.computeHash
    val hash2 = cert.computeHash
    hash1 shouldBe hash2
    hash1 should have length 64 // SHA-256 hex
  }

  it should "report allPassed correctly" in {
    val cert = FidelityCertificate(
      "c1", None, "s", "t", "1.0",
      List(
        InvariantResult("inv1", passed = true, "1.0", "1.0", ""),
        InvariantResult("inv2", passed = false, "0.9", "1.0", "")
      ),
      Instant.now(), "run-001"
    )
    cert.allPassed shouldBe false
  }
}
