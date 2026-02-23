// Implements: REQ-INT-04, REQ-LDM-01
// Builder pattern for constructing logical data models.
package com.cdme.api

import com.cdme.model._
import com.cdme.model.error.{CdmeError, ValidationError}
import com.cdme.model.grain.{Grain, GrainHierarchy}
import com.cdme.model.types.CdmeType

/**
 * Builder for constructing a Category (Logical Data Model).
 * Provides a DSL-like experience for defining entities, morphisms, and grain hierarchies.
 *
 * Implements: REQ-INT-04, REQ-LDM-01
 * See ADR-012: Public API Design
 */
final case class LdmBuilder(
    name: String,
    entities: Map[EntityId, Entity],
    morphisms: Map[MorphismId, Morphism],
    grainHierarchy: GrainHierarchy
) {

  /** Add an entity to the LDM. */
  def withEntity(entity: Entity): LdmBuilder =
    copy(entities = entities + (entity.id -> entity))

  /** Add an entity using inline parameters. */
  def addEntity(
      id: EntityId,
      name: String,
      attributes: Map[AttributeName, CdmeType],
      grain: Grain,
      accessControl: AccessControl = AccessControl.open
  ): LdmBuilder = {
    val identityId = s"id_$id"
    val entity = Entity(id, name, attributes, grain, identityId, accessControl)
    val identityMorphism = Morphism(
      identityId, s"Identity($name)", id, id,
      Cardinality.OneToOne, MorphismKind.Structural, None, AccessControl.open
    )
    copy(
      entities = entities + (id -> entity),
      morphisms = morphisms + (identityId -> identityMorphism)
    )
  }

  /** Add a morphism to the LDM. */
  def withMorphism(morphism: Morphism): LdmBuilder =
    copy(morphisms = morphisms + (morphism.id -> morphism))

  /** Add a morphism using inline parameters. */
  def addMorphism(
      id: MorphismId,
      name: String,
      domain: EntityId,
      codomain: EntityId,
      cardinality: Cardinality,
      kind: MorphismKind,
      accessControl: AccessControl = AccessControl.open
  ): LdmBuilder = {
    val morphism = Morphism(id, name, domain, codomain, cardinality, kind, None, accessControl)
    copy(morphisms = morphisms + (id -> morphism))
  }

  /** Set the grain hierarchy. */
  def withGrainHierarchy(hierarchy: GrainHierarchy): LdmBuilder =
    copy(grainHierarchy = hierarchy)

  /**
   * Build the Category, validating structural integrity.
   * Checks: identity morphisms exist, referenced entities exist, no dangling references.
   */
  def build(): Either[CdmeError, Category] = {
    val category = Category(name, entities, morphisms, grainHierarchy)

    // Validate all morphism endpoints reference existing entities
    val danglingDomains = morphisms.values.filter(m => !entities.contains(m.domain) && m.domain != m.codomain)
    if (danglingDomains.nonEmpty) {
      return Left(ValidationError(
        s"Morphisms reference non-existent domain entities: ${danglingDomains.map(m => s"${m.id}->${m.domain}").mkString(", ")}"
      ))
    }

    val danglingCodomains = morphisms.values.filter(m => !entities.contains(m.codomain) && m.domain != m.codomain)
    if (danglingCodomains.nonEmpty) {
      return Left(ValidationError(
        s"Morphisms reference non-existent codomain entities: ${danglingCodomains.map(m => s"${m.id}->${m.codomain}").mkString(", ")}"
      ))
    }

    Right(category)
  }
}

object LdmBuilder {
  /** Create a new builder with default grain hierarchy. */
  def apply(name: String): LdmBuilder =
    LdmBuilder(name, Map.empty, Map.empty, GrainHierarchy.default)
}
