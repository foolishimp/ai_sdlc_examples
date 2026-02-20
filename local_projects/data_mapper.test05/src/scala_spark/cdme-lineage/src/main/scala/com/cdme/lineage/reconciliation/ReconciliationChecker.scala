package com.cdme.lineage.reconciliation

// Implements: REQ-F-ADJ-006

/**
 * Result of a data reconciliation check.
 *
 * Reconciliation verifies the adjoint containment law:
 * `backward(forward(x)) supseteq x`
 *
 * Three outcomes are possible:
 *  - '''ExactMatch''': backward result exactly equals the original input
 *  - '''SupersetMatch''': backward result is a superset of the original input
 *    (valid — adjoint containment allows extra records)
 *  - '''Violation''': backward result is missing records from the original input
 *    (invalid — breaks the adjoint containment law)
 */
sealed trait ReconciliationResult

/**
 * The backward traversal result exactly matches the original input.
 * This is the ideal case for self-adjoint (lossless) morphisms.
 */
case object ExactMatch extends ReconciliationResult

/**
 * The backward traversal result is a strict superset of the original input.
 * This is valid for lossy morphisms where the backward function produces
 * a superset of the original keys.
 *
 * @param extra keys present in the backward result but not in the original input
 */
case class SupersetMatch(extra: Set[Any]) extends ReconciliationResult

/**
 * The backward traversal result is missing keys from the original input.
 * This is a violation of the adjoint containment law and indicates a bug
 * in the adjoint metadata capture or backward traversal logic.
 *
 * @param missing keys present in the original input but absent from the backward result
 */
case class Violation(missing: Set[Any]) extends ReconciliationResult

/**
 * Checks the adjoint containment law by reconciling the original input keys
 * with the backward traversal result.
 *
 * The containment law states: for every key `x` in the original input,
 * `x` must appear in `backward(forward(x))`.  The backward result may
 * contain additional keys (superset), but must not be missing any.
 */
object ReconciliationChecker {

  /**
   * Reconcile the original input key set against the backward traversal result.
   *
   * @param originalInput  the complete set of original input keys
   * @param backwardResult the set of keys recovered via backward traversal
   * @return the reconciliation result
   */
  def reconcile(
    originalInput: Set[Any],
    backwardResult: Set[Any]
  ): ReconciliationResult = {
    val missing = originalInput -- backwardResult
    val extra = backwardResult -- originalInput

    if (missing.nonEmpty) {
      Violation(missing)
    } else if (extra.isEmpty) {
      ExactMatch
    } else {
      SupersetMatch(extra)
    }
  }
}
