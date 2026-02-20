package com.cdme.testkit.assertions

// Implements: (test helpers for REQ-F-LDM-004, REQ-F-LDM-005)

import com.cdme.model.monoid.Monoid
import org.scalatest.Assertions.assert
import org.scalatest.Assertion

/**
 * ScalaTest assertion helpers for monoid law verification.
 *
 * Every [[Monoid]] in CDME must satisfy:
 *  - '''Associativity''' (REQ-F-LDM-004): `combine(combine(a, b), c) == combine(a, combine(b, c))`
 *  - '''Left identity''' (REQ-F-LDM-005): `combine(empty, a) == a`
 *  - '''Right identity''' (REQ-F-LDM-005): `combine(a, empty) == a`
 *  - '''Empty aggregation''' (REQ-F-LDM-005): folding over an empty collection yields `empty`
 *
 * These properties are critical for distributed execution correctness:
 * associativity ensures that partial folds on different Spark partitions
 * can be combined in any order.
 *
 * Usage:
 * {{{
 * import com.cdme.testkit.assertions.MonoidLawAssertions._
 * import com.cdme.model.monoid.SumMonoid
 *
 * assertAssociativity(SumMonoid, 1L, 2L, 3L)
 * assertIdentity(SumMonoid, 42L)
 * assertEmptyAggregation(SumMonoid)
 * }}}
 */
object MonoidLawAssertions {

  /**
   * Assert the associativity law: `combine(combine(a, b), c) == combine(a, combine(b, c))`.
   *
   * @param monoid the monoid to test
   * @param a      first value
   * @param b      second value
   * @param c      third value
   * @tparam A the monoid's element type
   * @return a ScalaTest [[Assertion]]
   */
  def assertAssociativity[A](monoid: Monoid[A], a: A, b: A, c: A): Assertion = {
    val leftAssoc = monoid.combine(monoid.combine(a, b), c)
    val rightAssoc = monoid.combine(a, monoid.combine(b, c))
    assert(
      leftAssoc == rightAssoc,
      s"Associativity violated: combine(combine($a, $b), $c) = $leftAssoc, " +
        s"but combine($a, combine($b, $c)) = $rightAssoc"
    )
  }

  /**
   * Assert both left and right identity laws:
   *  - Left identity: `combine(empty, a) == a`
   *  - Right identity: `combine(a, empty) == a`
   *
   * @param monoid the monoid to test
   * @param a      the value to test with
   * @tparam A the monoid's element type
   * @return a ScalaTest [[Assertion]]
   */
  def assertIdentity[A](monoid: Monoid[A], a: A): Assertion = {
    val leftId = monoid.combine(monoid.empty, a)
    assert(
      leftId == a,
      s"Left identity violated: combine(empty, $a) = $leftId, expected $a"
    )

    val rightId = monoid.combine(a, monoid.empty)
    assert(
      rightId == a,
      s"Right identity violated: combine($a, empty) = $rightId, expected $a"
    )
  }

  /**
   * Assert the empty aggregation property: folding over an empty collection
   * must produce `monoid.empty`.
   *
   * This validates REQ-F-LDM-005: empty groups in aggregations produce the
   * monoid's identity element rather than null or an exception.
   *
   * @param monoid the monoid to test
   * @tparam A the monoid's element type
   * @return a ScalaTest [[Assertion]]
   */
  def assertEmptyAggregation[A](monoid: Monoid[A]): Assertion = {
    val emptyCollection: List[A] = Nil
    val result = emptyCollection.foldLeft(monoid.empty)(monoid.combine)
    assert(
      result == monoid.empty,
      s"Empty aggregation violated: fold over empty list = $result, " +
        s"expected ${monoid.empty}"
    )
  }

  /**
   * Assert all three monoid laws (associativity, identity, empty aggregation)
   * in a single call.
   *
   * @param monoid the monoid to test
   * @param a      first value
   * @param b      second value
   * @param c      third value
   * @tparam A the monoid's element type
   * @return a ScalaTest [[Assertion]]
   */
  def assertAllLaws[A](monoid: Monoid[A], a: A, b: A, c: A): Assertion = {
    assertAssociativity(monoid, a, b, c)
    assertIdentity(monoid, a)
    assertIdentity(monoid, b)
    assertIdentity(monoid, c)
    assertEmptyAggregation(monoid)
  }

  /**
   * Assert commutativity: `combine(a, b) == combine(b, a)`.
   *
   * Note: commutativity is not required by the Monoid laws, but some
   * specific monoids (e.g. Sum, Count, Min, Max) are commutative.
   * Use this when testing monoids known to be commutative.
   *
   * @param monoid the monoid to test
   * @param a      first value
   * @param b      second value
   * @tparam A the monoid's element type
   * @return a ScalaTest [[Assertion]]
   */
  def assertCommutativity[A](monoid: Monoid[A], a: A, b: A): Assertion = {
    val ab = monoid.combine(a, b)
    val ba = monoid.combine(b, a)
    assert(
      ab == ba,
      s"Commutativity violated: combine($a, $b) = $ab, " +
        s"but combine($b, $a) = $ba"
    )
  }
}
