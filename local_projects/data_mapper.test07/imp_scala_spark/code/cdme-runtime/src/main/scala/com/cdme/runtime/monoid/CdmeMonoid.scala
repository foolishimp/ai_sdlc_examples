// Implements: REQ-LDM-04, REQ-LDM-04-A
// Monoid definitions for aggregation operations.
package com.cdme.runtime.monoid

/**
 * A Monoid is a type with an associative binary operation and an identity element.
 * All aggregation functions in CDME must satisfy monoid laws.
 *
 * Implements: REQ-LDM-04, REQ-LDM-04-A
 */
trait CdmeMonoid[A] {
  /** The identity element: combine(identity, a) == combine(a, identity) == a */
  def identity: A

  /** The binary operation: must be associative */
  def combine(x: A, y: A): A
}

object CdmeMonoid {

  /** Sum monoid for Long values. Identity = 0. */
  val sumLong: CdmeMonoid[Long] = new CdmeMonoid[Long] {
    val identity: Long = 0L
    def combine(x: Long, y: Long): Long = x + y
  }

  /** Sum monoid for Double values. Identity = 0.0. */
  val sumDouble: CdmeMonoid[Double] = new CdmeMonoid[Double] {
    val identity: Double = 0.0
    def combine(x: Double, y: Double): Double = x + y
  }

  /** Product monoid for Long values. Identity = 1. */
  val productLong: CdmeMonoid[Long] = new CdmeMonoid[Long] {
    val identity: Long = 1L
    def combine(x: Long, y: Long): Long = x * y
  }

  /** Count monoid: counts elements. Identity = 0. */
  val count: CdmeMonoid[Long] = new CdmeMonoid[Long] {
    val identity: Long = 0L
    def combine(x: Long, y: Long): Long = x + y
  }

  /** Min monoid for Long. Identity = Long.MaxValue. */
  val minLong: CdmeMonoid[Long] = new CdmeMonoid[Long] {
    val identity: Long = Long.MaxValue
    def combine(x: Long, y: Long): Long = Math.min(x, y)
  }

  /** Max monoid for Long. Identity = Long.MinValue. */
  val maxLong: CdmeMonoid[Long] = new CdmeMonoid[Long] {
    val identity: Long = Long.MinValue
    def combine(x: Long, y: Long): Long = Math.max(x, y)
  }

  /** String concatenation monoid. Identity = "". */
  val concatString: CdmeMonoid[String] = new CdmeMonoid[String] {
    val identity: String = ""
    def combine(x: String, y: String): String = x + y
  }

  /** Boolean AND monoid. Identity = true. */
  val andBoolean: CdmeMonoid[Boolean] = new CdmeMonoid[Boolean] {
    val identity: Boolean = true
    def combine(x: Boolean, y: Boolean): Boolean = x && y
  }

  /** Boolean OR monoid. Identity = false. */
  val orBoolean: CdmeMonoid[Boolean] = new CdmeMonoid[Boolean] {
    val identity: Boolean = false
    def combine(x: Boolean, y: Boolean): Boolean = x || y
  }

  /** List concatenation monoid. */
  def listMonoid[A]: CdmeMonoid[List[A]] = new CdmeMonoid[List[A]] {
    val identity: List[A] = Nil
    def combine(x: List[A], y: List[A]): List[A] = x ++ y
  }

  /** Set union monoid. */
  def setMonoid[A]: CdmeMonoid[Set[A]] = new CdmeMonoid[Set[A]] {
    val identity: Set[A] = Set.empty
    def combine(x: Set[A], y: Set[A]): Set[A] = x ++ y
  }

  /**
   * Fold a collection using a monoid.
   * Empty collections return the identity element (REQ-LDM-04-A).
   */
  def fold[A](values: Iterable[A])(implicit monoid: CdmeMonoid[A]): A =
    values.foldLeft(monoid.identity)(monoid.combine)
}
