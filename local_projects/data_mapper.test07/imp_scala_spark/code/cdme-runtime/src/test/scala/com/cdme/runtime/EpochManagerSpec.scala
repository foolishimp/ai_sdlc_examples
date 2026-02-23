// Validates: REQ-SHF-01, REQ-TRV-03
// Tests for epoch management.
package com.cdme.runtime

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.time.Instant

class EpochManagerSpec extends AnyFlatSpec with Matchers {

  val epoch1: Epoch = Epoch("ep1", Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-01-02T00:00:00Z"))
  val epoch2: Epoch = Epoch("ep2", Instant.parse("2024-01-02T00:00:00Z"), Instant.parse("2024-01-03T00:00:00Z"))
  val overlapping: Epoch = Epoch("ep3", Instant.parse("2024-01-01T12:00:00Z"), Instant.parse("2024-01-02T12:00:00Z"))

  "EpochManager.validateEpochCompatibility" should "accept same epoch" in {
    EpochManager.validateEpochCompatibility(epoch1, epoch1) shouldBe Right(())
  }

  it should "accept overlapping epochs" in {
    EpochManager.validateEpochCompatibility(epoch1, overlapping) shouldBe Right(())
  }

  it should "reject non-overlapping epochs" in {
    EpochManager.validateEpochCompatibility(epoch1, epoch2).isLeft shouldBe true
  }

  "EpochManager.epochsOverlap" should "detect overlap" in {
    EpochManager.epochsOverlap(epoch1, overlapping) shouldBe true
  }

  it should "detect non-overlap" in {
    EpochManager.epochsOverlap(epoch1, epoch2) shouldBe false
  }

  "EpochManager.createEpoch" should "create valid epoch" in {
    val result = EpochManager.createEpoch(
      "test", Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-01-02T00:00:00Z")
    )
    result.isRight shouldBe true
  }

  it should "reject invalid epoch (start >= end)" in {
    val result = EpochManager.createEpoch(
      "bad", Instant.parse("2024-01-02T00:00:00Z"), Instant.parse("2024-01-01T00:00:00Z")
    )
    result.isLeft shouldBe true
  }

  it should "reject zero-length epoch (start == end)" in {
    val t = Instant.parse("2024-01-01T00:00:00Z")
    EpochManager.createEpoch("zero", t, t).isLeft shouldBe true
  }
}
