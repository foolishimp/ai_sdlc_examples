// Implements: REQ-F-ACC-005
package cdme.model.access

import cdme.model.category.MorphismId

final case class AccessRule(
    morphismId: MorphismId,
    permittedRoles: Set[String]
) {
  def permits(principal: Principal): Boolean =
    principal.isUniversal || permittedRoles.contains("*") || permittedRoles.exists(principal.roles.contains)
}

object AccessRule {
  def allowAll(morphismId: MorphismId): AccessRule =
    AccessRule(morphismId, Set("*"))
}
