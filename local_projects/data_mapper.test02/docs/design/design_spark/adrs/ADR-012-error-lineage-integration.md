# ADR-012: Error Handling, Lineage & Run Reproducibility Integration

**Status**: Proposed
**Date**: 2025-12-16
**Supersedes**: Partial implementations in existing codebase
**Context**: Critical gaps identified in CDME Spark implementation

---

## Context

The current CDME Spark implementation (as of 2025-12-16) has established:

1. **Folder structure** for test runs (`RunConfig.scala`) with timestamped directories
2. **Error domain infrastructure** (`ErrorDomain.scala`) with accumulators and thresholds
3. **Adjoint wrapper architecture** (design only - not yet integrated)
4. **OpenLineage intention** (documented but not implemented)

However, **four critical gaps** prevent production readiness:

### Gap 1: Silent Record Drops (REQ-TYP-03 Violation)

**Problem**: Errors are accumulated but not persisted. Failed records disappear.

```scala
// Current: ErrorDomain.scala accumulates errors in memory
errorBuffer.add(error)  // But never written to disk!

// Missing: Dead-Letter Queue (DLQ) persistence
// Missing: Error records with full context
```

**Impact**: Violates REQ-TYP-03 ("All invalid records must be modeled as Left(E) and routed to the Error Sink; they must not be silently dropped").

---

### Gap 2: Run Reproducibility (REQ-TRV-05-A Incomplete)

**Problem**: Run folders exist but lack cryptographic binding to guarantee immutability.

```scala
// Current: RunConfig creates folders
test-data/runs/1216_143052_baseline/
  config.json              // ✓ Exists
  accounting/              // ✓ Exists

// Missing: Manifest with hashes
// Missing: Code version binding
// Missing: Input data hashes
```

**Impact**: Cannot prove a run is reproducible. No cryptographic guarantee that re-running produces same results.

---

### Gap 3: OpenLineage Integration (REQ-INT-03 Not Implemented)

**Problem**: Lineage design exists but no emission infrastructure.

```
// Designed but not built:
- OpenLineage event structure
- Facets (schema, dataQuality, errorInfo, sourceCode)
- Event emission points in pipeline
```

**Impact**: No standard lineage output. Cannot integrate with lineage tools (Marquez, Atlan, etc.).

---

### Gap 4: Adjoint-Executor Integration Gap (REQ-ADJ-04/05/06)

**Problem**: Adjoint wrappers designed but not connected to AccountingRunner.

```scala
// Exists in design:
SparkAdjointWrapper.groupByWithAdjoint(...)

// Missing in AccountingRunner:
- No calls to adjoint wrappers
- No adjoint metadata capture
- No reverse-join storage
```

**Impact**: Cannot trace aggregations back to source records.

---

## Decision

We will implement a **unified error, lineage, and run manifest architecture** that:

1. **Routes all errors to DLQ** with full context
2. **Generates cryptographically-bound run manifests** for reproducibility
3. **Emits standard OpenLineage events** for each run
4. **Integrates adjoint metadata capture** into the execution pipeline

---

## Design

### 0. Fundamental Accounting Invariant (Zero-Loss Guarantee)

**The Core Problem**: Row counts are not proofs. We cannot simply say "input had 100,000 rows, output has 82,000 rows, errors has 150 rows" and trust that we haven't lost data.

**The Solution**: Use adjoint metadata to provide a **mathematical proof** that every input record is accounted for.

#### The Accounting Equation

For any transformation pipeline:

```
INPUT_RECORDS = OUTPUT_RECORDS + FILTERED_RECORDS + ERROR_RECORDS + AGGREGATED_SOURCES

Where:
- INPUT_RECORDS: Original source keys from input dataset
- OUTPUT_RECORDS: Keys that passed through 1:1 (isomorphism)
- FILTERED_RECORDS: Keys captured by FilteredKeysMetadata (intentionally excluded)
- ERROR_RECORDS: Keys routed to DLQ (failed validation/parsing)
- AGGREGATED_SOURCES: Keys absorbed into aggregates (via ReverseJoinMetadata)
```

**Invariant**: `|input_keys| = |Σ(adjoint_partitions)|`

#### Record Accounting Ledger

Every run produces a `ledger.json` that **proves** zero-loss:

```json
{
  "ledger_version": "1.0",
  "run_id": "baseline-test",
  "input_dataset": "test-data/datasets/airline-clean",

  "input_accounting": {
    "total_records": 349660,
    "source_key_field": "segment_id",
    "input_hash": "sha256:abc123..."
  },

  "output_accounting": {
    "partitions": [
      {
        "partition_type": "AGGREGATED",
        "description": "Records aggregated into daily summaries",
        "record_count": 345000,
        "adjoint_type": "ReverseJoinMetadata",
        "adjoint_location": "adjoint/reverse_joins/daily_revenue_aggregation.parquet",
        "verification": "All source keys recoverable via backward traversal"
      },
      {
        "partition_type": "FILTERED",
        "description": "Records filtered by status != FLOWN",
        "record_count": 4000,
        "adjoint_type": "FilteredKeysMetadata",
        "adjoint_location": "adjoint/filtered_keys/flown_status_filter.parquet",
        "verification": "All filtered keys captured with filter predicate"
      },
      {
        "partition_type": "ERROR",
        "description": "Records failed validation",
        "record_count": 660,
        "adjoint_type": "ErrorRecords",
        "adjoint_location": "errors/",
        "verification": "All error records have source_key linking to input"
      }
    ],
    "total_accounted": 349660,
    "unaccounted": 0
  },

  "verification": {
    "accounting_balanced": true,
    "proof_method": "adjoint_backward_traversal",
    "verification_query": "SELECT COUNT(DISTINCT source_key) FROM (SELECT source_key FROM reverse_joins UNION SELECT source_key FROM filtered_keys UNION SELECT source_key FROM errors)"
  }
}
```

