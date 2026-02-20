// Implements: REQ-F-INT-002
package cdme.synthesis

import cdme.model.category.{MorphismId, EntityId, CardinalityType, ContainmentType}
import cdme.model.context.EpochId
import cdme.model.adjoint.{AdjointMorphism, BackwardResult}
import cdme.model.access.AccessRule
import cdme.model.error.ErrorObject

/**
 * Coalesce morphism: fallback chain (first non-null wins).
 *
 * Evaluates a chain of extraction functions in order, returning the first
 * non-null result. If all extractors return null/None, returns an error.
 *
 * @tparam A the input type
 * @tparam B the output type
 * @param id          morphism identifier
 * @param name        human-readable name
 * @param domain      source entity
 * @param codomain    target entity
 * @param extractors  ordered list of (name, extraction function) pairs
 * @param accessRules access control rules
 */
final case class CoalesceMorphism[A, B](
    id: MorphismId,
    name: String,
    domain: EntityId,
    codomain: EntityId,
    extractors: List[(String, A => Option[B])],
    accessRules: List[AccessRule] = Nil
) extends AdjointMorphism[A, B] {

  override val cardinality: CardinalityType = CardinalityType.OneToOne
  override val containment: ContainmentType = ContainmentType.Isomorphic

  override def forward(input: A): Either[ErrorObject, B] =
    extractors.view
      .flatMap { case (_, fn) => fn(input) }
      .headOption
      .toRight(ErrorObject(
        constraintType = cdme.model.error.ConstraintType.RefinementPredicate,
        offendingValues = Map("coalesce" -> "all_null"),
        sourceEntity = EntityId(domain.value),
        sourceEpoch = EpochId(""),
        morphismPath = List(MorphismId(id.value)),
        timestamp = java.time.Instant.now(),
        details = Some(s"All ${extractors.size} coalesce extractors returned null")
      ))

  override def backward(output: B): BackwardResult[A] =
    BackwardResult(
      records = Vector.empty,
      metadata = Map(
        "adjointType" -> "coalesce",
        "extractorCount" -> extractors.size.toString
      )
    )
}
