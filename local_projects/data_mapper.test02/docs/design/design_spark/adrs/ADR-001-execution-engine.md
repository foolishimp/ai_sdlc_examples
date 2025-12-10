# ADR-001: Apache Spark as Execution Engine

**Status**: Proposed
**Date**: 2025-12-10
**Deciders**: [TBD]
**Implements**: All 60 REQ-* requirements (Spark variant)

---

## Context

CDME requires a distributed execution engine capable of:
- Large-scale data transformations
- Complex multi-source joins
- Aggregations with monoid semantics
- Streaming support (future)
- Lineage capture

---

## Decision

**Use Apache Spark 3.5.x as the execution engine for the Spark variant**

---

## Rationale

| Requirement | Spark Capability |
|-------------|------------------|
| Distributed processing | Native partitioned execution |
| Complex joins | Broadcast, shuffle, sort-merge joins |
| Aggregations | groupBy with custom aggregators |
| Type system | Dataset[T] with compile-time safety |
| Lineage | OpenLineage/Spline integration |
| Streaming | Structured Streaming |
| Optimization | Catalyst query optimizer |

### Spark vs Alternatives

| Engine | Pros | Cons |
|--------|------|------|
| **Spark** | Mature, wide adoption, Catalyst optimizer | Memory overhead, JVM tuning |
| Flink | True streaming, low latency | Smaller ecosystem, steeper learning curve |
| Trino | Fast interactive queries | Limited transformation support |
| Polars | Fast single-node | No native distribution |

---

## Consequences

### Positive
- Catalyst optimizer for plan optimization
- DataFrame API maps well to morphism execution
- Wide ecosystem (Delta Lake, Iceberg, etc.)
- OpenLineage integration for lineage

### Negative
- JVM memory management complexity
- Shuffle operations can be expensive
- Learning curve for optimization

### Technology Choices Enabled
- ADR-002: Scala vs PySpark
- ADR-003: Delta Lake vs Iceberg
- ADR-004: Lineage backend

---

## Alternatives Considered

1. **Apache Flink**: Better streaming, but smaller batch ecosystem
2. **Trino/Presto**: Query-focused, not transformation-focused
3. **Custom Engine**: Too much effort for this project

---

## References

- [Apache Spark Documentation](https://spark.apache.org/docs/latest/)
- [Catalyst Optimizer](https://databricks.com/glossary/catalyst-optimizer)
