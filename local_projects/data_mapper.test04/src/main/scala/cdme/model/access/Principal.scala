// Implements: REQ-F-ACC-005
package cdme.model.access

/**
 * A principal requesting access to morphisms.
 *
 * Roles are opaque strings (SU-01). Role assignment is external -- CDME only
 * evaluates whether a principal has a role in the morphism's permitted set.
 * No role hierarchy is supported in v1.
 *
 * @param id    unique identifier for the principal
 * @param roles set of role names assigned to this principal
 */
final case class Principal(
    id: String,
    roles: Set[String]
):
  /** Check if this principal has a specific role. */
  def hasRole(role: String): Boolean = roles.contains(role)

  /** Check if this principal is a universal principal (has the wildcard role). */
  def isUniversal: Boolean = roles.contains("*")

object Principal:
  /** A principal with universal access. */
  val universal: Principal = Principal("system", Set("*"))
