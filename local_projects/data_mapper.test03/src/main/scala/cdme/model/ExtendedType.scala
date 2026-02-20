package cdme.model

// Implements: REQ-F-TYP-001, REQ-F-TYP-002, REQ-F-TYP-005, REQ-F-TYP-007

/** Extended type system supporting primitives, sum types, product types, and refinement types.
  *
  * No implicit casting is permitted (REQ-F-TYP-005). All type changes must be
  * expressed as explicit morphisms.
  */
sealed trait ExtendedType

object ExtendedType:

  // Primitive types
  case object StringType extends ExtendedType
  case object IntType extends ExtendedType
  case object LongType extends ExtendedType
  case class DecimalType(precision: Int, scale: Int) extends ExtendedType
  case object DateType extends ExtendedType
  case object TimestampType extends ExtendedType
  case object BooleanType extends ExtendedType

  // Sum type (tagged union): A | B
  case class SumType(alternatives: List[ExtendedType]) extends ExtendedType

  // Product type (tuple/record): (A, B, C)
  case class ProductType(fields: List[(String, ExtendedType)]) extends ExtendedType

  // Refinement type: Type + Predicate (REQ-F-TYP-002)
  case class RefinementType(base: ExtendedType, predicate: Predicate) extends ExtendedType

  // Semantic types (REQ-F-TYP-007) - optional enrichment
  case class SemanticType(base: ExtendedType, semanticTag: String) extends ExtendedType

/** A predicate for refinement types. */
sealed trait Predicate
object Predicate:
  case class GreaterThan(value: BigDecimal) extends Predicate
  case class LessThan(value: BigDecimal) extends Predicate
  case class Between(min: BigDecimal, max: BigDecimal) extends Predicate
  case class NonEmpty() extends Predicate
  case class Regex(pattern: String) extends Predicate
  case class Custom(name: String, description: String) extends Predicate
