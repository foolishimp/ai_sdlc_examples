package com.cdme.adjoint

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

// Validates: REQ-ADJ-04, REQ-ADJ-05, REQ-ADJ-06
class BackwardMetadataSpec extends AnyFlatSpec with Matchers {

  // ── REQ-ADJ-04: Reverse-join table for aggregations ──

  "ReverseJoinTable" should "map group keys to constituent keys (AC-CDME-004)" in {
    val rjt = ReverseJoinTable.fromPairs(List(
      "group1" -> "rec1", "group1" -> "rec2", "group1" -> "rec3",
      "group2" -> "rec4", "group2" -> "rec5"
    ))
    rjt.constituentsFor("group1") shouldBe Set("rec1", "rec2", "rec3")
    rjt.constituentsFor("group2") shouldBe Set("rec4", "rec5")
    rjt.totalConstituents shouldBe 5
  }

  it should "return empty for unknown group key" in {
    val rjt = ReverseJoinTable.empty
    rjt.constituentsFor("unknown") shouldBe Set.empty
  }

  // ── REQ-ADJ-05: Filtered keys ──

  "FilteredKeys" should "reconstruct full input from passed + filtered" in {
    val fk = FilteredKeys(
      passedKeys = Set("a", "b", "c"),
      filteredOutKeys = Set("d", "e")
    )
    fk.fullInputKeys shouldBe Set("a", "b", "c", "d", "e")
  }

  // ── REQ-ADJ-06: Kleisli parent map ──

  "KleisliParentMap" should "map child to parent" in {
    val kpm = KleisliParentMap(Map(
      "child1" -> "parent1",
      "child2" -> "parent1",
      "child3" -> "parent2"
    ))
    kpm.parentFor("child1") shouldBe Some("parent1")
    kpm.parentFor("child3") shouldBe Some("parent2")
    kpm.childrenFor("parent1") shouldBe Set("child1", "child2")
  }
}
