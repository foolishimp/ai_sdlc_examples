# ADR-002: Language Choice - Scala vs PySpark

**Status**: Proposed
**Date**: 2025-12-10
**Deciders**: [TBD]
**Depends On**: ADR-001 (Spark as Execution Engine)

---

## Context

Spark supports multiple language bindings:
- Scala (native)
- Python (PySpark)
- Java
- R

CDME requires:
- Type-safe morphism definitions
- Custom aggregators (monoids)
- Adjoint wrapper implementation
- Performance for large-scale operations

---

## Decision

**[TBD - Options below for discussion]**

---

## Options

### Option A: Scala

**Pros**:
- Native Spark language, no serialization overhead
- Compile-time type safety with Dataset[T]
- Full access to Catalyst internals
- Custom Aggregator implementation

**Cons**:
- Smaller talent pool
- Longer development cycle
- Build complexity (sbt/Maven)

### Option B: PySpark

**Pros**:
- Larger talent pool
- Faster development iteration
- Better ML/AI ecosystem integration
- Pandas UDFs for complex logic

**Cons**:
- Serialization overhead (Arrow helps)
- Runtime type errors
- Custom aggregators require Scala bridge

### Option C: Hybrid (Scala Core + PySpark API)

**Pros**:
- Performance-critical code in Scala
- User-facing API in Python
- Best of both worlds

**Cons**:
- Build complexity
- Two languages to maintain
- Interface boundaries

---

## Evaluation Criteria

| Criterion | Weight | Scala | PySpark | Hybrid |
|-----------|--------|-------|---------|--------|
| Type Safety | High | 5 | 2 | 4 |
| Performance | High | 5 | 3 | 5 |
| Developer Productivity | Medium | 3 | 5 | 3 |
| Talent Availability | Medium | 2 | 5 | 3 |
| Adjoint Implementation | High | 5 | 3 | 5 |

---

## Consequences

### If Scala
- Requires Scala expertise
- Maximum performance and type safety
- Easier Catalyst integration

### If PySpark
- Faster prototyping
- May need Scala for custom aggregators
- Arrow serialization for performance

### If Hybrid
- Most flexible
- Higher maintenance burden
- Clear API boundaries needed

---

## References

- [PySpark Performance Tuning](https://spark.apache.org/docs/latest/sql-pyspark-pandas-with-arrow.html)
- [Spark Dataset API](https://spark.apache.org/docs/latest/sql-programming-guide.html)
