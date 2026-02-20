// Implements: REQ-F-INT-007, REQ-F-ADJ-001
package cdme.integration.external

import cdme.model.category.{MorphismId, EntityId, CardinalityType, ContainmentType}
import cdme.model.adjoint.{AdjointMorphism, BackwardResult}
import cdme.model.access.AccessRule
import cdme.model.error.ErrorObject

/**
 * Opaque adjoint with declared (not computed) containment bounds.
 *
 * Used for external calculators where the backward function cannot be
 * automatically derived. The system trusts the declared containment bounds
 * but logs them for audit.
 *
 * @tparam A the domain type
 * @tparam B the codomain type
 * @param id               morphism identifier
 * @param name             human-readable name
 * @param domain           source entity
 * @param codomain         target entity
 * @param forwardFn        the forward computation
 * @param bounds           declared containment bounds
 * @param calculatorName   name of the external calculator (for lineage)
 * @param calculatorVersion version of the external calculator
 * @param accessRules      access control rules
 */
final case class OpaqueAdjoint[A, B](
    id: MorphismId,
    name: String,
    domain: EntityId,
    codomain: EntityId,
    forwardFn: A => Either[ErrorObject, B],
    bounds: ContainmentBounds,
    calculatorName: String,
    calculatorVersion: String,
    accessRules: List[AccessRule] = Nil
) extends AdjointMorphism[A, B]:

  override val cardinality: CardinalityType = CardinalityType.OneToOne
  override val containment: ContainmentType = ContainmentType.Opaque

  override def forward(input: A): Either[ErrorObject, B] =
    forwardFn(input)

  override def backward(output: B): BackwardResult[A] =
    BackwardResult(
      records = Vector.empty,
      metadata = Map(
        "adjointType" -> "opaque",
        "calculator" -> calculatorName,
        "version" -> calculatorVersion,
        "declaredUpperBound" -> bounds.upperBound.toString,
        "declaredLowerBound" -> bounds.lowerBound.toString,
        "note" -> "Containment bounds are declared, not verified"
      )
    )
