// Implements: REQ-LDM-01
// Category: the top-level container of entities and morphisms.
package com.cdme.model

import com.cdme.model.grain.GrainHierarchy

/**
 * A Category is a named container of entities (Objects) and morphisms (Arrows).
 * The LDM is a Category; the PDM is a separate Category; the functor maps between them.
 *
 * Implements: REQ-LDM-01
 */
final case class Category(
    name: String,
    entities: Map[EntityId, Entity],
    morphisms: Map[MorphismId, Morphism],
    grainHierarchy: GrainHierarchy
) {

  /** Look up an entity by ID. */
  def entity(id: EntityId): Option[Entity] =
    entities.get(id)

  /** Look up a morphism by ID. */
  def morphism(id: MorphismId): Option[Morphism] =
    morphisms.get(id)

  /** All morphisms originating from the given entity. */
  def outgoing(entityId: EntityId): List[Morphism] =
    morphisms.values.filter(_.domain == entityId).toList

  /** All morphisms targeting the given entity. */
  def incoming(entityId: EntityId): List[Morphism] =
    morphisms.values.filter(_.codomain == entityId).toList

  /** Check if an identity morphism exists for each entity. */
  def hasIdentityMorphisms: Boolean =
    entities.values.forall { e =>
      morphisms.contains(e.identityMorphismId)
    }
}
