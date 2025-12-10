# ADR-008: Scala Aggregation Patterns for Adjoints

**Status**: Accepted
**Date**: 2025-12-10
**Deciders**: Design Agent
**Depends On**: ADR-006 (Scala Type System), ADR-007 (Either Monad)
**Implements**: REQ-ADJ-01, REQ-ADJ-02, REQ-ADJ-03, REQ-TYP-04

---

## Context

CDME adjoint morphisms require aggregations that:
- Implement monoid semantics (associative, commutative, identity)
- Support custom aggregation logic beyond SQL standard functions
- Preserve type safety through aggregation pipeline
- Scale efficiently (partial aggregation, salting, bucketing)
- Handle window functions (running totals, ranks, LAG/LEAD)

Spark provides:
- Built-in aggregations: `sum`, `count`, `avg`, `min`, `max`
- **Aggregator[IN, BUF, OUT]** class for custom aggregations
- UDAF (User-Defined Aggregation Functions) - SQL-level
- Window functions with `.over()` API

Requirements:
- REQ-ADJ-01: Full aggregations (SUM, COUNT, etc.)
- REQ-ADJ-02: Group By with optimization strategies
- REQ-ADJ-03: Window functions (grain-preserving)
- REQ-TYP-04: Type-safe aggregator composition

---

## Decision

**Use Spark's `Aggregator[IN, BUF, OUT]` class with the following patterns:**

1. **Typed Aggregator** - For monoid-based custom aggregations
2. **Monoid type class** - Abstract aggregation semantics
3. **Aggregator composition** - Combine multiple aggregations type-safely
4. **Window wrappers** - Type-safe window specification builders
5. **Strategy pattern** - Runtime-selectable optimization (salting, bucketing)

---

## Rationale

### 1. Aggregator[IN, BUF, OUT] for Type Safety

Spark's `Aggregator` provides type-safe aggregations:

```scala
import org.apache.spark.sql.expressions.Aggregator
import org.apache.spark.sql.Encoder
import org.apache.spark.sql.Encoders

// Custom aggregator: sum of positive amounts only
class PositiveAmountSum extends Aggregator[Order, BigDecimal, BigDecimal] {
  // Zero value (monoid identity)
  def zero: BigDecimal = BigDecimal(0)

  // Combine single input with buffer (map-side partial aggregation)
  def reduce(buffer: BigDecimal, order: Order): BigDecimal = {
    if (order.total_amount > 0) buffer + order.total_amount
    else buffer
  }

  // Merge two buffers (reduce-side final aggregation)
  def merge(b1: BigDecimal, b2: BigDecimal): BigDecimal = b1 + b2

  // Extract final result
  def finish(buffer: BigDecimal): BigDecimal = buffer

  // Encoders for buffer and output
  def bufferEncoder: Encoder[BigDecimal] = Encoders.scalaDecimal
  def outputEncoder: Encoder[BigDecimal] = Encoders.scalaDecimal
}

// Usage with Dataset
val result: Dataset[(String, BigDecimal)] = orders
  .groupByKey(_.customer_id)
  .agg(new PositiveAmountSum().toColumn.name("total_positive_amount"))
```

**Benefits**:
- Type-safe input, buffer, output
- Compiler-enforced monoid properties
- Automatic partial aggregation (Spark optimization)
- Works with Dataset[T], not just DataFrame

### 2. Monoid Type Class for Aggregation Semantics

Abstract aggregation using Cats Monoid:

```scala
import cats.Monoid
import cats.implicits._

// Monoid instance for BigDecimal sum
implicit val bigDecimalSumMonoid: Monoid[BigDecimal] = new Monoid[BigDecimal] {
  def empty: BigDecimal = BigDecimal(0)
  def combine(x: BigDecimal, y: BigDecimal): BigDecimal = x + y
}

// Generic monoid aggregator
class MonoidAggregator[IN, OUT: Monoid](extract: IN => OUT)
  extends Aggregator[IN, OUT, OUT] {

  private val M = Monoid[OUT]

  def zero: OUT = M.empty
  def reduce(buffer: OUT, input: IN): OUT = M.combine(buffer, extract(input))
  def merge(b1: OUT, b2: OUT): OUT = M.combine(b1, b2)
  def finish(buffer: OUT): OUT = buffer

  def bufferEncoder: Encoder[OUT] = Encoders.kryo[OUT]
  def outputEncoder: Encoder[OUT] = Encoders.kryo[OUT]
}

// Usage: sum amounts
val sumAgg = new MonoidAggregator[Order, BigDecimal](_.total_amount)

// Usage: count (use Count monoid)
case class Count(value: Long)
implicit val countMonoid: Monoid[Count] = new Monoid[Count] {
  def empty: Count = Count(0)
  def combine(x: Count, y: Count): Count = Count(x.value + y.value)
}

val countAgg = new MonoidAggregator[Order, Count](_ => Count(1))
```

