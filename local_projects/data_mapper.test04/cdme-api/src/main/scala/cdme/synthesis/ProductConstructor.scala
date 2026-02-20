// Implements: REQ-F-INT-005
package cdme.synthesis

import cdme.model.category.{MorphismId, EntityId, CardinalityType, ContainmentType}
import cdme.model.context.EpochId
import cdme.model.adjoint.{AdjointMorphism, BackwardResult}
import cdme.model.access.AccessRule
import cdme.model.error.ErrorObject

/**
 * Product constructor: constructs product types from multiple inputs.
 *
 * Takes two inputs and combines them into a product (pair). Can be chained
 * for constructing larger product types.
 *
 * @tparam A the first input type
 * @tparam B the second input type
 * @tparam C the product output type
 * @param id          morphism identifier
 * @param name        human-readable name
 * @param domain      source entity (first input)
 * @param codomain    target entity (product)
 * @param combineFn   function combining two inputs into a product
 * @param projectFn   function projecting the product back to (A, B)
 * @param accessRules access control rules
 */
final case class ProductConstructor[A, B, C](
    id: MorphismId,
    name: String,
    domain: EntityId,
    codomain: EntityId,
    combineFn: (A, B) => Either[ErrorObject, C],
    projectFn: C => (A, B),
    secondInput: EntityId,
    accessRules: List[AccessRule] = Nil
) extends AdjointMorphism[A, C] {

  override val cardinality: CardinalityType = CardinalityType.OneToOne
  override val containment: ContainmentType = ContainmentType.Isomorphic

  /**
   * Forward requires both inputs. The second input must be provided via context.
   * This simplified interface expects the combine function to be partially applied.
   */
  override def forward(input: A): Either[ErrorObject, C] =
    // TODO: The runtime layer provides the second input via context injection.
    // For now, forward expects a partially applied combineFn.
    Left(ErrorObject(
      constraintType = cdme.model.error.ConstraintType.Custom("ProductConstructor"),
      offendingValues = Map.empty,
      sourceEntity = EntityId(domain.value),
      sourceEpoch = EpochId(""),
      morphismPath = List(MorphismId(id.value)),
      timestamp = java.time.Instant.now(),
      details = Some("ProductConstructor.forward requires runtime context injection for second input")
    ))

  override def backward(output: C): BackwardResult[A] = {
    val (a, _) = projectFn(output)
    BackwardResult(
      records = Vector(a),
      metadata = Map(
        "adjointType" -> "productConstructor",
        "secondInput" -> secondInput.value
      )
    )
  }
}