#### Verification Algorithm

```scala
/**
 * Verify that every input record is accounted for.
 * Returns Left(discrepancy) if records are missing, Right(()) if balanced.
 */
def verifyAccountingInvariant(
  inputKeys: Set[String],
  reverseJoinKeys: Set[String],
  filteredKeys: Set[String],
  errorKeys: Set[String]
): Either[AccountingDiscrepancy, Unit] = {

  val accountedKeys = reverseJoinKeys ++ filteredKeys ++ errorKeys

  val missingFromInput = inputKeys -- accountedKeys
  val extraInOutput = accountedKeys -- inputKeys

  if (missingFromInput.isEmpty && extraInOutput.isEmpty) {
    Right(())
  } else {
    Left(AccountingDiscrepancy(
      missingKeys = missingFromInput,
      extraKeys = extraInOutput,
      inputCount = inputKeys.size,
      accountedCount = accountedKeys.size
    ))
  }
}
```

#### Why Adjoints Are Essential

1. **ReverseJoinMetadata**: For aggregations (N:1), captures ALL source keys that contributed to each aggregate. Backward traversal recovers the full set.

2. **FilteredKeysMetadata**: For filters, captures ALL keys that were filtered out. Combined with output keys, recovers full input.

3. **ErrorRecords**: Each error record contains `source_key` linking back to input.

**Without adjoints, we can only count rows. With adjoints, we can PROVE every record's fate.**

#### Handling Different Morphism Types

| Morphism Type | Adjoint Classification | Accounting Partition | Proof Method |
|--------------|----------------------|---------------------|--------------|
| Parse/Load | LOSSY → ERROR | error_records | source_key in DLQ |
| Filter | EMBEDDING | filtered_keys | FilteredKeysMetadata |
| Join (inner) | EMBEDDING | filtered_keys | Unmatched keys captured |
| Aggregate | PROJECTION | aggregated_sources | ReverseJoinMetadata |
| Explode | PROJECTION | parent_child | ParentChildMetadata (inverse aggregation) |

#### Run Completion Verification

Before a run is marked COMPLETE, the accounting invariant MUST pass:

```scala
def completeRun(runId: String): Either[AccountingFailure, RunResult] = {
  // 1. Collect all adjoint metadata
  val reverseJoinKeys = loadReverseJoinKeys(s"$outputDir/adjoint/reverse_joins/")
  val filteredKeys = loadFilteredKeys(s"$outputDir/adjoint/filtered_keys/")
  val errorKeys = loadErrorKeys(s"$outputDir/errors/")

  // 2. Load input keys
  val inputKeys = loadInputKeys(config.datasetPath)

  // 3. Verify invariant
  verifyAccountingInvariant(inputKeys, reverseJoinKeys, filteredKeys, errorKeys) match {
    case Left(discrepancy) =>
      // CRITICAL: Run failed accounting - emit FAIL event
      emitOpenLineageFailure(runId, "ACCOUNTING_INVARIANT_VIOLATED", discrepancy)
      Left(AccountingFailure(discrepancy))

    case Right(()) =>
      // Write verified ledger
      writeLedger(s"$outputDir/ledger.json", ...)
      emitOpenLineageComplete(runId, ...)
      Right(RunResult(...))
  }
}
```

---

### 1. Complete Folder Structure

```
test-data/
├── datasets/                           # Stable source data (already exists)
│   └── <datasetId>/
│       ├── dataset_config.json
│       ├── generation_report.json
│       ├── reference/
│       │   ├── airports.jsonl
│       │   ├── airlines.jsonl
│       │   └── countries.jsonl
│       └── bookings/
│           └── <date>/
│               ├── segments.jsonl
│               ├── journeys.jsonl
│               ├── customers.jsonl
│               └── error_manifest.jsonl  # If error injection enabled
│
└── runs/                               # Timestamped runs (enhanced)
    └── MMDD_HHmmss_<runId>/
        │
        │── manifest.json               # Cryptographic binding (immutable)
        │── config.json                 # Run configuration
        │── ledger.json                 # NEW: Accounting proof (zero-loss guarantee)
        │
        ├── accounting/                 # Processed output (aggregates)
        │   └── <flight_date>/
        │       └── daily_revenue_summary.jsonl
        │
        ├── errors/                     # Dead-Letter Queue (parse/validation failures)
        │   ├── error_type=parse_error/
        │   │   └── *.jsonl
        │   ├── error_type=validation_error/
        │   │   └── *.jsonl
        │   ├── error_type=join_error/
        │   │   └── *.jsonl
        │   └── _summary.json           # Error counts by type
        │
        ├── adjoint/                    # Adjoint metadata (backward traversal)
        │   ├── reverse_joins/          # Aggregation N:1 mappings
        │   │   └── <morphism_id>.parquet
        │   │       # Schema: group_key, source_key, morphism_id, run_id
        │   ├── filtered_keys/          # Filter excluded keys
        │   │   └── <morphism_id>.parquet
        │   │       # Schema: source_key, filter_predicate, morphism_id, run_id
        │   └── parent_child/           # Explode 1:N mappings
        │       └── <morphism_id>.parquet
        │           # Schema: child_key, parent_key, morphism_id, run_id
        │
        ├── lineage/                    # OpenLineage events
        │   ├── run_start.json          # START event
        │   ├── run_complete.json       # COMPLETE event (or run_fail.json)
        │   └── job_events.jsonl        # Per-morphism events
        │
        └── processing_report.json      # Summary with metrics and lineage IDs
```

