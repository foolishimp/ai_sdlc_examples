// Implements: REQ-F-ACC-005, REQ-F-TRV-007
package cdme.model.access

import cdme.model.category.{Category, MorphismId}
import cdme.model.adjoint.AdjointMorphism

final case class TopologyView(
    category: Category,
    principal: Principal
) {
  lazy val accessibleMorphisms: Map[MorphismId, AdjointMorphism[_, _]] =
    category.morphisms.filter { case (_, morphism) =>
      morphism.accessRules.isEmpty || morphism.accessRules.exists(_.permits(principal))
    }

  lazy val filteredCategory: Category =
    category.copy(morphisms = accessibleMorphisms)

  def isAccessible(morphismId: MorphismId): Boolean =
    accessibleMorphisms.contains(morphismId)

  def filteredOutCount: Int =
    category.morphismCount - accessibleMorphisms.size
}

object TopologyView {
  def unfiltered(category: Category): TopologyView =
    TopologyView(category, Principal.universal)
}
