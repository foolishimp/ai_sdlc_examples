package com.cdme.compiler.types
// Implements: REQ-F-TYP-004, REQ-F-TYP-003

import com.cdme.model._
import com.cdme.model.types.{CdmeType, SemanticType, RefinementType}
import com.cdme.compiler.{TypeError, TypeUnificationFailure}

/**
 * Declares the subtype relationships between CDME types.
 *
 * The compiler uses this hierarchy to decide whether a type can be
 * implicitly promoted (subtype) or must be explicitly cast.
 *
 * @param subtypeRelations maps each type to its set of direct subtypes
 */
case class TypeHierarchy(subtypeRelations: Map[CdmeType, Set[CdmeType]]) {

  /**
   * Check whether `candidate` is a subtype of `supertype` (directly or transitively).
   */
  def isSubtypeOf(candidate: CdmeType, supertype: CdmeType): Boolean = {
    if (candidate == supertype) return true
    val directSubtypes = subtypeRelations.getOrElse(supertype, Set.empty)
    if (directSubtypes.contains(candidate)) return true
    // Transitive closure: check if candidate is a subtype of any direct subtype
    directSubtypes.exists(sub => isSubtypeOf(candidate, sub))
  }
}

/**
 * The result of a successful type unification.
 *
 * @param resolvedType   the unified type (the more general of the two)
 * @param coercionNeeded true if implicit coercion is required (subtype promotion)
 */
case class UnifiedType(
  resolvedType: CdmeType,
  coercionNeeded: Boolean
)

/**
 * Unifies types at morphism composition boundaries.
 *
 * Rules (in priority order):
 * 1. Exact match -- OK, no coercion.
 * 2. Subtype relationship -- OK, coercion needed.
 * 3. Semantic type unwrapping -- if either side is a [[SemanticType]], compare underlying.
 * 4. Refinement type widening -- a [[RefinementType]] is a subtype of its base.
 * 5. Otherwise -- reject.
 */
object TypeUnifier {

  /**
   * Attempt to unify the codomain type of one morphism with the domain type of the next.
   *
   * @param codomain  the output type of the preceding morphism
   * @param domain    the input type of the following morphism
   * @param hierarchy the declared type hierarchy
   * @return a [[UnifiedType]] on success, or a [[TypeError]] on failure
   */
  def unify(
    codomain: CdmeType,
    domain: CdmeType,
    hierarchy: TypeHierarchy
  ): Either[TypeError, UnifiedType] = {
    // Rule 1: exact match
    if (codomain == domain) {
      return Right(UnifiedType(domain, coercionNeeded = false))
    }

    // Rule 2: subtype check (codomain is subtype of domain)
    if (hierarchy.isSubtypeOf(codomain, domain)) {
      return Right(UnifiedType(domain, coercionNeeded = true))
    }

    // Rule 2b: subtype check (domain is subtype of codomain)
    if (hierarchy.isSubtypeOf(domain, codomain)) {
      return Right(UnifiedType(codomain, coercionNeeded = true))
    }

    // Rule 3: semantic type unwrapping
    val unwrappedCodomain = unwrapSemantic(codomain)
    val unwrappedDomain = unwrapSemantic(domain)
    if ((unwrappedCodomain != codomain || unwrappedDomain != domain) &&
        unwrappedCodomain == unwrappedDomain) {
      return Right(UnifiedType(unwrappedDomain, coercionNeeded = true))
    }

    // Rule 4: refinement type widening
    val widenedCodomain = widenRefinement(codomain)
    val widenedDomain = widenRefinement(domain)
    if (widenedCodomain == widenedDomain) {
      return Right(UnifiedType(widenedDomain, coercionNeeded = true))
    }

    // Check hierarchical compatibility on unwrapped/widened types
    if (hierarchy.isSubtypeOf(widenedCodomain, widenedDomain)) {
      return Right(UnifiedType(widenedDomain, coercionNeeded = true))
    }
    if (hierarchy.isSubtypeOf(widenedDomain, widenedCodomain)) {
      return Right(UnifiedType(widenedCodomain, coercionNeeded = true))
    }

    // Rule 5: reject
    Left(TypeUnificationFailure(
      expected = domain,
      actual = codomain,
      context = s"codomain=$codomain, domain=$domain"
    ))
  }

  /** Unwrap a [[SemanticType]] to its underlying type. */
  private def unwrapSemantic(t: CdmeType): CdmeType = t match {
    case SemanticType(_, underlying) => underlying
    case other => other
  }

  /** Widen a [[RefinementType]] to its base type. */
  private def widenRefinement(t: CdmeType): CdmeType = t match {
    case RefinementType(base, _) => base
    case other => other
  }
}
