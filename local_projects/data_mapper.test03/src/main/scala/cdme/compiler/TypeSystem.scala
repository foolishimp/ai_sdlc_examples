package cdme.compiler

// Implements: REQ-F-TYP-001, REQ-F-TYP-002, REQ-F-TYP-005, REQ-F-TYP-006, REQ-F-TYP-007

import cdme.model.{ExtendedType, Predicate}
import cdme.model.ExtendedType.*

/** Type system implementing type unification and refinement validation.
  *
  * No implicit casting permitted (REQ-F-TYP-005).
  * Composition requires exact type match or subtype relationship (REQ-F-TYP-006).
  */
object TypeSystem:

  sealed trait TypeError
  case class TypeMismatch(expected: ExtendedType, actual: ExtendedType) extends TypeError
  case class IncompatibleSemanticTypes(a: String, b: String) extends TypeError
  case class RefinementViolation(base: ExtendedType, predicate: Predicate) extends TypeError

  /** Unify two types for morphism composition (REQ-F-TYP-006).
    *
    * Composition is valid if:
    * 1. Codomain of f equals domain of g (exact match), or
    * 2. Codomain of f is a subtype of domain of g
    * Otherwise, composition is rejected.
    */
  def unify(a: ExtendedType, b: ExtendedType): Either[TypeError, ExtendedType] =
    if a == b then Right(a)
    else if isSubtypeOf(a, b) then Right(b)
    else Left(TypeMismatch(expected = b, actual = a))

  /** Check subtype relationship.
    *
    * Subtyping rules:
    * - RefinementType(base, pred) <: base
    * - SemanticType(base, tag) <: base
    * - SumType: A <: (A | B)
    */
  def isSubtypeOf(sub: ExtendedType, sup: ExtendedType): Boolean =
    (sub, sup) match
      case (RefinementType(base, _), _) => base == sup || isSubtypeOf(base, sup)
      case (SemanticType(base, _), _) if base == sup => true
      case (_, SumType(alts)) => alts.exists(alt => sub == alt || isSubtypeOf(sub, alt))
      case _ => false

  /** Validate semantic type compatibility (REQ-F-TYP-007).
    * Operations between incompatible semantic types are rejected.
    */
  def checkSemanticCompatibility(a: ExtendedType, b: ExtendedType): Either[TypeError, Unit] =
    (a, b) match
      case (SemanticType(_, tagA), SemanticType(_, tagB)) if tagA != tagB =>
        Left(IncompatibleSemanticTypes(tagA, tagB))
      case _ => Right(())
