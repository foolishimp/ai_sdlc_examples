// Implements: REQ-TYP-06
// Registry of declared subtype relationships in the LDM.
package com.cdme.model.types

/**
 * Nominal subtyping registry for CDME types.
 * Subtype relationships are domain declarations loaded from configuration,
 * separate from Scala's own type system.
 *
 * Implements: REQ-TYP-06
 */
final case class SubtypeRegistry(
    relationships: Map[CdmeType, Set[CdmeType]]
) {

  /**
   * Check if `sub` is declared as a subtype of `sup`.
   * Supports transitive closure: if A <: B and B <: C, then A <: C.
   */
  def isSubtypeOf(sub: CdmeType, sup: CdmeType): Boolean = {
    if (sub == sup) return true
    relationships.get(sup) match {
      case Some(subtypes) =>
        subtypes.contains(sub) || subtypes.exists(mid => isSubtypeOf(sub, mid))
      case None => false
    }
  }
}

object SubtypeRegistry {
  val empty: SubtypeRegistry = SubtypeRegistry(Map.empty)
}
