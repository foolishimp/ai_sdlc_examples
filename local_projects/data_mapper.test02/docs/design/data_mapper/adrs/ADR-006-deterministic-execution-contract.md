# ADR-006: Deterministic Execution Contract

**Status**: Accepted
**Date**: 2025-12-10
**Implements**: REQ-TRV-05, REQ-INT-06, REQ-INT-07, REQ-TYP-04

---

## Context

For audit, compliance, and debugging, CDME must guarantee:

> **Same inputs + Same mapping + Same lookups = Bit-identical outputs**

This is harder than it sounds. Common sources of non-determinism:

1. **Timestamps**: `current_timestamp()` varies per execution
2. **Random values**: `random()`, `uuid()` differ each run
3. **Unversioned lookups**: Reference data changes between runs
4. **Partition ordering**: Parallel execution may process in different order
5. **External calls**: API responses may vary
6. **Floating point**: Aggregation order can affect precision

---

## Decision

**CDME enforces a Deterministic Execution Contract. All transformations must be deterministic, and non-deterministic operations are either forbidden or wrapped.**

### Forbidden Operations

These operations are rejected at compile time:

| Operation | Why Forbidden | Alternative |
|-----------|---------------|-------------|
| `CURRENT_TIMESTAMP()` | Varies per run | Use `epoch_timestamp` from job context |
| `RANDOM()` | Non-deterministic | Use `hash(key, seed)` for pseudo-random |
| `UUID()` (v1, v4) | Non-deterministic | Use `UUID_V5(namespace, key)` |
| `NOW()` | Varies per run | Use `epoch_timestamp` |
| Unversioned lookup | Data may change | Require explicit version |

### Required Versioning

**Lookups (REQ-INT-06)**:
```yaml
mapping:
  lookups:
    - entity: CountryCode
      version: "2024.01.15"  # Explicit version

    - entity: ExchangeRate
      version_semantics: AS_OF
      as_of_field: order_date  # Deterministic per record

    - entity: ProductCatalog
      version: "v3.2.1"  # Semantic version
```

**External Morphisms (REQ-INT-08)**:
```yaml
external_morphism:
  name: CashflowEngine
  version: "2.1.0"  # Pinned version
  determinism: DECLARED  # Caller asserts determinism
  input_hash_logged: true  # Log hash of inputs for verification
```

### Deterministic Key Generation (REQ-INT-07)

```yaml
key_generation:
  strategy: HASH
  algorithm: SHA256
  inputs: [customer_id, order_date]
  # Same inputs → Same key, always
```

### Epoch Timestamp

Every job execution has a single `epoch_timestamp`:

```yaml
job:
  epoch:
    timestamp: "2024-01-15T00:00:00Z"  # Fixed for entire job
    timezone: UTC
```

All "current time" references use this:
```sql
-- FORBIDDEN:
SELECT CURRENT_TIMESTAMP()

-- ALLOWED:
SELECT ${epoch_timestamp}  -- Injected from job context
```

---

## Consequences

### Positive

- **Reproducibility** - Re-run produces identical output
- **Auditability** - Can prove what inputs produced what outputs
- **Debugging** - Reproduce exact conditions of failed run
- **Testing** - Deterministic tests don't flake

### Negative

- **Flexibility loss** - Can't use `random()` even when appropriate
- **Lookup overhead** - Must version all reference data
- **Learning curve** - Teams accustomed to `NOW()` must adapt

### Neutral

- Non-deterministic operations can be allowed with explicit `allow_non_deterministic: true` flag (audited)
- Floating point precision handled via consistent aggregation ordering

---

## Cloud Implementation Notes

### Job Context Injection

```yaml
# Job configuration
job:
  id: "job_20240115_001"
  epoch_timestamp: "2024-01-15T00:00:00Z"
  lookup_versions:
    CountryCode: "2024.01.15"
    ExchangeRate: "AS_OF:order_date"
    ProductCatalog: "v3.2.1"
  random_seed: 42  # For any pseudo-random operations
```

### Manifest Output

Every job produces a manifest of exact inputs:

```json
{
  "job_id": "job_20240115_001",
  "epoch_timestamp": "2024-01-15T00:00:00Z",
  "inputs": {
    "orders": {
      "path": "s3://bucket/orders/date=2024-01-15/",
      "checksum": "sha256:abc123..."
    }
  },
  "lookups": {
    "CountryCode": {"version": "2024.01.15", "checksum": "sha256:def456..."},
    "ExchangeRate": {"version": "AS_OF", "snapshot": "2024-01-15"}
  },
  "output": {
    "path": "s3://bucket/output/job_20240115_001/",
    "checksum": "sha256:ghi789..."
  }
}
```

### Verification

```python
def verify_determinism(job_id: str) -> bool:
    """Re-run job with same inputs, compare output checksum."""
    manifest = load_manifest(job_id)
    rerun_output = execute_job(
        inputs=manifest.inputs,
        lookups=manifest.lookups,
        epoch_timestamp=manifest.epoch_timestamp
    )
    return rerun_output.checksum == manifest.output.checksum
```

---

## Idempotency of Failure (REQ-TYP-04)

Error handling is also deterministic:

```
Same failing input + Same conditions = Same ErrorObject

ErrorObject includes:
- Deterministic error_id: hash(source_key, morphism_id, error_type)
- Same offending_values
- Same morphism_path
```

This enables:
- Comparing error domains across re-runs
- Detecting when fixes actually resolve errors
- Regression testing of error handling

---

## Requirements Addressed

- **REQ-TRV-05**: Deterministic reproducibility - Core contract enforced
- **REQ-INT-06**: Versioned lookups - All lookups require version
- **REQ-INT-07**: Identity synthesis - Deterministic key generation
- **REQ-TYP-04**: Idempotency of failure - Errors are deterministic
