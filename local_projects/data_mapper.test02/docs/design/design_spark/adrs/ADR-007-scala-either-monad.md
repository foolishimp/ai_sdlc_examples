# ADR-007: Error Handling with Either Monad

**Status**: Accepted
**Date**: 2025-12-10
**Deciders**: Design Agent
**Depends On**: ADR-006 (Scala Type System)
**Implements**: REQ-TYP-03, REQ-ERR-01, REQ-ERR-02, REQ-ERR-03

---

## Context

CDME requires comprehensive error handling where:
- All failures are captured (no silent drops - REQ-ERR-01)
- Errors route to Error Domain DataFrame (REQ-TYP-03)
- Multiple errors can accumulate without halting (REQ-ERR-02)
- Error context includes source keys, morphism path, and details (REQ-ERR-03)

Scala provides multiple error handling mechanisms:
- `scala.util.{Try, Either}` - Standard library
- `cats.data.{Either, Validated, EitherT}` - Cats library
- Exception-based handling (anti-pattern for data processing)
- Custom ADTs for domain errors

---

## Decision

**Use Cats `Either[E, A]` with error accumulation via `Validated`:**

1. **Cats Either** (not scala.util.Either) - For sequential error-short-circuiting
2. **Cats Validated** - For parallel error accumulation
3. **Custom Error ADT** - Typed error domain with variants
4. **DataFrame integration** - Either → separate success/error DataFrames
5. **EitherT** monad transformer - For composing Either with Dataset operations

---

## Rationale

### 1. Cats Either vs scala.util.Either

```scala
// scala.util.Either - no type class instances, not right-biased (pre-2.12)
val result: Either[String, Int] = Right(42)
result.map(_ + 1)  // Requires explicit .right.map in Scala 2.11

// cats.data.Either - right-biased, full monad support
import cats.implicits._
val result: Either[String, Int] = 42.asRight
result.map(_ + 1)  // Just works
result.flatMap(x => (x * 2).asRight)
```

**Benefits**:
- Full monad instance (map, flatMap, traverse, etc.)
- Comprehensive combinators (leftMap, bimap, etc.)
- Integration with Cats ecosystem
- Consistent with functional programming patterns

### 2. Validated for Error Accumulation

Use `Validated` when we want to collect ALL errors, not short-circuit:

```scala
import cats.data.Validated
import cats.data.Validated.{Valid, Invalid}
import cats.implicits._

// Validate multiple fields, accumulate errors
case class ValidationError(field: String, message: String)

def validateOrder(order: Order): Validated[List[ValidationError], Order] = {
  (
    validateAmount(order.total_amount),
    validateDate(order.order_date),
    validateStatus(order.status)
  ).mapN((_, _, _) => order)
}

// Result: Invalid(List(
//   ValidationError("total_amount", "Must be positive"),
//   ValidationError("order_date", "Cannot be in future")
// ))
```

**When to use Validated vs Either**:
- **Either**: Sequential operations, fail-fast (morphism chains)
- **Validated**: Parallel validations, collect all errors (record validation)

### 3. Custom Error Domain ADT

Define a typed error hierarchy:

```scala
sealed trait CdmeError {
  def sourceKey: String
  def morphismPath: String
  def context: Map[String, String]
}

object CdmeError {
  case class RefinementError(
    sourceKey: String,
    morphismPath: String,
    field: String,
    expected: String,
    actual: String,
    context: Map[String, String] = Map.empty
  ) extends CdmeError

  case class TypeCastError(
    sourceKey: String,
    morphismPath: String,
    field: String,
    targetType: String,
    actualValue: String,
    context: Map[String, String] = Map.empty
  ) extends CdmeError

  case class JoinKeyNotFoundError(
    sourceKey: String,
    morphismPath: String,
    joinKey: String,
    targetEntity: String,
    context: Map[String, String] = Map.empty
  ) extends CdmeError

  case class ValidationError(
    sourceKey: String,
    morphismPath: String,
    rule: String,
    violation: String,
    context: Map[String, String] = Map.empty
  ) extends CdmeError

  case class NullFieldError(
    sourceKey: String,
    morphismPath: String,
    field: String,
    context: Map[String, String] = Map.empty
  ) extends CdmeError

  case class GrainSafetyError(
    sourceKey: String,
    morphismPath: String,
    sourceGrain: String,
    targetGrain: String,
    violation: String,
    context: Map[String, String] = Map.empty
  ) extends CdmeError
}
```

