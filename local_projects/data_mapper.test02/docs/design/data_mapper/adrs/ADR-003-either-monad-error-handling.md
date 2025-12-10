# ADR-003: Either Monad for Error Handling

**Status**: Accepted
**Date**: 2025-12-10
**Implements**: REQ-TYP-03, REQ-TYP-03-A, REQ-TYP-04, REQ-ERROR-01

---

## Context

Data pipelines encounter errors: type mismatches, refinement violations, lookup failures, external system errors. Traditional approaches:

1. **Exceptions** - Throw on error, catch at top level, fail job
2. **Null/Sentinel** - Return null or special value on error
3. **Try/Catch per row** - Wrap each transformation, log and continue
4. **Either Monad** - Return `Either[Error, Value]`, errors are data

### The Problem with Traditional Approaches

**Exceptions**:
- One bad record kills entire batch
- Or: try/catch everywhere creates spaghetti
- Error context lost in stack traces

**Null/Sentinel**:
- Nulls propagate silently
- "Magic values" (-1, "ERROR") corrupt downstream
- No error context preserved

**Try/Catch per row**:
- Verbose, error-prone
- Inconsistent handling across transformations
- Error logging disconnected from data

---

## Decision

**CDME uses the Either Monad pattern where every transformation returns `Either[Error, Value]` and errors are routed to an Error Domain as first-class data.**

### Core Types

```
Either[E, A] = Left(E) | Right(A)

Where:
  E = ErrorObject (structured error with context)
  A = Valid output value
```

### ErrorObject Structure

```yaml
ErrorObject:
  error_id: UUID
  error_type: enum [REFINEMENT_VIOLATION, TYPE_ERROR, LOOKUP_FAILURE, ...]
  constraint_name: String  # Which constraint failed
  offending_values: Map<String, Any>  # The bad values
  source_entity: String
  source_key: String  # Primary key of failing record
  source_epoch: String
  morphism_path: String  # Where in the pipeline
  timestamp: Timestamp
  metadata: Map<String, Any>  # Additional context
```

### Processing Model

```
Input Record
    │
    ▼
┌─────────────────┐
│  Transformation │──────▶ Right(value) ──▶ Continue pipeline
│  (Morphism)     │
└─────────────────┘
         │
         ▼
    Left(error)
         │
         ▼
┌─────────────────┐
│  Error Domain   │  (queryable, exportable)
│  (DLQ Table)    │
└─────────────────┘
```

### Composition Rules

Either composes via `flatMap`:
```
f: A → Either[E, B]
g: B → Either[E, C]

(g ∘ f): A → Either[E, C]
  = a → f(a).flatMap(g)
  = if f(a) is Left(e), return Left(e)
    if f(a) is Right(b), return g(b)
```

Errors short-circuit the transformation chain for that record but do NOT stop the batch.

---

## Consequences

### Positive

- **Failures are data** - Errors queryable, auditable, replayable
- **No silent drops** - Every record either succeeds or is in Error Domain
- **Composable** - Error handling is uniform across all morphisms
- **Deterministic** - Same input + same conditions = same errors (REQ-TYP-04)
- **Batch continues** - One bad record doesn't kill the job
- **Debugging** - Full context preserved in ErrorObject

### Negative

- **Performance overhead** - Wrapping every value in Either
- **Learning curve** - Teams unfamiliar with functional error handling
- **Storage cost** - Error Domain tables can grow large

### Neutral

- Threshold-based halting (REQ-TYP-03-A) is orthogonal - halt if error rate exceeds limit
- Error Domain retention policy is configurable

---

## Cloud Implementation Notes

### Error Domain Storage

| Cloud | Implementation |
|-------|---------------|
| AWS | S3 Parquet + Athena, or DynamoDB for hot queries |
| Azure | ADLS Parquet + Synapse |
| GCP | GCS Parquet + BigQuery |
| Spark | DataFrame with error schema, write to Delta/Iceberg |
| dbt | Error capture model, incremental append |

### Error Domain Schema (Parquet/Delta)

```sql
CREATE TABLE error_domain (
  error_id STRING,
  job_id STRING,
  epoch_id STRING,
  error_type STRING,
  constraint_name STRING,
  offending_values STRING,  -- JSON
  source_entity STRING,
  source_key STRING,
  source_epoch STRING,
  morphism_path STRING,
  created_at TIMESTAMP,
  metadata STRING  -- JSON
)
PARTITIONED BY (epoch_id, error_type);
```

### Threshold Halting (REQ-TYP-03-A)

```yaml
job:
  error_handling:
    threshold:
      type: percentage  # or absolute
      value: 5          # Halt if > 5% errors
      sample_size: 10000  # Check after this many records
    on_breach:
      action: halt
      commit_partial: false  # Rollback successful records
```

---

## Requirements Addressed

- **REQ-TYP-03**: Error Domain semantics - Either monad routes to Error Domain
- **REQ-TYP-03-A**: Batch failure threshold - Orthogonal halting logic
- **REQ-TYP-04**: Idempotency of failure - Same error for same input
- **REQ-ERROR-01**: Minimal error object - ErrorObject contains required fields
