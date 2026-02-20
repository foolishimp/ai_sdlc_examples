// Implements: REQ-F-LDM-006, REQ-F-LDM-007, REQ-F-INT-003
// ADR-008: Monoid Typeclass for Aggregation
package cdme.model.grain

/**
 * Monoid typeclass for aggregation operations.
 *
 * @tparam T the type being aggregated
 */
trait Monoid[T] {
  def empty: T
  def combine(a: T, b: T): T
  def name: String
}

object Monoid {

  implicit val SumMonoidLong: Monoid[Long] = new Monoid[Long] {
    def empty: Long = 0L
    def combine(a: Long, b: Long): Long = a + b
    def name: String = "SumMonoid[Long]"
  }

  implicit val SumMonoidInt: Monoid[Int] = new Monoid[Int] {
    def empty: Int = 0
    def combine(a: Int, b: Int): Int = a + b
    def name: String = "SumMonoid[Int]"
  }

  implicit val SumMonoidDouble: Monoid[Double] = new Monoid[Double] {
    def empty: Double = 0.0
    def combine(a: Double, b: Double): Double = a + b
    def name: String = "SumMonoid[Double]"
  }

  implicit val SumMonoidBigDecimal: Monoid[BigDecimal] = new Monoid[BigDecimal] {
    def empty: BigDecimal = BigDecimal(0)
    def combine(a: BigDecimal, b: BigDecimal): BigDecimal = a + b
    def name: String = "SumMonoid[BigDecimal]"
  }

  val CountMonoid: Monoid[Long] = new Monoid[Long] {
    def empty: Long = 0L
    def combine(a: Long, b: Long): Long = a + b
    def name: String = "CountMonoid"
  }

  implicit val MinMonoidLong: Monoid[Long] = new Monoid[Long] {
    def empty: Long = Long.MaxValue
    def combine(a: Long, b: Long): Long = math.min(a, b)
    def name: String = "MinMonoid[Long]"
  }

  implicit val MaxMonoidLong: Monoid[Long] = new Monoid[Long] {
    def empty: Long = Long.MinValue
    def combine(a: Long, b: Long): Long = math.max(a, b)
    def name: String = "MaxMonoid[Long]"
  }

  implicit val ConcatMonoid: Monoid[String] = new Monoid[String] {
    def empty: String = ""
    def combine(a: String, b: String): String = a + b
    def name: String = "ConcatMonoid"
  }

  def custom[T](emptyVal: T, combineFn: (T, T) => T, monoidName: String): Monoid[T] =
    new Monoid[T] {
      def empty: T = emptyVal
      def combine(a: T, b: T): T = combineFn(a, b)
      def name: String = monoidName
    }

  implicit class MonoidOps[T](val values: Iterable[T]) extends AnyVal {
    def combineAll(implicit m: Monoid[T]): T =
      values.foldLeft(m.empty)(m.combine)
  }
}
