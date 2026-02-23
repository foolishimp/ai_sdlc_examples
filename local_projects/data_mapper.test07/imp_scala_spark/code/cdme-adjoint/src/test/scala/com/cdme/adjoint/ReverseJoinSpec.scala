// Validates: REQ-ADJ-04, REQ-ADJ-05, REQ-ACC-03
// Tests for reverse-join capture and filtered keys capture.
package com.cdme.adjoint

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ReverseJoinSpec extends AnyFlatSpec with Matchers {

  "ReverseJoinCapture" should "start empty" in {
    val rj = ReverseJoinCapture.empty
    rj.groupCount shouldBe 0
    rj.totalInputKeys shouldBe 0
  }

  it should "capture individual contributions" in {
    val rj = ReverseJoinCapture.empty
      .capture("group1", "key1")
      .capture("group1", "key2")
      .capture("group2", "key3")

    rj.groupCount shouldBe 2
    rj.lookup("group1") shouldBe Right(Set("key1", "key2"))
    rj.lookup("group2") shouldBe Right(Set("key3"))
  }

  it should "capture batch contributions" in {
    val rj = ReverseJoinCapture.empty
      .captureBatch("group1", Set("k1", "k2", "k3"))

    rj.lookup("group1") shouldBe Right(Set("k1", "k2", "k3"))
    rj.totalInputKeys shouldBe 3
  }

  it should "return error for unknown group key" in {
    val rj = ReverseJoinCapture.empty
    rj.lookup("unknown").isLeft shouldBe true
  }

  it should "accumulate across multiple captures" in {
    val rj = ReverseJoinCapture.empty
      .capture("g1", "k1")
      .captureBatch("g1", Set("k2", "k3"))

    rj.lookup("g1") shouldBe Right(Set("k1", "k2", "k3"))
  }

  "FilteredKeysCapture" should "start empty" in {
    val fc = FilteredKeysCapture.empty
    fc.totalKeys shouldBe 0
    fc.passedKeys shouldBe empty
    fc.filteredKeys shouldBe empty
  }

  it should "record passed keys" in {
    val fc = FilteredKeysCapture.empty.record("k1", passed = true)
    fc.passedKeys should contain("k1")
    fc.filteredKeys should not contain "k1"
  }

  it should "record filtered keys" in {
    val fc = FilteredKeysCapture.empty.record("k1", passed = false)
    fc.filteredKeys should contain("k1")
    fc.passedKeys should not contain "k1"
  }

  it should "track total keys" in {
    val fc = FilteredKeysCapture.empty
      .record("k1", passed = true)
      .record("k2", passed = false)
      .record("k3", passed = true)

    fc.totalKeys shouldBe 3
    fc.passedKeys should have size 2
    fc.filteredKeys should have size 1
  }
}
