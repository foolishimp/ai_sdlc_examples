// Validates: REQ-PDM-01, REQ-PDM-04
// Tests for the PDM binder API.
package com.cdme.api

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class PdmBinderSpec extends AnyFlatSpec with Matchers {

  "PdmBinder" should "build with valid format bindings" in {
    val result = PdmBinder()
      .bindEntity("trade", "parquet", "/data/trades")
      .bindEntity("position", "delta", "/data/positions")
      .build()

    result.isRight shouldBe true
    result.toOption.get.bindings should have size 2
  }

  it should "reject invalid format" in {
    val result = PdmBinder()
      .bindEntity("trade", "xml", "/data/trades")
      .build()

    result.isLeft shouldBe true
  }

  it should "support lookups" in {
    val result = PdmBinder()
      .bindEntity("trade", "parquet", "/data/trades")
      .withLookup("rates", "csv", "/data/rates.csv", "v1.0")
      .build()

    result.isRight shouldBe true
    result.toOption.get.lookups should have size 1
    result.toOption.get.lookups("rates").version shouldBe "v1.0"
  }

  it should "support options on bindings" in {
    val result = PdmBinder()
      .bindEntity("trade", "csv", "/data/trades.csv", Map("header" -> "true", "delimiter" -> "|"))
      .build()

    result.isRight shouldBe true
    result.toOption.get.bindings("trade").options should contain("header" -> "true")
  }

  it should "build empty PDM" in {
    val result = PdmBinder().build()
    result.isRight shouldBe true
    result.toOption.get.bindings shouldBe empty
  }

  it should "accept multiple formats" in {
    val validFormats = List("parquet", "csv", "json", "delta", "iceberg", "jdbc", "avro")
    validFormats.foreach { fmt =>
      val result = PdmBinder().bindEntity("e", fmt, "/path").build()
      result.isRight shouldBe true
    }
  }
}
