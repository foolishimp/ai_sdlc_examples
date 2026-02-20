// Implements: REQ-F-ADJ-001, REQ-F-ADJ-005, REQ-F-ADJ-008
// ADR-011: Reverse-Join Table Strategy for Aggregation Adjoints
package cdme.model.adjoint

import cdme.model.category.{MorphismId, EntityId, CardinalityType, ContainmentType}
import cdme.model.access.AccessRule
import cdme.model.grain.Monoid
import cdme.model.error.ErrorObject

/**
 * Aggregation adjoint for fold operations.
 *
 * The forward function aggregates multiple domain records into a single
 * codomain value using a declared Monoid (ADR-008). The backward function
 * uses a reverse-join table to map the aggregated output back to the
 * contributing input record keys (ADR-011).
 *
 * The reverse-join table is captured during forward execution and persisted
 * within the same epoch context.
 *
 * @tparam A the domain type
 * @tparam B the codomain (aggregated) type
 * @param id           morphism identifier
 * @param name         human-readable name
 * @param domain       source entity
 * @param codomain     target entity
 * @param forwardFn    the aggregation function
 * @param groupKeyFn   extracts the group key from a domain value for reverse-join
 * @param monoidName   name of the monoid used (for lineage)
 * @param accessRules  access control rules
 */
final case class AggregationAdjoint[A, B](
    id: MorphismId,
    name: String,
    domain: EntityId,
    codomain: EntityId,
    forwardFn: A => Either[ErrorObject, B],
    groupKeyFn: A => String,
    monoidName: String,
    accessRules: List[AccessRule] = Nil
) extends AdjointMorphism[A, B]:

  override val cardinality: CardinalityType = CardinalityType.ManyToOne
  override val containment: ContainmentType = ContainmentType.Aggregation

  override def forward(input: A): Either[ErrorObject, B] =
    forwardFn(input)

  /**
   * Backward uses the reverse-join table (not provided here as a parameter
   * because it is captured at runtime during forward execution).
   *
   * The returned BackwardResult contains metadata indicating that a
   * reverse-join table lookup is required.
   */
  override def backward(output: B): BackwardResult[A] =
    // At the model level, backward for aggregation returns an empty result
    // with metadata indicating reverse-join lookup is required.
    // The runtime layer populates actual results from the ReverseJoinTable.
    BackwardResult(
      records = Vector.empty,
      metadata = Map(
        "adjointType" -> "aggregation",
        "reverseJoinRequired" -> "true",
        "monoid" -> monoidName
      )
    )
