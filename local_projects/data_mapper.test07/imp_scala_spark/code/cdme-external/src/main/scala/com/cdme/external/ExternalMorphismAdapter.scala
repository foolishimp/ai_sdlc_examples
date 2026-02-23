// Implements: REQ-INT-08
// Adapts external calculators to behave as CDME morphisms.
package com.cdme.external

import com.cdme.model._
import com.cdme.model.adjoint.{AdjointSpec, KleisliAdjoint}

/**
 * Wraps an external calculator to behave as a Morphism in the LDM topology.
 * Implements: REQ-INT-08
 */
object ExternalMorphismAdapter {

  /**
   * Convert an external calculator to a Morphism.
   * The morphism kind is External, and cardinality is OneToOne (standard function).
   */
  def asMorphism(
      calculator: ExternalCalculator,
      domain: EntityId,
      codomain: EntityId
  ): Morphism = Morphism(
    id = s"ext_${calculator.id}",
    name = s"External(${calculator.name})",
    domain = domain,
    codomain = codomain,
    cardinality = Cardinality.OneToOne,
    morphismKind = MorphismKind.External,
    adjoint = None,
    accessControl = AccessControl.open
  )

  /**
   * Create an adjoint spec for an external calculator, if a backward
   * implementation is provided.
   */
  def asAdjointSpec(
      calculator: ExternalCalculator,
      hasBackward: Boolean
  ): Option[AdjointSpec] = {
    if (hasBackward) Some(KleisliAdjoint(parentChildCapture = false))
    else None
  }
}
