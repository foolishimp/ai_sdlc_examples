package com.cdme.model

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

// Validates: REQ-LDM-06
class GrainSpec extends AnyFlatSpec with Matchers {

  "Grain" should "have correct ordering (Atomic < Daily < Monthly < Yearly)" in {
    Grain.Atomic.level should be < Grain.Daily.level
    Grain.Daily.level should be < Grain.Monthly.level
    Grain.Monthly.level should be < Grain.Yearly.level
  }

  it should "correctly identify compatible grains" in {
    Grain.isCompatible(Grain.Atomic, Grain.Atomic) shouldBe true
    Grain.isCompatible(Grain.Daily, Grain.Daily) shouldBe true
    Grain.isCompatible(Grain.Atomic, Grain.Daily) shouldBe false
  }

  it should "correctly identify when aggregation is needed" in {
    Grain.requiresAggregation(Grain.Atomic, Grain.Daily) shouldBe true
    Grain.requiresAggregation(Grain.Daily, Grain.Monthly) shouldBe true
    Grain.requiresAggregation(Grain.Daily, Grain.Daily) shouldBe false
    Grain.requiresAggregation(Grain.Monthly, Grain.Daily) shouldBe false
  }

  it should "support custom grains" in {
    val weekly = Grain.Custom("Weekly", 1)  // same level as Daily
    Grain.isCompatible(weekly, Grain.Daily) shouldBe true
    Grain.requiresAggregation(Grain.Atomic, weekly) shouldBe true
  }

  it should "support ordering" in {
    val grains = List(Grain.Yearly, Grain.Atomic, Grain.Monthly, Grain.Daily)
    grains.sorted(Grain.ordering) shouldBe List(Grain.Atomic, Grain.Daily, Grain.Monthly, Grain.Yearly)
  }
}
