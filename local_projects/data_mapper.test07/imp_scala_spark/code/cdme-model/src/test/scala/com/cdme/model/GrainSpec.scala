// Validates: REQ-LDM-06, REQ-TRV-02
// Tests for Grain ordering and GrainHierarchy.
package com.cdme.model

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import com.cdme.model.grain.{Grain, GrainHierarchy}

class GrainSpec extends AnyFlatSpec with Matchers with ScalaCheckPropertyChecks {

  "Grain ordering" should "follow Atomic < Daily < Monthly < Quarterly < Yearly" in {
    Grain.Atomic should be < Grain.Daily
    Grain.Daily should be < Grain.Monthly
    Grain.Monthly should be < Grain.Quarterly
    Grain.Quarterly should be < Grain.Yearly
  }

  it should "support equality comparison" in {
    Grain.Atomic.compare(Grain.Atomic) shouldBe 0
    Grain.Daily.compare(Grain.Daily) shouldBe 0
  }

  it should "support reverse comparison" in {
    Grain.Yearly should be > Grain.Atomic
    Grain.Monthly should be > Grain.Daily
  }

  "Custom grain" should "be ordered by its level" in {
    val weekly = Grain.Custom("Weekly", 1)
    weekly.compare(Grain.Daily) shouldBe 0 // same level
    weekly should be > Grain.Atomic
    weekly should be < Grain.Monthly
  }

  it should "support custom names" in {
    val biweekly = Grain.Custom("Biweekly", 1)
    biweekly.name shouldBe "Biweekly"
  }

  "Grain.canAggregate" should "allow coarsening" in {
    Grain.canAggregate(Grain.Atomic, Grain.Daily) shouldBe true
    Grain.canAggregate(Grain.Daily, Grain.Monthly) shouldBe true
    Grain.canAggregate(Grain.Atomic, Grain.Yearly) shouldBe true
  }

  it should "forbid same-grain aggregation" in {
    Grain.canAggregate(Grain.Daily, Grain.Daily) shouldBe false
  }

  it should "forbid refinement (coarse to fine)" in {
    Grain.canAggregate(Grain.Monthly, Grain.Daily) shouldBe false
    Grain.canAggregate(Grain.Yearly, Grain.Atomic) shouldBe false
  }

  "GrainHierarchy.default" should "contain all standard grains" in {
    val hierarchy = GrainHierarchy.default
    hierarchy.grains should have size 5
    hierarchy.grains should contain allOf (Grain.Atomic, Grain.Daily, Grain.Monthly, Grain.Quarterly, Grain.Yearly)
  }

  it should "find path from Atomic to Yearly" in {
    val path = GrainHierarchy.default.findPath(Grain.Atomic, Grain.Yearly)
    path shouldBe defined
    path.get should have size 5
    path.get.head shouldBe Grain.Atomic
    path.get.last shouldBe Grain.Yearly
  }

  it should "find path from Daily to Monthly" in {
    val path = GrainHierarchy.default.findPath(Grain.Daily, Grain.Monthly)
    path shouldBe Some(List(Grain.Daily, Grain.Monthly))
  }

  it should "not find reverse path" in {
    GrainHierarchy.default.findPath(Grain.Yearly, Grain.Atomic) shouldBe None
  }

  it should "return sorted grains" in {
    val sorted = GrainHierarchy.default.sortedGrains
    sorted shouldBe List(Grain.Atomic, Grain.Daily, Grain.Monthly, Grain.Quarterly, Grain.Yearly)
  }

  "Grain ordering" should "be reflexive" in {
    Grain.standardGrains.foreach { g =>
      g.compare(g) shouldBe 0
    }
  }

  it should "be transitive" in {
    val grains = Grain.standardGrains
    for {
      a <- grains
      b <- grains if a < b
      c <- grains if b < c
    } {
      a should be < c
    }
  }
}
