// Implements: REQ-F-ADJ-001, REQ-F-ADJ-006
package cdme.model.adjoint

import cdme.model.category.{MorphismId, EntityId, CardinalityType, ContainmentType}
import cdme.model.access.AccessRule
import cdme.model.error.ErrorObject

/**
 * Filter adjoint that separates records into passed and filtered-out sets.
 *
 * The forward function applies a predicate: records that pass continue
 * to the codomain; records that fail are tagged and stored in a
 * FilteredKeyStore for traceability.
 *
 * The backward function returns both passed and filtered-out records
 * with tags indicating their filter status.
 *
 * @tparam A the record type (domain and codomain are the same entity type)
 * @param id                   morphism identifier
 * @param name                 human-readable name
 * @param domain               source entity
 * @param codomain             target entity (typically same as domain for filters)
 * @param predicateFn          the filter predicate: true = pass, false = filter out
 * @param predicateDescription human-readable description for lineage
 * @param accessRules          access control rules
 */
final case class FilterAdjoint[A](
    id: MorphismId,
    name: String,
    domain: EntityId,
    codomain: EntityId,
    predicateFn: A => Boolean,
    predicateDescription: String,
    accessRules: List[AccessRule] = Nil
) extends AdjointMorphism[A, A]:

  override val cardinality: CardinalityType = CardinalityType.OneToOne
  override val containment: ContainmentType = ContainmentType.Filter

  /**
   * Forward: apply the predicate. Records that fail are routed as errors.
   */
  override def forward(input: A): Either[ErrorObject, A] =
    if predicateFn(input) then Right(input)
    else
      // Filtered records are not errors per se; the runtime handles
      // routing filtered records to the FilteredKeyStore.
      // At the model level, we signal filtering via Left with a
      // filter-specific error object.
      Left(ErrorObject(
        constraintType = cdme.model.error.ConstraintType.RefinementPredicate,
        offendingValues = Map("filter" -> predicateDescription),
        sourceEntity = cdme.model.error.EntityId(domain.value),
        sourceEpoch = cdme.model.error.EpochId(""),
        morphismPath = List(cdme.model.error.MorphismId(id.value)),
        timestamp = java.time.Instant.now(),
        details = Some(s"Filtered by: $predicateDescription")
      ))

  /**
   * Backward: returns the input record with metadata about filter status.
   * The actual filtered-out keys are stored in the FilteredKeyStore at runtime.
   */
  override def backward(output: A): BackwardResult[A] =
    BackwardResult(
      records = Vector(output),
      metadata = Map(
        "adjointType" -> "filter",
        "filterPredicate" -> predicateDescription
      )
    )
