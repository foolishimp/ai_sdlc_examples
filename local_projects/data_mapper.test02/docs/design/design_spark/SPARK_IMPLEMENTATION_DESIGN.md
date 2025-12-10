# CDME - Spark Implementation Design

**Document Type**: Implementation Design Specification
**Project**: Categorical Data Mapping & Computation Engine (CDME)
**Variant**: `design_spark` (Apache Spark)
**Version**: 1.1
**Date**: 2025-12-10
**Updated**: 2025-12-11 (Spark 4.0 migration)
**Status**: Draft
**Derived From**:
- [Generic Reference Design](../data_mapper/AISDLC_IMPLEMENTATION_DESIGN.md)
- [Requirements](../../requirements/AISDLC_IMPLEMENTATION_REQUIREMENTS.md)

---

## Purpose

This document defines the **Apache Spark-specific implementation** of the CDME architecture. It maps abstract components to Spark primitives and documents technology decisions via ADRs.

**Target Use Cases**:
- Large-scale distributed batch processing
- Complex multi-source joins
- Streaming with structured streaming
- ML pipeline integration

---

## Technology Stack

| Layer | Technology | Version | Notes |
|-------|------------|---------|-------|
| Execution Engine | Apache Spark | 4.0.1 | See ADR-001, ADR-011 |
| Language | Scala | 2.13.12 | See ADR-002 |
| Java Runtime | OpenJDK | 17 or 21 (LTS) | See ADR-011 |
| Storage Format | TBD | - | Delta/Iceberg/Parquet (ADR-003) |
| Lineage | TBD | - | OpenLineage/Spline (ADR-004) |
| Metadata Store | TBD | - | Hive Metastore/Unity Catalog |

**Version History**:
- v1.0 (2025-12-10): Initial design with Spark 3.5.0, Scala 2.12.18
- v1.1 (2025-12-11): Updated to Spark 4.0.1, Scala 2.13.12, Java 17/21 (ADR-011)

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         CDME Spark Implementation                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────────┐│
│  │                          SPARK DRIVER                                   ││
│  │  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐     ││
│  │  │  LDM Parser     │───▶│ Topological     │───▶│  Catalyst       │     ││
│  │  │  (YAML/JSON)    │    │ Compiler        │    │  Plan Generator │     ││
│  │  └─────────────────┘    └─────────────────┘    └────────┬────────┘     ││
│  │                                                          │              ││
│  │  ┌─────────────────┐    ┌─────────────────┐    ┌────────▼────────┐     ││
│  │  │  PDM Resolver   │───▶│ Implementation  │───▶│  Spark          │     ││
│  │  │  (Catalog)      │    │ Functor         │    │  LogicalPlan    │     ││
│  │  └─────────────────┘    └─────────────────┘    └─────────────────┘     ││
│  └─────────────────────────────────────────────────────────────────────────┘│
│                                      │                                       │
│                                      ▼                                       │
│  ┌─────────────────────────────────────────────────────────────────────────┐│
│  │                          SPARK EXECUTORS                                ││
│  │  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐     ││
│  │  │  DataFrame      │    │  Adjoint        │    │  Error          │     ││
│  │  │  Transformations│    │  Wrappers       │    │  Accumulator    │     ││
│  │  └─────────────────┘    └─────────────────┘    └─────────────────┘     ││
│  │                                                                         ││
│  │  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐     ││
│  │  │  Broadcast      │    │  Reverse-Join   │    │  Lineage        │     ││
│  │  │  Lookups        │    │  Capture        │    │  Writer         │     ││
│  │  └─────────────────┘    └─────────────────┘    └─────────────────┘     ││
│  └─────────────────────────────────────────────────────────────────────────┘│
│                                      │                                       │
│                                      ▼                                       │
│  ┌─────────────────────────────────────────────────────────────────────────┐│
│  │                          STORAGE LAYER                                  ││
│  │  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐     ││
│  │  │  Delta Lake     │    │  Adjoint        │    │  Error          │     ││
│  │  │  Tables         │    │  Metadata       │    │  Domain         │     ││
│  │  └─────────────────┘    └─────────────────┘    └─────────────────┘     ││
│  └─────────────────────────────────────────────────────────────────────────┘│
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Component Mapping

### TopologicalCompiler → Spark Catalyst Integration

**Generic Interface**: `TopologicalCompiler`
**Spark Implementation**: `SparkTopologicalCompiler`

```scala
/**
 * Spark-specific topological compiler that generates Catalyst LogicalPlans.
 */
class SparkTopologicalCompiler(spark: SparkSession) extends TopologicalCompiler {

  // Compile LDM path to Catalyst LogicalPlan
  def compile(mapping: MappingDefinition): LogicalPlan = {
    // 1. Validate topology
    // 2. Check grain safety
    // 3. Generate Catalyst plan
    // 4. Apply adjoint capture rules
  }

  // Leverage Catalyst optimizer
  def optimize(plan: LogicalPlan): LogicalPlan = {
    spark.sessionState.optimizer.execute(plan)
  }
}
```