**Benefits**:
- Exhaustive pattern matching
- Type-safe error construction
- Clear error taxonomy (REQ-ERR-03)

### 4. DataFrame Integration

Split Either results into success/error DataFrames:

```scala
trait MorphismResult[T] {
  def successDS: Dataset[T]
  def errorDS: Dataset[CdmeErrorRecord]
}

object MorphismExecutor {
  def execute[A, B](
    input: Dataset[A],
    morphism: Morphism[A, B]
  ): MorphismResult[B] = {

    // Apply morphism, returns Dataset[Either[CdmeError, B]]
    val results: Dataset[Either[CdmeError, B]] =
      input.map(morphism.apply)

    // Partition into success and error
    val successes: Dataset[B] =
      results.flatMap(_.toOption)

    val errors: Dataset[CdmeErrorRecord] =
      results.flatMap(_.left.toOption.map(toCdmeErrorRecord))

    MorphismResult(successes, errors)
  }

  def toCdmeErrorRecord(error: CdmeError): CdmeErrorRecord = {
    CdmeErrorRecord(
      source_key = error.sourceKey,
      error_type = error.getClass.getSimpleName,
      error_code = deriveErrorCode(error),
      morphism_path = error.morphismPath,
      expected = extractExpected(error),
      actual = extractActual(error),
      context = error.context,
      run_id = ExecutionContext.current.runId,
      epoch = ExecutionContext.current.epoch,
      timestamp = Timestamp.now()
    )
  }
}
```

### 5. EitherT Monad Transformer

For composing Either with Dataset operations:

```scala
import cats.data.EitherT
import cats.implicits._

// Problem: Dataset[Either[E, A]] is awkward to compose
val ds1: Dataset[Either[CdmeError, Order]] = ???
val ds2: Dataset[Either[CdmeError, Customer]] = ???

// Solution: EitherT[Dataset, CdmeError, A]
type DatasetEither[A] = EitherT[Dataset, CdmeError, A]

def enrich(order: Order): DatasetEither[EnrichedOrder] = {
  for {
    customer <- EitherT(lookupCustomer(order.customer_id))
    product <- EitherT(lookupProduct(order.product_id))
  } yield EnrichedOrder(order, customer, product)
}

// Unwrap back to Dataset[Either[CdmeError, EnrichedOrder]]
val result: Dataset[Either[CdmeError, EnrichedOrder]] = enrich(order).value
```

**Benefits**:
- Cleaner for-comprehension syntax
- Hides Dataset[Either[E, A]] plumbing
- Still allows partition-level parallelism

---

## Implementation Strategy

### Morphism Signature

```scala
trait Morphism[A, B] {
  def apply(input: A)(implicit ctx: ExecutionContext): Either[CdmeError, B]

  def morphismPath: String
}

// Example: Filter morphism
class FilterMorphism[A](predicate: A => Boolean, path: String)
  extends Morphism[A, A] {

  def apply(input: A)(implicit ctx: ExecutionContext): Either[CdmeError, A] = {
    if (predicate(input)) {
      Right(input)
    } else {
      Left(CdmeError.ValidationError(
        sourceKey = extractKey(input),
        morphismPath = path,
        rule = "filter",
        violation = "Predicate failed"
      ))
    }
  }

  override def morphismPath: String = path
}
```

### Validation with Validated

