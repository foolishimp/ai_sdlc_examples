// Validates: REQ-PDM-01
// Tests for CDME type to Spark type mapping.
package com.cdme.spark

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import com.cdme.model.types.CdmeType
import com.cdme.model.types.CdmeType._

class SparkTypeMapperSpec extends AnyFlatSpec with Matchers {

  "SparkTypeMapper" should "map IntType to IntegerType" in {
    val result = SparkTypeMapper.mapType(IntType)
    result.isRight shouldBe true
    result.toOption.get.name shouldBe "IntegerType"
    result.toOption.get.nullable shouldBe false
  }

  it should "map FloatType to DoubleType" in {
    SparkTypeMapper.mapType(FloatType).toOption.get.name shouldBe "DoubleType"
  }

  it should "map StringType to StringType" in {
    SparkTypeMapper.mapType(StringType).toOption.get.name shouldBe "StringType"
  }

  it should "map BooleanType to BooleanType" in {
    SparkTypeMapper.mapType(BooleanType).toOption.get.name shouldBe "BooleanType"
  }

  it should "map DateType to DateType" in {
    SparkTypeMapper.mapType(DateType).toOption.get.name shouldBe "DateType"
  }

  it should "map TimestampType to TimestampType" in {
    SparkTypeMapper.mapType(TimestampType).toOption.get.name shouldBe "TimestampType"
  }

  it should "map OptionType to nullable" in {
    val result = SparkTypeMapper.mapType(OptionType(IntType))
    result.isRight shouldBe true
    result.toOption.get.nullable shouldBe true
    result.toOption.get.name shouldBe "IntegerType"
  }

  it should "map ListType to ArrayType" in {
    val result = SparkTypeMapper.mapType(ListType(StringType))
    result.isRight shouldBe true
    result.toOption.get.name should include("ArrayType")
  }

  it should "map ProductType to StructType" in {
    val result = SparkTypeMapper.mapType(ProductType(Map("a" -> IntType, "b" -> StringType)))
    result.isRight shouldBe true
    result.toOption.get.name should include("StructType")
  }

  it should "map SemanticType with metadata" in {
    val result = SparkTypeMapper.mapType(SemanticType(FloatType, "Money"))
    result.isRight shouldBe true
    result.toOption.get.metadata should contain("semantic" -> "Money")
  }

  it should "map RefinementType with metadata" in {
    val result = SparkTypeMapper.mapType(RefinementType(IntType, "Positive", "x > 0"))
    result.isRight shouldBe true
    result.toOption.get.metadata should contain("refinement" -> "Positive")
  }

  it should "map DecimalType with precision and scale" in {
    val result = SparkTypeMapper.mapType(DecimalType(18, 2))
    result.isRight shouldBe true
    result.toOption.get.name should include("DecimalType")
  }

  "mapEntitySchema" should "map all attributes" in {
    val attrs = Map("id" -> StringType, "amount" -> FloatType, "count" -> IntType)
    val result = SparkTypeMapper.mapEntitySchema(attrs)
    result.isRight shouldBe true
    result.toOption.get should have size 3
  }

  it should "handle empty attributes" in {
    SparkTypeMapper.mapEntitySchema(Map.empty).isRight shouldBe true
  }
}
