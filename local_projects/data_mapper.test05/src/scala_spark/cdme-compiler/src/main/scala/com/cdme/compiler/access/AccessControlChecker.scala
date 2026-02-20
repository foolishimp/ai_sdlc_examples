package com.cdme.compiler.access
// Implements: REQ-F-LDM-006

import com.cdme.model._
import com.cdme.model.entity.{AccessControlList, Role}
import com.cdme.model.morphism.{StructuralMorphism, ComputationalMorphism, AlgebraicMorphism}
import com.cdme.model.graph.LdmGraph
import com.cdme.compiler.{AccessError, AccessDeniedError}

/**
 * A security principal with a set of assigned roles.
 *
 * Used by the compiler to check whether a principal may traverse
 * each morphism in a path.
 *
 * @param roles the roles assigned to this principal
 */
case class Principal(roles: Set[Role])

/**
 * Enforces role-based access control on morphisms.
 *
 * Access control is checked at compile time. If a morphism carries an ACL,
 * the principal must possess at least one of the required roles. Morphisms
 * without an ACL are accessible to all principals.
 */
object AccessControlChecker {

  /**
   * Check whether the principal may traverse the given morphism.
   *
   * @param morphism  the morphism to check
   * @param principal the security principal
   * @return Unit on success, or an [[AccessError]] if denied
   */
  def check(morphism: Morphism, principal: Principal): Either[AccessError, Unit] = {
    val acl = morphismAcl(morphism)
    acl match {
      case None =>
        // No ACL => open access
        Right(())
      case Some(accessControlList) =>
        val requiredRoles = accessControlList.allowedRoles
        if (requiredRoles.isEmpty || principal.roles.exists(requiredRoles.contains)) {
          Right(())
        } else {
          Left(AccessDeniedError(
            morphismId = morphismIdValue(morphism),
            requiredRoles = requiredRoles,
            principalRoles = principal.roles
          ))
        }
    }
  }

  /**
   * Return a filtered copy of the graph with inaccessible morphisms removed.
   *
   * This is useful for "what can I see?" queries: the principal gets a view
   * of the graph containing only the morphisms they are permitted to traverse.
   *
   * @param graph     the full LDM graph
   * @param principal the security principal
   * @return a graph with only accessible morphisms
   */
  def filterGraph(graph: LdmGraph, principal: Principal): LdmGraph = {
    val accessibleEdges = graph.edges.filter { case (_, morphism) =>
      check(morphism, principal).isRight
    }
    LdmGraph(
      nodes = graph.nodes,
      edges = accessibleEdges,
      version = graph.version
    )
  }

  // ---------------------------------------------------------------------------
  // Private helpers
  // ---------------------------------------------------------------------------

  private def morphismAcl(m: Morphism): Option[AccessControlList] = m match {
    case s: StructuralMorphism     => s.acl
    case c: ComputationalMorphism  => c.acl
    case a: AlgebraicMorphism      => a.acl
  }

  private def morphismIdValue(m: Morphism): String = m match {
    case s: StructuralMorphism     => s.id.value
    case c: ComputationalMorphism  => c.id.value
    case a: AlgebraicMorphism      => a.id.value
  }
}
