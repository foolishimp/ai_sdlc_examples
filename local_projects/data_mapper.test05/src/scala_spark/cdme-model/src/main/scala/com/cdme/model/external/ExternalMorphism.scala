package com.cdme.model.external

// Implements: REQ-F-SYN-008

import com.cdme.model.types.CdmeType

/**
 * Registration record for an external computational morphism.
 *
 * External morphisms are calculators implemented outside the CDME engine
 * (e.g. in a separate service or library). The registration captures the
 * contract: input/output types and the registrant's assertion of determinism.
 *
 * Per REQ-F-SYN-008 and design decision SC-001, the engine logs the
 * determinism assertion but does not verify it at runtime. The registrant
 * is responsible for ensuring deterministic behaviour when asserted.
 *
 * @param id                    unique identifier for the external calculator
 * @param calculatorVersion     version of the calculator implementation
 * @param domain                the input type expected by the calculator
 * @param codomain              the output type produced by the calculator
 * @param deterministicAsserted whether the registrant asserts deterministic behaviour
 */
case class ExternalMorphismRegistration(
  id: String,
  calculatorVersion: String,
  domain: CdmeType,
  codomain: CdmeType,
  deterministicAsserted: Boolean
)

// Implements: REQ-F-SYN-007

/**
 * A key generator produces deterministic, reproducible keys from input values.
 *
 * Key generators are used to create stable identifiers for output records
 * so that the same inputs always produce the same keys. This supports
 * deterministic reproducibility (REQ-F-TRV-005) and enables adjoint
 * backward traversal by key.
 *
 * Implementations must be:
 *  - '''Deterministic''': same inputs always produce the same key
 *  - '''Collision-resistant''': different inputs should produce different keys
 *  - '''Reproducible''': key generation does not depend on external state
 */
trait KeyGenerator {

  /**
   * Generate a deterministic key from the given input values.
   *
   * @param inputs the values from which to derive the key
   * @return a stable string key
   */
  def generate(inputs: Any*): String
}
