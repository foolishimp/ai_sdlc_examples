// Implements: REQ-F-ADJ-001, REQ-F-ADJ-004
package cdme.model.adjoint

import cdme.model.category.{MorphismId, EntityId, CardinalityType, ContainmentType}
import cdme.model.access.AccessRule
import cdme.model.error.ErrorObject

/**
 * Kleisli adjoint for 1:N (one-to-many) morphisms.
 *
 * The forward function expands a single domain record into multiple codomain
 * records. The backward function collects the parent record for a given
 * codomain value.
 *
 * In the path compiler, 1:N traversals trigger Kleisli context lifting:
 * the context type transitions from Scalar to List (flatMap prevents nesting).
 *
 * @tparam A the domain type
 * @tparam B the codomain type
 * @param id          morphism identifier
 * @param name        human-readable name
 * @param domain      source entity
 * @param codomain    target entity
 * @param forwardFn   expands one A into multiple B values
 * @param parentFn    returns the parent A for a given B
 * @param accessRules access control rules
 */
final case class KleisliAdjoint[A, B](
    id: MorphismId,
    name: String,
    domain: EntityId,
    codomain: EntityId,
    forwardFn: A => Either[ErrorObject, Vector[B]],
    parentFn: B => A,
    accessRules: List[AccessRule] = Nil
) extends AdjointMorphism[A, B]:

  override val cardinality: CardinalityType = CardinalityType.OneToMany
  override val containment: ContainmentType = ContainmentType.Expansion

  override def forward(input: A): Either[ErrorObject, B] =
    // For Kleisli, forward produces multiple outputs.
    // The single-value signature is used for composition;
    // the actual expansion is handled by the runtime via forwardExpand.
    forwardFn(input).map(_.headOption.getOrElse(
      throw new IllegalStateException("Kleisli forward produced empty result")
    ))

  /**
   * Expand a single domain value into multiple codomain values.
   * This is the primary forward operation for 1:N morphisms.
   *
   * @param input the domain value
   * @return Right(expanded values) or Left(error)
   */
  def forwardExpand(input: A): Either[ErrorObject, Vector[B]] =
    forwardFn(input)

  override def backward(output: B): BackwardResult[A] =
    BackwardResult(
      records = Vector(parentFn(output)),
      metadata = Map("adjointType" -> "kleisli", "relation" -> "parent")
    )
