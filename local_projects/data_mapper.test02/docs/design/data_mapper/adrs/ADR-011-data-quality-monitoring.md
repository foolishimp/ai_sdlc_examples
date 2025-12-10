# ADR-011: Data Quality Monitoring Strategy

**Status**: Accepted
**Date**: 2025-12-10
**Implements**: REQ-PDM-06, REQ-DQ-01, REQ-DQ-02, REQ-DQ-03, REQ-DQ-04

---

## Context

CDME provides strong compile-time guarantees through its type system (REQ-TYP-*), but runtime data quality issues remain:

1. **Late-arriving data** - Records that arrive after their epoch has closed
2. **Volume anomalies** - Unexpected row counts indicating upstream failures
3. **Distribution drift** - Statistical changes in data characteristics
4. **Business rule violations** - Domain-specific constraints beyond types
5. **Data understanding** - Profiling for baseline establishment

### The Challenge

CDME's type system catches structural errors at compile time. But value-level and statistical anomalies require runtime monitoring:

| Issue Type | Compile-Time | Runtime |
|------------|--------------|---------|
| Schema mismatch | ✓ Type system | - |
| Type violation | ✓ Refinement types | ✓ Value validation |
| Null in required field | ✓ Option types | ✓ Count validation |
| Wrong row count | - | ✓ Volume monitoring |
| Distribution change | - | ✓ Distribution monitoring |
| Business rule | - | ✓ Custom rules |
| Late arrival | - | ✓ Temporal detection |

### Options Considered

1. **External DQ tool** - Great Expectations, dbt tests, Soda
2. **Embedded monitoring** - Built into CDME execution
3. **Hybrid approach** - CDME core + pluggable backends

---

## Decision

**CDME implements embedded data quality monitoring with configurable strategies, integrated with the Error Domain and telemetry systems.**

### Why Embedded

| Approach | Pros | Cons | Decision |
|----------|------|------|----------|
| External tool | Ecosystem, UI | Separate config, loose integration | Reject |
| **Embedded** | Tight integration, single config | Build effort | **Accept** |
| Hybrid | Flexibility | Complexity | Future consideration |

### Key Design Principles

1. **Configuration over code** - DQ rules in YAML, not scattered logic
2. **Integrated with Error Domain** - DQ violations are errors, not separate
3. **Observable** - Metrics flow to telemetry (REQ-TRV-04)
4. **Lineage-aware** - DQ decisions are part of lineage

---

## Late Arrival Handling (REQ-PDM-06)

### Strategy Options

| Strategy | Behavior | Use Case |
|----------|----------|----------|
| REJECT | Route to Error Domain | Strict SLA systems |
| REPROCESS | Trigger epoch reprocessing | High-accuracy requirements |
| ACCUMULATE | Buffer for next epoch | Streaming with eventual consistency |
| BACKFILL | Separate pipeline | Historical corrections |

### Detection Mechanism

```
event_time vs processing_time comparison:

  Event Time: 2024-12-14 23:55:00 (when the order was placed)
  Processing Time: 2024-12-15 04:30:00 (when we received it)
  Epoch: 2024-12-14 (closed at 2024-12-15 02:00:00)
  Watermark: 2 hours

  Result: LATE (processing_time > epoch_close + watermark)
```

### Configuration Schema

```yaml
late_arrival:
  detection:
    event_time_field: string      # Field containing event timestamp
    processing_time_field: string # Field containing processing timestamp
    watermark: duration           # Acceptable lateness window

  strategy: REJECT | REPROCESS | ACCUMULATE | BACKFILL

  # Strategy-specific config
  reject_config:
    error_code: string            # Error code for Error Domain

  reprocess_config:
    trigger_mode: IMMEDIATE | SCHEDULED
    max_reprocess_depth: int      # How many epochs back to reprocess

  accumulate_config:
    buffer_location: string       # Where to store buffered records
    max_buffer_epochs: int        # Maximum epochs to buffer
    flush_to_next_epoch: boolean  # Include in next epoch

  backfill_config:
    pipeline_name: string         # Separate pipeline to route to
    priority: HIGH | NORMAL | LOW

  lineage:
    mark_as_late: boolean         # Add late arrival flag to lineage
    late_arrival_flag: string     # Field name for flag
    original_epoch_field: string  # Field for original epoch
```

