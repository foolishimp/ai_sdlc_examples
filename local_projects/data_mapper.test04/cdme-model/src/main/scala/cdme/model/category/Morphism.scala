// Implements: REQ-F-LDM-001, REQ-F-LDM-002, REQ-F-ADJ-001
package cdme.model.category

import cdme.model.access.AccessRule

case class MorphismId(value: String) {
  def show: String = s"MorphismId($value)"
}

sealed trait CardinalityType
object CardinalityType {
  case object OneToOne extends CardinalityType
  case object ManyToOne extends CardinalityType
  case object OneToMany extends CardinalityType
  case object ManyToMany extends CardinalityType

  val values: List[CardinalityType] = List(OneToOne, ManyToOne, OneToMany, ManyToMany)
}

sealed trait ContainmentType
object ContainmentType {
  case object Isomorphic extends ContainmentType
  case object Preimage extends ContainmentType
  case object Expansion extends ContainmentType
  case object Aggregation extends ContainmentType
  case object Filter extends ContainmentType
  case object Opaque extends ContainmentType
}

trait MorphismBase {
  def id: MorphismId
  def name: String
  def domain: EntityId
  def codomain: EntityId
  def cardinality: CardinalityType
  def containment: ContainmentType
  def accessRules: List[AccessRule]
}
