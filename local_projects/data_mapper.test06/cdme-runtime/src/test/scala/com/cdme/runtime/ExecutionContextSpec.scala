package com.cdme.runtime

import java.time.Instant
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import com.cdme.compiler._
import com.cdme.model._

// Validates: REQ-TRV-05-A, REQ-TRV-05-B
class ExecutionContextSpec extends AnyFlatSpec with Matchers {

  "RunManifest" should "capture immutable run hierarchy (REQ-TRV-05-A)" in {
    val manifest = RunManifest(
      runId = "run-001",
      configHash = "abc123",
      codeHash = "def456",
      designHash = "ghi789",
      mappingVersion = "1.0.0",
      sourceBindings = Map("trades" -> "/data/trades.parquet"),
      lookupVersions = Map("currency_ref" -> "v2.3"),
      cdmeVersion = "0.1.0",
      timestamp = Instant.now()
    )
    manifest.runId should not be empty
    manifest.configHash should not be empty
    manifest.codeHash should not be empty
    manifest.designHash should not be empty
  }

  "ExecutionContext" should "bind epoch to run" in {
    val manifest = RunManifest(
      "run-001", "abc", "def", "ghi", "1.0",
      Map.empty, Map.empty, "0.1.0", Instant.now()
    )
    val epoch = Epoch("epoch-2025-01-01", Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2025-01-02T00:00:00Z"), GenerationGrain.Event)
    val plan = CompiledPlan(Nil, CostEstimate(0, 0, 0), Map.empty, Map.empty, Map.empty)

    val ctx = ExecutionContext(manifest, epoch, plan)
    ctx.epoch.generationGrain shouldBe GenerationGrain.Event
    ctx.lineageMode shouldBe LineageMode.Full
  }

  "FailureThreshold" should "support absolute and percentage thresholds" in {
    val threshold = FailureThreshold(maxAbsolute = Some(1000), maxPercentage = Some(0.05))
    threshold.maxAbsolute shouldBe Some(1000)
    threshold.maxPercentage shouldBe Some(0.05)
  }

  "ArtifactVersion" should "track version with content hash (REQ-TRV-05-B)" in {
    val av = ArtifactVersion("mapping-v1", "1.0.0", "sha256:abc", Instant.now())
    av.version shouldBe "1.0.0"
    av.contentHash should startWith("sha256:")
  }
}
