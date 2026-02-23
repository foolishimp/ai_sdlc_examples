// Implements: REQ-TYP-01, REQ-TYP-02, REQ-TYP-07
// The CDME type system as a sealed ADT hierarchy.
package com.cdme.model.types

/**
 * Sealed ADT representing the CDME type system.
 * Enables exhaustive pattern matching (ADR-001).
 *
 * Implements: REQ-TYP-01
 */
sealed trait CdmeType

object CdmeType {
  // --- Primitive types ---
  case object IntType       extends CdmeType
  case object FloatType     extends CdmeType
  case object StringType    extends CdmeType
  case object BooleanType   extends CdmeType
  case object DateType      extends CdmeType
  case object TimestampType extends CdmeType

  // --- Parameterised types ---
  /** Nullable wrapper. Implements: REQ-TYP-01 */
  final case class DecimalType(precision: Int, scale: Int) extends CdmeType

  /** Nullable wrapper. Implements: REQ-TYP-01 */
  final case class OptionType(inner: CdmeType) extends CdmeType

  /** Ordered collection. Implements: REQ-TYP-01 */
  final case class ListType(element: CdmeType) extends CdmeType

  /** Named product type (record). Implements: REQ-TYP-01 */
  final case class ProductType(fields: Map[String, CdmeType]) extends CdmeType

  /** Tagged union type. Implements: REQ-TYP-01 */
  final case class SumType(variants: Map[String, CdmeType]) extends CdmeType

  /**
   * Refinement type: base type + predicate for data quality enforcement.
   * Implements: REQ-TYP-02
   */
  final case class RefinementType(
      base: CdmeType,
      predicateName: String,
      predicateExpr: String
  ) extends CdmeType

  /**
   * Semantic type: base type + semantic label to prevent category errors.
   * Implements: REQ-TYP-07
   */
  final case class SemanticType(
      base: CdmeType,
      semanticLabel: String
  ) extends CdmeType

  /** Check if a type is a primitive (non-parameterised). */
  def isPrimitive(t: CdmeType): Boolean = t match {
    case IntType | FloatType | StringType | BooleanType | DateType | TimestampType => true
    case _: DecimalType => true
    case _ => false
  }
}
