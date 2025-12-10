# CDME Spark Solution Design

**Document Type**: Solution Design
**Solution**: `design_spark` (Spark/PySpark/Scala Implementation)
**Version**: 1.0
**Date**: 2025-12-10
**Status**: Draft
**References**: [data_mapper Core Design](../data_mapper/AISDLC_IMPLEMENTATION_DESIGN.md)

---

## 1. Overview

This document defines a **Spark-based implementation** of the CDME (Categorical Data Mapping & Computation Engine). The design is **language-agnostic** - implementable in PySpark or Scala Spark.

### 1.1 Scope: MVP Steel Thread

The MVP implements a **steel thread** through the core CDME concepts:

```
CONFIG → INIT → COMPILE → EXECUTE → OUTPUT
   │        │       │         │         │
   │        │       │         │         └── Results + Lineage + Errors
   │        │       │         └── Spark Job Execution
   │        │       └── Path Validation, Type Checking, Plan Generation
   │        └── Schema Registry Load, Session Setup
   └── YAML Configuration (Mapping + Sources + Types)
```

### 1.2 MVP Requirements Coverage

| Requirement | MVP Coverage | Notes |
|-------------|--------------|-------|
| REQ-LDM-01 | ✓ | Schema as graph (YAML-based registry) |
| REQ-LDM-02 | ✓ | Cardinality types (1:1, N:1, 1:N) |
| REQ-LDM-03 | ✓ | Path validation at compile time |
| REQ-LDM-06 | ✓ | Grain metadata |
| REQ-PDM-01 | ✓ | LDM/PDM separation |
| REQ-TRV-01 | ✓ | Context lifting (Kleisli) |
| REQ-TRV-02 | ✓ | Grain safety |
| REQ-TYP-01 | ✓ | Basic type system |
| REQ-TYP-03 | ✓ | Error domain (Either monad) |
| REQ-INT-03 | Partial | Basic lineage (input/output tracking) |
| REQ-AI-01 | ✓ | Topological validation |

### 1.3 Out of Scope for MVP

- Adjoint morphisms (reverse traversal)
- Cross-domain fidelity
- Advanced lineage modes (Key-Derivable, Sampled)
- Data quality monitoring
- Immutable run hierarchy (full implementation)

---

## 2. Architecture Overview

### 2.1 Component Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           CDME SPARK ENGINE                              │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌─────────────┐   ┌─────────────┐   ┌─────────────┐   ┌─────────────┐ │
│  │   CONFIG    │   │  REGISTRY   │   │  COMPILER   │   │  EXECUTOR   │ │
│  │   LOADER    │──►│   MANAGER   │──►│             │──►│             │ │
│  │             │   │             │   │             │   │             │ │
│  │ - YAML      │   │ - LDM Store │   │ - Validator │   │ - Spark Job │ │
│  │ - Mapping   │   │ - PDM Store │   │ - Planner   │   │ - Transform │ │
│  │ - Sources   │   │ - Type Sys  │   │ - Optimizer │   │ - Error Hdl │ │
│  └─────────────┘   └─────────────┘   └─────────────┘   └─────────────┘ │
│         │                 │                 │                 │         │
│         └─────────────────┴─────────────────┴─────────────────┘         │
│                                     │                                    │
│                             ┌───────▼───────┐                           │
│                             │    OUTPUT     │                           │
│                             │               │                           │
│                             │ - DataFrame   │                           │
│                             │ - Error DF    │                           │
│                             │ - Lineage     │                           │
│                             │ - Metrics     │                           │
│                             └───────────────┘                           │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Core Abstractions

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         CORE ABSTRACTIONS                                │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  Entity                    Morphism                    Mapping           │
│  ┌───────────────┐        ┌───────────────┐          ┌───────────────┐  │
│  │ name          │        │ name          │          │ name          │  │
│  │ grain         │        │ source        │          │ source_entity │  │
│  │ grain_key     │        │ target        │          │ target_entity │  │
│  │ attributes[]  │        │ cardinality   │          │ morphisms[]   │  │
│  │ relationships │        │ join_key      │          │ projections[] │  │
│  └───────────────┘        │ transform_fn  │          │ filters[]     │  │
│                           └───────────────┘          └───────────────┘  │
│                                                                          │
│  ExecutionContext         ExecutionResult            ErrorRecord         │
│  ┌───────────────┐        ┌───────────────┐          ┌───────────────┐  │
│  │ run_id        │        │ data (DF)     │          │ source_key    │  │
│  │ epoch         │        │ errors (DF)   │          │ error_type    │  │
│  │ spark_session │        │ lineage       │          │ morphism_path │  │
│  │ config        │        │ metrics       │          │ context       │  │
│  └───────────────┘        └───────────────┘          └───────────────┘  │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Configuration Schema

