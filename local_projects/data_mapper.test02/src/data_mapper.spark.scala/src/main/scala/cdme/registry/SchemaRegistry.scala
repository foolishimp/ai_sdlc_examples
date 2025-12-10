package cdme.registry

import cats.implicits._
import cdme.core._
import cdme.config.ConfigError

/**
 * Schema registry implementation.
 * Implements: REQ-LDM-01, REQ-LDM-03 (Path Validation)
 */
class SchemaRegistryImpl(
  entities: Map[String, Entity],
  bindings: Map[String, PhysicalBinding]
) extends SchemaRegistry {

  /**
   * Get entity by name.
   */
  override def getEntity(name: String): Either[CdmeError, Entity] = {
    entities.get(name).toRight(
      CdmeError.CompilationError(s"Entity not found: $name")
    )
  }

  /**
   * Get relationship by entity and relationship name.
   */
  override def getRelationship(entityName: String, relName: String): Either[CdmeError, Relationship] = {
    for {
      entity <- getEntity(entityName)
      rel <- entity.relationships.find(_.name == relName).toRight(
        CdmeError.CompilationError(s"Relationship not found: $entityName.$relName")
      )
    } yield rel
  }

  /**
   * Get physical binding by entity name.
   */
  override def getBinding(entityName: String): Either[CdmeError, PhysicalBinding] = {
    bindings.get(entityName).toRight(
      CdmeError.CompilationError(s"Physical binding not found for entity: $entityName")
    )
  }

  /**
   * Validate path (e.g., "Order.customer.name").
   * Implements: REQ-LDM-03
   */
  override def validatePath(path: String): Either[CdmeError, PathValidationResult] = {
    val segments = path.split('.')

    if (segments.isEmpty) {
      return Left(CdmeError.PathNotFoundError(path))
    }

    val entityName = segments.head
    val remainingPath = segments.tail.toList

    for {
      entity <- getEntity(entityName)
      result <- validatePathSegments(entity, remainingPath, List(EntitySegment(entityName)))
    } yield result
  }

  /**
   * Recursively validate path segments.
   */
  private def validatePathSegments(
    currentEntity: Entity,
    segments: List[String],
    acc: List[PathSegment]
  ): Either[CdmeError, PathValidationResult] = segments match {
    case Nil =>
      // No more segments - invalid (path must end in attribute)
      Left(CdmeError.PathNotFoundError(s"Path must end in attribute: ${acc.map(segmentName).mkString(".")}"))

    case segment :: Nil =>
      // Last segment - must be attribute
      currentEntity.attributes.find(_.name == segment) match {
        case Some(attr) =>
          val allSegments = acc :+ AttributeSegment(attr.name, attr.dataType)
          Right(PathValidationResult(
            path = allSegments.map(segmentName).mkString("."),
            segments = allSegments,
            finalType = attr.dataType,
            valid = true
          ))
        case None =>
          Left(CdmeError.PathNotFoundError(s"Attribute not found: ${currentEntity.name}.$segment"))
      }

    case segment :: rest =>
      // Intermediate segment - must be relationship
      currentEntity.relationships.find(_.name == segment) match {
        case Some(rel) =>
          for {
            targetEntity <- getEntity(rel.target)
            result <- validatePathSegments(
              targetEntity,
              rest,
              acc :+ RelationshipSegment(rel.name, rel.cardinality)
            )
          } yield result
        case None =>
          Left(CdmeError.PathNotFoundError(s"Relationship not found: ${currentEntity.name}.$segment"))
      }
  }

  private def segmentName(segment: PathSegment): String = segment match {
    case EntitySegment(name) => name
    case RelationshipSegment(name, _) => name
    case AttributeSegment(name, _) => name
  }
}

object SchemaRegistryImpl {

  /**
   * Create registry from loaded configuration.
   */
  def fromConfig(
    entities: Map[String, Entity],
    bindings: Map[String, PhysicalBinding]
  ): Either[ConfigError, SchemaRegistryImpl] = {

    // Validate cross-references
    for {
      _ <- validateEntityReferences(entities)
      _ <- validateBindingReferences(entities, bindings)
    } yield new SchemaRegistryImpl(entities, bindings)
  }

  /**
   * Validate that all relationship targets exist.
   */
  private def validateEntityReferences(
    entities: Map[String, Entity]
  ): Either[ConfigError, Unit] = {
    val allRelationships = entities.values.flatMap(_.relationships)
    val invalidTargets = allRelationships
      .filterNot(r => entities.contains(r.target))
      .toList

    if (invalidTargets.isEmpty) {
      Right(())
    } else {
      val targets = invalidTargets.map(r => s"${r.name} -> ${r.target}").mkString(", ")
      Left(ConfigError.UnresolvedReferences(
        s"Relationships reference unknown entities: $targets"
      ))
    }
  }

  /**
   * Validate that all bindings reference valid entities.
   */
  private def validateBindingReferences(
    entities: Map[String, Entity],
    bindings: Map[String, PhysicalBinding]
  ): Either[ConfigError, Unit] = {
    val invalidBindings = bindings.values
      .filterNot(b => entities.contains(b.entity))
      .toList

    if (invalidBindings.isEmpty) {
      Right(())
    } else {
      val bindingNames = invalidBindings.map(_.entity).mkString(", ")
      Left(ConfigError.UnresolvedReferences(
        s"Physical bindings reference unknown entities: $bindingNames"
      ))
    }
  }
}