---

## Volume Threshold Monitoring (REQ-DQ-01)

### Threshold Types

1. **Absolute thresholds** - Fixed min/max row counts
2. **Relative thresholds** - Percentage of historical baseline
3. **Zero tolerance** - Special handling for empty inputs

### Configuration Schema

```yaml
volume_thresholds:
  enabled: boolean

  absolute:
    min_records: int | null       # Minimum acceptable count
    max_records: int | null       # Maximum acceptable count

  relative:
    baseline_window: duration     # Historical window (e.g., "7d")
    min_percentage: float         # Min as % of baseline (e.g., 0.5 = 50%)
    max_percentage: float         # Max as % of baseline (e.g., 2.0 = 200%)

  zero_records: WARN | HALT       # How to handle empty input

  on_violation:
    below_min: WARN | HALT | QUARANTINE
    above_max: WARN | HALT | QUARANTINE

  metrics:
    emit_to_telemetry: boolean
    include_baseline: boolean
    include_trend: boolean
```

### Baseline Computation

```python
def compute_baseline(source: str, window: str) -> VolumeBaseline:
    """
    Compute historical baseline for volume comparison.

    Returns rolling statistics over the window period.
    """
    historical_counts = get_historical_counts(source, window)

    return VolumeBaseline(
        average=mean(historical_counts),
        stddev=stddev(historical_counts),
        min=min(historical_counts),
        max=max(historical_counts),
        sample_count=len(historical_counts),
        window=window
    )
```

---

## Distribution Monitoring (REQ-DQ-02)

### Metric Types

| Metric | Applicable To | Description |
|--------|--------------|-------------|
| null_rate | All | % of null values |
| distinct_count | Categorical | Cardinality |
| min_max | Numeric | Value range |
| percentiles | Numeric | Distribution shape |
| mean_stddev | Numeric | Central tendency |
| top_n | Categorical | Most frequent values |
| value_distribution | Categorical | Full frequency table |

### Drift Detection Methods

| Method | Use Case | Sensitivity |
|--------|----------|-------------|
| Threshold | Simple rate changes | Low |
| Chi-squared | Categorical distributions | Medium |
| KL Divergence | Any distribution | High |
| KS Test | Numeric distributions | High |

### Configuration Schema

```yaml
distribution_monitoring:
  enabled: boolean

  fields:
    - name: string
      type: NUMERIC | CATEGORICAL
      metrics: [MetricType]
      thresholds:
        null_rate_max: float | null
        null_rate_change: float | null
        cardinality_change: float | null
        percentile_99_change: float | null
        distribution_divergence:
          method: chi_squared | kl_divergence | ks_test
          threshold: float

  baseline:
    window: duration              # Historical window for baseline
    min_samples: int              # Minimum days needed for baseline

  on_violation: WARN | HALT | QUARANTINE
```

---

## Custom Validation Rules (REQ-DQ-03)

### Rule Expression Language

Rules use a SQL-like expression syntax:

```yaml
validation_rules:
  - id: string
    description: string
    entity: string
    expression: string            # SQL-like boolean expression

    # Optional: reference lookups
    references:
      - lookup: string
        version: string | LATEST

    severity: ERROR | WARNING
    on_failure: ERROR_DOMAIN | QUARANTINE | LOG_ONLY
    error_code: string
    error_message: string         # Template with {field} placeholders
```

### Expression Examples

```sql
-- Date sequence validation
order_date <= ship_date OR ship_date IS NULL

-- Conditional requirement
status != 'CLOSED' OR close_date IS NOT NULL

-- Range validation
order_amount > 0 AND order_amount < 10000000

-- Lookup validation
country_code IN (SELECT code FROM lookup.CountryCode)

-- Cross-field consistency
currency = 'USD' OR fx_rate IS NOT NULL
```

### Execution Order

```
Input Data
    │
    ▼
┌──────────────────────┐
│ 1. Type Validation   │  ← Compile-time (REQ-TYP-01)
│    (schema check)    │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│ 2. Refinement Types  │  ← Runtime (REQ-TYP-02)
│    (value predicates)│
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│ 3. Custom Rules      │  ← Runtime (REQ-DQ-03)
│    (business logic)  │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│ 4. Transformation    │
│    (morphism exec)   │
└──────────────────────┘
```