### 3.1 Master Configuration

```yaml
# config/cdme_config.yaml
cdme:
  version: "1.0"

  # Registry paths
  registry:
    ldm_path: "config/ldm/"          # Logical Data Model definitions
    pdm_path: "config/pdm/"          # Physical Data Model bindings
    types_path: "config/types/"       # Custom type definitions

  # Execution settings
  execution:
    mode: BATCH                       # BATCH | STREAMING
    error_threshold: 0.05             # 5% error rate halts job
    lineage_mode: BASIC               # BASIC | FULL | SAMPLED

  # Output settings
  output:
    data_path: "s3://bucket/output/data/"
    error_path: "s3://bucket/output/errors/"
    lineage_path: "s3://bucket/output/lineage/"
```

### 3.2 LDM Entity Definition

```yaml
# config/ldm/order.yaml
entity:
  name: Order
  description: "Customer order transactions"

  grain:
    level: ATOMIC
    key: [order_id]

  attributes:
    - name: order_id
      type: String
      nullable: false
      primary_key: true

    - name: customer_id
      type: String
      nullable: false

    - name: order_date
      type: Date
      nullable: false

    - name: total_amount
      type: Decimal(18,2)
      nullable: false
      semantic_type: Money
      refinement: "value > 0"

    - name: status
      type: String
      nullable: false
      allowed_values: [PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED]

  relationships:
    - name: customer
      target: Customer
      cardinality: N:1
      join_key: customer_id
      description: "Order belongs to one customer"

    - name: line_items
      target: OrderLineItem
      cardinality: 1:N
      join_key: order_id
      description: "Order contains multiple line items"
```

### 3.3 PDM Physical Binding

```yaml
# config/pdm/order_binding.yaml
binding:
  entity: Order

  physical:
    type: PARQUET                     # PARQUET | DELTA | JDBC | CSV
    location: "s3://data-lake/orders/"
    partition_columns: [order_date]

  boundaries:
    epoch:
      type: PARTITION_FILTER
      field: order_date

  generation_grain: EVENT             # EVENT | SNAPSHOT
```

### 3.4 Mapping Definition

```yaml
# config/mappings/order_summary.yaml
mapping:
  name: order_summary
  description: "Daily order summary per customer"

  source:
    entity: Order
    epoch: "${epoch}"                 # Parameterized

  target:
    entity: DailyOrderSummary
    grain:
      level: DAILY
      key: [customer_id, order_date]

  # Morphism chain
  morphisms:
    # Filter active orders
    - name: filter_active
      type: FILTER
      predicate: "status != 'CANCELLED'"

    # Traverse to customer
    - name: enrich_customer
      type: TRAVERSE
      path: customer
      cardinality: N:1

  # Output projections
  projections:
    - name: customer_id
      source: customer.customer_id

    - name: order_date
      source: order_date

    - name: customer_name
      source: customer.name

    - name: order_count
      source: order_id
      aggregation: COUNT

    - name: total_amount
      source: total_amount
      aggregation: SUM

  # Validation rules
  validations:
    - name: amount_positive
      expression: "total_amount > 0"
      severity: ERROR
```

---

## 4. Pipeline Stages

### 4.1 Stage 1: CONFIG (Configuration Loading)

