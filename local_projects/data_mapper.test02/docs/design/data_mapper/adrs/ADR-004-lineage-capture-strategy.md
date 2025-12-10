# ADR-004: Lineage Capture Strategy (Three Modes)

**Status**: Accepted
**Date**: 2025-12-10
**Implements**: REQ-INT-03, RIC-LIN-01, RIC-LIN-04, RIC-LIN-06, RIC-LIN-07, REQ-ADJ-11

---

## Context

CDME requires lineage - the ability to trace any output record back to its source records. This is critical for:

1. **Regulatory compliance** - BCBS 239, FRTB require auditability
2. **Impact analysis** - "Which sources affected this output?"
3. **Data reconciliation** - Verify no records lost in transformation
4. **Debugging** - Trace bad output to bad input

### The Challenge

Full lineage (store every input→output key mapping) is expensive:
- Storage: O(input_rows × output_rows) for many-to-many relationships
- Performance: Capturing lineage adds overhead to every transformation
- Query cost: Lineage tables can dwarf the actual data

But minimal lineage may be insufficient for audit requirements.

---

## Decision

**CDME supports three lineage modes, selectable per job, balancing cost vs completeness:**

### Mode 1: Full Lineage

Every output key explicitly mapped to contributing input keys.

```yaml
lineage_mode: FULL

# Storage:
{
  "target_key": "cust_123_2024-01-15",
  "source_mappings": [
    {"entity": "orders", "keys": ["ord_001", "ord_002", "ord_003"]},
    {"entity": "payments", "keys": ["pay_101"]}
  ]
}
```

**Use when**: Regulatory audit, debugging, small datasets
**Cost**: High (O(n) storage per transformation)

### Mode 2: Key-Derivable Lineage

Store compressed "envelopes" + deterministic reconstruction function.

```yaml
lineage_mode: KEY_DERIVABLE

# Storage (envelope):
{
  "segment_id": "order_agg_2024-01-15",
  "start_key": 1000000,
  "end_key": 1999999,
  "key_gen_function": "order_id_hash_v2",
  "offset": 0,
  "count": 1000000
}
```

Reconstruction: Given output key + envelope + plan, regenerate source keys deterministically.

**Use when**: Large datasets, sorted/sequential keys, cost-sensitive
**Cost**: Medium (O(segments) storage, O(n) reconstruction)

### Mode 3: Sampled Lineage

Statistical sample of lineage, sufficient for debugging but not audit.

```yaml
lineage_mode: SAMPLED
sample_rate: 0.01  # 1% of records

# Storage:
{
  "sample_id": "sample_001",
  "target_key": "cust_456_2024-01-15",
  "source_keys": ["ord_789"],
  "sample_rate": 0.01
}
```

**Use when**: Development, testing, exploratory analysis
**Cost**: Low (O(sample_rate × n))

---

## Checkpointing Policy (RIC-LIN-07)

Regardless of mode, checkpoints are required at:

1. **All inputs** - Capture input key ranges/counts
2. **All outputs** - Capture output key ranges/counts
3. **After lossy morphisms** - Aggregations, filters, joins with drops

```
Input ──▶ [Lossless Chain] ──▶ [Lossy: Aggregate] ──▶ [Lossless] ──▶ Output
  ▲                                    ▲                              ▲
  │                                    │                              │
  Checkpoint                        Checkpoint                    Checkpoint
```

---

## Consequences

### Positive

- **Flexibility** - Choose cost/completeness tradeoff per job
- **Scalability** - Key-Derivable mode handles petabyte scale
- **Audit compliance** - Full mode meets regulatory requirements
- **Development friendly** - Sampled mode for fast iteration

### Negative

- **Complexity** - Three modes to implement and maintain
- **Key-Derivable limitations** - Requires deterministic key functions
- **Mode mismatch** - Can't reconstruct full lineage from sampled

### Neutral

- Adjoint metadata (reverse-join tables) follows same mode selection
- Mode is per-job, not per-morphism

---

## Cloud Implementation Notes

### Lineage Storage

| Mode | AWS | Azure | GCP |
|------|-----|-------|-----|
| Full | S3 Parquet + Athena | ADLS + Synapse | GCS + BigQuery |
| Key-Derivable | DynamoDB (envelopes) | Cosmos DB | Firestore |
| Sampled | S3 JSON | ADLS JSON | GCS JSON |

### OpenLineage Export

All modes export to OpenLineage format for interoperability:

```json
{
  "eventType": "COMPLETE",
  "job": {"name": "order_aggregation"},
  "inputs": [
    {"namespace": "cdme", "name": "orders", "facets": {...}}
  ],
  "outputs": [
    {"namespace": "cdme", "name": "daily_summary", "facets": {...}}
  ],
  "run": {
    "facets": {
      "cdme_lineage": {
        "mode": "KEY_DERIVABLE",
        "checkpoints": [...]
      }
    }
  }
}
```

### Adjoint Metadata Alignment (REQ-ADJ-11)

Adjoint backward metadata follows the same mode:

| Lineage Mode | Adjoint Metadata |
|--------------|------------------|
| Full | Full reverse-join tables |
| Key-Derivable | Compressed envelopes + reconstruction |
| Sampled | Sampled reverse mappings |

---

## Requirements Addressed

- **REQ-INT-03**: Traceability - All modes provide target→source tracing
- **RIC-LIN-01**: Lineage modes - Three modes defined
- **RIC-LIN-04**: Reconstructability - Key-Derivable mode specification
- **RIC-LIN-06**: Lossless vs Lossy - Checkpoint policy based on classification
- **RIC-LIN-07**: Checkpointing policy - Required checkpoint locations
- **REQ-ADJ-11**: Adjoint metadata storage - Aligned with lineage mode
