package com.cdme.model.monoid

// Implements: REQ-F-LDM-004, REQ-F-LDM-005

/**
 * A Monoid defines an associative binary operation with an identity element.
 *
 * In CDME, monoids are the formal mechanism for aggregation. Every
 * [[com.cdme.model.morphism.AlgebraicMorphism]] declares a monoid that
 * the runtime uses to fold values when changing grain.
 *
 * Laws that must hold:
 *  - '''Associativity''': `combine(combine(a, b), c) == combine(a, combine(b, c))`
 *  - '''Identity''': `combine(a, empty) == a` and `combine(empty, a) == a`
 *
 * These laws are validated by the compiler (REQ-F-LDM-004) and can be
 * property-tested via `cdme-testkit` MonoidLawAssertions.
 *
 * @tparam A the type of values being aggregated
 */
trait Monoid[A] {

  /**
   * Associatively combine two values.
   *
   * @param a the first value
   * @param b the second value
   * @return the combined result
   */
  def combine(a: A, b: A): A

  /**
   * The identity element. Combining any value with `empty` must return
   * the original value unchanged.
   *
   * @return the identity element
   */
  def empty: A
}

// ---------------------------------------------------------------------------
// Built-in monoid instances
// ---------------------------------------------------------------------------

/**
 * Monoid under addition for Long values.
 *
 * Identity: 0L
 * Operation: a + b
 */
object SumMonoid extends Monoid[Long] {
  override def combine(a: Long, b: Long): Long = a + b
  override def empty: Long = 0L
}

/**
 * Monoid that counts occurrences (equivalent to SumMonoid when each
 * input contributes 1).
 *
 * Identity: 0L
 * Operation: a + b
 */
object CountMonoid extends Monoid[Long] {
  override def combine(a: Long, b: Long): Long = a + b
  override def empty: Long = 0L
}

/**
 * Monoid that selects the minimum value.
 *
 * Uses Option to represent the identity: None is the identity element,
 * indicating no value has been seen yet.
 *
 * Identity: None
 * Operation: min(a, b)
 *
 * @tparam A the value type, which must have an Ordering
 */
class MinMonoid[A](implicit ord: Ordering[A]) extends Monoid[Option[A]] {
  override def combine(a: Option[A], b: Option[A]): Option[A] = (a, b) match {
    case (Some(x), Some(y)) => Some(ord.min(x, y))
    case (Some(_), None)    => a
    case (None, Some(_))    => b
    case (None, None)       => None
  }
  override def empty: Option[A] = None
}

/**
 * Monoid that selects the maximum value.
 *
 * Uses Option to represent the identity: None is the identity element.
 *
 * Identity: None
 * Operation: max(a, b)
 *
 * @tparam A the value type, which must have an Ordering
 */
class MaxMonoid[A](implicit ord: Ordering[A]) extends Monoid[Option[A]] {
  override def combine(a: Option[A], b: Option[A]): Option[A] = (a, b) match {
    case (Some(x), Some(y)) => Some(ord.max(x, y))
    case (Some(_), None)    => a
    case (None, Some(_))    => b
    case (None, None)       => None
  }
  override def empty: Option[A] = None
}

/**
 * Monoid under string concatenation.
 *
 * Identity: ""
 * Operation: a + b (string concatenation)
 */
object ConcatMonoid extends Monoid[String] {
  override def combine(a: String, b: String): String = a + b
  override def empty: String = ""
}

/**
 * Monoid under list concatenation.
 *
 * Identity: Nil
 * Operation: a ++ b (list concatenation)
 *
 * @tparam A the element type
 */
class ListMonoid[A] extends Monoid[List[A]] {
  override def combine(a: List[A], b: List[A]): List[A] = a ++ b
  override def empty: List[A] = Nil
}
