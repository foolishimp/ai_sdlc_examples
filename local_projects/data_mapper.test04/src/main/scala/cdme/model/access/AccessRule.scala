// Implements: REQ-F-ACC-005
package cdme.model.access

import cdme.model.category.MorphismId

/**
 * An access control rule on a morphism.
 *
 * RBAC is opt-in per morphism: morphisms without access rules are accessible
 * to all principals. When access rules are present, only principals with at
 * least one matching role are permitted.
 *
 * @param morphismId    the morphism this rule applies to
 * @param permittedRoles set of role names that may access the morphism
 */
final case class AccessRule(
    morphismId: MorphismId,
    permittedRoles: Set[String]
):
  /**
   * Check whether a principal is permitted by this rule.
   *
   * @param principal the requesting principal
   * @return true if the principal has at least one permitted role
   */
  def permits(principal: Principal): Boolean =
    permittedRoles.exists(principal.roles.contains)

object AccessRule:
  /**
   * Create an access rule allowing all roles.
   *
   * @param morphismId the morphism identifier
   * @return an access rule with the universal wildcard role
   */
  def allowAll(morphismId: MorphismId): AccessRule =
    AccessRule(morphismId, Set("*"))
