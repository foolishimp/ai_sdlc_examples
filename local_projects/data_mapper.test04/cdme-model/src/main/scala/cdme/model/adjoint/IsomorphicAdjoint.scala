// Implements: REQ-F-ADJ-001, REQ-F-ADJ-002
package cdme.model.adjoint

import cdme.model.category.{MorphismId, EntityId, CardinalityType, ContainmentType}
import cdme.model.access.AccessRule
import cdme.model.error.ErrorObject

final case class IsomorphicAdjoint[A, B](
    id: MorphismId,
    name: String,
    domain: EntityId,
    codomain: EntityId,
    forwardFn: A => Either[ErrorObject, B],
    backwardFn: B => A,
    accessRules: List[AccessRule] = Nil
) extends AdjointMorphism[A, B] {

  override val cardinality: CardinalityType = CardinalityType.OneToOne
  override val containment: ContainmentType = ContainmentType.Isomorphic

  override def forward(input: A): Either[ErrorObject, B] =
    forwardFn(input)

  override def backward(output: B): BackwardResult[A] =
    BackwardResult(Vector(backwardFn(output)))
}
