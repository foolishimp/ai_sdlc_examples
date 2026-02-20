// Implements: REQ-F-ADJ-001, REQ-F-ADJ-006
package cdme.model.adjoint

import cdme.model.category.{MorphismId, EntityId, CardinalityType, ContainmentType}
import cdme.model.context.EpochId
import cdme.model.access.AccessRule
import cdme.model.error.ErrorObject

final case class FilterAdjoint[A](
    id: MorphismId,
    name: String,
    domain: EntityId,
    codomain: EntityId,
    predicateFn: A => Boolean,
    predicateDescription: String,
    accessRules: List[AccessRule] = Nil
) extends AdjointMorphism[A, A] {

  override val cardinality: CardinalityType = CardinalityType.OneToOne
  override val containment: ContainmentType = ContainmentType.Filter

  override def forward(input: A): Either[ErrorObject, A] =
    if (predicateFn(input)) Right(input)
    else
      Left(ErrorObject(
        constraintType = cdme.model.error.ConstraintType.RefinementPredicate,
        offendingValues = Map("filter" -> predicateDescription),
        sourceEntity = EntityId(domain.value),
        sourceEpoch = EpochId(""),
        morphismPath = List(MorphismId(id.value)),
        timestamp = java.time.Instant.now(),
        details = Some(s"Filtered by: $predicateDescription")
      ))

  override def backward(output: A): BackwardResult[A] =
    BackwardResult(
      records = Vector(output),
      metadata = Map(
        "adjointType" -> "filter",
        "filterPredicate" -> predicateDescription
      )
    )
}
