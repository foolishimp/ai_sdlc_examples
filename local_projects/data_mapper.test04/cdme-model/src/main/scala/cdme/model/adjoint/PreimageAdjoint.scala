// Implements: REQ-F-ADJ-001, REQ-F-ADJ-003
package cdme.model.adjoint

import cdme.model.category.{MorphismId, EntityId, CardinalityType, ContainmentType}
import cdme.model.access.AccessRule
import cdme.model.error.ErrorObject

final case class PreimageAdjoint[A, B](
    id: MorphismId,
    name: String,
    domain: EntityId,
    codomain: EntityId,
    forwardFn: A => Either[ErrorObject, B],
    preimageFn: B => Vector[A],
    accessRules: List[AccessRule] = Nil
) extends AdjointMorphism[A, B] {

  override val cardinality: CardinalityType = CardinalityType.ManyToOne
  override val containment: ContainmentType = ContainmentType.Preimage

  override def forward(input: A): Either[ErrorObject, B] =
    forwardFn(input)

  override def backward(output: B): BackwardResult[A] =
    BackwardResult(
      records = preimageFn(output),
      metadata = Map("adjointType" -> "preimage")
    )
}
