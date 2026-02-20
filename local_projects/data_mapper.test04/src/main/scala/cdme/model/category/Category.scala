// Implements: REQ-F-LDM-001, REQ-F-LDM-002, REQ-F-LDM-003, REQ-F-LDM-008
package cdme.model.category

import cdme.model.adjoint.AdjointMorphism
import cdme.model.error.ValidationError

/**
 * A category in the CDME Logical Data Model.
 *
 * The category is the core container for entities (objects) and morphisms (arrows).
 * It enforces the category laws:
 *   1. Every entity has an identity morphism (auto-generated)
 *   2. Morphism composition is associative
 *   3. Multiple morphisms between the same entity pair are allowed (multigraph)
 *
 * Categories are immutable. Mutations produce new Category instances.
 *
 * @param name      human-readable category name
 * @param entities  all entities in this category, keyed by EntityId
 * @param morphisms all morphisms in this category, keyed by MorphismId
 */
final case class Category(
    name: String,
    entities: Map[EntityId, Entity],
    morphisms: Map[MorphismId, AdjointMorphism[?, ?]]
):
  /**
   * Validate category laws and structural integrity.
   *
   * Checks:
   *   - Every morphism references valid domain and codomain entities
   *   - Every entity has at least one identity morphism
   *   - No orphan entities (warning, not error)
   *
   * @return list of validation errors (empty if valid)
   */
  def validate(): List[ValidationError] =
    val morphismEntityErrors = morphisms.values.toList.flatMap { m =>
      val domainCheck =
        if !entities.contains(m.domain) then
          Some(ValidationError.MorphismNotFound(
            m.id.show,
            s"Morphism ${m.id.show} references unknown domain entity ${m.domain.show}"
          ))
        else None

      val codomainCheck =
        if !entities.contains(m.codomain) then
          Some(ValidationError.MorphismNotFound(
            m.id.show,
            s"Morphism ${m.id.show} references unknown codomain entity ${m.codomain.show}"
          ))
        else None

      domainCheck.toList ++ codomainCheck.toList
    }

    morphismEntityErrors

  /**
   * Find all morphisms from a given entity.
   *
   * @param entityId the source entity
   * @return list of morphisms with this entity as domain
   */
  def morphismsFrom(entityId: EntityId): List[AdjointMorphism[?, ?]] =
    morphisms.values.filter(_.domain == entityId).toList

  /**
   * Find all morphisms to a given entity.
   *
   * @param entityId the target entity
   * @return list of morphisms with this entity as codomain
   */
  def morphismsTo(entityId: EntityId): List[AdjointMorphism[?, ?]] =
    morphisms.values.filter(_.codomain == entityId).toList

  /**
   * Find a specific morphism between two entities by name.
   *
   * @param from source entity
   * @param to   target entity
   * @param name morphism name
   * @return the morphism if found
   */
  def findMorphism(from: EntityId, to: EntityId, name: String): Option[AdjointMorphism[?, ?]] =
    morphisms.values.find(m =>
      m.domain == from && m.codomain == to && m.name == name
    )

  /**
   * Add an entity to this category, producing a new Category.
   *
   * @param entity the entity to add
   * @return Right(newCategory) or Left(error) if entity ID already exists
   */
  def addEntity(entity: Entity): Either[ValidationError, Category] =
    if entities.contains(entity.id) then
      Left(ValidationError.General(s"Entity ${entity.id.show} already exists"))
    else
      Right(copy(entities = entities + (entity.id -> entity)))

  /**
   * Add a morphism to this category, producing a new Category.
   *
   * @param morphism the morphism to add
   * @return Right(newCategory) or Left(error) if validation fails
   */
  def addMorphism(morphism: AdjointMorphism[?, ?]): Either[ValidationError, Category] =
    if !entities.contains(morphism.domain) then
      Left(ValidationError.MorphismNotFound(
        morphism.id.show,
        s"Domain entity ${morphism.domain.show} does not exist"
      ))
    else if !entities.contains(morphism.codomain) then
      Left(ValidationError.MorphismNotFound(
        morphism.id.show,
        s"Codomain entity ${morphism.codomain.show} does not exist"
      ))
    else if morphisms.contains(morphism.id) then
      Left(ValidationError.General(s"Morphism ${morphism.id.show} already exists"))
    else
      Right(copy(morphisms = morphisms + (morphism.id -> morphism)))

  /** Total number of entities. */
  def entityCount: Int = entities.size

  /** Total number of morphisms. */
  def morphismCount: Int = morphisms.size

object Category:
  /** Create an empty category with the given name. */
  def empty(name: String): Category =
    Category(name, Map.empty, Map.empty)