**Purpose**: Load and validate all configuration files.

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           CONFIG STAGE                                   │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  Input:                              Output:                             │
│  ┌─────────────────┐                ┌─────────────────┐                 │
│  │ config/*.yaml   │                │ CdmeConfig      │                 │
│  │ ldm/*.yaml      │  ──────────►   │ - registry_cfg  │                 │
│  │ pdm/*.yaml      │                │ - execution_cfg │                 │
│  │ mappings/*.yaml │                │ - output_cfg    │                 │
│  └─────────────────┘                └─────────────────┘                 │
│                                                                          │
│  Validations:                                                           │
│  - YAML syntax valid                                                    │
│  - Required fields present                                              │
│  - Cross-references resolve (entity names, types)                       │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

**Interface**:

```python
# Language-agnostic interface (Python-style pseudocode)

class ConfigLoader:
    def load(config_path: str) -> CdmeConfig:
        """
        Load master config and all referenced files.

        Returns:
            CdmeConfig with validated configuration

        Raises:
            ConfigValidationError if config is invalid
        """
        pass

class CdmeConfig:
    registry: RegistryConfig
    execution: ExecutionConfig
    output: OutputConfig
    mappings: Dict[str, MappingConfig]
```

---

### 4.2 Stage 2: INIT (Initialization)

**Purpose**: Initialize runtime context, load registry, establish Spark session.

```
┌─────────────────────────────────────────────────────────────────────────┐
│                            INIT STAGE                                    │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  Input:                              Output:                             │
│  ┌─────────────────┐                ┌─────────────────┐                 │
│  │ CdmeConfig      │                │ ExecutionContext│                 │
│  │                 │  ──────────►   │ - run_id        │                 │
│  │                 │                │ - epoch         │                 │
│  │                 │                │ - spark_session │                 │
│  └─────────────────┘                │ - registry      │                 │
│                                     │ - type_system   │                 │
│                                     └─────────────────┘                 │
│                                                                          │
│  Actions:                                                               │
│  1. Generate unique run_id                                              │
│  2. Create/get SparkSession                                             │
│  3. Load LDM entities into registry                                     │
│  4. Load PDM bindings into registry                                     │
│  5. Initialize type system                                              │
│  6. Validate registry consistency                                       │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

**Interface**:

```python
class CdmeEngine:
    def init(config: CdmeConfig, epoch: str) -> ExecutionContext:
        """
        Initialize CDME engine for execution.

        Args:
            config: Validated configuration
            epoch: Data epoch to process (e.g., "2024-12-15")

        Returns:
            ExecutionContext ready for compilation
        """
        pass

class ExecutionContext:
    run_id: str                       # Unique identifier for this run
    epoch: str                        # Data epoch being processed
    spark: SparkSession               # Spark session
    registry: SchemaRegistry          # Loaded LDM + PDM
    type_system: TypeSystem           # Type definitions + validators
    config: CdmeConfig                # Original config

class SchemaRegistry:
    entities: Dict[str, Entity]       # name -> Entity
    relationships: Dict[str, List[Relationship]]  # entity -> relationships
    bindings: Dict[str, PhysicalBinding]  # entity -> physical location

    def get_entity(name: str) -> Entity
    def get_relationship(entity: str, rel_name: str) -> Relationship
    def get_binding(entity: str) -> PhysicalBinding
    def validate_path(path: str) -> PathValidationResult
```

---

### 4.3 Stage 3: COMPILE (Compilation)

**Purpose**: Validate mapping, check types/grains, generate execution plan.

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          COMPILE STAGE                                   │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  Input:                              Output:                             │
│  ┌─────────────────┐                ┌─────────────────┐                 │
│  │ ExecutionContext│                │ ExecutionPlan   │                 │
│  │ MappingConfig   │  ──────────►   │ - source_plan   │                 │
│  │                 │                │ - morphism_ops  │                 │
│  │                 │                │ - projection_op │                 │
│  └─────────────────┘                │ - sink_plan     │                 │
│                                     └─────────────────┘                 │
│                                                                          │
│  Validations (Compile-Time):                                            │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │ 1. PATH VALIDATION                                               │   │
│  │    - All entity/relationship references exist                    │   │
│  │    - Attribute paths resolve correctly                           │   │
│  │                                                                  │   │
│  │ 2. TYPE UNIFICATION                                              │   │
│  │    - Join keys have compatible types                             │   │
│  │    - Aggregation inputs match expected types                     │   │
│  │    - Projection outputs match target schema                      │   │
│  │                                                                  │   │
│  │ 3. GRAIN SAFETY                                                  │   │
│  │    - No mixing grains without explicit aggregation               │   │
│  │    - 1:N traversals flagged, require context handling            │   │
│  │    - Target grain achievable from source + aggregations          │   │
│  │                                                                  │   │
│  │ 4. CARDINALITY TRACKING                                          │   │
│  │    - Track row count implications through chain                  │   │
│  │    - Flag potential fan-outs                                     │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

**Interface**:

```python
class Compiler:
    def compile(
        context: ExecutionContext,
        mapping: MappingConfig
    ) -> Either[CompilationError, ExecutionPlan]:
        """
        Compile mapping to execution plan.

        Returns:
            Right(ExecutionPlan) if valid
            Left(CompilationError) if validation fails
        """
        pass

class ExecutionPlan:
    source: SourcePlan                # How to read source data
    morphisms: List[MorphismOp]       # Ordered transformation operations
    projection: ProjectionOp          # Final column selection + aggregation
    sink: SinkPlan                    # How to write output

    # Metadata
    grain_transitions: List[GrainTransition]
    cardinality_analysis: CardinalityAnalysis
    estimated_cost: CostEstimate

class MorphismOp:
    """Base class for transformation operations"""
    name: str
    input_schema: Schema
    output_schema: Schema

class FilterOp(MorphismOp):
    predicate: Expression

class TraverseOp(MorphismOp):
    relationship: Relationship
    cardinality: Cardinality          # 1:1, N:1, 1:N
    join_type: JoinType               # INNER, LEFT, etc.

class AggregateOp(MorphismOp):
    group_by: List[str]
    aggregations: List[AggregationExpr]
```

---

### 4.4 Stage 4: EXECUTE (Execution)

**Purpose**: Execute the plan using Spark, handle errors, capture lineage.

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          EXECUTE STAGE                                   │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  Input:                              Output:                             │
│  ┌─────────────────┐                ┌─────────────────┐                 │
│  │ ExecutionContext│                │ ExecutionResult │                 │
│  │ ExecutionPlan   │  ──────────►   │ - data_df       │                 │
│  │                 │                │ - error_df      │                 │
│  │                 │                │ - lineage       │                 │
│  └─────────────────┘                │ - metrics       │                 │
│                                     └─────────────────┘                 │
│                                                                          │
│  Execution Flow:                                                        │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                                                                  │   │
│  │  1. READ SOURCE                                                  │   │
│  │     spark.read.parquet(source_path).filter(epoch_predicate)     │   │
│  │                          │                                       │   │
│  │                          ▼                                       │   │
│  │  2. APPLY MORPHISMS (in order)                                   │   │
│  │     for morphism in plan.morphisms:                              │   │
│  │         df = morphism.apply(df, context)                         │   │
│  │         # Each morphism returns Either[ErrorDF, DataFrame]       │   │
│  │                          │                                       │   │
│  │                          ▼                                       │   │
│  │  3. APPLY PROJECTIONS                                            │   │
│  │     df.groupBy(grain_key).agg(aggregations).select(projections) │   │
│  │                          │                                       │   │
│  │                          ▼                                       │   │
│  │  4. VALIDATE OUTPUT                                              │   │
│  │     - Check refinement types                                     │   │
│  │     - Route failures to error_df                                 │   │
│  │                          │                                       │   │
│  │                          ▼                                       │   │
│  │  5. WRITE OUTPUT                                                 │   │
│  │     data_df.write.parquet(output_path)                          │   │
│  │     error_df.write.parquet(error_path)                          │   │
│  │                                                                  │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

**Interface**:

```python
class Executor:
    def execute(
        context: ExecutionContext,
        plan: ExecutionPlan
    ) -> ExecutionResult:
        """
        Execute the compiled plan.

        Returns:
            ExecutionResult with data, errors, lineage, metrics
        """
        pass

class ExecutionResult:
    data: DataFrame                   # Valid output records
    errors: DataFrame                 # Error domain records
    lineage: LineageRecord            # Input/output tracking
    metrics: ExecutionMetrics         # Row counts, timing, etc.
    status: ExecutionStatus           # SUCCESS | PARTIAL | FAILED

class LineageRecord:
    run_id: str
    epoch: str
    mapping_name: str
    source_entity: str
    source_location: str
    source_row_count: int
    target_entity: str
    target_location: str
    target_row_count: int
    error_count: int
    morphism_chain: List[str]
    timestamp: datetime

class ExecutionMetrics:
    source_row_count: int
    output_row_count: int
    error_row_count: int
    duration_ms: int
    morphism_stats: Dict[str, MorphismStats]
```

---

## 5. Error Handling (Either Monad)

### 5.1 Error Domain Design

All failures route to the Error Domain - no exceptions, no silent drops.

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         ERROR DOMAIN                                     │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  Either[Error, Value] Pattern:                                          │
│                                                                          │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                                                                  │   │
│  │  Input Record                                                    │   │
│  │       │                                                          │   │
│  │       ▼                                                          │   │
│  │  ┌─────────────┐                                                │   │
│  │  │  Transform  │                                                │   │
│  │  └─────────────┘                                                │   │
│  │       │                                                          │   │
│  │       ├──── Success ────► Right(Value) ────► Output DataFrame    │   │
│  │       │                                                          │   │
│  │       └──── Failure ────► Left(Error) ────► Error DataFrame      │   │
│  │                                                                  │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                          │
│  Error Record Schema:                                                   │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │ source_key      STRING    - Primary key of failed record         │   │
│  │ error_type      STRING    - REFINEMENT | TYPE | JOIN | VALIDATION│   │
│  │ error_code      STRING    - Specific error code                  │   │
│  │ morphism_path   STRING    - Where in pipeline failure occurred   │   │
│  │ expected        STRING    - What was expected                    │   │
│  │ actual          STRING    - What was found                       │   │
│  │ context         MAP       - Additional context (field values)    │   │
│  │ run_id          STRING    - Execution run ID                     │   │
│  │ epoch           STRING    - Data epoch                           │   │
│  │ timestamp       TIMESTAMP - When error occurred                  │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### 5.2 Error Types

| Error Type | Cause | Example |
|------------|-------|---------|
| REFINEMENT | Value violates refinement predicate | amount = -50 (expected > 0) |
| TYPE | Type cast failure | "abc" cannot cast to Integer |
| JOIN | Join key not found in target | customer_id = "C999" not in Customer |
| VALIDATION | Custom rule violation | ship_date < order_date |
| NULL | Unexpected null in non-nullable field | customer_id is NULL |

### 5.3 Batch Failure Threshold

```python
class ErrorThresholdChecker:
    def check(
        total_rows: int,
        error_rows: int,
        threshold: float
    ) -> Either[ThresholdExceeded, Continue]:
        """
        Check if error rate exceeds threshold.

        If error_rows / total_rows > threshold:
            Return Left(ThresholdExceeded) - halt job
        Else:
            Return Right(Continue) - proceed
        """
        pass
```

---

## 6. Implementation Patterns

### 6.1 Spark DataFrame Operations

**Pattern: Morphism as DataFrame Transformation**

```python
# Pseudocode - works in PySpark or Scala

class TraverseMorphism:
    def apply(
        df: DataFrame,
        relationship: Relationship,
        context: ExecutionContext
    ) -> DataFrame:

        # Get target entity binding
        target_binding = context.registry.get_binding(relationship.target)

        # Load target DataFrame
        target_df = context.spark.read.parquet(target_binding.location)

        # Perform join based on cardinality
        if relationship.cardinality == "N:1":
            # Lookup join - row count preserved
            return df.join(
                target_df,
                df[relationship.join_key] == target_df[relationship.target_key],
                "left"
            )
        elif relationship.cardinality == "1:N":
            # Explosion join - row count increases
            # Must track this for grain safety
            return df.join(
                target_df,
                df[relationship.join_key] == target_df[relationship.target_key],
                "inner"
            ).withColumn("_grain_context", lit("EXPLODED"))
```

**Pattern: Error Handling with Columns**

```python
class RefinementValidator:
    def validate(
        df: DataFrame,
        column: str,
        predicate: str,
        error_code: str
    ) -> Tuple[DataFrame, DataFrame]:
        """
        Split DataFrame into valid and error records.

        Returns:
            (valid_df, error_df)
        """
        # Add validation column
        validated = df.withColumn(
            "_valid",
            expr(predicate)
        )

        # Split
        valid_df = validated.filter(col("_valid")).drop("_valid")

        error_df = validated.filter(~col("_valid")).select(
            col(primary_key).alias("source_key"),
            lit("REFINEMENT").alias("error_type"),
            lit(error_code).alias("error_code"),
            lit(f"source.{column}").alias("morphism_path"),
            lit(predicate).alias("expected"),
            col(column).cast("string").alias("actual")
        )

        return (valid_df, error_df)
```

### 6.2 Grain Safety Implementation

```python
class GrainTracker:
    """
    Track grain through morphism chain.
    Prevent mixing incompatible grains.
    """

    def __init__(self, source_grain: GrainLevel):
        self.current_grain = source_grain
        self.grain_stack = [source_grain]

    def traverse(self, relationship: Relationship) -> Either[GrainError, GrainLevel]:
        if relationship.cardinality == "1:N":
            # Grain explodes - push to stack
            new_grain = GrainLevel(
                level=self.current_grain.level - 1,  # Finer grain
                context="EXPLODED"
            )
            self.grain_stack.append(new_grain)
            self.current_grain = new_grain
            return Right(new_grain)

        elif relationship.cardinality == "N:1":
            # Grain preserved - lookup
            return Right(self.current_grain)

    def aggregate(self, target_grain: GrainLevel) -> Either[GrainError, GrainLevel]:
        if target_grain.level < self.current_grain.level:
            return Left(GrainError("Cannot aggregate to finer grain"))

        # Pop grain stack until we reach target
        while self.current_grain.level < target_grain.level:
            self.grain_stack.pop()
            self.current_grain = self.grain_stack[-1]

        return Right(target_grain)
```

---

## 7. File Structure

```
design_spark/
├── SPARK_SOLUTION_DESIGN.md          # This document
│
├── config/
│   ├── cdme_config.yaml              # Master configuration
│   │
│   ├── ldm/                          # Logical Data Model
│   │   ├── order.yaml
│   │   ├── customer.yaml
│   │   ├── order_line_item.yaml
│   │   └── product.yaml
│   │
│   ├── pdm/                          # Physical Data Model
│   │   ├── order_binding.yaml
│   │   ├── customer_binding.yaml
│   │   └── product_binding.yaml
│   │
│   ├── types/                        # Custom types
│   │   ├── money.yaml
│   │   └── email.yaml
│   │
│   └── mappings/                     # Mapping definitions
│       ├── order_summary.yaml
│       └── customer_360.yaml
│
├── src/                              # Implementation (PySpark or Scala)
│   ├── config/
│   │   └── config_loader.py
│   │
│   ├── registry/
│   │   ├── schema_registry.py
│   │   └── type_system.py
│   │
│   ├── compiler/
│   │   ├── compiler.py
│   │   ├── validator.py
│   │   └── planner.py
│   │
│   ├── executor/
│   │   ├── executor.py
│   │   ├── morphisms/
│   │   │   ├── filter_morphism.py
│   │   │   ├── traverse_morphism.py
│   │   │   └── aggregate_morphism.py
│   │   └── error_handler.py
│   │
│   └── main.py                       # Entry point
│
└── tests/
    ├── test_config_loader.py
    ├── test_compiler.py
    ├── test_executor.py
    └── fixtures/
        └── sample_data/
```

---

## 8. Entry Point (Steel Thread)

### 8.1 Main Execution Flow

```python
# main.py - Entry point for CDME Spark

def main(config_path: str, mapping_name: str, epoch: str):
    """
    Execute CDME mapping.

    Args:
        config_path: Path to cdme_config.yaml
        mapping_name: Name of mapping to execute
        epoch: Data epoch (e.g., "2024-12-15")
    """

    # STAGE 1: CONFIG
    print(f"[CONFIG] Loading configuration from {config_path}")
    config = ConfigLoader.load(config_path)

    # STAGE 2: INIT
    print(f"[INIT] Initializing CDME engine for epoch {epoch}")
    engine = CdmeEngine()
    context = engine.init(config, epoch)
    print(f"[INIT] Run ID: {context.run_id}")
    print(f"[INIT] Loaded {len(context.registry.entities)} entities")

    # STAGE 3: COMPILE
    print(f"[COMPILE] Compiling mapping: {mapping_name}")
    mapping = config.mappings[mapping_name]

    compile_result = Compiler.compile(context, mapping)

    if compile_result.is_left():
        error = compile_result.left()
        print(f"[COMPILE] FAILED: {error}")
        sys.exit(1)

    plan = compile_result.right()
    print(f"[COMPILE] Plan generated with {len(plan.morphisms)} morphisms")
    print(f"[COMPILE] Grain transitions: {plan.grain_transitions}")

    # STAGE 4: EXECUTE
    print(f"[EXECUTE] Executing plan...")
    result = Executor.execute(context, plan)

    # Report results
    print(f"[EXECUTE] Status: {result.status}")
    print(f"[EXECUTE] Input rows: {result.metrics.source_row_count}")
    print(f"[EXECUTE] Output rows: {result.metrics.output_row_count}")
    print(f"[EXECUTE] Error rows: {result.metrics.error_row_count}")
    print(f"[EXECUTE] Duration: {result.metrics.duration_ms}ms")

    # Write lineage
    print(f"[OUTPUT] Writing lineage to {config.output.lineage_path}")
    write_lineage(result.lineage, config.output.lineage_path)

    if result.status == ExecutionStatus.FAILED:
        sys.exit(1)

    return result


if __name__ == "__main__":
    import sys
    main(
        config_path=sys.argv[1],
        mapping_name=sys.argv[2],
        epoch=sys.argv[3]
    )
```

### 8.2 Example Execution

```bash
# Run order summary mapping for December 15, 2024
spark-submit \
    --master yarn \
    --deploy-mode cluster \
    main.py \
    config/cdme_config.yaml \
    order_summary \
    2024-12-15
```

**Expected Output**:

```
[CONFIG] Loading configuration from config/cdme_config.yaml
[INIT] Initializing CDME engine for epoch 2024-12-15
[INIT] Run ID: run_20241215_143052_a1b2c3
[INIT] Loaded 4 entities
[COMPILE] Compiling mapping: order_summary
[COMPILE] Plan generated with 2 morphisms
[COMPILE] Grain transitions: [ATOMIC -> DAILY]
[EXECUTE] Executing plan...
[EXECUTE] Status: SUCCESS
[EXECUTE] Input rows: 1000000
[EXECUTE] Output rows: 50000
[EXECUTE] Error rows: 150
[EXECUTE] Duration: 45230ms
[OUTPUT] Writing lineage to s3://bucket/output/lineage/
```

---

## 9. Requirements Traceability

| Requirement | Component | Implementation |
|-------------|-----------|----------------|
| REQ-LDM-01 | SchemaRegistry | YAML entity definitions, graph structure |
| REQ-LDM-02 | Relationship | cardinality field (1:1, N:1, 1:N) |
| REQ-LDM-03 | Compiler.validate_path | Path validation at compile time |
| REQ-LDM-06 | GrainTracker | Grain metadata, grain safety |
| REQ-PDM-01 | PhysicalBinding | Separate LDM/PDM configuration |
| REQ-TRV-01 | TraverseMorphism | Kleisli arrow for 1:N |
| REQ-TRV-02 | GrainTracker | Grain safety validation |
| REQ-TYP-01 | TypeSystem | Type definitions, validation |
| REQ-TYP-03 | ErrorHandler | Either monad, Error DataFrame |
| REQ-INT-03 | LineageRecord | Basic input/output lineage |
| REQ-AI-01 | Compiler | Topological validation |

---

## 10. Extension Points

The following are identified for future iterations:

| Extension | Requirements | Priority |
|-----------|--------------|----------|
| Adjoint morphisms | REQ-ADJ-* | High |
| Full lineage modes | RIC-LIN-* | Medium |
| Immutable run hierarchy | REQ-TRV-05-A/B | High |
| Cross-domain fidelity | REQ-COV-* | Medium |
| Data quality monitoring | REQ-DQ-* | Medium |
| Streaming mode | - | Low |

---

## 11. Next Steps

1. **Create sample configuration files** (ldm, pdm, mappings)
2. **Implement ConfigLoader** with YAML parsing
3. **Implement SchemaRegistry** with entity/relationship graph
4. **Implement Compiler** with path validation
5. **Implement Executor** with basic morphisms
6. **Add unit tests** for each component
7. **Run steel thread** end-to-end with sample data

---

**Document Status**: Draft
**Last Updated**: 2025-12-10
**Audience**: Data Engineers, Platform Engineers
