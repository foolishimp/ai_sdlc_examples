// Implements: REQ-F-INT-002
package cdme.synthesis

import cdme.model.category.{MorphismId, EntityId, CardinalityType, ContainmentType}
import cdme.model.adjoint.{AdjointMorphism, BackwardResult}
import cdme.model.access.AccessRule
import cdme.model.error.ErrorObject

/**
 * Conditional morphism: if-then-else as a morphism.
 *
 * Evaluates a predicate on the input and applies one of two morphisms
 * depending on the result. Both branches must produce the same output type.
 *
 * @tparam A the input type
 * @tparam B the output type
 * @param id           morphism identifier
 * @param name         human-readable name
 * @param domain       source entity
 * @param codomain     target entity
 * @param predicate    the branching condition
 * @param thenBranch   morphism applied when predicate is true
 * @param elseBranch   morphism applied when predicate is false
 * @param predicateDescription human-readable predicate for lineage
 * @param accessRules  access control rules
 */
final case class ConditionalMorphism[A, B](
    id: MorphismId,
    name: String,
    domain: EntityId,
    codomain: EntityId,
    predicate: A => Boolean,
    thenBranch: A => Either[ErrorObject, B],
    elseBranch: A => Either[ErrorObject, B],
    predicateDescription: String,
    accessRules: List[AccessRule] = Nil
) extends AdjointMorphism[A, B]:

  override val cardinality: CardinalityType = CardinalityType.OneToOne
  override val containment: ContainmentType = ContainmentType.Isomorphic

  override def forward(input: A): Either[ErrorObject, B] =
    if predicate(input) then thenBranch(input)
    else elseBranch(input)

  override def backward(output: B): BackwardResult[A] =
    // Backward for conditional returns empty with metadata about branching
    BackwardResult(
      records = Vector.empty,
      metadata = Map(
        "adjointType" -> "conditional",
        "predicate" -> predicateDescription
      )
    )
