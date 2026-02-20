// Implements: REQ-F-LDM-001, REQ-F-LDM-002, REQ-F-LDM-004, REQ-F-LDM-005
package cdme.model.category

import cdme.model.types.CdmeType
import cdme.model.grain.Grain
import cdme.model.access.AccessRule

/**
 * Opaque type for entity identifiers.
 */
opaque type EntityId = String

object EntityId:
  def apply(value: String): EntityId = value
  extension (id: EntityId)
    def value: String = id
    def show: String = s"EntityId($id)"

/**
 * Opaque type for attribute identifiers.
 */
opaque type AttributeId = String

object AttributeId:
  def apply(value: String): AttributeId = value
  extension (id: AttributeId)
    def value: String = id

/**
 * A typed entity in the Logical Data Model (LDM).
 *
 * Entities are the objects of the category. Each entity has:
 *   - A unique identifier and human-readable name
 *   - A grain level indicating its granularity (REQ-F-LDM-004)
 *   - A set of typed attributes (REQ-F-LDM-005)
 *   - Access control rules (REQ-F-ACC-005)
 *
 * Identity morphisms are auto-generated for each entity (Section 2.1).
 * Multiple morphisms between the same entity pair are supported (multigraph).
 *
 * @param id          unique entity identifier
 * @param name        human-readable entity name
 * @param grain       the grain level of this entity
 * @param attributes  ordered map of attribute names to their CDME types
 * @param accessRules optional access control rules
 */
final case class Entity(
    id: EntityId,
    name: String,
    grain: Grain,
    attributes: Vector[(AttributeId, CdmeType)],
    accessRules: List[AccessRule] = Nil
):
  /** Look up an attribute type by its identifier. */
  def attributeType(attrId: AttributeId): Option[CdmeType] =
    attributes.find(_._1 == attrId).map(_._2)

  /** All attribute identifiers in this entity. */
  def attributeIds: Vector[AttributeId] =
    attributes.map(_._1)

  /** The number of attributes on this entity. */
  def attributeCount: Int = attributes.size
