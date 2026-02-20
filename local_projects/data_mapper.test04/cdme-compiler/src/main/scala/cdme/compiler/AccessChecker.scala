// Implements: REQ-F-ACC-005
package cdme.compiler

import cdme.model.category.{Category, MorphismId}
import cdme.model.access.{Principal, TopologyView, AccessRule}
import cdme.model.error.ValidationError

/**
 * RBAC topology filtering for path compilation.
 *
 * Evaluates access control during path validation (definition time).
 * Denied morphisms are removed from the topology so they cannot be
 * referenced even by name.
 */
object AccessChecker {

  /**
   * Create a filtered topology view for a given principal.
   *
   * @param category  the full category
   * @param principal the requesting principal
   * @return the filtered topology view
   */
  def filterTopology(
      category: Category,
      principal: Principal
  ): TopologyView =
    TopologyView(category, principal)

  /**
   * Check whether a specific morphism is accessible to a principal.
   *
   * @param morphismId the morphism to check
   * @param category   the category containing the morphism
   * @param principal  the requesting principal
   * @return Right(()) if accessible, Left(error) if denied
   */
  def checkAccess(
      morphismId: MorphismId,
      category: Category,
      principal: Principal
  ): Either[ValidationError, Unit] =
    category.morphisms.get(morphismId) match {
      case None =>
        Left(ValidationError.MorphismNotFound(
          morphismId.value,
          s"Morphism ${morphismId.show} not found"
        ))
      case Some(morphism) =>
        if (morphism.accessRules.isEmpty)
          Right(()) // No rules = accessible to all
        else if (morphism.accessRules.exists(_.permits(principal)))
          Right(())
        else
          Left(ValidationError.AccessDenied(
            morphismId.value,
            principal.id,
            s"Principal '${principal.id}' denied access to morphism '${morphismId.show}'"
          ))
    }
}
