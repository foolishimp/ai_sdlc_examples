package com.cdme.model

// Implements: REQ-TYP-01 (Extended Type System), REQ-TYP-02 (Refinement Types), REQ-TYP-07 (Semantic Types)
sealed trait SemanticType

object SemanticType {
  case object IntType extends SemanticType
  case object LongType extends SemanticType
  case object FloatType extends SemanticType
  case object DoubleType extends SemanticType
  case object StringType extends SemanticType
  case object BooleanType extends SemanticType
  case object DateType extends SemanticType
  case object TimestampType extends SemanticType

  case class OptionType(inner: SemanticType) extends SemanticType
  case class EitherType(left: SemanticType, right: SemanticType) extends SemanticType
  case class ProductType(fields: Map[String, SemanticType]) extends SemanticType
  case class ListType(inner: SemanticType) extends SemanticType

  // Implements: REQ-TYP-02
  case class RefinementType(base: SemanticType, predicateName: String) extends SemanticType

  // Implements: REQ-TYP-07
  case class DomainType(name: String, base: SemanticType) extends SemanticType

  /** Check structural type compatibility for composition (REQ-TYP-06) */
  def isCompatible(codomain: SemanticType, domain: SemanticType): Boolean = (codomain, domain) match {
    case (a, b) if a == b => true
    case (OptionType(a), OptionType(b)) => isCompatible(a, b)
    case (a, OptionType(b)) => isCompatible(a, b)  // T is subtype of Option[T]
    case (ListType(a), ListType(b)) => isCompatible(a, b)
    case (DomainType(_, base), other) => isCompatible(base, other)
    case (other, DomainType(_, base)) => isCompatible(other, base)
    case (ProductType(fa), ProductType(fb)) =>
      fb.forall { case (k, v) => fa.get(k).exists(isCompatible(_, v)) }
    case _ => false
  }
}
