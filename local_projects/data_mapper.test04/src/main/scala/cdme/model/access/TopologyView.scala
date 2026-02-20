// Implements: REQ-F-ACC-005, REQ-F-TRV-007
package cdme.model.access

import cdme.model.category.{Category, MorphismId}
import cdme.model.adjoint.AdjointMorphism

/**
 * A filtered view of a category based on a principal's access rights.
 *
 * Denied morphisms are removed from the topology before path compilation,
 * making them invisible -- they cannot be referenced even by name.
 *
 * @param category  the underlying full category
 * @param principal the requesting principal
 */
final case class TopologyView(
    category: Category,
    principal: Principal
):
  /**
   * The set of accessible morphisms for this principal.
   *
   * A morphism is accessible if:
   *   1. It has no access rules (accessible to all), OR
   *   2. At least one of its access rules permits this principal
   */
  lazy val accessibleMorphisms: Map[MorphismId, AdjointMorphism[?, ?]] =
    category.morphisms.filter { (_, morphism) =>
      morphism.accessRules.isEmpty || morphism.accessRules.exists(_.permits(principal))
    }

  /**
   * The filtered category containing only accessible morphisms.
   * Entities are retained even if all their morphisms are filtered.
   */
  lazy val filteredCategory: Category =
    category.copy(morphisms = accessibleMorphisms)

  /**
   * Check whether a specific morphism is accessible.
   *
   * @param morphismId the morphism to check
   * @return true if accessible
   */
  def isAccessible(morphismId: MorphismId): Boolean =
    accessibleMorphisms.contains(morphismId)

  /**
   * Get the count of morphisms removed by access filtering.
   */
  def filteredOutCount: Int =
    category.morphismCount - accessibleMorphisms.size

object TopologyView:
  /**
   * Create an unfiltered topology view (universal access).
   *
   * @param category the category
   * @return a view with all morphisms accessible
   */
  def unfiltered(category: Category): TopologyView =
    TopologyView(category, Principal.universal)