**Spark-Specific Considerations**:
- Use Catalyst's type system for type unification
- Leverage predicate pushdown for grain filtering
- Integrate with Spark's cost-based optimizer

---

### MorphismExecutor → DataFrame Transformations

**Generic Interface**: `MorphismExecutor`
**Spark Implementation**: `DataFrameMorphismExecutor`

```scala
/**
 * Execute morphisms as DataFrame transformations.
 */
class DataFrameMorphismExecutor extends MorphismExecutor {

  // 1:1 morphism → map/select
  def execute1to1[A, B](df: DataFrame, morphism: Morphism[A, B]): DataFrame = {
    df.select(morphism.transform(col("*")))
  }

  // N:1 morphism → join
  def executeNto1[A, B](df: DataFrame, lookup: DataFrame, morphism: Morphism[A, B]): DataFrame = {
    df.join(broadcast(lookup), morphism.joinCondition)
  }

  // 1:N morphism → explode (Kleisli)
  def execute1toN[A, B](df: DataFrame, morphism: Morphism[A, B]): DataFrame = {
    df.select(explode(morphism.expand(col("*"))).as("expanded"))
  }

  // Aggregation → groupBy with monoid
  def executeAggregation[A, B](df: DataFrame, morphism: AggregationMorphism[A, B]): DataFrame = {
    df.groupBy(morphism.groupKeys: _*)
      .agg(morphism.monoid.combine(col(morphism.valueCol)))
  }
}
```

---

### Adjoint Wrappers → Reverse-Join Capture

**Generic Interface**: `AdjointMetadata`
**Spark Implementation**: `SparkAdjointWrapper`

```scala
/**
 * Wraps Spark operations to capture adjoint backward metadata.
 */
object SparkAdjointWrapper {

  /**
   * Wrap groupBy to capture reverse-join table.
   *
   * Implements: REQ-ADJ-04
   */
  def groupByWithAdjoint(
    df: DataFrame,
    groupCols: Seq[String],
    aggExprs: Seq[Column]
  ): AdjointResult[DataFrame] = {

    // Forward: standard groupBy
    val aggregated = df.groupBy(groupCols.map(col): _*).agg(aggExprs.head, aggExprs.tail: _*)

    // Capture reverse-join: group_key → constituent_keys
    val reverseJoin = df.select(
      struct(groupCols.map(col): _*).as("group_key"),
      col("_row_id").as("source_key")  // Assumes row ID column
    )

    AdjointResult(
      output = aggregated,
      metadata = ReverseJoinMetadata(reverseJoin),
      classification = PROJECTION
    )
  }

  /**
   * Wrap filter to capture filtered-out keys.
   *
   * Implements: REQ-ADJ-05
   */
  def filterWithAdjoint(
    df: DataFrame,
    condition: Column,
    captureFiltered: Boolean = true
  ): AdjointResult[DataFrame] = {

    val passed = df.filter(condition)

    val metadata = if (captureFiltered) {
      val filteredOut = df.filter(!condition).select("_row_id")
      FilteredKeysMetadata(filteredOut)
    } else {
      NoMetadata  // Lossy mode
    }

    AdjointResult(
      output = passed,
      metadata = metadata,
      classification = if (captureFiltered) EMBEDDING else LOSSY
    )
  }

  /**
   * Wrap explode (1:N) to capture parent-child mapping.
   *
   * Implements: REQ-ADJ-06
   */
  def explodeWithAdjoint(
    df: DataFrame,
    arrayCol: String
  ): AdjointResult[DataFrame] = {

    val exploded = df.select(
      col("_row_id").as("parent_key"),
      explode(col(arrayCol)).as("child"),
      monotonically_increasing_id().as("child_key")
    )

    val parentChildMapping = exploded.select("child_key", "parent_key")

    AdjointResult(
      output = exploded,
      metadata = ParentChildMetadata(parentChildMapping),
      classification = PROJECTION
    )
  }
}
```

---

### SheafManager → Partition Management

**Generic Interface**: `SheafManager`
**Spark Implementation**: `SparkSheafManager`

