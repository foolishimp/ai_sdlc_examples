package com.cdme.spark.types

// Implements: REQ-F-TYP-001

import com.cdme.model.types._
import org.apache.spark.sql.types._

/**
 * Bidirectional mapping between the CDME type system and Spark SQL types.
 *
 * The forward direction (`toSparkType`) is used when constructing DataFrames
 * from CDME entities. The reverse direction (`fromSparkType`) is used when
 * ingesting external DataFrames back into the CDME domain.
 *
 * Mapping rules:
 *  - [[PrimitiveType]] maps one-to-one to Spark atomic types
 *  - [[ProductType]] maps to [[StructType]] with one [[StructField]] per named field
 *  - [[SumType]] maps to a [[StructType]] with a `_discriminator` string field
 *    followed by nullable variant fields (only one populated per row)
 *  - [[RefinementType]] maps to its base type (the predicate is enforced
 *    separately by the Error Router at runtime)
 *  - [[SemanticType]] maps to its underlying structural type
 *  - [[OptionType]] sets the field's `nullable` flag to true
 *  - [[ListType]] maps to [[ArrayType]]
 */
object SparkTypeMapper {

  /**
   * Convert a CDME type to the corresponding Spark SQL [[DataType]].
   *
   * @param cdmeType the CDME type to convert
   * @return the equivalent Spark SQL data type
   */
  def toSparkType(cdmeType: CdmeType): DataType = cdmeType match {
    case PrimitiveType(kind) => primitiveKindToSpark(kind)

    case ProductType(_, fields) =>
      StructType(fields.map { nf =>
        StructField(nf.name, toSparkType(nf.cdmeType), nullable = false)
      })

    case SumType(_, variants) =>
      // Encode as a struct with a discriminator + nullable variant slots
      val discriminator = StructField("_discriminator", StringType, nullable = false)
      val variantFields = variants.zipWithIndex.map { case (v, i) =>
        StructField(s"_variant_$i", toSparkType(v), nullable = true)
      }
      StructType(discriminator +: variantFields)

    case RefinementType(baseType, _) =>
      // Predicate enforcement happens at runtime; Spark sees only the base type
      toSparkType(baseType)

    case SemanticType(_, underlying) =>
      toSparkType(underlying)

    case OptionType(inner) =>
      // Option is represented as the inner type with nullable = true
      // (the nullable flag is set at the StructField level, not the DataType level)
      toSparkType(inner)

    case ListType(inner) =>
      ArrayType(toSparkType(inner), containsNull = true)
  }

  /**
   * Convert a Spark SQL [[DataType]] to the corresponding CDME type.
   *
   * This is a best-effort reverse mapping. Structural information that only
   * exists in the CDME model (e.g. semantic names, predicates) cannot be
   * recovered from a Spark schema alone.
   *
   * @param sparkType the Spark SQL data type to convert
   * @return the equivalent CDME type
   */
  def fromSparkType(sparkType: DataType): CdmeType = sparkType match {
    case IntegerType   => PrimitiveType(IntegerKind)
    case LongType      => PrimitiveType(LongKind)
    case FloatType     => PrimitiveType(FloatKind)
    case DoubleType    => PrimitiveType(DoubleKind)
    case _: DecimalType => PrimitiveType(DecimalKind)
    case StringType    => PrimitiveType(StringKind)
    case BooleanType   => PrimitiveType(BooleanKind)
    case DateType      => PrimitiveType(DateKind)
    case TimestampType => PrimitiveType(TimestampKind)

    case StructType(fields) =>
      ProductType(
        name = "unnamed_struct",
        fields = fields.toList.map { sf =>
          NamedField(sf.name, fromSparkType(sf.dataType))
        }
      )

    case ArrayType(elementType, _) =>
      ListType(fromSparkType(elementType))

    case _ =>
      // Fallback for unsupported Spark types
      PrimitiveType(StringKind)
  }

  /**
   * Map a [[PrimitiveKind]] to the corresponding Spark SQL atomic [[DataType]].
   */
  private def primitiveKindToSpark(kind: PrimitiveKind): DataType = kind match {
    case IntegerKind   => IntegerType
    case LongKind      => LongType
    case FloatKind     => FloatType
    case DoubleKind    => DoubleType
    case DecimalKind   => DecimalType(38, 18)
    case StringKind    => StringType
    case BooleanKind   => BooleanType
    case DateKind      => DateType
    case TimestampKind => TimestampType
  }
}
