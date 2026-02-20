// Implements: REQ-F-ADJ-001, REQ-F-ADJ-005, REQ-F-ADJ-008
// ADR-011: Reverse-Join Table Strategy for Aggregation Adjoints
package cdme.model.adjoint

import cdme.model.category.{MorphismId, EntityId, CardinalityType, ContainmentType}
import cdme.model.access.AccessRule
import cdme.model.error.ErrorObject

final case class AggregationAdjoint[A, B](
    id: MorphismId,
    name: String,
    domain: EntityId,
    codomain: EntityId,
    forwardFn: A => Either[ErrorObject, B],
    groupKeyFn: A => String,
    monoidName: String,
    accessRules: List[AccessRule] = Nil
) extends AdjointMorphism[A, B] {

  override val cardinality: CardinalityType = CardinalityType.ManyToOne
  override val containment: ContainmentType = ContainmentType.Aggregation

  override def forward(input: A): Either[ErrorObject, B] =
    forwardFn(input)

  override def backward(output: B): BackwardResult[A] =
    BackwardResult(
      records = Vector.empty,
      metadata = Map(
        "adjointType" -> "aggregation",
        "reverseJoinRequired" -> "true",
        "monoid" -> monoidName
      )
    )
}