**Key Additions**:
1. `ledger.json` - The accounting proof that all records are accounted for
2. `adjoint/` partitioned by metadata type for efficient backward queries
3. `errors/` partitioned by error type for analysis
4. Clear separation of concerns: output vs errors vs adjoint vs lineage

---

### 2. Run Manifest Schema (Cryptographic Binding)

**Purpose**: REQ-TRV-05-A - Guarantee immutable, reproducible runs.

**File**: `test-data/runs/MMDD_HHmmss_<runId>/manifest.json`

```json
{
  "manifest_version": "1.0",
  "run_id": "baseline-test",
  "timestamp": "2025-12-16T14:30:52Z",
  "folder_name": "1216_143052_baseline-test",

  "cryptographic_binding": {
    "config_hash": "sha256:a1b2c3d4...",
    "code_version": {
      "git_commit": "35eefcf",
      "git_branch": "main",
      "git_dirty": false,
      "source_hash": "sha256:e5f6g7h8..."
    },
    "input_hashes": {
      "dataset_path": "test-data/datasets/airline-clean",
      "dataset_manifest_hash": "sha256:i9j0k1l2...",
      "reference_data_hash": "sha256:m3n4o5p6...",
      "bookings_data_hash": "sha256:q7r8s9t0..."
    }
  },

  "processing_window": {
    "flight_date_start": "2024-01-15",
    "flight_date_end": "2024-02-15",
    "booking_date_start": "2024-01-01",
    "booking_date_end": "2024-01-10"
  },

  "execution_metadata": {
    "spark_version": "4.0.1",
    "scala_version": "2.13.12",
    "java_version": "21",
    "cdme_version": "1.0.0-alpha"
  },

  "output_locations": {
    "accounting_dir": "test-data/runs/1216_143052_baseline-test/accounting",
    "errors_dir": "test-data/runs/1216_143052_baseline-test/errors",
    "lineage_dir": "test-data/runs/1216_143052_baseline-test/lineage",
    "adjoint_dir": "test-data/runs/1216_143052_baseline-test/adjoint"
  },

  "openlineage_run_id": "01234567-89ab-cdef-0123-456789abcdef"
}
```

**Verification Algorithm**:

```scala
def verifyRunReproducibility(manifest: RunManifest): Either[String, Unit] = {
  // 1. Hash current config
  val currentConfigHash = sha256(readFile("config.json"))
  if (currentConfigHash != manifest.cryptographicBinding.configHash) {
    return Left(s"Config hash mismatch: $currentConfigHash != ${manifest.cryptographicBinding.configHash}")
  }

  // 2. Verify code version
  val currentCommit = getCurrentGitCommit()
  if (currentCommit != manifest.cryptographicBinding.codeVersion.gitCommit) {
    return Left(s"Code version mismatch: $currentCommit != ${manifest.cryptographicBinding.codeVersion.gitCommit}")
  }

  // 3. Verify input hashes
  val currentInputHash = sha256DigestDirectory(manifest.cryptographicBinding.inputHashes.datasetPath)
  if (currentInputHash != manifest.cryptographicBinding.inputHashes.datasetManifestHash) {
    return Left(s"Input data has changed: $currentInputHash != ${manifest.cryptographicBinding.inputHashes.datasetManifestHash}")
  }

  Right(())
}
```

---

### 3. Error Handling Architecture (DLQ Integration)

**Purpose**: REQ-TYP-03 - Route all errors to Error Domain, never drop.

#### 3.1 DLQ Record Schema

**File**: `test-data/runs/<run>/errors/<error_type>_errors.jsonl`

```jsonlines
{"source_key":"BKG_001","error_type":"refinement_error","morphism_path":"Flight.ticket_price","expected":"> 0","actual":"-50.00","context":{"entity":"Flight","field":"ticket_price","flight_id":"FL123","epoch":"2024-01-15"},"run_id":"1216_143052_baseline-test","openlineage_run_id":"01234567-89ab-cdef-0123-456789abcdef","timestamp":"2025-12-16T14:35:22Z","error_code":"NEGATIVE_PRICE"}
{"source_key":"BKG_042","error_type":"type_cast_error","morphism_path":"Booking.passenger_count","expected":"Integer","actual":"N/A","context":{"entity":"Booking","field":"passenger_count","booking_id":"BK042","epoch":"2024-01-15"},"run_id":"1216_143052_baseline-test","openlineage_run_id":"01234567-89ab-cdef-0123-456789abcdef","timestamp":"2025-12-16T14:35:23Z","error_code":"INVALID_INTEGER"}
```

**Schema**:

| Field | Type | Description | REQ-ERROR-01 Mapping |
|-------|------|-------------|---------------------|
| `source_key` | String | Unique ID of failed record | ✓ Source Entity identifier |
| `error_type` | String | Error category (refinement_error, type_cast_error, join_error, validation_error, null_error) | ✓ Type of constraint that failed |
| `morphism_path` | String | Path in graph where error occurred | ✓ Morphism path at which failure occurred |
| `expected` | String | Expected value or constraint | ✓ (Implicit in constraint type) |
| `actual` | String | Actual offending value | ✓ Offending value(s) |
| `context` | Map[String, String] | Additional context (entity, epoch, field, etc.) | ✓ Source Entity and Epoch |
| `run_id` | String | Run identifier | Traceability |
| `openlineage_run_id` | UUID | OpenLineage run UUID | Lineage integration |
| `timestamp` | Timestamp | When error occurred | Diagnostics |
| `error_code` | String | Specific error code | Categorization |

