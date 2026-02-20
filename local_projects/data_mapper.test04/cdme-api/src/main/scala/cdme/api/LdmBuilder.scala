// Implements: REQ-F-API-001, REQ-F-LDM-001, REQ-F-LDM-002, REQ-F-LDM-004, REQ-F-LDM-005
// ADR-010: Programmatic API over Configuration Files
package cdme.api

import cdme.model.category.{Category, Entity, EntityId, AttributeId}
import cdme.model.types.CdmeType
import cdme.model.grain.Grain
import cdme.model.access.AccessRule
import cdme.model.error.ValidationError

/**
 * Fluent API for constructing Logical Data Model entities.
 *
 * ADR-010 mandates that the Scala API is the primary interface for LDM
 * construction (not YAML/XML). All API methods return Either[ValidationError, T].
 *
 * Usage:
 * {{{
 *   val tradeEntity = LdmBuilder("Trade")
 *     .grain(Grain.Atomic)
 *     .attribute("id", PrimitiveType.IntType)
 *     .attribute("amount", SemanticType(PrimitiveType.DecimalType, SemanticTag("Money")))
 *     .attribute("tradeDate", PrimitiveType.DateType)
 *     .build()
 * }}}
 *
 * @param entityName the name of the entity being built
 */
final case class LdmBuilder(
    entityName: String,
    private val entityGrain: Option[Grain] = None,
    private val entityAttributes: Vector[(AttributeId, CdmeType)] = Vector.empty,
    private val entityAccessRules: List[AccessRule] = Nil
) {
  /**
   * Set the grain level for this entity.
   *
   * @param g the grain level
   * @return updated builder
   */
  def grain(g: Grain): LdmBuilder =
    copy(entityGrain = Some(g))

  /**
   * Add a typed attribute to this entity.
   *
   * @param name the attribute name
   * @param tpe  the attribute type
   * @return updated builder
   */
  def attribute(name: String, tpe: CdmeType): LdmBuilder =
    copy(entityAttributes = entityAttributes :+ (AttributeId(name), tpe))

  /**
   * Add an access rule to this entity.
   *
   * @param rule the access rule
   * @return updated builder
   */
  def withAccessRule(rule: AccessRule): LdmBuilder =
    copy(entityAccessRules = entityAccessRules :+ rule)

  /**
   * Build the entity.
   *
   * @return Right(entity) or Left(error) if required fields are missing
   */
  def build(): Either[ValidationError, Entity] =
    entityGrain match {
      case None =>
        Left(ValidationError.General(s"Entity '$entityName' requires a grain level"))
      case Some(g) =>
        Right(Entity(
          id = EntityId(entityName.toLowerCase.replaceAll("\\s+", "_")),
          name = entityName,
          grain = g,
          attributes = entityAttributes,
          accessRules = entityAccessRules
        ))
    }
}

/**
 * Builder for constructing a complete Category from multiple entities and morphisms.
 *
 * @param categoryName the name of the category
 */
final case class CategoryBuilder(
    categoryName: String,
    private val currentCategory: Category = Category.empty("")
) {
  /**
   * Add an entity to the category.
   *
   * @param entity the entity to add
   * @return Right(updated builder) or Left(error)
   */
  def addEntity(entity: Entity): Either[ValidationError, CategoryBuilder] =
    currentCategory.addEntity(entity).map(cat =>
      copy(currentCategory = cat)
    )

  /**
   * Add a morphism to the category.
   *
   * @param morphism the morphism to add
   * @return Right(updated builder) or Left(error)
   */
  def addMorphism(morphism: cdme.model.adjoint.AdjointMorphism[_, _]): Either[ValidationError, CategoryBuilder] =
    currentCategory.addMorphism(morphism).map(cat =>
      copy(currentCategory = cat)
    )

  /**
   * Build the category, validating all laws.
   *
   * @return Right(category) or Left(errors) if validation fails
   */
  def build(): Either[List[ValidationError], Category] = {
    val category = currentCategory.copy(name = categoryName)
    val errors = category.validate()
    if (errors.isEmpty) Right(category)
    else Left(errors)
  }
}

object LdmBuilder {
  /**
   * Start building an entity with the given name.
   *
   * @param name the entity name
   * @return a new LdmBuilder
   */
  def entity(name: String): LdmBuilder = LdmBuilder(name)
}
