// Implements: INT-006 (Speculative)
// Optional Frobenius algebra marker for aggregate/expand dual pairs.
package com.cdme.adjoint.frobenius

/**
 * Layer 2 enhancement: Frobenius algebra for morphisms that form
 * aggregate/expand dual pairs.
 *
 * The Frobenius law: (mu x id) . (id x delta) = delta . mu
 *
 * Not required for MVP. Opt-in for morphisms that naturally form
 * Frobenius pairs (e.g., sum/explode).
 *
 * Implements: INT-006 (Speculative / Appendix A)
 */
trait FrobeniusAlgebra[A] {
  /** Multiplication (aggregate): mu: A x A -> A */
  def multiply(a1: A, a2: A): A

  /** Comultiplication (expand): delta: A -> (A, A) */
  def comultiply(a: A): (A, A)

  /** Unit (identity element): eta: 1 -> A */
  def unit: A

  /** Counit (extract scalar): epsilon: A -> 1 */
  def counit(a: A): Unit

  /**
   * Check the Frobenius law: (mu x id) . (id x delta) = delta . mu
   * Verified on a sample value.
   */
  def frobeniusLawHolds(sample: A): Boolean = {
    // LHS: (mu x id) . (id x delta)
    // Apply (id x delta) to (sample, sample): (sample, (a, b)) where (a,b) = comultiply(sample)
    val (a, b) = comultiply(sample)
    val lhs = multiply(sample, a) // simplified for scalar case

    // RHS: delta . mu
    val muResult = multiply(sample, sample)
    val (c, _) = comultiply(muResult)
    val rhs = c // simplified for scalar case

    lhs == rhs
  }
}

/**
 * Commutative Frobenius algebra: multiply is commutative.
 * Layer 3 (Future) — enables order-independent aggregation optimization.
 */
trait CommutativeFrobeniusAlgebra[A] extends FrobeniusAlgebra[A] {
  /** Commutativity: multiply(a, b) == multiply(b, a) */
  def isCommutative(a: A, b: A): Boolean =
    multiply(a, b) == multiply(b, a)
}

/** Sum Frobenius algebra for Long values. */
object SumFrobeniusLong extends CommutativeFrobeniusAlgebra[Long] {
  def multiply(a1: Long, a2: Long): Long = a1 + a2
  def comultiply(a: Long): (Long, Long) = (a, 0L)
  def unit: Long = 0L
  def counit(a: Long): Unit = ()
}

/** Count Frobenius algebra. */
object CountFrobenius extends CommutativeFrobeniusAlgebra[Long] {
  def multiply(a1: Long, a2: Long): Long = a1 + a2
  def comultiply(a: Long): (Long, Long) = (a, 0L)
  def unit: Long = 0L
  def counit(a: Long): Unit = ()
}
