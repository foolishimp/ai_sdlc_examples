# ADR-005: Adjoint Metadata Storage Strategy

**Status**: Proposed
**Date**: 2025-12-10
**Deciders**: [TBD]
**Depends On**: ADR-003 (Storage Format)
**Implements**: REQ-ADJ-04, REQ-ADJ-05, REQ-ADJ-06, REQ-ADJ-11

---

## Context

CDME adjoint morphisms require metadata capture to enable backward transformations:
- **Aggregations**: Reverse-join table (group_key → source_keys)
- **Filters**: Filtered-out keys
- **Explosions (1:N)**: Parent-child mapping

This metadata must be:
- Captured during forward execution
- Stored efficiently
- Queryable for backward traversal
- Configurable (full vs sampled)

---

## Decision

**[TBD - Options below for discussion]**

---

## Options

### Option A: Separate Delta Tables

Store each morphism's adjoint metadata in dedicated tables.

```
adjoint_metadata/
├── adj_order_aggregation/      # Reverse-join for groupBy
├── adj_order_filter/           # Filtered keys
└── adj_order_explode/          # Parent-child mapping
```

**Pros**:
- Clear separation
- Independent lifecycle management
- Easy to query
- Partition pruning for backward lookups

**Cons**:
- Many small tables
- More catalog entries
- Join overhead for multi-step backward

### Option B: Inline with Output

Store metadata as hidden columns in output DataFrame.

```scala
outputDF.withColumn("_adjoint_source_keys", col("source_keys_array"))
```

**Pros**:
- Co-located with data
- Single write operation
- No additional tables

**Cons**:
- Increases output size
- Complicates schema
- Hidden columns in user data

### Option C: Compressed Key Store (Roaring Bitmaps)

Store key sets using Roaring Bitmaps for compression.

```scala
// Store as binary column
outputDF.withColumn("_adjoint_keys", roaringBitmapEncode(col("source_keys")))
```

**Pros**:
- Efficient for high-cardinality groups
- Small storage overhead
- Fast set operations

**Cons**:
- Requires bitmap library
- Keys must be integers (or hashed)
- Decoding overhead

### Option D: Hybrid Strategy (Configurable)

Select strategy based on morphism characteristics:

| Morphism Type | Key Cardinality | Strategy |
|---------------|-----------------|----------|
| Aggregation | Low (< 1000 keys/group) | Inline array |
| Aggregation | High (> 1000 keys/group) | Roaring Bitmap |
| Filter | Any | Separate table (filtered keys only) |
| Explode | Any | Inline parent reference |

---

## Storage Schema

### Reverse-Join Table (Aggregations)

```sql
CREATE TABLE adjoint_reverse_join (
  morphism_id STRING,
  execution_id STRING,
  epoch_id STRING,
  group_key STRUCT<customer_id: STRING, order_date: DATE>,
  source_keys ARRAY<BIGINT>,  -- Or BINARY for Roaring Bitmap
  created_at TIMESTAMP
)
USING DELTA
PARTITIONED BY (epoch_id, morphism_id);
```

### Filtered Keys Table

```sql
CREATE TABLE adjoint_filtered_keys (
  morphism_id STRING,
  execution_id STRING,
  epoch_id STRING,
  filter_predicate STRING,
  filtered_keys ARRAY<BIGINT>,  -- Keys that failed filter
  created_at TIMESTAMP
)
USING DELTA
PARTITIONED BY (epoch_id, morphism_id);
```

---

## Performance Considerations

### Storage Overhead

| Strategy | Overhead | Notes |
|----------|----------|-------|
| Separate Tables | ~10-20% of output | Configurable retention |
| Inline Arrays | ~20-50% of output | Depends on group size |
| Roaring Bitmaps | ~5-10% of output | Best for high cardinality |

### Query Performance

```scala
// Backward lookup: O(1) per group with partition pruning
spark.read.table("adjoint_reverse_join")
  .filter(col("morphism_id") === "order_aggregation")
  .filter(col("group_key.customer_id") === targetCustomerId)
  .select("source_keys")
```

---

## Retention Policy

Adjoint metadata should follow lineage mode (RIC-LIN-01):

| Lineage Mode | Retention |
|--------------|-----------|
| FULL | Indefinite (or regulatory period) |
| KEY_DERIVABLE | Same as output |
| SAMPLED | Short-term (7-30 days) |

---

## Consequences

### If Separate Tables
- Clear operational model
- Independent scaling
- More infrastructure

### If Inline
- Simpler architecture
- Larger outputs
- Schema complexity

### If Roaring Bitmaps
- Best compression
- Library dependency
- Integer keys only

### If Hybrid
- Optimal per use case
- More implementation complexity
- Configurable

---

## References

- [Roaring Bitmaps](https://roaringbitmap.org/)
- [Delta Lake Partitioning](https://docs.delta.io/latest/delta-batch.html#partition-data)
