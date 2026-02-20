// Implements: REQ-F-LDM-001, REQ-F-LDM-002, REQ-F-LDM-004, REQ-F-LDM-005
package cdme.model.category

import cdme.model.types.CdmeType
import cdme.model.grain.Grain
import cdme.model.access.AccessRule

case class EntityId(value: String) {
  def show: String = s"EntityId($value)"
}

case class AttributeId(value: String)

final case class Entity(
    id: EntityId,
    name: String,
    grain: Grain,
    attributes: Vector[(AttributeId, CdmeType)],
    accessRules: List[AccessRule] = Nil
) {
  def attributeType(attrId: AttributeId): Option[CdmeType] =
    attributes.find(_._1 == attrId).map(_._2)

  def attributeIds: Vector[AttributeId] =
    attributes.map(_._1)

  def attributeCount: Int = attributes.size
}