```scala
/**
 * Manage epochs via Spark partitioning and Delta time travel.
 */
class SparkSheafManager(spark: SparkSession) extends SheafManager {

  // Define epoch from partition
  def defineEpoch(table: String, epochCol: String, epochValue: Any): Epoch = {
    SparkEpoch(table, epochCol, epochValue)
  }

  // Check sheaf consistency for join
  def checkConsistency(left: DataFrame, right: DataFrame): Result[Unit, SheafViolation] = {
    // Check partition alignment
    val leftPartitions = left.rdd.partitions
    val rightPartitions = right.rdd.partitions

    // Validate epoch compatibility
    // ...
  }

  // Resolve temporal semantics with Delta time travel
  def resolveTemporalSemantics(
    table: String,
    semantics: TemporalSemantics,
    asOfTimestamp: Option[Timestamp]
  ): DataFrame = semantics match {
    case AS_OF => spark.read.format("delta")
      .option("timestampAsOf", asOfTimestamp.get)
      .table(table)
    case LATEST => spark.read.format("delta").table(table)
    case EXACT(version) => spark.read.format("delta")
      .option("versionAsOf", version)
      .table(table)
  }
}
```

---

### ErrorDomain → Spark Accumulators + DLQ

**Generic Interface**: `ErrorDomain`
**Spark Implementation**: `SparkErrorDomain`

```scala
/**
 * Error handling via accumulators and dead-letter queue.
 */
class SparkErrorDomain(spark: SparkSession, config: ErrorConfig) extends ErrorDomain {

  // Accumulator for error stats
  private val errorCount = spark.sparkContext.longAccumulator("errors")
  private val errorBuffer = spark.sparkContext.collectionAccumulator[ErrorObject]("error_buffer")

  // Route error (called from executor)
  def routeError(error: ErrorObject): Unit = {
    errorCount.add(1)
    if (errorBuffer.value.size < config.bufferLimit) {
      errorBuffer.add(error)
    }
  }

  // Check threshold (called from driver)
  def checkThreshold(): ThresholdResult = {
    val count = errorCount.value
    val totalRows = ... // From telemetry

    if (count > config.absoluteThreshold) {
      ThresholdResult.Halt(ABSOLUTE_COUNT, count)
    } else if (count.toDouble / totalRows > config.percentageThreshold) {
      ThresholdResult.Halt(PERCENTAGE, count.toDouble / totalRows)
    } else {
      ThresholdResult.Continue
    }
  }

  // Write to DLQ (dead-letter queue)
  def flushToDLQ(): Unit = {
    val errors = errorBuffer.value.asScala.toSeq
    spark.createDataFrame(errors).write
      .format("delta")
      .mode("append")
      .save(config.dlqPath)
  }
}
```

---

### ResidueCollector → OpenLineage Integration

**Generic Interface**: `ResidueCollector`
**Spark Implementation**: `SparkLineageCollector`

```scala
/**
 * Capture lineage and emit OpenLineage events.
 */
class SparkLineageCollector(config: LineageConfig) extends ResidueCollector {

  // Capture lineage based on mode
  def capture(
    morphism: Morphism,
    inputDF: DataFrame,
    outputDF: DataFrame,
    mode: LineageMode
  ): LineageRecord = mode match {

    case FULL =>
      // Capture all input/output keys
      val inputKeys = inputDF.select("_row_id").collect()
      val outputKeys = outputDF.select("_row_id").collect()
      FullLineageRecord(morphism.id, inputKeys, outputKeys)

    case KEY_DERIVABLE =>
      // Capture key envelopes only
      val envelope = KeyEnvelope(
        segmentId = morphism.id,
        startKey = inputDF.agg(min("_row_id")).first().getLong(0),
        endKey = inputDF.agg(max("_row_id")).first().getLong(0),
        keyGenFunction = morphism.keyFunction
      )
      KeyDerivableRecord(morphism.id, envelope)

    case SAMPLED =>
      // Statistical sample
      val sample = inputDF.sample(0.01).select("_row_id").collect()
      SampledLineageRecord(morphism.id, sample, sampleRate = 0.01)
  }

  // Emit OpenLineage event
  def emitOpenLineage(record: LineageRecord): Unit = {
    val event = OpenLineageEvent(
      eventType = "COMPLETE",
      job = OpenLineageJob(name = record.morphismId),
      inputs = record.inputs.map(toOpenLineageDataset),
      outputs = record.outputs.map(toOpenLineageDataset)
    )
    openLineageClient.emit(event)
  }
}
```

---

## Adjoint Metadata Storage

### Storage Strategies

| Strategy | Implementation | Use Case | Overhead |
|----------|---------------|----------|----------|
| INLINE | Store as columns in output DataFrame | Small metadata | Low |
| SEPARATE_TABLE | Delta table per morphism | Large aggregations | Medium |
| COMPRESSED | Roaring Bitmaps for key sets | High-cardinality | Low |

### Delta Lake Schema for Adjoint Metadata

