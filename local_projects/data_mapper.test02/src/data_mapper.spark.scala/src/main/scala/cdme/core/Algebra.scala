package cdme.core

import cats.Monoid

/**
 * Algebraic abstractions for CDME.
 * Implements: REQ-ADJ-01 (Monoid-based aggregations)
 */

/**
 * Aggregator type class.
 */
trait Aggregator[A, B] {
  def zero: B
  def combine(b: B, a: A): B
  def merge(b1: B, b2: B): B
  def finish(b: B): B
}

object Aggregator {

  /**
   * Create aggregator from Monoid.
   */
  def fromMonoid[A, B](extract: A => B)(implicit M: Monoid[B]): Aggregator[A, B] =
    new Aggregator[A, B] {
      def zero: B = M.empty
      def combine(b: B, a: A): B = M.combine(b, extract(a))
      def merge(b1: B, b2: B): B = M.combine(b1, b2)
      def finish(b: B): B = b
    }
}

/**
 * Common monoid instances for aggregations.
 */
object MonoidInstances {

  /**
   * Monoid for BigDecimal sum.
   */
  implicit val bigDecimalSumMonoid: Monoid[BigDecimal] = new Monoid[BigDecimal] {
    def empty: BigDecimal = BigDecimal(0)
    def combine(x: BigDecimal, y: BigDecimal): BigDecimal = x + y
  }

  /**
   * Monoid for Long sum (counts).
   */
  implicit val longSumMonoid: Monoid[Long] = new Monoid[Long] {
    def empty: Long = 0L
    def combine(x: Long, y: Long): Long = x + y
  }

  /**
   * Monoid for Min.
   */
  def minMonoid[A: Ordering]: Monoid[Option[A]] = new Monoid[Option[A]] {
    def empty: Option[A] = None
    def combine(x: Option[A], y: Option[A]): Option[A] = (x, y) match {
      case (Some(a), Some(b)) => Some(if (implicitly[Ordering[A]].lt(a, b)) a else b)
      case (Some(a), None) => Some(a)
      case (None, Some(b)) => Some(b)
      case (None, None) => None
    }
  }

  /**
   * Monoid for Max.
   */
  def maxMonoid[A: Ordering]: Monoid[Option[A]] = new Monoid[Option[A]] {
    def empty: Option[A] = None
    def combine(x: Option[A], y: Option[A]): Option[A] = (x, y) match {
      case (Some(a), Some(b)) => Some(if (implicitly[Ordering[A]].gt(a, b)) a else b)
      case (Some(a), None) => Some(a)
      case (None, Some(b)) => Some(b)
      case (None, None) => None
    }
  }

  /**
   * Monoid for Average (sum and count).
   */
  case class Avg(sum: BigDecimal, count: Long) {
    def average: Option[BigDecimal] =
      if (count > 0) Some(sum / count) else None
  }

  implicit val avgMonoid: Monoid[Avg] = new Monoid[Avg] {
    def empty: Avg = Avg(BigDecimal(0), 0)
    def combine(x: Avg, y: Avg): Avg =
      Avg(x.sum + y.sum, x.count + y.count)
  }
}

/**
 * Aggregation strategy.
 * Implements: REQ-ADJ-02 (Group By Optimization)
 */
sealed trait AggregationStrategy
object AggregationStrategy {
  case object Partial extends AggregationStrategy
  case class Salted(buckets: Int) extends AggregationStrategy
  case object Bucketed extends AggregationStrategy
  case class Broadcast(dimensionTable: String) extends AggregationStrategy
}
