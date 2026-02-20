// Implements: REQ-F-INT-007
package cdme.integration.external

import cdme.model.types.CdmeType
import cdme.model.category.{MorphismId, EntityId, CardinalityType, ContainmentType}
import cdme.model.adjoint.{AdjointMorphism, BackwardResult}
import cdme.model.access.AccessRule
import cdme.model.error.{ErrorObject, ValidationError}

/**
 * Registration record for an external black-box calculator.
 *
 * External calculators are registered as standard morphisms with typed
 * domain/codomain declarations. Determinism is asserted by contract
 * (declared, not verified at runtime). See SA-04 for contract violation handling.
 *
 * @tparam A the input (domain) type
 * @tparam B the output (codomain) type
 * @param name              calculator name
 * @param version           calculator version for lineage
 * @param domainType        declared input type
 * @param codomainType      declared output type
 * @param isDeterministic   determinism assertion (by contract)
 */
final case class ExternalCalculator[A, B](
    name: String,
    version: String,
    domainType: CdmeType,
    codomainType: CdmeType,
    isDeterministic: Boolean,
    computeFn: A => Either[ErrorObject, B]
)

/**
 * Registered morphism wrapper for an external calculator.
 */
final case class RegisteredCalculatorMorphism[A, B](
    id: MorphismId,
    name: String,
    domain: EntityId,
    codomain: EntityId,
    calculator: ExternalCalculator[A, B],
    containmentBounds: ContainmentBounds,
    accessRules: List[AccessRule] = Nil
) extends AdjointMorphism[A, B]:

  override val cardinality: CardinalityType = CardinalityType.OneToOne
  override val containment: ContainmentType = ContainmentType.Opaque

  override def forward(input: A): Either[ErrorObject, B] =
    calculator.computeFn(input)

  override def backward(output: B): BackwardResult[A] =
    // Opaque backward: declared containment bounds but no actual inverse
    BackwardResult(
      records = Vector.empty,
      metadata = Map(
        "adjointType" -> "opaque",
        "calculator" -> calculator.name,
        "version" -> calculator.version,
        "containmentUpper" -> containmentBounds.upperBound.toString,
        "containmentLower" -> containmentBounds.lowerBound.toString
      )
    )

/**
 * Manually declared containment bounds for opaque adjoints.
 *
 * @param upperBound maximum records in preimage (worst case)
 * @param lowerBound minimum records in preimage (best case)
 */
final case class ContainmentBounds(
    upperBound: Long,
    lowerBound: Long = 1L
)

/**
 * Registry for external calculators.
 */
object CalculatorRegistry:

  /**
   * Register an external calculator as a standard morphism.
   *
   * @param calc              the calculator registration
   * @param domain            the domain entity
   * @param codomain          the codomain entity
   * @param containmentBounds declared containment bounds
   * @return Right(registeredMorphism) or Left(error)
   */
  def register[A, B](
      calc: ExternalCalculator[A, B],
      domain: EntityId,
      codomain: EntityId,
      containmentBounds: ContainmentBounds
  ): Either[ValidationError, RegisteredCalculatorMorphism[A, B]] =
    // Type compatibility checking would happen here against the category
    Right(RegisteredCalculatorMorphism(
      id = MorphismId(s"ext_${calc.name}_${calc.version}"),
      name = s"external:${calc.name}",
      domain = domain,
      codomain = codomain,
      calculator = calc,
      containmentBounds = containmentBounds
    ))
