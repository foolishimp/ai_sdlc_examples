// Implements: REQ-F-ADJ-001, REQ-F-ADJ-002
package cdme.model.adjoint

import cdme.model.category.{MorphismId, EntityId, CardinalityType, ContainmentType}
import cdme.model.access.AccessRule
import cdme.model.error.ErrorObject

/**
 * Isomorphic adjoint: exact inverse where backward(forward(x)) = x.
 *
 * Used for 1:1 cardinality morphisms where the forward function is bijective.
 * Exactness validation at definition time is limited to structural checks
 * (type bijection); runtime property-based testing is recommended for
 * functional exactness (SA-03).
 *
 * @tparam A the domain type
 * @tparam B the codomain type
 * @param id          morphism identifier
 * @param name        human-readable name
 * @param domain      source entity
 * @param codomain    target entity
 * @param forwardFn   the forward transformation
 * @param backwardFn  the exact inverse transformation
 * @param accessRules access control rules
 */
final case class IsomorphicAdjoint[A, B](
    id: MorphismId,
    name: String,
    domain: EntityId,
    codomain: EntityId,
    forwardFn: A => Either[ErrorObject, B],
    backwardFn: B => A,
    accessRules: List[AccessRule] = Nil
) extends AdjointMorphism[A, B]:

  override val cardinality: CardinalityType = CardinalityType.OneToOne
  override val containment: ContainmentType = ContainmentType.Isomorphic

  override def forward(input: A): Either[ErrorObject, B] =
    forwardFn(input)

  override def backward(output: B): BackwardResult[A] =
    BackwardResult(Vector(backwardFn(output)))
