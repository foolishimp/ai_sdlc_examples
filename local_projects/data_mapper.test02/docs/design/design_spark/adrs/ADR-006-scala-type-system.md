# ADR-006: Scala Type System for CDME

**Status**: Accepted
**Date**: 2025-12-10
**Deciders**: Design Agent
**Depends On**: ADR-002 (Language Choice - Scala)
**Implements**: REQ-TYP-01, REQ-TYP-02, REQ-LDM-03, REQ-TRV-02

---

## Context

CDME requires a type-safe implementation where:
- Entity schemas are enforced at compile time
- Path traversals (entity.relationship.field) are validated before runtime
- Morphism chains preserve type correctness
- Grain safety violations are caught early
- Refinement types express domain constraints (e.g., "amount > 0")

Scala provides multiple mechanisms for encoding these requirements:
- Case classes for entity definitions
- Dataset[T] vs DataFrame (untyped)
- Type-safe column references
- Path-dependent types
- Refinement types / tagged types

---

## Decision

**Use Scala's type system with the following architecture:**

1. **Dataset[T] for all morphism operations** - Not DataFrame
2. **Case classes for entity schemas** - Auto-derived from LDM
3. **Type-safe column references** - Compile-time path validation
4. **Refined types for domain constraints** - Using `refined` library
5. **Type-level grain tracking** - Phantom types for grain safety

---

## Rationale

### 1. Dataset[T] vs DataFrame

```scala
// DataFrame - untyped, runtime errors
val df: DataFrame = spark.read.parquet("orders")
df.select("custmer_id")  // Typo - runtime error!

// Dataset[Order] - typed, compile-time safety
case class Order(customer_id: String, amount: BigDecimal, order_date: Date)
val ds: Dataset[Order] = spark.read.parquet("orders").as[Order]
ds.map(o => o.customer_id)  // Compile error if typo!
```

**Trade-offs**:
- **Pros**: Compile-time safety, refactoring-friendly, IDE support
- **Cons**: Slightly more verbose, schema evolution requires recompilation
- **Decision**: Type safety aligns with CDME's correctness-first principles (REQ-TYP-01)

### 2. Case Classes for Entity Schemas

Auto-generate from LDM YAML:

```scala
// Generated from config/ldm/order.yaml
case class Order(
  order_id: String,
  customer_id: String,
  order_date: Date,
  total_amount: Refined[BigDecimal, Positive],  // Refinement type
  status: OrderStatus  // Sealed trait enum
)

sealed trait OrderStatus
object OrderStatus {
  case object Pending extends OrderStatus
  case object Confirmed extends OrderStatus
  case object Shipped extends OrderStatus
  case object Delivered extends OrderStatus
  case object Cancelled extends OrderStatus
}
```

**Benefits**:
- Single source of truth (LDM YAML → Scala types)
- Automatic schema validation
- Exhaustive pattern matching for enums

### 3. Type-Safe Path Validation

Use Shapeless or compile-time macros to validate paths:

```scala
// LDM defines: Order.customer (N:1 → Customer)
//               Customer.name (String)

// Compile-time validated path
val path = Path[Order]
  .traverse(_.customer)  // N:1 relationship
  .field(_.name)         // String field
// Type: Path[Order, String]

// Compilation fails for invalid paths:
val badPath = Path[Order]
  .traverse(_.invalid_rel)  // Compile error: relationship doesn't exist
```

**Implementation**: Use `shapeless.Witness` or `scala-reflect` macros to validate at compile time.

### 4. Refinement Types for Domain Constraints

Use the `refined` library to encode value constraints:

```scala
import eu.timepit.refined._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive

// From LDM: refinement: "value > 0"
type PositiveAmount = BigDecimal Refined Positive

case class Order(
  order_id: String,
  total_amount: PositiveAmount  // Cannot be <= 0
)

// Validation at parse time:
val amount: Either[String, PositiveAmount] =
  refineV[Positive](BigDecimal(-50))
// Left("Predicate failed: (-50.0 > 0).")
```

**Benefits**:
- REQ-TYP-02: Express invariants in types
- Move validation to compile/parse time
- Self-documenting code

### 5. Type-Level Grain Tracking (Phantom Types)

Track grain at type level to prevent invalid compositions:

```scala
// Phantom type for grain level
sealed trait GrainLevel
trait Atomic extends GrainLevel
trait Daily extends GrainLevel
trait Customer extends GrainLevel

// Dataset wrapper with grain phantom type
case class GrainedDataset[T, G <: GrainLevel](ds: Dataset[T])

// Morphisms preserve grain in type signature
trait Morphism[A, B, G <: GrainLevel] {
  def apply(input: GrainedDataset[A, G]): GrainedDataset[B, G]
}

// Aggregation changes grain explicitly
trait AdjointMorphism[A, B, GIn <: GrainLevel, GOut <: GrainLevel] {
  def apply(input: GrainedDataset[A, GIn]): GrainedDataset[B, GOut]
}

// Compile-time grain safety (REQ-TRV-02)
val orders: GrainedDataset[Order, Atomic] = loadOrders()

// OK: Aggregate from Atomic → Daily
val dailySummary: GrainedDataset[DailyOrderSummary, Daily] =
  aggregateMorphism.apply(orders)

// Compile error: Cannot mix Daily + Atomic without aggregation
val invalid = orders.join(dailySummary)  // Type mismatch!
```

---

## Implementation Strategy

### Code Generation Pipeline

```
LDM YAML → Scala Case Classes → Compiled Schema Registry
```

**Generator**:

```scala
object SchemaGenerator {
  def generateFromLDM(ldmPath: String): String = {
    val entities = loadLDMEntities(ldmPath)
    entities.map { entity =>
      s"""
      |case class ${entity.name}(
      |  ${entity.attributes.map(attr =>
      |    s"${attr.name}: ${scalaType(attr)}"
      |  ).mkString(",\n  ")}
      |)
      """.stripMargin
    }.mkString("\n\n")
  }

  def scalaType(attr: Attribute): String = {
    val baseType = attr.`type` match {
      case "String" => "String"
      case "Integer" => "Int"
      case "Long" => "Long"
      case "Decimal(p,s)" => "BigDecimal"
      case "Date" => "java.sql.Date"
      case "Timestamp" => "java.sql.Timestamp"
    }

    // Apply refinement if present
    val refined = attr.refinement match {
      case Some("value > 0") => s"$baseType Refined Positive"
      case Some("value >= 0") => s"$baseType Refined NonNegative"
      case None => baseType
    }

    // Apply nullability
    if (attr.nullable) s"Option[$refined]" else refined
  }
}
```

---

## Consequences

### Positive

- **REQ-TYP-01**: Full type safety - invalid operations caught at compile time
- **REQ-LDM-03**: Path validation before execution
- **REQ-TRV-02**: Grain safety enforced by type system
- **Refactoring safety**: IDE-assisted refactoring across codebase
- **Documentation**: Types serve as machine-checked documentation

### Negative

- **Compilation time**: Type-heavy code increases compile time
- **Learning curve**: Requires Scala expertise (refined, shapeless, phantom types)
- **Schema evolution**: Changes require recompilation
- **Error messages**: Complex type errors can be cryptic

### Mitigations

- **Incremental compilation**: Use sbt incremental compiler
- **Documentation**: Provide examples and troubleshooting guide
- **Escape hatch**: Allow DataFrame fallback for prototyping
- **Error formatting**: Custom compiler plugins for better error messages

---

## Alternatives Considered

### Alternative A: DataFrame-Only (Rejected)

**Rationale**: Loses compile-time safety - contradicts REQ-TYP-01.

### Alternative B: Hybrid (Dataset for Core, DataFrame for Extensions)

**Rationale**: Could work but introduces confusion about when to use which.

### Alternative C: Dotty (Scala 3) Types

**Rationale**: Scala 3 has better type inference and match types, but ecosystem maturity is lower. Revisit in 2026.

---

## References

- [Dataset API Guide](https://spark.apache.org/docs/latest/sql-programming-guide.html#datasets-and-dataframes)
- [Refined Library](https://github.com/fthomas/refined)
- [Shapeless Guide](https://github.com/milessabin/shapeless)
- [Type-Level Programming in Scala](https://blog.rockthejvm.com/type-level-programming-part-1/)
- REQ-TYP-01: Type-Safe Morphism Composition
- REQ-TYP-02: Refinement Type Encoding
- REQ-LDM-03: Compile-Time Path Validation
- REQ-TRV-02: Grain Safety Enforcement
