// Implements: REQ-F-ADJ-001, REQ-F-ADJ-004
package cdme.model.adjoint

import cdme.model.category.{MorphismId, EntityId, CardinalityType, ContainmentType}
import cdme.model.access.AccessRule
import cdme.model.error.ErrorObject

final case class KleisliAdjoint[A, B](
    id: MorphismId,
    name: String,
    domain: EntityId,
    codomain: EntityId,
    forwardFn: A => Either[ErrorObject, Vector[B]],
    parentFn: B => A,
    accessRules: List[AccessRule] = Nil
) extends AdjointMorphism[A, B] {

  override val cardinality: CardinalityType = CardinalityType.OneToMany
  override val containment: ContainmentType = ContainmentType.Expansion

  override def forward(input: A): Either[ErrorObject, B] =
    forwardFn(input).map(_.headOption.getOrElse(
      throw new IllegalStateException("Kleisli forward produced empty result")
    ))

  def forwardExpand(input: A): Either[ErrorObject, Vector[B]] =
    forwardFn(input)

  override def backward(output: B): BackwardResult[A] =
    BackwardResult(
      records = Vector(parentFn(output)),
      metadata = Map("adjointType" -> "kleisli", "relation" -> "parent")
    )
}
