// Implements: REQ-F-TYP-001, REQ-F-TYP-002, REQ-F-TYP-003, REQ-F-TYP-004, REQ-F-TYP-005, REQ-BR-TYP-001
// ADR-002: Sealed ADT for Type System Encoding
package cdme.model.types

import java.time.{LocalDate, Instant}

case class SemanticTag(value: String)

sealed trait CdmeType {
  def typeName: String
}

sealed trait PrimitiveKind
object PrimitiveKind {
  case object StringKind extends PrimitiveKind
  case object IntKind extends PrimitiveKind
  case object LongKind extends PrimitiveKind
  case object DecimalKind extends PrimitiveKind
  case object DateKind extends PrimitiveKind
  case object TimestampKind extends PrimitiveKind
  case object BooleanKind extends PrimitiveKind

  val values: List[PrimitiveKind] = List(StringKind, IntKind, LongKind, DecimalKind, DateKind, TimestampKind, BooleanKind)
}

final case class PrimitiveType(kind: PrimitiveKind) extends CdmeType {
  override def typeName: String = kind match {
    case PrimitiveKind.StringKind    => "String"
    case PrimitiveKind.IntKind       => "Int"
    case PrimitiveKind.LongKind      => "Long"
    case PrimitiveKind.DecimalKind   => "Decimal"
    case PrimitiveKind.DateKind      => "Date"
    case PrimitiveKind.TimestampKind => "Timestamp"
    case PrimitiveKind.BooleanKind   => "Boolean"
  }
}

object PrimitiveType {
  val StringType: PrimitiveType    = PrimitiveType(PrimitiveKind.StringKind)
  val IntType: PrimitiveType       = PrimitiveType(PrimitiveKind.IntKind)
  val LongType: PrimitiveType      = PrimitiveType(PrimitiveKind.LongKind)
  val DecimalType: PrimitiveType   = PrimitiveType(PrimitiveKind.DecimalKind)
  val DateType: PrimitiveType      = PrimitiveType(PrimitiveKind.DateKind)
  val TimestampType: PrimitiveType = PrimitiveType(PrimitiveKind.TimestampKind)
  val BooleanType: PrimitiveType   = PrimitiveType(PrimitiveKind.BooleanKind)
}

final case class SumType(
    discriminant: String,
    variants: List[(String, CdmeType)]
) extends CdmeType {
  override def typeName: String =
    variants.map(_._1).mkString(" | ")
}

final case class ProductType(
    name: Option[String],
    fields: Vector[(String, CdmeType)]
) extends CdmeType {
  override def typeName: String =
    name.getOrElse(fields.map { case (n, t) => s"$n: ${t.typeName}" }.mkString("(", ", ", ")"))

  def fieldType(fieldName: String): Option[CdmeType] =
    fields.find(_._1 == fieldName).map(_._2)
}

final case class RefinementType(
    base: CdmeType,
    predicate: Any => Boolean,
    predicateDescription: String
) extends CdmeType {
  override def typeName: String = s"${base.typeName}{$predicateDescription}"
}

final case class SemanticType(
    base: CdmeType,
    tag: SemanticTag
) extends CdmeType {
  override def typeName: String = s"${tag.value}[${base.typeName}]"
}

final case class OptionType(inner: CdmeType) extends CdmeType {
  override def typeName: String = s"Option[${inner.typeName}]"
}

final case class ListType(element: CdmeType) extends CdmeType {
  override def typeName: String = s"List[${element.typeName}]"
}
