// Implements: REQ-F-ACC-005
package cdme.model.access

final case class Principal(
    id: String,
    roles: Set[String]
) {
  def hasRole(role: String): Boolean = roles.contains(role)
  def isUniversal: Boolean = roles.contains("*")
}

object Principal {
  val universal: Principal = Principal("system", Set("*"))
}