---

## Profiling Integration (REQ-DQ-04)

### Profiling Modes

| Mode | Trigger | Output | Use Case |
|------|---------|--------|----------|
| DRY_RUN | Explicit request | Full profile | Initial data understanding |
| CONTINUOUS | Every execution | Incremental stats | Baseline maintenance |
| ON_DEMAND | API call | Full profile | Ad-hoc analysis |

### Output Schema

```yaml
profiling_output:
  entity: string
  epoch: string
  record_count: int
  profiled_at: ISO8601
  sample_size: int | null         # If sampled
  sample_method: string | null

  fields:
    field_name:
      type_declared: string
      type_inferred: string
      null_rate: float
      unique_rate: float | null   # For key candidates
      min: any | null             # For numeric
      max: any | null
      mean: float | null
      median: float | null
      stddev: float | null
      percentiles: {p5, p25, p50, p75, p95} | null
      min_length: int | null      # For string
      max_length: int | null
      pattern: string | null      # Detected pattern
      distinct_count: int | null  # For categorical
      top_values: [{value, count}] | null

  referential_integrity:
    "foreign_key → target":
      valid: int
      orphan: int
      orphan_rate: float
```

### Baseline Population

Profiling results feed distribution monitoring baselines:

```
Profiling Output → Baseline Store → Distribution Monitoring
                      │
                      └──► Initial baseline from first profile
                      └──► Rolling update from continuous profiles
```

---

## Integration with Error Domain

All DQ violations integrate with the standard Error Domain (REQ-TYP-03):

### Error Types

| Error Type | Source | Severity |
|------------|--------|----------|
| LATE_ARRIVAL | Late arrival detector | Configurable |
| VOLUME_THRESHOLD_VIOLATION | Volume monitor | Configurable |
| DISTRIBUTION_DRIFT | Distribution monitor | Configurable |
| CUSTOM_RULE_VIOLATION | Rule engine | Per-rule |

### Error Record Structure

```yaml
error:
  error_type: string              # DQ error type
  source_key: string | null       # Record key (null for epoch-level)
  morphism_path: string           # Where in pipeline
  context:                        # Error-type-specific context
    # Late arrival
    event_time: ISO8601
    processing_time: ISO8601
    epoch: string
    lateness: duration
    strategy_applied: string

    # Volume
    actual_count: int
    threshold: int
    threshold_type: string
    baseline_average: float | null

    # Distribution
    field: string
    metric: string
    expected: any
    actual: any
    threshold: any

    # Custom rule
    rule_id: string
    rule_description: string
    values: {field: value}
```

---

## Consequences

### Positive

- **Unified configuration** - All DQ rules in source YAML
- **Integrated lineage** - DQ decisions are traceable
- **Consistent error handling** - Uses Error Domain, not separate
- **Observable** - Metrics flow to telemetry

### Negative

- **Build effort** - Not leveraging existing DQ tools
- **Learning curve** - New configuration syntax
- **Maintenance** - Must maintain DQ engine

### Neutral

- Solution designs define specific thresholds and rules
- AWS implementation uses standard services (Glue, DynamoDB, CloudWatch)

---

## AWS Implementation Mapping

| Component | AWS Service | Notes |
|-----------|------------|-------|
| Config Store | DynamoDB | Thresholds, rules per source |
| Baseline Store | S3 + Athena | Historical profiles, queryable |
| DQ Engine | Glue ETL / EMR Spark | Integrated with transformation |
| Metrics | CloudWatch Metrics | Volume, distribution stats |
| Alerts | SNS | Threshold violations |
| Quarantine | S3 bucket | Flagged records |
| Error Domain | S3 (parquet) | DQ errors alongside other errors |

---

## Requirements Addressed

- **REQ-PDM-06**: Late arrival handling with explicit strategies ✓
- **REQ-DQ-01**: Volume threshold monitoring ✓
- **REQ-DQ-02**: Distribution monitoring ✓
- **REQ-DQ-03**: Custom validation rules ✓
- **REQ-DQ-04**: Profiling integration ✓
