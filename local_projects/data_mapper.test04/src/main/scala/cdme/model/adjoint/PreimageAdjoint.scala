// Implements: REQ-F-ADJ-001, REQ-F-ADJ-003
package cdme.model.adjoint

import cdme.model.category.{MorphismId, EntityId, CardinalityType, ContainmentType}
import cdme.model.access.AccessRule
import cdme.model.error.ErrorObject

/**
 * Preimage adjoint for N:1 (many-to-one) morphisms.
 *
 * The forward function maps multiple domain records to a single codomain record.
 * The backward function returns the preimage set -- all domain records that
 * map to the given codomain value.
 *
 * @tparam A the domain type
 * @tparam B the codomain type
 * @param id            morphism identifier
 * @param name          human-readable name
 * @param domain        source entity
 * @param codomain      target entity
 * @param forwardFn     the forward transformation
 * @param preimageFn    returns the set of domain values mapping to a given codomain value
 * @param accessRules   access control rules
 */
final case class PreimageAdjoint[A, B](
    id: MorphismId,
    name: String,
    domain: EntityId,
    codomain: EntityId,
    forwardFn: A => Either[ErrorObject, B],
    preimageFn: B => Vector[A],
    accessRules: List[AccessRule] = Nil
) extends AdjointMorphism[A, B]:

  override val cardinality: CardinalityType = CardinalityType.ManyToOne
  override val containment: ContainmentType = ContainmentType.Preimage

  override def forward(input: A): Either[ErrorObject, B] =
    forwardFn(input)

  override def backward(output: B): BackwardResult[A] =
    BackwardResult(
      records = preimageFn(output),
      metadata = Map("adjointType" -> "preimage")
    )
