package com.cdme.model

// Implements: REQ-LDM-04 (Algebraic Aggregation / Monoid Laws)
trait Monoid[A] {
  def empty: A
  def combine(x: A, y: A): A
}

object Monoid {
  def apply[A](implicit m: Monoid[A]): Monoid[A] = m

  implicit val sumInt: Monoid[Int] = new Monoid[Int] {
    def empty: Int = 0
    def combine(x: Int, y: Int): Int = x + y
  }

  implicit val sumLong: Monoid[Long] = new Monoid[Long] {
    def empty: Long = 0L
    def combine(x: Long, y: Long): Long = x + y
  }

  implicit val sumDouble: Monoid[Double] = new Monoid[Double] {
    def empty: Double = 0.0
    def combine(x: Double, y: Double): Double = x + y
  }

  val productDouble: Monoid[Double] = new Monoid[Double] {
    def empty: Double = 1.0
    def combine(x: Double, y: Double): Double = x * y
  }

  implicit val concatString: Monoid[String] = new Monoid[String] {
    def empty: String = ""
    def combine(x: String, y: String): String = x + y
  }

  implicit def optionMonoid[A](implicit m: Monoid[A]): Monoid[Option[A]] = new Monoid[Option[A]] {
    def empty: Option[A] = None
    def combine(x: Option[A], y: Option[A]): Option[A] = (x, y) match {
      case (Some(a), Some(b)) => Some(m.combine(a, b))
      case (Some(a), None) => Some(a)
      case (None, Some(b)) => Some(b)
      case (None, None) => None
    }
  }

  implicit def listMonoid[A]: Monoid[List[A]] = new Monoid[List[A]] {
    def empty: List[A] = Nil
    def combine(x: List[A], y: List[A]): List[A] = x ++ y
  }

  /** Named monoid registry for mapping definitions */
  val registry: Map[String, Monoid[_]] = Map(
    "sum_int" -> sumInt,
    "sum_long" -> sumLong,
    "sum_double" -> sumDouble,
    "product_double" -> productDouble,
    "concat_string" -> concatString
  )
}
