# CDME Spark - Categorical Data Mapping & Computation Engine

A Scala/Spark implementation of the CDME framework for type-safe, grain-aware data transformations.

## Overview

CDME implements a functional approach to data mapping with:
- **Compile-time path validation** - Invalid entity/relationship references caught before execution
- **Grain safety** - Prevents mixing incompatible grain levels without explicit aggregation
- **Either-based error handling** - No silent failures, all errors captured
- **Monoid-based aggregations** - Type-safe, composable aggregations
- **Morphism chains** - Functional composition of data transformations

## Architecture

```
CONFIG → INIT → COMPILE → EXECUTE → OUTPUT
```

### Components

1. **Core** (`cdme.core`) - Domain types, errors, grain definitions
2. **Config** (`cdme.config`) - YAML configuration loading with circe
3. **Registry** (`cdme.registry`) - Schema registry with path validation
4. **Compiler** (`cdme.compiler`) - Compile-time validation and plan generation
5. **Executor** (`cdme.executor`) - Spark-based execution engine
6. **Morphisms** (`cdme.executor.morphisms`) - Transformation primitives

## Build

```bash
# Compile
sbt compile

# Run tests
sbt test

# Build fat JAR
sbt assembly

# Run locally
sbt "run config/cdme_config.yaml order_summary 2024-12-15"
```

## Project Structure

```
src/
├── main/scala/cdme/
│   ├── core/
│   │   ├── Types.scala          # Error ADT, Either types
│   │   ├── Domain.scala         # Entity, Grain, Morphism abstractions
│   │   └── Algebra.scala        # Monoid instances, Aggregator
│   ├── config/
│   │   ├── ConfigModel.scala    # YAML configuration case classes
│   │   └── ConfigLoader.scala   # circe-yaml parsing
│   ├── registry/
│   │   └── SchemaRegistry.scala # LDM/PDM registry, path validation
│   ├── compiler/
│   │   └── Compiler.scala       # Validation, plan generation
│   ├── executor/
│   │   ├── Executor.scala       # Main executor
│   │   └── morphisms/
│   │       ├── FilterMorphism.scala
│   │       └── AggregateMorphism.scala
│   └── Main.scala               # Entry point (steel thread)
└── test/scala/cdme/
    └── CompilerSpec.scala       # Basic tests
```

## Dependencies

- **Scala**: 2.12.18 (Spark 3.5.x compatibility)
- **Spark**: 3.5.0 (core, sql)
- **Cats**: 2.10.0 (functional programming)
- **Circe**: 0.14.6 (JSON/YAML parsing)
- **Refined**: 0.11.0 (refinement types)
- **ScalaTest**: 3.2.17 (testing)

## Design Documentation