**REQ-ERROR-01 Compliance**: ✓ All required fields present.

#### 3.2 Error Capture Points

Errors are captured at these pipeline stages:

```scala
// 1. Source data ingestion (type casting, null checks)
val (validBookings, typeErrors) = loadBookings()
  .mapPartitions(validateTypes)  // Either[TypeCastError, Booking]

// 2. Morphism execution (refinement types, join failures)
val (validFlights, refinementErrors) = bookings
  .mapPartitions(validateRefinements)  // Either[RefinementError, Flight]

// 3. Join operations (join key not found)
val (matched, joinErrors) = flights
  .leftOuterJoin(reference)
  .mapPartitions(handleJoinFailures)  // Either[JoinError, Enriched]

// 4. Aggregation output (validation rules)
val (validOutput, validationErrors) = aggregated
  .mapPartitions(validateOutput)  // Either[ValidationError, Output]
```

#### 3.3 Threshold Checking and Halt

**Implementation** (already exists in `ErrorDomain.scala`):

```scala
// After each major stage, check threshold
val thresholdResult = errorDomain.checkThreshold(totalRows = processedCount)

thresholdResult match {
  case ThresholdResult.Continue =>
    // Proceed to next stage

  case ThresholdResult.Halt(reason, errorCount, threshold) =>
    // Flush errors to DLQ
    errorDomain.flushToDLQ()

    // Emit failure event
    emitOpenLineageFailure(reason, errorCount, threshold)

    // Halt job
    throw ThresholdExceededException(
      s"Error threshold exceeded: $errorCount errors (${reason}), threshold: $threshold"
    )
}
```

#### 3.4 DLQ Writer Integration

**Enhanced `SparkErrorDomain.flushToDLQ()`**:

```scala
class SparkErrorDomain(spark: SparkSession, config: ErrorConfig, runId: String, openLineageRunId: String) extends ErrorDomain {

  /**
   * Flush errors to DLQ with partitioning by error type.
   * Implements: REQ-TYP-03
   */
  def flushToDLQ(): Unit = {
    val errors = getErrors()

    if (errors.nonEmpty) {
      import spark.implicits._

      // Enhance errors with run metadata
      val enrichedErrors = errors.map { err =>
        ErrorRecord(
          sourceKey = err.sourceKey,
          errorType = err.errorType,
          morphismPath = err.morphismPath,
          expected = err.expected,
          actual = err.actual,
          context = err.context,
          runId = runId,
          openLineageRunId = openLineageRunId,
          timestamp = java.time.Instant.now(),
          errorCode = deriveErrorCode(err)
        )
      }

      val errorsDF = enrichedErrors.toDF()

      // Partition by error type for queryability
      errorsDF
        .repartition(col("error_type"))
        .write
        .mode("append")
        .partitionBy("error_type")
        .json(s"${config.dlqPath}")

      // Also write summary
      val errorSummary = errorsDF
        .groupBy("error_type", "morphism_path")
        .agg(
          count("*").as("count"),
          first("error_code").as("sample_error_code"),
          first("actual").as("sample_actual_value")
        )

      errorSummary.write
        .mode("overwrite")
        .json(s"${config.dlqPath}/_summary.json")
    }
  }

  private def deriveErrorCode(err: ErrorObject): String = {
    s"${err.errorType.toUpperCase}_${err.morphismPath.replace(".", "_").toUpperCase}"
  }
}

case class ErrorRecord(
  sourceKey: String,
  errorType: String,
  morphismPath: String,
  expected: String,
  actual: String,
  context: Map[String, String],
  runId: String,
  openLineageRunId: String,
  timestamp: java.time.Instant,
  errorCode: String
)
```

---

### 4. OpenLineage Integration

**Purpose**: REQ-INT-03 - Standard lineage emission for tool integration.

#### 4.1 OpenLineage Event Structure

