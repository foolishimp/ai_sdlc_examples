package com.cdme.model.types

// Implements: REQ-F-TYP-001, REQ-F-TYP-002, REQ-F-TYP-003, REQ-F-TYP-005

/**
 * The CDME type system — a sealed hierarchy that models all data types
 * expressible in the Categorical Data Mapping Engine.
 *
 * The hierarchy supports:
 *  - Primitive types (scalars)
 *  - Sum types (tagged unions / coproducts)
 *  - Product types (records / tuples)
 *  - Refinement types (base type constrained by a predicate)
 *  - Semantic types (named wrappers adding domain meaning to a structural type)
 *  - Option and List type constructors
 *
 * All types are immutable and compose via sealed-trait exhaustive matching.
 */
sealed trait CdmeType

/**
 * A primitive scalar type identified by its [[PrimitiveKind]].
 *
 * @param kind the specific primitive kind (Integer, String, Date, etc.)
 */
case class PrimitiveType(kind: PrimitiveKind) extends CdmeType

/**
 * Enumeration of supported primitive data kinds.
 *
 * Maps one-to-one with Spark SQL atomic types when lowered
 * through the type mapper in `cdme-spark`.
 */
sealed trait PrimitiveKind

case object IntegerKind   extends PrimitiveKind
case object LongKind      extends PrimitiveKind
case object FloatKind     extends PrimitiveKind
case object DoubleKind    extends PrimitiveKind
case object DecimalKind   extends PrimitiveKind
case object StringKind    extends PrimitiveKind
case object BooleanKind   extends PrimitiveKind
case object DateKind      extends PrimitiveKind
case object TimestampKind extends PrimitiveKind

/**
 * A tagged union (coproduct). A value of a [[SumType]] is exactly one
 * of the listed variant types.
 *
 * @param name     human-readable name for this sum type
 * @param variants the alternative types comprising this sum
 */
case class SumType(name: String, variants: List[CdmeType]) extends CdmeType

/**
 * A record (product). A value of a [[ProductType]] contains all of the
 * listed named fields.
 *
 * @param name   human-readable name for this product type
 * @param fields ordered list of named, typed fields
 */
case class ProductType(name: String, fields: List[NamedField]) extends CdmeType

/**
 * A single named field within a [[ProductType]].
 *
 * @param name     field name
 * @param cdmeType field type
 */
case class NamedField(name: String, cdmeType: CdmeType)

/**
 * A refinement type constrains a base type with a [[Predicate]].
 * Values of a refinement type must satisfy the predicate at runtime;
 * violations are routed to the Error Domain as [[com.cdme.model.error.RefinementViolation]].
 *
 * @param baseType  the underlying structural type
 * @param predicate the constraint that values must satisfy
 */
case class RefinementType(baseType: CdmeType, predicate: Predicate) extends CdmeType

/**
 * A semantic type adds domain-specific meaning to a structural type
 * without changing its physical representation. Two semantic types with
 * different names are incompatible even if their underlying types match.
 *
 * Example: `SemanticType("Currency", PrimitiveType(StringKind))` is not
 * unifiable with `SemanticType("Country", PrimitiveType(StringKind))`.
 *
 * @param semanticName domain-specific type name
 * @param underlying   the structural type this wraps
 */
case class SemanticType(semanticName: String, underlying: CdmeType) extends CdmeType

/**
 * An optional value — may or may not be present.
 *
 * @param inner the type of the value when present
 */
case class OptionType(inner: CdmeType) extends CdmeType

/**
 * A homogeneous list of values.
 *
 * @param inner the element type
 */
case class ListType(inner: CdmeType) extends CdmeType