See `docs/design/design_spark/` for:
- **SPARK_SOLUTION_DESIGN.md** - Complete solution design
- **SPARK_IMPLEMENTATION_DESIGN.md** - Implementation mapping
- **adrs/** - Architecture Decision Records:
  - ADR-006: Scala Type System
  - ADR-007: Either Monad for Error Handling
  - ADR-008: Scala Aggregation Patterns
  - ADR-009: Scala Project Structure
  - ADR-010: YAML Configuration Parsing

## Key Features Implemented

### 1. Type-Safe Error Handling (ADR-007)

```scala
sealed trait CdmeError {
  def sourceKey: String
  def morphismPath: String
  def context: Map[String, String]
}

// All operations return Either[CdmeError, A]
def compile(mapping: MappingConfig): Either[CdmeError, ExecutionPlan]
```

### 2. Grain Safety (REQ-TRV-02)

```scala
case class Grain(
  level: GrainLevel,
  key: List[String]
)

// Grain transitions validated at compile time
GrainValidator.validateTransition(sourceGrain, targetGrain, hasAggregation)
```

### 3. Path Validation (REQ-LDM-03)

```scala
// Validates: "Order.customer.name"
registry.validatePath(path) match {
  case Right(PathValidationResult(_, _, finalType, true)) => // Valid
  case Left(error) => // Invalid path
}
```

### 4. Monoid-Based Aggregations (REQ-ADJ-01)

```scala
implicit val bigDecimalSumMonoid: Monoid[BigDecimal] = new Monoid[BigDecimal] {
  def empty: BigDecimal = BigDecimal(0)
  def combine(x: BigDecimal, y: BigDecimal): BigDecimal = x + y
}
```

## Configuration Example

### LDM Entity (ldm/order.yaml)
```yaml
entity:
  name: Order
  grain:
    level: ATOMIC
    key: [order_id]
  attributes:
    - name: order_id
      type: String
      nullable: false
      primary_key: true
    - name: total_amount
      type: Decimal(18,2)
      nullable: false
      refinement: "value > 0"
  relationships:
    - name: customer
      target: Customer
      cardinality: N:1
      join_key: customer_id
```

### PDM Binding (pdm/order_binding.yaml)
```yaml
binding:
  entity: Order
  physical:
    type: PARQUET
    location: "s3://data-lake/orders/"
    partition_columns: [order_date]
```

### Mapping (mappings/order_summary.yaml)
```yaml
mapping:
  name: order_summary
  source:
    entity: Order
    epoch: "${epoch}"
  target:
    entity: DailyOrderSummary
    grain:
      level: DAILY
      key: [order_date]
  morphisms:
    - name: filter_active
      type: FILTER
      predicate: "status != 'CANCELLED'"
  projections:
    - name: order_date
      source: order_date
    - name: total_amount
      source: total_amount
      aggregation: SUM
```

## Usage

### Spark Submit

```bash
spark-submit \
  --class cdme.Main \
  --master yarn \
  --deploy-mode cluster \
  target/scala-2.12/cdme-spark.jar \
  config/cdme_config.yaml \
  order_summary \
  2024-12-15
```

### Programmatic

```scala
import cdme._
import cdme.config._

val result = CdmeEngine.executeDirect(
  entities = entities,
  bindings = bindings,
  mapping = mappingConfig,
  epoch = "2024-12-15"
)

result match {
  case Right(ExecutionResult(data, errors, _, _, sourceCount, outputCount)) =>
    println(s"Success: $sourceCount → $outputCount rows")
    data.show()

  case Left(error) =>
    println(s"Error: ${error.errorType}")
}
```

## Testing

```bash
# Run all tests
sbt test

# Run specific test
sbt "testOnly cdme.CompilerSpec"

# With coverage
sbt clean coverage test coverageReport
```

## Traceability

| Requirement | Component | Implementation |
|-------------|-----------|----------------|
| REQ-LDM-01 | SchemaRegistry | YAML entity definitions, graph structure |
| REQ-LDM-02 | Relationship | Cardinality enum (1:1, N:1, 1:N) |
| REQ-LDM-03 | SchemaRegistry.validatePath | Compile-time path validation |
| REQ-LDM-06 | Grain | Grain hierarchy with levels |
| REQ-TRV-02 | GrainValidator | Grain safety validation |
| REQ-TYP-03 | CdmeError ADT | Either-based error domain |
| REQ-ADJ-01 | MonoidInstances | Monoid-based aggregations |
| REQ-CFG-01 | ConfigLoader | Type-safe circe-yaml parsing |
| REQ-CFG-02 | SchemaRegistryImpl | Parse-time validation |

## Implementation Status

### Completed
- ✅ Build configuration (sbt)
- ✅ Core types (Either, Error ADT, Domain classes)
- ✅ Configuration loading (circe-yaml)
- ✅ Schema registry with path validation
- ✅ Compiler with grain safety
- ✅ Executor with basic morphisms
- ✅ Main entry point (steel thread)
- ✅ Basic tests

### Future Enhancements
- [ ] Full morphism implementations (Traverse, Window)
- [ ] Error DataFrame handling
- [ ] Lineage capture (OpenLineage)
- [ ] Advanced aggregation strategies (Salting, Bucketed)
- [ ] Streaming support
- [ ] Integration tests with Spark
- [ ] Property-based tests (ScalaCheck)

## License

MIT

## References

- [Design Documentation](docs/design/design_spark/)
- [AI SDLC Method](../.ai-workspace/templates/AISDLC_METHOD_REFERENCE.md)
- [Spark Documentation](https://spark.apache.org/docs/latest/)
- [Cats Documentation](https://typelevel.org/cats/)
- [Circe Documentation](https://circe.github.io/circe/)
