// Validates: RIC-SKW-01
// Tests for skew detection and salted join strategies.
package com.cdme.spark

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SkewMitigatorSpec extends AnyFlatSpec with Matchers {

  "SkewMitigator.detectSkew" should "detect skewed distribution" in {
    val frequencies = Map("key1" -> 1000L, "key2" -> 1L, "key3" -> 1L)
    SkewMitigator.detectSkew(frequencies, 10.0) shouldBe true
  }

  it should "not flag uniform distribution" in {
    val frequencies = Map("key1" -> 100L, "key2" -> 100L, "key3" -> 100L)
    SkewMitigator.detectSkew(frequencies, 10.0) shouldBe false
  }

  it should "handle empty distribution" in {
    SkewMitigator.detectSkew(Map.empty, 10.0) shouldBe false
  }

  it should "handle single key" in {
    // Single key: max/avg = 1, which is not > threshold
    SkewMitigator.detectSkew(Map("key1" -> 100L), 10.0) shouldBe false
  }

  "SkewMitigator.generateSalts" should "generate correct number of salts" in {
    SkewMitigator.generateSalts(10) should have size 10
    SkewMitigator.generateSalts(1) should have size 1
  }

  it should "start from 0" in {
    SkewMitigator.generateSalts(3) shouldBe Seq(0, 1, 2)
  }

  "SkewMitigator.saltedKey" should "produce deterministic salted keys" in {
    SkewMitigator.saltedKey("key1", 0) shouldBe "key1_salt_0"
    SkewMitigator.saltedKey("key1", 5) shouldBe "key1_salt_5"
  }

  it should "produce different keys for different salts" in {
    SkewMitigator.saltedKey("key1", 0) should not be SkewMitigator.saltedKey("key1", 1)
  }

  "PhysicalBindingFunctor.validateTarget" should "accept parquet format" in {
    val target = com.cdme.compiler.PhysicalTarget("parquet", "/data/table", Map.empty)
    PhysicalBindingFunctor.validateTarget(target) shouldBe Right(())
  }

  it should "accept csv format" in {
    val target = com.cdme.compiler.PhysicalTarget("csv", "/data/file.csv", Map.empty)
    PhysicalBindingFunctor.validateTarget(target) shouldBe Right(())
  }

  it should "reject unknown format" in {
    val target = com.cdme.compiler.PhysicalTarget("xml", "/data/file.xml", Map.empty)
    PhysicalBindingFunctor.validateTarget(target).isLeft shouldBe true
  }
}
