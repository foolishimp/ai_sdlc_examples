// Implements: REQ-INT-08
// External calculator registry: registers black-box morphisms.
package com.cdme.external

import com.cdme.model._
import com.cdme.model.types.CdmeType
import com.cdme.model.error.{CdmeError, ExternalCalculatorError, ConfigError}

/**
 * Registry for external calculators (black-box computational morphisms).
 * Registers calculators with their type signatures and determinism contracts.
 *
 * Implements: REQ-INT-08
 * See ADR-010: External Calculator Registry
 */
final case class ExternalCalculatorRegistry(
    calculators: Map[CalculatorId, ExternalCalculator]
) {

  /**
   * Register a new external calculator.
   * Validates that the calculator ID is unique.
   */
  def register(calculator: ExternalCalculator): Either[CdmeError, ExternalCalculatorRegistry] = {
    if (calculators.contains(calculator.id)) {
      Left(ConfigError(s"Calculator '${calculator.id}' already registered"))
    } else {
      Right(copy(calculators = calculators + (calculator.id -> calculator)))
    }
  }

  /**
   * Look up a registered calculator by ID.
   */
  def lookup(id: CalculatorId): Either[CdmeError, ExternalCalculator] =
    calculators.get(id) match {
      case Some(calc) => Right(calc)
      case None       => Left(ExternalCalculatorError(id, s"Calculator '$id' not found in registry"))
    }

  /**
   * Invoke a registered calculator.
   * The actual computation is delegated to the calculator's function.
   */
  def invoke(
      id: CalculatorId,
      input: Any
  ): Either[CdmeError, Any] = {
    lookup(id).flatMap { calc =>
      calc.function match {
        case Some(f) =>
          try {
            Right(f(input))
          } catch {
            case e: Exception =>
              Left(ExternalCalculatorError(id, s"Invocation failed: ${e.getMessage}"))
          }
        case None =>
          Left(ExternalCalculatorError(id, "Calculator has no registered function"))
      }
    }
  }

  /** All registered calculator IDs. */
  def registeredIds: Set[CalculatorId] = calculators.keySet
}

object ExternalCalculatorRegistry {
  val empty: ExternalCalculatorRegistry = ExternalCalculatorRegistry(Map.empty)
}

/**
 * An external calculator registration.
 * Implements: REQ-INT-08
 */
final case class ExternalCalculator(
    id: CalculatorId,
    name: String,
    version: String,
    domainType: CdmeType,
    codomainType: CdmeType,
    determinismAssertion: Boolean,
    metadata: Map[String, String],
    function: Option[Any => Any]
)

/**
 * Determinism contract for external calculators.
 * Trust-based: the registrant asserts determinism, we do not runtime-verify.
 */
final case class DeterminismContract(
    calculatorId: CalculatorId,
    isDeterministic: Boolean,
    assertedBy: String,
    assertedAt: java.time.Instant
)
