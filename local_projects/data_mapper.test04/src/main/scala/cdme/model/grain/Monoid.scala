// Implements: REQ-F-LDM-006, REQ-F-LDM-007, REQ-F-INT-003
// ADR-008: Monoid Typeclass for Aggregation
package cdme.model.grain

/**
 * Monoid typeclass for aggregation operations.
 *
 * ADR-008 mandates using the typeclass pattern for monoidal aggregation.
 * Every aggregation morphism must provide a Monoid instance. The monoid laws
 * (associativity and identity) must hold:
 *
 *   - combine(combine(a, b), c) == combine(a, combine(b, c))  (associativity)
 *   - combine(a, empty) == a  (right identity)
 *   - combine(empty, a) == a  (left identity)
 *
 * Custom monoids must assert associativity by declaration. Property-based
 * testing is recommended but not enforced by the system.
 *
 * @tparam T the type being aggregated
 */
trait Monoid[T]:
  /** The identity element. combine(a, empty) == a and combine(empty, a) == a. */
  def empty: T

  /** Associative binary operation. */
  def combine(a: T, b: T): T

  /** Human-readable name for lineage and diagnostics. */
  def name: String

object Monoid:

  /** Summation monoid for numeric types. */
  given SumMonoidLong: Monoid[Long] with
    def empty: Long = 0L
    def combine(a: Long, b: Long): Long = a + b
    def name: String = "SumMonoid[Long]"

  /** Summation monoid for Int. */
  given SumMonoidInt: Monoid[Int] with
    def empty: Int = 0
    def combine(a: Int, b: Int): Int = a + b
    def name: String = "SumMonoid[Int]"

  /** Summation monoid for Double. */
  given SumMonoidDouble: Monoid[Double] with
    def empty: Double = 0.0
    def combine(a: Double, b: Double): Double = a + b
    def name: String = "SumMonoid[Double]"

  /** Summation monoid for BigDecimal. */
  given SumMonoidBigDecimal: Monoid[BigDecimal] with
    def empty: BigDecimal = BigDecimal(0)
    def combine(a: BigDecimal, b: BigDecimal): BigDecimal = a + b
    def name: String = "SumMonoid[BigDecimal]"

  /** Count monoid: always adds 1 per element. Typically used with mapCombine. */
  val CountMonoid: Monoid[Long] = new Monoid[Long]:
    def empty: Long = 0L
    def combine(a: Long, b: Long): Long = a + b
    def name: String = "CountMonoid"

  /** Min monoid for Long (identity is Long.MaxValue). */
  given MinMonoidLong: Monoid[Long] with
    def empty: Long = Long.MaxValue
    def combine(a: Long, b: Long): Long = math.min(a, b)
    def name: String = "MinMonoid[Long]"

  /** Max monoid for Long (identity is Long.MinValue). */
  given MaxMonoidLong: Monoid[Long] with
    def empty: Long = Long.MinValue
    def combine(a: Long, b: Long): Long = math.max(a, b)
    def name: String = "MaxMonoid[Long]"

  /** String concatenation monoid. */
  given ConcatMonoid: Monoid[String] with
    def empty: String = ""
    def combine(a: String, b: String): String = a + b
    def name: String = "ConcatMonoid"

  /** Helper to create custom monoids. */
  def custom[T](emptyVal: T, combineFn: (T, T) => T, monoidName: String): Monoid[T] =
    new Monoid[T]:
      def empty: T = emptyVal
      def combine(a: T, b: T): T = combineFn(a, b)
      def name: String = monoidName

  /** Combine a collection of values using the monoid. */
  extension [T](values: Iterable[T])
    def combineAll(using m: Monoid[T]): T =
      values.foldLeft(m.empty)(m.combine)
