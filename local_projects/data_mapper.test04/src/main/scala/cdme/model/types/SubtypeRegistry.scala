// Implements: REQ-F-TYP-004, REQ-F-TYP-005, REQ-BR-TYP-001
// ADR-002: Nominal subtyping via declared SubtypeRegistry
package cdme.model.types

import cdme.model.error.ValidationError

/**
 * Registry for declared subtype relationships between CDME types.
 *
 * Subtyping in CDME is nominal (not structural) per design decision SA-01.
 * All subtype relationships must be explicitly declared. No implicit casting
 * is permitted (REQ-BR-TYP-001).
 *
 * The registry is immutable; adding a relationship produces a new registry instance.
 *
 * @param relationships set of (subtype, supertype) pairs
 */
final case class SubtypeRegistry(
    relationships: Set[(CdmeType, CdmeType)]
):
  /**
   * Check whether `sub` is a declared subtype of `sup`.
   *
   * @param sub candidate subtype
   * @param sup candidate supertype
   * @return true if the relationship is declared (directly or transitively)
   */
  def isSubtypeOf(sub: CdmeType, sup: CdmeType): Boolean =
    if sub == sup then true
    else
      // Direct relationship
      relationships.contains((sub, sup)) ||
      // Transitive: sub <: mid <: sup
      relationships
        .filter(_._1 == sub)
        .exists((_, mid) => isSubtypeOf(mid, sup))

  /**
   * Declare a new subtype relationship.
   *
   * @param sub  the subtype
   * @param sup  the supertype
   * @return Right(updatedRegistry) or Left if cycle detected
   */
  def declare(sub: CdmeType, sup: CdmeType): Either[ValidationError, SubtypeRegistry] =
    if isSubtypeOf(sup, sub) then
      Left(ValidationError.TypeMismatch(
        s"Declaring ${sub.typeName} <: ${sup.typeName} would create a cycle"
      ))
    else
      Right(copy(relationships = relationships + ((sub, sup))))

  /**
   * Find all declared supertypes of the given type.
   *
   * @param t the type to query
   * @return set of all supertypes (direct and transitive)
   */
  def supertypesOf(t: CdmeType): Set[CdmeType] =
    val direct = relationships.filter(_._1 == t).map(_._2)
    direct ++ direct.flatMap(supertypesOf)

object SubtypeRegistry:
  /** An empty registry with no declared relationships. */
  val empty: SubtypeRegistry = SubtypeRegistry(Set.empty)
