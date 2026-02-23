// Implements: REQ-PDM-01
// Maps CDME types to Spark StructField types.
package com.cdme.spark

import com.cdme.model.types.CdmeType
import com.cdme.model.types.CdmeType._
import com.cdme.model.error.{CdmeError, ValidationError}

/**
 * Maps CDME types to Spark type names (strings representing Spark types).
 * Actual Spark type conversion requires Spark on the classpath (provided scope).
 *
 * Implements: REQ-PDM-01
 * See ADR-006: Spark Integration Strategy
 */
object SparkTypeMapper {

  /** Spark type name representation (avoids hard dependency on Spark classes). */
  final case class SparkType(name: String, nullable: Boolean, metadata: Map[String, String])

  /**
   * Map a CDME type to its Spark type representation.
   */
  def mapType(cdmeType: CdmeType): Either[CdmeError, SparkType] = cdmeType match {
    case IntType       => Right(SparkType("IntegerType", nullable = false, Map.empty))
    case FloatType     => Right(SparkType("DoubleType", nullable = false, Map.empty))
    case StringType    => Right(SparkType("StringType", nullable = false, Map.empty))
    case BooleanType   => Right(SparkType("BooleanType", nullable = false, Map.empty))
    case DateType      => Right(SparkType("DateType", nullable = false, Map.empty))
    case TimestampType => Right(SparkType("TimestampType", nullable = false, Map.empty))

    case DecimalType(precision, scale) =>
      Right(SparkType(s"DecimalType($precision,$scale)", nullable = false, Map.empty))

    case OptionType(inner) =>
      mapType(inner).map(_.copy(nullable = true))

    case ListType(element) =>
      mapType(element).map(e => SparkType(s"ArrayType(${e.name})", nullable = false, Map.empty))

    case ProductType(fields) =>
      val mappedFields = fields.toList.map { case (name, tpe) =>
        mapType(tpe).map(st => (name, st))
      }
      val errors = mappedFields.collect { case Left(e) => e }
      if (errors.nonEmpty) Left(errors.head)
      else {
        val fieldDescs = mappedFields.collect { case Right((n, st)) => s"$n:${st.name}" }.mkString(",")
        Right(SparkType(s"StructType($fieldDescs)", nullable = false, Map.empty))
      }

    case SumType(variants) =>
      // Encode as struct with discriminator field + all variant fields as nullable
      val fieldDescs = variants.toList.flatMap { case (name, tpe) =>
        mapType(tpe).toOption.map(st => s"$name:${st.name}")
      }
      Right(SparkType(s"StructType(_tag:StringType,${fieldDescs.mkString(",")})", nullable = false, Map.empty))

    case RefinementType(base, predicateName, _) =>
      mapType(base).map(_.copy(metadata = Map("refinement" -> predicateName)))

    case SemanticType(base, label) =>
      mapType(base).map(_.copy(metadata = Map("semantic" -> label)))
  }

  /**
   * Map all attributes of an entity to Spark types.
   */
  def mapEntitySchema(
      attributes: Map[String, CdmeType]
  ): Either[CdmeError, Map[String, SparkType]] = {
    val results = attributes.map { case (name, tpe) =>
      mapType(tpe).map(st => name -> st)
    }
    val errors = results.collect { case Left(e) => e }
    if (errors.nonEmpty) Left(errors.head)
    else Right(results.collect { case Right(pair) => pair }.toMap)
  }
}