```scala
import cats.data.ValidatedNel  // Validated with NonEmptyList

def validateRecord[A](
  record: A,
  validators: List[Validator[A]]
): ValidatedNel[CdmeError, A] = {
  validators.traverse(v => v.validate(record)).map(_ => record)
}

trait Validator[A] {
  def validate(record: A): ValidatedNel[CdmeError, Unit]
}

// Example: Amount validator
object PositiveAmountValidator extends Validator[Order] {
  def validate(order: Order): ValidatedNel[CdmeError, Unit] = {
    if (order.total_amount > 0) {
      Validated.valid(())
    } else {
      Validated.invalidNel(
        CdmeError.RefinementError(
          sourceKey = order.order_id,
          morphismPath = "source.Order",
          field = "total_amount",
          expected = "value > 0",
          actual = order.total_amount.toString
        )
      )
    }
  }
}

// Usage: Collect all errors
val result: ValidatedNel[CdmeError, Order] =
  validateRecord(order, List(
    PositiveAmountValidator,
    FutureDateValidator,
    ValidStatusValidator
  ))
```

---

## Error Threshold Checking

```scala
trait ErrorThresholdChecker {
  def check(
    totalRows: Long,
    errorRows: Long,
    threshold: Double
  ): Either[ThresholdExceeded, Unit] = {
    val errorRate = errorRows.toDouble / totalRows.toDouble

    if (errorRate > threshold) {
      Left(ThresholdExceeded(
        s"Error rate $errorRate exceeds threshold $threshold"
      ))
    } else {
      Right(())
    }
  }
}

case class ThresholdExceeded(message: String) extends Exception(message)
```

---

## Consequences

### Positive

- **REQ-TYP-03**: All errors captured in typed Either
- **REQ-ERR-01**: No silent failures - errors route to Error Domain
- **REQ-ERR-02**: Validated enables error accumulation
- **REQ-ERR-03**: Rich error context with typed ADT
- **Composability**: Monadic error handling chains naturally
- **Type safety**: Pattern matching ensures exhaustive error handling

### Negative

- **Learning curve**: Requires understanding Either, Validated, EitherT
- **Performance overhead**: Either boxing/unboxing per record
- **Library dependency**: Requires Cats library
- **Error verbosity**: Rich error types increase memory footprint

### Mitigations

- **Documentation**: Provide clear examples and patterns
- **Performance**: Benchmark; optimize hot paths if needed
- **Cats is standard**: Widely adopted in Scala ecosystem
- **Configurable detail**: Allow error detail level configuration

---

## Alternatives Considered

### Alternative A: Exception-Based (Rejected)

```scala
// Anti-pattern for data processing
try {
  processOrder(order)
} catch {
  case e: ValidationException => // Lost in executor logs
}
```

**Rejected**: Loses error records, breaks distributed processing (REQ-ERR-01).

### Alternative B: Option-Based (Rejected)

```scala
def process(order: Order): Option[ProcessedOrder]
```

**Rejected**: No error context - can't diagnose failures (REQ-ERR-03).

### Alternative C: ZIO (Considered)

ZIO provides `ZIO[R, E, A]` with error channel.

**Not chosen**: Heavier runtime, less Spark integration, steeper learning curve. Revisit if ZIO-Spark matures.

---

## References

- [Cats Either Documentation](https://typelevel.org/cats/datatypes/either.html)
- [Cats Validated Documentation](https://typelevel.org/cats/datatypes/validated.html)
- [EitherT Monad Transformer](https://typelevel.org/cats/datatypes/eithert.html)
- [Error Handling Best Practices in Scala](https://blog.softwaremill.com/practical-guide-to-error-handling-in-scala-cats-2-4-987d3e59cb38)
- REQ-TYP-03: Error Domain Implementation
- REQ-ERR-01: No Silent Failures
- REQ-ERR-02: Error Accumulation
- REQ-ERR-03: Rich Error Context
