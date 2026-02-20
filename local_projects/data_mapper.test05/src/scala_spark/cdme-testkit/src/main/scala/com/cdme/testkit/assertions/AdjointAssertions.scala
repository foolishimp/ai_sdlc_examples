package com.cdme.testkit.assertions

// Implements: (test helpers for REQ-F-ADJ-001..003)

import com.cdme.model.adjoint.{AdjointPair, LossyContainment, SelfAdjoint}
import org.scalatest.Assertions.assert
import org.scalatest.Assertion

/**
 * ScalaTest assertion helpers for adjoint pair properties.
 *
 * These assertions verify the two containment laws that every adjoint
 * pair must satisfy:
 *
 *  - '''Containment law''' (REQ-F-ADJ-003): For any valid input `x`,
 *    `backward(forward(x))` must be a superset of `{x}`.
 *    That is, the backward function must recover at least the original input.
 *
 *  - '''Self-adjoint law''' (REQ-F-ADJ-002): For isomorphic (1:1) morphisms,
 *    `backward(forward(x))` must equal `{x}` exactly.
 *
 * Usage:
 * {{{
 * import com.cdme.testkit.assertions.AdjointAssertions._
 *
 * assertContainmentLaw(adjointPair, inputValue)
 * assertSelfAdjoint(adjointPair, inputValue)
 * }}}
 */
object AdjointAssertions {

  /**
   * Assert the containment law: `backward(forward(x)) supseteq {x}`.
   *
   * This is the general adjoint property that holds for all morphisms,
   * including lossy ones. The backward function is permitted to return
   * additional elements (superset), but must include the original input.
   *
   * If `forward(x)` returns `Left` (error), the assertion is trivially
   * satisfied because the input was rejected, not lost.
   *
   * @param adjoint the adjoint pair to test
   * @param input   the input value to test with
   * @tparam A the domain type
   * @tparam B the codomain type
   * @return a ScalaTest [[Assertion]]
   */
  def assertContainmentLaw[A, B](adjoint: AdjointPair[A, B], input: A): Assertion = {
    adjoint.forward(input) match {
      case Right(output) =>
        val recovered = adjoint.backward(output)
        assert(
          recovered.contains(input),
          s"Containment law violated: backward(forward($input)) = $recovered, " +
            s"which does not contain the original input"
        )
      case Left(_) =>
        // Forward produced an error — the input was rejected, not lost.
        // The containment law is trivially satisfied.
        assert(true)
    }
  }

  /**
   * Assert the self-adjoint law: `backward(forward(x)) == {x}`.
   *
   * This stricter property applies only to self-adjoint (isomorphic)
   * morphisms where the backward function is an exact inverse.
   * The recovered set must contain exactly the original input and nothing else.
   *
   * @param adjoint the adjoint pair to test (should have `SelfAdjoint` containment)
   * @param input   the input value to test with
   * @tparam A the domain type
   * @tparam B the codomain type
   * @return a ScalaTest [[Assertion]]
   */
  def assertSelfAdjoint[A, B](adjoint: AdjointPair[A, B], input: A): Assertion = {
    adjoint.forward(input) match {
      case Right(output) =>
        val recovered = adjoint.backward(output)
        assert(
          recovered == Set(input),
          s"Self-adjoint law violated: backward(forward($input)) = $recovered, " +
            s"expected exactly Set($input)"
        )
      case Left(error) =>
        // Forward produced an error — cannot verify self-adjoint property.
        // This is acceptable: the self-adjoint property only applies to
        // successful forward executions.
        assert(true, s"Forward returned error: $error — self-adjoint check skipped")
    }
  }

  /**
   * Assert the containment law for all values in a collection.
   *
   * Convenience method for batch testing.
   *
   * @param adjoint the adjoint pair to test
   * @param inputs  the collection of input values to test
   * @tparam A the domain type
   * @tparam B the codomain type
   * @return a ScalaTest [[Assertion]]
   */
  def assertContainmentLawForAll[A, B](
    adjoint: AdjointPair[A, B],
    inputs: Iterable[A]
  ): Assertion = {
    inputs.foreach(input => assertContainmentLaw(adjoint, input))
    assert(true)
  }

  /**
   * Assert that the adjoint pair's containment type is consistent with
   * its actual behavior on the given input.
   *
   * If the containment type is [[SelfAdjoint]], verifies the strict
   * equality property. If [[LossyContainment]], verifies only the
   * superset property.
   *
   * @param adjoint the adjoint pair to test
   * @param input   the input value to test with
   * @tparam A the domain type
   * @tparam B the codomain type
   * @return a ScalaTest [[Assertion]]
   */
  def assertContainmentTypeConsistency[A, B](
    adjoint: AdjointPair[A, B],
    input: A
  ): Assertion = {
    adjoint.containmentType match {
      case SelfAdjoint      => assertSelfAdjoint(adjoint, input)
      case LossyContainment => assertContainmentLaw(adjoint, input)
    }
  }
}