**Benefits**:
- REQ-TYP-04: Monoid laws enforced by type class
- Reusable across different aggregation types
- Clear separation of aggregation logic from Spark plumbing

### 3. Aggregator Composition

Compose multiple aggregators without multiple passes:

```scala
// Combine sum and count in single pass
case class SumAndCount(sum: BigDecimal, count: Long)

implicit val sumAndCountMonoid: Monoid[SumAndCount] = new Monoid[SumAndCount] {
  def empty: SumAndCount = SumAndCount(BigDecimal(0), 0)
  def combine(x: SumAndCount, y: SumAndCount): SumAndCount =
    SumAndCount(x.sum + y.sum, x.count + y.count)
}

class SumAndCountAggregator extends Aggregator[Order, SumAndCount, SumAndCount] {
  def zero: SumAndCount = SumAndCount(BigDecimal(0), 0)

  def reduce(buffer: SumAndCount, order: Order): SumAndCount =
    SumAndCount(buffer.sum + order.total_amount, buffer.count + 1)

  def merge(b1: SumAndCount, b2: SumAndCount): SumAndCount =
    SumAndCount(b1.sum + b2.sum, b1.count + b2.count)

  def finish(buffer: SumAndCount): SumAndCount = buffer

  def bufferEncoder: Encoder[SumAndCount] = Encoders.product[SumAndCount]
  def outputEncoder: Encoder[SumAndCount] = Encoders.product[SumAndCount]
}

// Usage
val result: Dataset[(String, SumAndCount)] = orders
  .groupByKey(_.customer_id)
  .agg(new SumAndCountAggregator().toColumn)

// Extract average in one pass
val withAvg = result.map { case (customerId, sc) =>
  (customerId, sc.sum, sc.count, sc.sum / sc.count)
}
```

**Benefits**:
- Single shuffle instead of multiple
- Type-safe intermediate buffer
- Efficient for complex aggregations (avg, stddev, etc.)

### 4. Type-Safe Group By with Grain Tracking

Wrapper for type-safe group by:

```scala
case class GroupedDataset[K, V, GOut <: GrainLevel](
  grouped: KeyValueGroupedDataset[K, V]
)

object AdjointOps {
  def groupBy[T, K, GIn <: GrainLevel, GOut <: GrainLevel](
    ds: GrainedDataset[T, GIn],
    key: T => K
  )(implicit ev: GrainTransition[GIn, GOut]): GroupedDataset[K, T, GOut] = {
    GroupedDataset[K, T, GOut](ds.ds.groupByKey(key))
  }

  def agg[K, V, A, GOut <: GrainLevel](
    grouped: GroupedDataset[K, V, GOut],
    aggregator: Aggregator[V, _, A]
  ): GrainedDataset[(K, A), GOut] = {
    GrainedDataset[(K, A), GOut](
      grouped.grouped.agg(aggregator.toColumn)
    )
  }
}

// Usage with compile-time grain validation
val orders: GrainedDataset[Order, Atomic] = loadOrders()

val dailySummary: GrainedDataset[(String, SumAndCount), Daily] =
  AdjointOps.agg(
    AdjointOps.groupBy[Order, String, Atomic, Daily](orders, _.customer_id),
    new SumAndCountAggregator()
  )
```

### 5. Window Function Type-Safe Wrappers

Type-safe window specification builder:

```scala
import org.apache.spark.sql.expressions.{Window, WindowSpec}
import org.apache.spark.sql.Column

case class WindowDef[T, G <: GrainLevel](
  partitionBy: Seq[T => Any],
  orderBy: Seq[(T => Any, SortOrder)],
  frame: Option[FrameSpec]
)

sealed trait SortOrder
case object Ascending extends SortOrder
case object Descending extends SortOrder

case class FrameSpec(
  frameType: FrameType,
  start: FrameBound,
  end: FrameBound
)

sealed trait FrameType
case object RowsFrame extends FrameType
case object RangeFrame extends FrameType

sealed trait FrameBound
case object UnboundedPreceding extends FrameBound
case object UnboundedFollowing extends FrameBound
case object CurrentRow extends FrameBound
case class Offset(n: Int) extends FrameBound

object WindowOps {
  def buildWindowSpec[T](window: WindowDef[T, _]): WindowSpec = {
    var spec = Window.partitionBy(
      window.partitionBy.map(f => col(f.toString)): _*
    )

    if (window.orderBy.nonEmpty) {
      val orderCols = window.orderBy.map { case (f, order) =>
        val c = col(f.toString)
        order match {
          case Ascending => c.asc
          case Descending => c.desc
        }
      }
      spec = spec.orderBy(orderCols: _*)
    }

    window.frame match {
      case Some(frame) =>
        val (start, end) = (toWindowBound(frame.start), toWindowBound(frame.end))
        frame.frameType match {
          case RowsFrame => spec.rowsBetween(start, end)
          case RangeFrame => spec.rangeBetween(start, end)
        }
      case None => spec
    }
  }

  private def toWindowBound(bound: FrameBound): Long = bound match {
    case UnboundedPreceding => Window.unboundedPreceding
    case UnboundedFollowing => Window.unboundedFollowing
    case CurrentRow => Window.currentRow
    case Offset(n) => n.toLong
  }
}

// Usage: Running total
val windowDef = WindowDef[Order, Atomic](
  partitionBy = Seq(_.customer_id),
  orderBy = Seq((_.order_date, Ascending)),
  frame = Some(FrameSpec(
    frameType = RowsFrame,
    start = UnboundedPreceding,
    end = CurrentRow
  ))
)

val spec = WindowOps.buildWindowSpec(windowDef)

val withRunningTotal = orders.withColumn(
  "running_total",
  sum("total_amount").over(spec)
)
```

**Benefits**:
- Type-safe window specification
- No string-based column references
- Reusable window definitions
- REQ-ADJ-03: Window function support

---

## Optimization Strategy Pattern

Runtime-selectable aggregation strategies:

```scala
sealed trait AggregationStrategy
case object PartialAggregation extends AggregationStrategy
case class Salted(buckets: Int) extends AggregationStrategy
case object Bucketed extends AggregationStrategy
case class Broadcast(dimensionTable: String) extends AggregationStrategy

trait AggregationExecutor[K, V, A] {
  def execute(
    ds: Dataset[V],
    key: V => K,
    aggregator: Aggregator[V, _, A],
    strategy: AggregationStrategy
  ): Dataset[(K, A)]
}

class DefaultAggregationExecutor[K: Encoder, V, A] extends AggregationExecutor[K, V, A] {
  def execute(
    ds: Dataset[V],
    key: V => K,
    aggregator: Aggregator[V, _, A],
    strategy: AggregationStrategy
  ): Dataset[(K, A)] = strategy match {

    case PartialAggregation =>
      // Standard groupByKey (Spark does partial aggregation automatically)
      ds.groupByKey(key).agg(aggregator.toColumn)

    case Salted(buckets) =>
      // Two-stage aggregation with salting
      val salted = ds.map(v => (key(v), Random.nextInt(buckets), v))
      val partial = salted
        .groupBy($"_1", $"_2")
        .agg(aggregator.toColumn.as("partial"))
        .as[(K, Int, A)]

      partial
        .groupBy($"_1")
        .agg(aggregator.toColumn.as("final"))
        .as[(K, A)]

    case Bucketed =>
      // Assumes source is already bucketed
      ds.groupByKey(key).agg(aggregator.toColumn)

    case Broadcast(dimTable) =>
      // Broadcast join + aggregation
      val dim = spark.table(dimTable)
      ds.join(broadcast(dim), key, "left")
        .groupByKey(key)
        .agg(aggregator.toColumn)
  }
}
```

---

## Implementation Examples

### Example 1: Custom Weighted Average Aggregator

