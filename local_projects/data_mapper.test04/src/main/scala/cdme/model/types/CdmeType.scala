// Implements: REQ-F-TYP-001, REQ-F-TYP-002, REQ-F-TYP-003, REQ-F-TYP-004, REQ-F-TYP-005, REQ-BR-TYP-001
// ADR-002: Sealed ADT for Type System Encoding
package cdme.model.types

import java.time.{LocalDate, Instant}

/** Unique identifier for a semantic type tag. */
opaque type SemanticTag = String
object SemanticTag:
  def apply(value: String): SemanticTag = value
  extension (tag: SemanticTag) def value: String = tag

/**
 * Root of the CDME type system hierarchy.
 *
 * All data types in the logical data model are represented as instances of this
 * sealed trait. No implicit casting is permitted (REQ-BR-TYP-001) -- all type
 * conversions must go through explicit ConversionMorphism instances.
 *
 * ADR-002 mandates a sealed ADT rather than Scala 3 union types because
 * Spark DataFrames require schema-level type information with runtime discriminants.
 */
sealed trait CdmeType:
  /** Human-readable name for diagnostics and lineage. */
  def typeName: String

/**
 * Primitive types supported by CDME.
 *
 * These correspond to the atomic storage types available in physical data systems.
 */
enum PrimitiveKind:
  case StringKind, IntKind, LongKind, DecimalKind, DateKind, TimestampKind, BooleanKind

/**
 * A primitive (atomic) CDME type.
 *
 * @param kind the specific primitive kind
 */
final case class PrimitiveType(kind: PrimitiveKind) extends CdmeType:
  override def typeName: String = kind match
    case PrimitiveKind.StringKind    => "String"
    case PrimitiveKind.IntKind       => "Int"
    case PrimitiveKind.LongKind      => "Long"
    case PrimitiveKind.DecimalKind   => "Decimal"
    case PrimitiveKind.DateKind      => "Date"
    case PrimitiveKind.TimestampKind => "Timestamp"
    case PrimitiveKind.BooleanKind   => "Boolean"

/** Convenience constructors for common primitive types. */
object PrimitiveType:
  val StringType: PrimitiveType    = PrimitiveType(PrimitiveKind.StringKind)
  val IntType: PrimitiveType       = PrimitiveType(PrimitiveKind.IntKind)
  val LongType: PrimitiveType      = PrimitiveType(PrimitiveKind.LongKind)
  val DecimalType: PrimitiveType   = PrimitiveType(PrimitiveKind.DecimalKind)
  val DateType: PrimitiveType      = PrimitiveType(PrimitiveKind.DateKind)
  val TimestampType: PrimitiveType = PrimitiveType(PrimitiveKind.TimestampKind)
  val BooleanType: PrimitiveType   = PrimitiveType(PrimitiveKind.BooleanKind)

/**
 * Tagged union (sum type) with an explicit discriminant field.
 *
 * Each variant is a named CdmeType. The discriminant field name is used for
 * serialisation and physical schema mapping.
 *
 * @param discriminant the name of the tag/discriminant field
 * @param variants     ordered list of (name, type) variants
 */
final case class SumType(
    discriminant: String,
    variants: List[(String, CdmeType)]
) extends CdmeType:
  override def typeName: String =
    variants.map(_._1).mkString(" | ")

/**
 * Product type (named record or positional tuple).
 *
 * Fields are ordered; the Map preserves insertion order via LinkedHashMap semantics
 * in the Scala standard library.
 *
 * @param name   optional name for the product type
 * @param fields ordered mapping of field name to field type
 */
final case class ProductType(
    name: Option[String],
    fields: Vector[(String, CdmeType)]
) extends CdmeType:
  override def typeName: String =
    name.getOrElse(fields.map((n, t) => s"$n: ${t.typeName}").mkString("(", ", ", ")"))

  /** Look up a field type by name. */
  def fieldType(fieldName: String): Option[CdmeType] =
    fields.find(_._1 == fieldName).map(_._2)

/**
 * Refinement type: a base type constrained by a predicate.
 *
 * The predicate is a Scala lambda evaluated at data processing time (ADR-002).
 * The predicateDescription stores a human-readable string for lineage.
 *
 * @param base                 the underlying type being refined
 * @param predicate            runtime predicate function (Any to support erasure)
 * @param predicateDescription human-readable description stored in lineage
 */
final case class RefinementType(
    base: CdmeType,
    predicate: Any => Boolean,
    predicateDescription: String
) extends CdmeType:
  override def typeName: String = s"${base.typeName}{$predicateDescription}"

/**
 * Semantic type: domain-meaningful wrapper over a base type.
 *
 * Uses a SemanticTag for zero-cost abstraction at compile time (ADR-001),
 * with runtime tagging for serialisation.
 *
 * @param base the underlying primitive or compound type
 * @param tag  the semantic domain tag (e.g., "Currency", "ISIN")
 */
final case class SemanticType(
    base: CdmeType,
    tag: SemanticTag
) extends CdmeType:
  override def typeName: String = s"${tag.value}[${base.typeName}]"

/**
 * Optional type wrapper.
 *
 * @param inner the type that may be absent
 */
final case class OptionType(inner: CdmeType) extends CdmeType:
  override def typeName: String = s"Option[${inner.typeName}]"

/**
 * List type wrapper.
 *
 * @param element the element type
 */
final case class ListType(element: CdmeType) extends CdmeType:
  override def typeName: String = s"List[${element.typeName}]"