```sql
-- Reverse-join table for aggregations
CREATE TABLE adjoint_reverse_join (
  morphism_id STRING,
  execution_id STRING,
  group_key STRUCT<...>,
  source_keys ARRAY<BIGINT>,
  epoch_id STRING,
  created_at TIMESTAMP
)
USING DELTA
PARTITIONED BY (epoch_id, morphism_id);

-- Filtered keys table
CREATE TABLE adjoint_filtered_keys (
  morphism_id STRING,
  execution_id STRING,
  filtered_keys ARRAY<BIGINT>,  -- Or Roaring Bitmap binary
  filter_predicate STRING,
  epoch_id STRING,
  created_at TIMESTAMP
)
USING DELTA
PARTITIONED BY (epoch_id, morphism_id);
```

---

## Architecture Decision Records

See [adrs/](adrs/) for all technology decisions.

| ADR | Decision | Status |
|-----|----------|--------|
| [ADR-001](adrs/ADR-001-execution-engine.md) | Why Apache Spark | Superseded by ADR-011 |
| [ADR-002](adrs/ADR-002-language-choice.md) | Scala vs PySpark | Proposed |
| [ADR-003](adrs/ADR-003-storage-format.md) | Delta Lake vs Iceberg vs Parquet | Proposed |
| [ADR-004](adrs/ADR-004-lineage-backend.md) | OpenLineage vs Spline | Proposed |
| [ADR-005](adrs/ADR-005-adjoint-metadata.md) | Adjoint Metadata Storage Strategy | Proposed |
| [ADR-006](adrs/ADR-006-scala-type-system.md) | Scala Type System for CDME | Accepted |
| [ADR-007](adrs/ADR-007-scala-either-monad.md) | Either Monad for Error Handling | Accepted |
| [ADR-008](adrs/ADR-008-scala-aggregation-patterns.md) | Scala Aggregation Patterns | Accepted |
| [ADR-009](adrs/ADR-009-scala-project-structure.md) | Scala Project Structure and Build System | Accepted |
| [ADR-010](adrs/ADR-010-scala-config-parsing.md) | Scala Configuration Parsing | Accepted |
| [ADR-011](adrs/ADR-011-spark-4-0-migration.md) | **Migration to Spark 4.0.1** | **Accepted** |

---

## Design-to-Requirement Traceability

| Spark Component | Generic Component | Requirements |
|-----------------|-------------------|--------------|
| SparkTopologicalCompiler | TopologicalCompiler | REQ-LDM-01..03, REQ-TYP-01..06, REQ-AI-01 |
| DataFrameMorphismExecutor | MorphismExecutor | REQ-LDM-04, REQ-TRV-01, REQ-INT-01..08 |
| SparkAdjointWrapper | AdjointCompiler + MorphismExecutor | REQ-ADJ-01..07 |
| SparkSheafManager | SheafManager | REQ-PDM-03, REQ-TRV-03, REQ-SHF-01 |
| SparkErrorDomain | ErrorDomain | REQ-TYP-03, REQ-ERROR-01, RIC-ERR-01 |
| SparkLineageCollector | ResidueCollector | REQ-INT-03, RIC-LIN-01..07 |
| DeltaImplementationFunctor | ImplementationFunctor | REQ-PDM-01..05, RIC-PHY-01 |

---

## Performance Considerations

### Adjoint Overhead

| Operation | Forward Cost | Adjoint Capture Cost | Notes |
|-----------|--------------|---------------------|-------|
| groupBy | O(n) | O(n) for reverse-join | Consider sampling for large groups |
| filter | O(n) | O(filtered) | Only capture if reconciliation needed |
| explode | O(n*m) | O(n) for parent map | Low overhead |
| join | O(n+m) | O(matched) | Capture join keys only |

### Optimization Strategies

1. **Lazy adjoint capture**: Only materialize reverse-join when backward is called
2. **Roaring Bitmaps**: Compress key sets for high-cardinality groupings
3. **Partition-local metadata**: Store adjoint metadata co-located with output partitions
4. **TTL-based pruning**: Auto-delete adjoint metadata after retention period

---

## Migration to Spark 4.0

**Important**: This design has been updated for Apache Spark 4.0.1 (see ADR-011).

### Key Changes from Spark 3.5

| Aspect | Spark 3.5 | Spark 4.0 |
|--------|-----------|-----------|
| Scala Version | 2.12.18 | 2.13.12 |
| Java Support | 8, 11, 17 | 17, 21 (25 TBD) |
| ANSI SQL Mode | Optional | Default-enabled |
| VARIANT Type | N/A | Available |
| Spark Connect | Optional | Default-enabled |

### Impact on CDME Design

- **Type Safety**: ANSI SQL mode aligns with REQ-TYP-01 goals
- **Performance**: Catalyst optimizer improvements benefit complex joins
- **Adjoint Metadata**: VARIANT type enables flexible metadata storage
- **Future-Proof**: Java 17/21 LTS support through 2029/2031

See [ADR-011](adrs/ADR-011-spark-4-0-migration.md) for complete migration details.

---

**Document Status**: Draft
**Last Updated**: 2025-12-11
**Version**: 1.1