```scala
case class WeightedValue(value: BigDecimal, weight: BigDecimal)

class WeightedAverageAggregator extends Aggregator[Order, WeightedValue, BigDecimal] {
  def zero: WeightedValue = WeightedValue(BigDecimal(0), BigDecimal(0))

  def reduce(buffer: WeightedValue, order: Order): WeightedValue = {
    val weight = order.quantity
    WeightedValue(
      value = buffer.value + (order.unit_price * weight),
      weight = buffer.weight + weight
    )
  }

  def merge(b1: WeightedValue, b2: WeightedValue): WeightedValue =
    WeightedValue(b1.value + b2.value, b1.weight + b2.weight)

  def finish(buffer: WeightedValue): BigDecimal =
    if (buffer.weight > 0) buffer.value / buffer.weight else BigDecimal(0)

  def bufferEncoder: Encoder[WeightedValue] = Encoders.product[WeightedValue]
  def outputEncoder: Encoder[BigDecimal] = Encoders.scalaDecimal
}
```

### Example 2: Top-N Aggregator

```scala
case class TopN[T](values: Vector[T], n: Int)

class TopNAggregator[T: Ordering](n: Int)
  extends Aggregator[T, TopN[T], Seq[T]] {

  private val ord = implicitly[Ordering[T]]

  def zero: TopN[T] = TopN(Vector.empty, n)

  def reduce(buffer: TopN[T], value: T): TopN[T] = {
    val updated = (buffer.values :+ value).sorted(ord.reverse).take(n)
    TopN(updated.toVector, n)
  }

  def merge(b1: TopN[T], b2: TopN[T]): TopN[T] = {
    val merged = (b1.values ++ b2.values).sorted(ord.reverse).take(n)
    TopN(merged.toVector, n)
  }

  def finish(buffer: TopN[T]): Seq[T] = buffer.values

  def bufferEncoder: Encoder[TopN[T]] = Encoders.kryo[TopN[T]]
  def outputEncoder: Encoder[Seq[T]] = Encoders.kryo[Seq[T]]
}

// Usage: Top 5 orders per customer
val top5Orders = orders
  .groupByKey(_.customer_id)
  .agg(new TopNAggregator[BigDecimal](5).toColumn)
```

---

## Consequences

### Positive

- **REQ-ADJ-01**: Full aggregations via Aggregator class
- **REQ-ADJ-02**: Group by with strategy pattern for optimization
- **REQ-ADJ-03**: Window functions with type-safe wrappers
- **REQ-TYP-04**: Type-safe monoid composition
- **Performance**: Automatic partial aggregation by Spark
- **Extensibility**: Easy to add custom aggregators

### Negative

- **Verbosity**: Aggregator class requires boilerplate
- **Encoders**: Must provide Encoders for buffer/output types
- **Learning curve**: Requires understanding Aggregator internals
- **Kryo fallback**: Complex types may require Kryo serialization

### Mitigations

- **Generators**: Auto-generate common aggregators from config
- **Encoder derivation**: Use Spark's automatic encoder derivation
- **Documentation**: Provide aggregator cookbook
- **Kryo registration**: Pre-register common buffer types

---

## Alternatives Considered

### Alternative A: UDAF (User-Defined Aggregation Function)

SQL-level aggregation functions.

**Rejected**: Untyped, no compile-time safety, DataFrame-only.

### Alternative B: RDD-based aggregations

```scala
rdd.aggregateByKey(zeroValue)(seqOp, combOp)
```

**Rejected**: Loses Catalyst optimization, no Dataset[T] integration.

### Alternative C: Algebird Aggregators

Twitter's Algebird provides monoid-based aggregators.

**Considered**: Good abstraction, but not Spark-native. Could be used in custom Aggregator implementations.

---

## References

- [Spark Aggregator API](https://spark.apache.org/docs/latest/api/scala/org/apache/spark/sql/expressions/Aggregator.html)
- [Cats Monoid](https://typelevel.org/cats/typeclasses/monoid.html)
- [Spark Window Functions](https://spark.apache.org/docs/latest/api/sql/index.html#window-functions)
- [Algebird Aggregators](https://twitter.github.io/algebird/)
- REQ-ADJ-01: Full Aggregations
- REQ-ADJ-02: Group By Optimization
- REQ-ADJ-03: Window Functions
- REQ-TYP-04: Monoid Composition
