# ADR-003: Storage Format - Delta Lake vs Iceberg vs Parquet

**Status**: Proposed
**Date**: 2025-12-10
**Deciders**: [TBD]
**Depends On**: ADR-001 (Spark as Execution Engine)
**Implements**: REQ-PDM-01, REQ-PDM-05, REQ-TRV-03

---

## Context

CDME requires storage that supports:
- ACID transactions (for adjoint metadata)
- Time travel (for temporal semantics AS_OF)
- Schema evolution
- Efficient partition pruning
- Integration with Spark

---

## Decision

**[TBD - Options below for discussion]**

---

## Options

### Option A: Delta Lake

**Pros**:
- Native Databricks integration
- Time travel for temporal semantics
- ACID transactions
- Z-ordering for optimization
- Change Data Feed for CDC

**Cons**:
- Databricks ecosystem lock-in concerns
- Open source version has fewer features

### Option B: Apache Iceberg

**Pros**:
- Vendor-neutral (Netflix, Apple, etc.)
- Hidden partitioning
- Schema evolution
- Time travel
- Multiple engine support (Spark, Flink, Trino)

**Cons**:
- Smaller community than Delta
- Fewer managed service options

### Option C: Plain Parquet + Metadata

**Pros**:
- Universal compatibility
- No additional dependencies
- Simple and proven

**Cons**:
- No native time travel
- No ACID transactions
- Manual partition management
- No schema evolution safety

---

## Evaluation Matrix

| Feature | Weight | Delta | Iceberg | Parquet |
|---------|--------|-------|---------|---------|
| Time Travel | High | 5 | 5 | 1 |
| ACID | High | 5 | 5 | 1 |
| Adjoint Metadata Storage | High | 5 | 5 | 3 |
| Vendor Neutrality | Medium | 3 | 5 | 5 |
| Ecosystem Support | Medium | 5 | 4 | 5 |
| Operational Simplicity | Medium | 4 | 4 | 5 |

---

## CDME-Specific Considerations

### Time Travel for Temporal Semantics (REQ-TRV-03)

```scala
// Delta: AS_OF query
spark.read.format("delta")
  .option("timestampAsOf", "2025-01-01")
  .table("orders")

// Iceberg: AS_OF query
spark.read
  .option("as-of-timestamp", "2025-01-01T00:00:00.000Z")
  .table("orders")
```

### Adjoint Metadata Storage (REQ-ADJ-11)

Both Delta and Iceberg support:
- Transactional writes for reverse-join tables
- Partition pruning for efficient backward lookups
- Merge operations for incremental capture

---

## Consequences

### If Delta Lake
- Best Databricks integration
- Simpler operational model
- Change Data Feed useful for sync

### If Iceberg
- Multi-engine flexibility
- Better for non-Databricks deployments
- Growing ecosystem

### If Parquet
- Maximum compatibility
- Must build time travel manually
- More operational complexity

---

## References

- [Delta Lake Documentation](https://docs.delta.io/)
- [Apache Iceberg Documentation](https://iceberg.apache.org/)
- [Lakehouse Comparison](https://www.databricks.com/blog/delta-lake-vs-apache-iceberg)
