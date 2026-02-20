// Validates: REQ-F-TRV-003, REQ-F-TRV-004, REQ-F-PDM-002, REQ-F-PDM-003, REQ-BR-LDM-002
// Tests for epoch alignment, temporal semantics, boundary detection
package cdme.model.context

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import java.time.Instant
import cdme.model.error.ValidationError

class ContextSpec extends AnyFunSuite with Matchers {

  // --- Test Fixtures ---

  private val epoch1 = Epoch(
    id = EpochId("2026-02-20"),
    startTime = Instant.parse("2026-02-20T00:00:00Z"),
    endTime = Instant.parse("2026-02-20T23:59:59Z")
  )

  private val epoch2 = Epoch(
    id = EpochId("2026-02-21"),
    startTime = Instant.parse("2026-02-21T00:00:00Z"),
    endTime = Instant.parse("2026-02-21T23:59:59Z")
  )

  // --- Epoch Tests ---

  test("Epoch: created with valid time range") {
    epoch1.id.value shouldBe "2026-02-20"
    epoch1.durationMs should be > 0L
  }

  test("Epoch: rejects end time before start time") {
    an[IllegalArgumentException] should be thrownBy {
      Epoch(
        id = EpochId("bad"),
        startTime = Instant.parse("2026-02-21T00:00:00Z"),
        endTime = Instant.parse("2026-02-20T00:00:00Z")
      )
    }
  }

  test("Epoch: allows equal start and end time (point epoch)") {
    val instant = Instant.parse("2026-02-20T12:00:00Z")
    val pointEpoch = Epoch(EpochId("point"), instant, instant)
    pointEpoch.durationMs shouldBe 0L
  }

  test("Epoch: contains checks instant within boundaries") {
    val midday = Instant.parse("2026-02-20T12:00:00Z")
    epoch1.contains(midday) shouldBe true

    val outsideDay = Instant.parse("2026-02-21T12:00:00Z")
    epoch1.contains(outsideDay) shouldBe false
  }

  test("Epoch: contains includes boundaries") {
    epoch1.contains(epoch1.startTime) shouldBe true
    epoch1.contains(epoch1.endTime) shouldBe true
  }

  // --- EpochId Tests ---

  test("EpochId: opaque type preserves value") {
    val id = EpochId("test-epoch")
    id.value shouldBe "test-epoch"
    id.show shouldBe "EpochId(test-epoch)"
  }

  // --- TemporalSemantic Tests ---

  test("TemporalSemantic: all three semantics exist") {
    TemporalSemantic.values should contain allOf(
      TemporalSemantic.AsOf,
      TemporalSemantic.Latest,
      TemporalSemantic.Exact
    )
  }

  // --- ContextFiber Join Compatibility ---

  test("REQ-F-TRV-003: same epoch and partition are join-compatible") {
    val fiber1 = ContextFiber(epoch1, "region=US", TemporalSemantic.AsOf)
    val fiber2 = ContextFiber(epoch1, "region=US", TemporalSemantic.AsOf)
    fiber1.isJoinCompatibleWith(fiber2) shouldBe Right(())
  }

  test("REQ-F-TRV-003: different epochs are NOT join-compatible") {
    val fiber1 = ContextFiber(epoch1, "region=US", TemporalSemantic.AsOf)
    val fiber2 = ContextFiber(epoch2, "region=US", TemporalSemantic.AsOf)
    val result = fiber1.isJoinCompatibleWith(fiber2)
    result.isLeft shouldBe true
    result.left.toOption.get shouldBe a[ValidationError.ContextViolation]
    result.left.toOption.get.message should include("Epoch mismatch")
  }

  test("REQ-F-TRV-003: different partitions are NOT join-compatible") {
    val fiber1 = ContextFiber(epoch1, "region=US", TemporalSemantic.AsOf)
    val fiber2 = ContextFiber(epoch1, "region=EU", TemporalSemantic.AsOf)
    val result = fiber1.isJoinCompatibleWith(fiber2)
    result.isLeft shouldBe true
    result.left.toOption.get.message should include("Partition scope mismatch")
  }

  test("REQ-F-TRV-003: universal partition is compatible with any scope") {
    val fiber1 = ContextFiber(epoch1, "*", TemporalSemantic.AsOf)
    val fiber2 = ContextFiber(epoch1, "region=US", TemporalSemantic.AsOf)
    fiber1.isJoinCompatibleWith(fiber2) shouldBe Right(())
    fiber2.isJoinCompatibleWith(fiber1) shouldBe Right(())
  }

  // --- REQ-F-TRV-004: Boundary Detection ---

  test("REQ-F-TRV-004: detects epoch boundary crossing") {
    val fiber1 = ContextFiber(epoch1, "region=US", TemporalSemantic.AsOf)
    val fiber2 = ContextFiber(epoch2, "region=US", TemporalSemantic.AsOf)

    val crossing = fiber1.detectBoundaryCrossing(fiber2)
    crossing shouldBe defined
    crossing.get shouldBe a[ValidationError.BoundaryViolation]
    crossing.get.message should include("Epoch boundary crossing")
  }

  test("REQ-F-TRV-004: detects partition boundary crossing") {
    val fiber1 = ContextFiber(epoch1, "region=US", TemporalSemantic.AsOf)
    val fiber2 = ContextFiber(epoch1, "region=EU", TemporalSemantic.AsOf)

    val crossing = fiber1.detectBoundaryCrossing(fiber2)
    crossing shouldBe defined
    crossing.get.message should include("Partition boundary crossing")
  }

  test("REQ-F-TRV-004: no boundary crossing for same context") {
    val fiber1 = ContextFiber(epoch1, "region=US", TemporalSemantic.AsOf)
    val fiber2 = ContextFiber(epoch1, "region=US", TemporalSemantic.Latest)

    fiber1.detectBoundaryCrossing(fiber2) shouldBe None
  }

  test("REQ-F-TRV-004: universal partition does not trigger boundary crossing") {
    val fiber1 = ContextFiber(epoch1, "*", TemporalSemantic.AsOf)
    val fiber2 = ContextFiber(epoch1, "region=US", TemporalSemantic.AsOf)

    fiber1.detectBoundaryCrossing(fiber2) shouldBe None
  }

  // --- ContextFiber.UniversalPartition ---

  test("UniversalPartition constant is '*'") {
    ContextFiber.UniversalPartition shouldBe "*"
  }
}
