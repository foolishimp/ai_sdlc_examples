// Implements: REQ-LDM-05
// Access control metadata for morphisms and entities.
package com.cdme.model

/**
 * Role-based access control metadata.
 * Morphisms with restricted access appear non-existent to unauthorized principals.
 */
final case class AccessControl(
    allowedRoles: Set[String],
    deniedRoles: Set[String]
) {
  def isAccessible(principalRoles: Set[String]): Boolean = {
    val notDenied = deniedRoles.intersect(principalRoles).isEmpty
    val allowed   = allowedRoles.isEmpty || allowedRoles.intersect(principalRoles).nonEmpty
    notDenied && allowed
  }
}

object AccessControl {
  val open: AccessControl = AccessControl(Set.empty, Set.empty)
}