**Reference**: [OpenLineage Spec 1.0](https://openlineage.io/docs/spec/overview)

**File**: `test-data/runs/<run>/lineage/run_event.json`

```json
{
  "eventType": "START",
  "eventTime": "2025-12-16T14:30:52.000Z",
  "run": {
    "runId": "01234567-89ab-cdef-0123-456789abcdef",
    "facets": {
      "nominalTime": {
        "_producer": "https://github.com/cdme/spark-executor",
        "_schemaURL": "https://openlineage.io/spec/facets/1-0-0/NominalTimeRunFacet.json",
        "nominalStartTime": "2024-01-15T00:00:00Z",
        "nominalEndTime": "2024-02-15T23:59:59Z"
      },
      "cdme_run": {
        "_producer": "https://github.com/cdme/spark-executor",
        "_schemaURL": "https://cdme.io/spec/facets/1-0-0/CdmeRunFacet.json",
        "run_id": "baseline-test",
        "folder_name": "1216_143052_baseline-test",
        "config_hash": "sha256:a1b2c3d4...",
        "code_version": "35eefcf",
        "input_dataset": "test-data/datasets/airline-clean",
        "reproducibility_manifest": "test-data/runs/1216_143052_baseline-test/manifest.json"
      }
    }
  },
  "job": {
    "namespace": "cdme",
    "name": "AccountingRunner",
    "facets": {
      "sourceCode": {
        "_producer": "https://github.com/cdme/spark-executor",
        "_schemaURL": "https://openlineage.io/spec/facets/1-0-0/SourceCodeJobFacet.json",
        "language": "Scala",
        "sourceCode": "AccountingRunner.scala"
      },
      "sql": {
        "_producer": "https://github.com/cdme/spark-executor",
        "_schemaURL": "https://openlineage.io/spec/facets/1-0-0/SQLJobFacet.json",
        "query": "-- Logical query plan\nSELECT flight_date, SUM(revenue) AS total_revenue\nFROM enriched_bookings\nGROUP BY flight_date"
      }
    }
  },
  "inputs": [],
  "outputs": [],
  "producer": "https://github.com/cdme/spark-executor/v1.0.0"
}
```

#### 4.2 Dataset Facets (Schema, Stats, Quality)

**File**: `test-data/runs/<run>/lineage/job_events.jsonl`

```jsonlines
{"eventType":"COMPLETE","eventTime":"2025-12-16T14:35:55.000Z","run":{"runId":"01234567-89ab-cdef-0123-456789abcdef"},"job":{"namespace":"cdme","name":"AccountingRunner"},"inputs":[{"namespace":"file","name":"test-data/datasets/airline-clean/bookings","facets":{"schema":{"_producer":"https://github.com/cdme/spark-executor","_schemaURL":"https://openlineage.io/spec/facets/1-0-0/SchemaDatasetFacet.json","fields":[{"name":"booking_id","type":"string"},{"name":"flight_id","type":"string"},{"name":"passenger_count","type":"integer"},{"name":"ticket_price","type":"decimal(10,2)"}]},"dataQualityMetrics":{"_producer":"https://github.com/cdme/spark-executor","_schemaURL":"https://openlineage.io/spec/facets/1-0-0/DataQualityMetricsInputDatasetFacet.json","rowCount":100000,"bytes":5242880,"fileCount":10,"columnMetrics":{"ticket_price":{"nullCount":0,"distinctCount":8523,"min":-50.0,"max":5000.0}}}}}],"outputs":[{"namespace":"file","name":"test-data/runs/1216_143052_baseline-test/accounting","facets":{"schema":{"_producer":"https://github.com/cdme/spark-executor","_schemaURL":"https://openlineage.io/spec/facets/1-0-0/SchemaDatasetFacet.json","fields":[{"name":"flight_date","type":"date"},{"name":"total_revenue","type":"decimal(18,2)"},{"name":"flight_count","type":"long"}]},"dataQualityMetrics":{"_producer":"https://github.com/cdme/spark-executor","_schemaURL":"https://openlineage.io/spec/facets/1-0-0/DataQualityMetricsOutputDatasetFacet.json","rowCount":31,"bytes":1024,"fileCount":1,"columnMetrics":{"total_revenue":{"nullCount":0,"distinctCount":31,"min":15000.0,"max":250000.0}}},"cdme_errors":{"_producer":"https://github.com/cdme/spark-executor","_schemaURL":"https://cdme.io/spec/facets/1-0-0/CdmeErrorFacet.json","error_count":150,"error_rate":0.0015,"errors_by_type":{"refinement_error":120,"type_cast_error":30},"dlq_location":"test-data/runs/1216_143052_baseline-test/errors"}}}],"producer":"https://github.com/cdme/spark-executor/v1.0.0"}
```

#### 4.3 Emission Points

```scala
object OpenLineageEmitter {

  /**
   * Emit START event at run initialization.
   */
  def emitRunStart(
    runId: UUID,
    config: TransformerRunConfig,
    manifest: RunManifest
  ): Unit = {
    val event = OpenLineageEvent(
      eventType = "START",
      eventTime = Instant.now(),
      run = RunFacet(runId, ...),
      job = JobFacet(...),
      inputs = List.empty,  // Will be populated in COMPLETE
      outputs = List.empty
    )

    writeEvent(s"${config.outputDir}/lineage/run_event.json", event)
  }

  /**
   * Emit COMPLETE event after successful run.
   */
  def emitRunComplete(
    runId: UUID,
    config: TransformerRunConfig,
    inputStats: DatasetStats,
    outputStats: DatasetStats,
    errorStats: ErrorStats
  ): Unit = {
    val event = OpenLineageEvent(
      eventType = "COMPLETE",
      eventTime = Instant.now(),
      run = RunFacet(runId, ...),
      job = JobFacet(...),
      inputs = List(
        InputDataset(
          namespace = "file",
          name = config.datasetPath,
          facets = InputFacets(
            schema = extractSchema(inputStats),
            dataQualityMetrics = inputStats.toMetrics()
          )
        )
      ),
      outputs = List(
        OutputDataset(
          namespace = "file",
          name = s"${config.outputDir}/accounting",
          facets = OutputFacets(
            schema = extractSchema(outputStats),
            dataQualityMetrics = outputStats.toMetrics(),
            cdmeErrors = CdmeErrorFacet(
              errorCount = errorStats.totalErrors,
              errorRate = errorStats.errorRate,
              errorsByType = errorStats.byType,
              dlqLocation = s"${config.outputDir}/errors"
            )
          )
        )
      )
    )

    appendEvent(s"${config.outputDir}/lineage/job_events.jsonl", event)
  }

  /**
   * Emit FAIL event if threshold exceeded.
   */
  def emitRunFailure(
    runId: UUID,
    config: TransformerRunConfig,
    reason: ThresholdReason,
    errorCount: Long,
    threshold: Double
  ): Unit = {
    val event = OpenLineageEvent(
      eventType = "FAIL",
      eventTime = Instant.now(),
      run = RunFacet(
        runId,
        facets = Map(
          "cdme_failure" -> CdmeFailureFacet(
            reason = reason.toString,
            errorCount = errorCount,
            threshold = threshold,
            message = s"Error threshold exceeded: $errorCount errors ($reason), threshold: $threshold"
          )
        )
      ),
      job = JobFacet(...),
      inputs = List.empty,
      outputs = List.empty
    )

    appendEvent(s"${config.outputDir}/lineage/job_events.jsonl", event)
  }
}
```

#### 4.4 Custom CDME Facets

**Error Facet** (`CdmeErrorFacet.json`):

```json
{
  "$schema": "https://json-schema.org/draft-07/schema#",
  "$id": "https://cdme.io/spec/facets/1-0-0/CdmeErrorFacet.json",
  "title": "CDME Error Facet",
  "type": "object",
  "properties": {
    "_producer": {
      "type": "string"
    },
    "_schemaURL": {
      "type": "string"
    },
    "error_count": {
      "type": "integer",
      "description": "Total number of errors routed to DLQ"
    },
    "error_rate": {
      "type": "number",
      "description": "Error rate as decimal (errors / total_records)"
    },
    "errors_by_type": {
      "type": "object",
      "description": "Error counts grouped by error type",
      "additionalProperties": {
        "type": "integer"
      }
    },
    "dlq_location": {
      "type": "string",
      "description": "Path to dead-letter queue directory"
    }
  },
  "required": ["_producer", "_schemaURL", "error_count", "error_rate", "errors_by_type", "dlq_location"]
}
```

---

### 5. Adjoint Metadata Integration

**Purpose**: REQ-ADJ-04/05/06 - Connect adjoint wrappers to execution pipeline.

#### 5.1 Adjoint Capture in AccountingRunner

**Current** (no adjoint capture):

```scala
// AccountingRunner.scala (current)
val dailySummary = enrichedBookings
  .groupBy($"flight_date")
  .agg(
    sum($"revenue").as("total_revenue"),
    count("*").as("flight_count")
  )
```

**Enhanced** (with adjoint capture):

```scala
// AccountingRunner.scala (enhanced)
import cdme.executor.SparkAdjointWrapper

val adjointResult = SparkAdjointWrapper.groupByWithAdjoint(
  df = enrichedBookings,
  groupCols = Seq("flight_date"),
  aggExprs = Seq(
    sum($"revenue").as("total_revenue"),
    count("*").as("flight_count")
  ),
  adjointConfig = AdjointConfig(
    captureReverseJoin = true,
    morphismId = "daily_revenue_aggregation",
    runId = config.runId,
    outputPath = s"${config.outputDir}/adjoint/reverse_joins"
  )
)

val dailySummary = adjointResult.output

// Persist adjoint metadata
adjointResult.metadata match {
  case ReverseJoinMetadata(reverseJoinDF) =>
    reverseJoinDF.write
      .mode("overwrite")
      .parquet(s"${config.outputDir}/adjoint/reverse_joins/daily_revenue_aggregation.parquet")

  case _ => // No metadata
}
```

#### 5.2 Reverse-Join Metadata Schema

**File**: `test-data/runs/<run>/adjoint/reverse_joins/<morphism_id>.parquet`

| Column | Type | Description |
|--------|------|-------------|
| `group_key` | Struct | Aggregation key (e.g., `{flight_date: "2024-01-15"}`) |
| `source_key` | String | Original record key (e.g., `booking_id`) |
| `morphism_id` | String | Morphism that performed aggregation |
| `run_id` | String | Run identifier |
| `epoch` | String | Data epoch |

**Example**:

```
group_key                   | source_key | morphism_id                  | run_id          | epoch
----------------------------|------------|------------------------------|-----------------|------------
{flight_date: "2024-01-15"} | BKG_001    | daily_revenue_aggregation    | baseline-test   | 2024-01-15
{flight_date: "2024-01-15"} | BKG_002    | daily_revenue_aggregation    | baseline-test   | 2024-01-15
{flight_date: "2024-01-15"} | BKG_003    | daily_revenue_aggregation    | baseline-test   | 2024-01-15
```

**Backward Traversal Query**:

```scala
// Given target record: flight_date = "2024-01-15", total_revenue = 125000.00
// Find source records that contributed to this aggregate

val reverseJoin = spark.read.parquet(
  "test-data/runs/1216_143052_baseline-test/adjoint/reverse_joins/daily_revenue_aggregation.parquet"
)

val sourceKeys = reverseJoin
  .filter($"group_key.flight_date" === "2024-01-15")
  .select("source_key")
  .collect()
  .map(_.getString(0))

// sourceKeys = ["BKG_001", "BKG_002", "BKG_003", ...]

// Now load original bookings
val sourceRecords = spark.read.parquet(
  "test-data/datasets/airline-clean/bookings"
).filter($"booking_id".isin(sourceKeys: _*))

// Result: All bookings that contributed to 2024-01-15 revenue
```

#### 5.3 Filtered Keys Metadata

**Purpose**: REQ-ADJ-05 - Capture filtered-out records for completeness.

**File**: `test-data/runs/<run>/adjoint/filtered_keys/<morphism_id>.parquet`

```scala
val adjointResult = SparkAdjointWrapper.filterWithAdjoint(
  df = bookings,
  condition = $"ticket_price" > 0,  // Filter out negative prices
  captureFiltered = true,
  adjointConfig = AdjointConfig(
    morphismId = "positive_price_filter",
    runId = config.runId,
    outputPath = s"${config.outputDir}/adjoint/filtered_keys"
  )
)

// adjointResult.metadata = FilteredKeysMetadata(filteredOutDF)
```

| Column | Type | Description |
|--------|------|-------------|
| `source_key` | String | Key of filtered record |
| `filter_predicate` | String | Predicate that excluded it |
| `morphism_id` | String | Filter morphism |
| `run_id` | String | Run identifier |
| `epoch` | String | Data epoch |

---

### 6. Updated Processing Pipeline

**Complete flow with error handling, lineage, and adjoint capture**:

```scala
object AccountingRunner {

  def run(config: TransformerRunConfig): Unit = {

    // 0. Initialize run
    val spark = SparkSession.builder()
      .appName(s"AccountingRunner-${config.runId}")
      .getOrCreate()

    val outputDir = config.initialize()

    // 1. Generate manifest
    val manifest = generateManifest(config)
    writeManifest(s"$outputDir/manifest.json", manifest)

    // 2. Initialize error domain
    val errorDomain = new SparkErrorDomain(
      spark = spark,
      config = ErrorConfig.default(s"$outputDir/errors"),
      runId = config.runId,
      openLineageRunId = manifest.openLineageRunId.toString
    )

    // 3. Emit OpenLineage START event
    OpenLineageEmitter.emitRunStart(
      runId = manifest.openLineageRunId,
      config = config,
      manifest = manifest
    )

    // 4. Load source data
    val (bookingsDF, typeErrors) = loadBookings(config, errorDomain)

    // 5. Apply filters with adjoint capture
    val (validBookings, filterErrors) = SparkAdjointWrapper.filterWithAdjoint(
      df = bookingsDF,
      condition = $"ticket_price" > 0,
      captureFiltered = true,
      adjointConfig = AdjointConfig(
        morphismId = "positive_price_filter",
        runId = config.runId,
        outputPath = s"$outputDir/adjoint/filtered_keys"
      )
    )

    // Route filter errors
    filterErrors.foreach(errorDomain.routeError)

    // 6. Check threshold after filters
    val thresholdAfterFilter = errorDomain.checkThreshold(bookingsDF.count())
    handleThreshold(thresholdAfterFilter, config, manifest, errorDomain)

    // 7. Join with reference data
    val (enrichedBookings, joinErrors) = enrichWithReferenceData(
      validBookings,
      config,
      errorDomain
    )

    // 8. Aggregate with adjoint capture
    val adjointResult = SparkAdjointWrapper.groupByWithAdjoint(
      df = enrichedBookings,
      groupCols = Seq("flight_date"),
      aggExprs = Seq(
        sum($"revenue").as("total_revenue"),
        count("*").as("flight_count")
      ),
      adjointConfig = AdjointConfig(
        captureReverseJoin = true,
        morphismId = "daily_revenue_aggregation",
        runId = config.runId,
        outputPath = s"$outputDir/adjoint/reverse_joins"
      )
    )

    val dailySummary = adjointResult.output

    // Persist adjoint metadata
    adjointResult.metadata match {
      case ReverseJoinMetadata(reverseJoinDF) =>
        reverseJoinDF.write
          .mode("overwrite")
          .parquet(s"$outputDir/adjoint/reverse_joins/daily_revenue_aggregation.parquet")
      case _ =>
    }

    // 9. Write output
    dailySummary.write
      .mode("overwrite")
      .partitionBy("flight_date")
      .json(s"${config.accountingDir}")

    // 10. Flush errors to DLQ
    errorDomain.flushToDLQ()

    // 11. Collect stats
    val inputStats = collectInputStats(bookingsDF)
    val outputStats = collectOutputStats(dailySummary)
    val errorStats = ErrorStats(
      totalErrors = errorDomain.getErrorCount(),
      errorRate = errorDomain.getErrorCount().toDouble / bookingsDF.count(),
      byType = groupErrorsByType(errorDomain.getErrors())
    )

    // 12. Emit OpenLineage COMPLETE event
    OpenLineageEmitter.emitRunComplete(
      runId = manifest.openLineageRunId,
      config = config,
      inputStats = inputStats,
      outputStats = outputStats,
      errorStats = errorStats
    )

    // 13. Write processing report
    writeProcessingReport(s"$outputDir/processing_report.json", ...)

    spark.stop()
  }

  private def handleThreshold(
    result: ThresholdResult,
    config: TransformerRunConfig,
    manifest: RunManifest,
    errorDomain: SparkErrorDomain
  ): Unit = result match {
    case ThresholdResult.Continue =>
      // OK - continue

    case ThresholdResult.Halt(reason, errorCount, threshold) =>
      // Flush errors
      errorDomain.flushToDLQ()

      // Emit failure event
      OpenLineageEmitter.emitRunFailure(
        runId = manifest.openLineageRunId,
        config = config,
        reason = reason,
        errorCount = errorCount,
        threshold = threshold
      )

      // Halt
      throw ThresholdExceededException(
        s"Error threshold exceeded: $errorCount errors ($reason), threshold: $threshold"
      )
  }

  private def generateManifest(config: TransformerRunConfig): RunManifest = {
    RunManifest(
      runId = config.runId,
      timestamp = config.timestamp,
      folderName = config.runFolderName,
      cryptographicBinding = CryptographicBinding(
        configHash = sha256(writeConfigToString(config)),
        codeVersion = CodeVersion(
          gitCommit = getCurrentGitCommit(),
          gitBranch = getCurrentGitBranch(),
          gitDirty = isGitDirty(),
          sourceHash = sha256DigestDirectory("src/")
        ),
        inputHashes = InputHashes(
          datasetPath = config.datasetPath,
          datasetManifestHash = sha256(readFile(s"${config.datasetPath}/dataset_config.json")),
          referenceDataHash = sha256DigestDirectory(s"${config.datasetPath}/reference"),
          bookingsDataHash = sha256DigestDirectory(s"${config.datasetPath}/bookings")
        )
      ),
      processingWindow = ProcessingWindow(...),
      executionMetadata = ExecutionMetadata(...),
      outputLocations = OutputLocations(...),
      openLineageRunId = UUID.randomUUID()
    )
  }
}
```

---

## Implementation Roadmap

### Phase 1: Error Domain (Week 1)

- [x] Error domain infrastructure (already exists)
- [ ] Enhance `flushToDLQ()` with JSONL output
- [ ] Add error partitioning by type
- [ ] Write error summary file
- [ ] Integration test: Inject errors, verify DLQ output

**Acceptance Criteria**:
- Errors written to `test-data/runs/<run>/errors/<error_type>_errors.jsonl`
- Error summary generated
- REQ-ERROR-01 fields verified

---

### Phase 2: Run Manifest (Week 2)

- [ ] Implement `RunManifest` case class
- [ ] Implement SHA-256 hashing utilities
- [ ] Implement Git metadata extraction
- [ ] Write manifest.json at run start
- [ ] Implement `verifyRunReproducibility()`
- [ ] Integration test: Modify input, verify hash mismatch detected

**Acceptance Criteria**:
- Manifest written with all hashes
- Re-run with same inputs produces same hashes
- Modified input detected

---

### Phase 3: OpenLineage Emission (Week 3)

- [ ] Implement OpenLineage event structures
- [ ] Implement `OpenLineageEmitter.emitRunStart()`
- [ ] Implement `OpenLineageEmitter.emitRunComplete()`
- [ ] Implement `OpenLineageEmitter.emitRunFailure()`
- [ ] Define custom CDME facets (error, run)
- [ ] Integration test: Verify events parseable by Marquez

**Acceptance Criteria**:
- START, COMPLETE, FAIL events emitted
- Events validate against OpenLineage JSON schemas
- Marquez can ingest events

---

### Phase 4: Adjoint Integration (Week 4)

- [ ] Implement `AdjointConfig` case class
- [ ] Enhance `SparkAdjointWrapper.groupByWithAdjoint()` with persistence
- [ ] Enhance `SparkAdjointWrapper.filterWithAdjoint()` with persistence
- [ ] Modify `AccountingRunner` to use adjoint wrappers
- [ ] Write reverse-join Parquet files
- [ ] Write filtered-keys Parquet files
- [ ] Integration test: Backward traversal from aggregate to sources

**Acceptance Criteria**:
- Adjoint metadata written to `adjoint/` directory
- Backward query returns correct source records
- REQ-ADJ-04/05/06 verified

---

## Consequences

### Positive

1. **REQ-TYP-03 Compliance**: No more silent drops - all errors routed to DLQ with full context
2. **REQ-TRV-05-A Compliance**: Cryptographic binding ensures reproducibility
3. **REQ-INT-03 Compliance**: Standard OpenLineage enables tool integration (Marquez, Atlan, etc.)
4. **REQ-ADJ-04/05/06 Compliance**: Adjoint metadata enables backward traversal from aggregates
5. **Debugging**: Complete error records with context accelerate root cause analysis
6. **Auditability**: Immutable manifests provide cryptographic proof of execution
7. **Interoperability**: OpenLineage standard enables cross-tool lineage

---

### Negative

1. **Storage Overhead**: DLQ, adjoint metadata, lineage events increase storage (estimate: +20%)
2. **Performance Impact**: Hash computation, metadata capture adds latency (estimate: +5-10%)
3. **Complexity**: More moving parts require careful testing and maintenance
4. **Schema Management**: Custom OpenLineage facets require schema versioning

---

### Neutral

1. **Folder Structure**: Clearer organization with explicit subdirectories
2. **Standards Adoption**: Commitment to OpenLineage standard (industry best practice)
3. **Backward Compatibility**: Existing RunConfig.scala compatible with enhancements

---

## Requirements Addressed

- **REQ-TYP-03**: Error Domain Semantics - All errors routed to DLQ, never dropped ✓
- **REQ-TYP-03-A**: Batch Failure Threshold - Circuit breaker halts on threshold ✓
- **REQ-ERROR-01**: Minimal Error Object Content - DLQ schema includes all required fields ✓
- **REQ-TRV-05**: Deterministic Reproducibility - Guaranteed via cryptographic binding ✓
- **REQ-TRV-05-A**: Immutable Run Hierarchy - Manifest with hashes ensures immutability ✓
- **REQ-TRV-05-B**: Artifact Version Binding - Code version, config hash in manifest ✓
- **REQ-INT-03**: Traceability - OpenLineage events provide full lineage ✓
- **REQ-ADJ-04**: Adjoint for Aggregations - Reverse-join metadata captured ✓
- **REQ-ADJ-05**: Adjoint for Filters - Filtered keys captured ✓
- **REQ-ADJ-06**: Adjoint for Explosions - Parent-child mapping (not yet implemented, future work)
- **RIC-ERR-01**: Circuit Breakers - Threshold checking prevents runaway errors ✓
- **RIC-LIN-01**: OpenLineage Standard - Emission of standard events ✓

---

## References

- [CDME Requirements Specification](../../requirements/mapper_requirements.md)
- [OpenLineage Specification 1.0](https://openlineage.io/docs/spec/overview)
- [Existing ErrorDomain.scala](../../../../src/data_mapper.spark.scala/src/main/scala/cdme/executor/ErrorDomain.scala)
- [Existing RunConfig.scala](../../../../src/data_mapper.spark.scala/src/test/scala/cdme/testdata/RunConfig.scala)
- [Spark Implementation Design](../SPARK_IMPLEMENTATION_DESIGN.md)
- [ADR-007: Either Monad for Error Handling](./ADR-007-scala-either-monad.md)

---

**Next Actions**:
1. Review this ADR with stakeholders
2. Prioritize implementation phases
3. Create Jira tickets for each phase
4. Begin Phase 1: Error Domain enhancements
