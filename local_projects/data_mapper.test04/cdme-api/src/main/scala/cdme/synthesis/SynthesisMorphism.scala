// Implements: REQ-F-INT-001, REQ-F-INT-002, REQ-F-INT-005
package cdme.synthesis

import cdme.model.category.{MorphismId, EntityId, CardinalityType, ContainmentType}
import cdme.model.adjoint.{AdjointMorphism, BackwardResult}
import cdme.model.access.AccessRule
import cdme.model.error.ErrorObject

/**
 * Synthesis morphism: derives new attributes via pure functions over existing entities.
 *
 * Synthesis morphisms are first-class morphisms in the LDM -- they compose,
 * type-check, and have adjoints. Lineage is maintained for every derived value.
 *
 * @tparam A the input type (source entity)
 * @tparam B the output type (derived attribute)
 * @param id           morphism identifier
 * @param name         human-readable name
 * @param domain       source entity
 * @param codomain     target entity (may be same as domain if adding derived attributes)
 * @param deriveFn     the pure derivation function
 * @param reverseFn    the backward function for lineage
 * @param accessRules  access control rules
 */
final case class SynthesisMorphism[A, B](
    id: MorphismId,
    name: String,
    domain: EntityId,
    codomain: EntityId,
    deriveFn: A => Either[ErrorObject, B],
    reverseFn: B => BackwardResult[A],
    accessRules: List[AccessRule] = Nil
) extends AdjointMorphism[A, B] {

  override val cardinality: CardinalityType = CardinalityType.OneToOne
  override val containment: ContainmentType = ContainmentType.Isomorphic

  override def forward(input: A): Either[ErrorObject, B] =
    deriveFn(input)

  override def backward(output: B): BackwardResult[A] =
    reverseFn(output)
}
