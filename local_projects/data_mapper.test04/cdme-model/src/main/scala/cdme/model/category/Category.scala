// Implements: REQ-F-LDM-001, REQ-F-LDM-002, REQ-F-LDM-003, REQ-F-LDM-008
package cdme.model.category

import cdme.model.adjoint.AdjointMorphism
import cdme.model.error.ValidationError

final case class Category(
    name: String,
    entities: Map[EntityId, Entity],
    morphisms: Map[MorphismId, AdjointMorphism[_, _]]
) {
  def validate(): List[ValidationError] = {
    val morphismEntityErrors = morphisms.values.toList.flatMap { m =>
      val domainCheck =
        if (!entities.contains(m.domain))
          Some(ValidationError.MorphismNotFound(
            m.id.show,
            s"Morphism ${m.id.show} references unknown domain entity ${m.domain.show}"
          ))
        else None

      val codomainCheck =
        if (!entities.contains(m.codomain))
          Some(ValidationError.MorphismNotFound(
            m.id.show,
            s"Morphism ${m.id.show} references unknown codomain entity ${m.codomain.show}"
          ))
        else None

      domainCheck.toList ++ codomainCheck.toList
    }

    morphismEntityErrors
  }

  def morphismsFrom(entityId: EntityId): List[AdjointMorphism[_, _]] =
    morphisms.values.filter(_.domain == entityId).toList

  def morphismsTo(entityId: EntityId): List[AdjointMorphism[_, _]] =
    morphisms.values.filter(_.codomain == entityId).toList

  def findMorphism(from: EntityId, to: EntityId, name: String): Option[AdjointMorphism[_, _]] =
    morphisms.values.find(m =>
      m.domain == from && m.codomain == to && m.name == name
    )

  def addEntity(entity: Entity): Either[ValidationError, Category] =
    if (entities.contains(entity.id))
      Left(ValidationError.General(s"Entity ${entity.id.show} already exists"))
    else
      Right(copy(entities = entities + (entity.id -> entity)))

  def addMorphism(morphism: AdjointMorphism[_, _]): Either[ValidationError, Category] =
    if (!entities.contains(morphism.domain))
      Left(ValidationError.MorphismNotFound(
        morphism.id.show,
        s"Domain entity ${morphism.domain.show} does not exist"
      ))
    else if (!entities.contains(morphism.codomain))
      Left(ValidationError.MorphismNotFound(
        morphism.id.show,
        s"Codomain entity ${morphism.codomain.show} does not exist"
      ))
    else if (morphisms.contains(morphism.id))
      Left(ValidationError.General(s"Morphism ${morphism.id.show} already exists"))
    else
      Right(copy(morphisms = morphisms + (morphism.id -> morphism)))

  def entityCount: Int = entities.size
  def morphismCount: Int = morphisms.size
}

object Category {
  def empty(name: String): Category =
    Category(name, Map.empty, Map.empty)
}
