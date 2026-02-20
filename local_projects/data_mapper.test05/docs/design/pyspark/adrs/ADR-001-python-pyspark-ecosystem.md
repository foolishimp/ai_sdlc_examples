# ADR-001: Python 3.12 + PySpark Ecosystem

**Status**: Accepted
**Date**: 2026-02-21
**Deciders**: Architecture team
**Requirements Addressed**: REQ-NFR-DIST-001, REQ-NFR-PERF-001, REQ-NFR-PERF-002

---

## Context

The CDME requires a distributed execution runtime capable of processing 1M+ records per hour with full lineage, type checking, and monoidal aggregation. The requirements document assumes Apache Spark as the primary distributed runtime (D-001). The original dependency list references Scala 2.13.12 (D-003) and sbt (D-004), but this design targets a broader audience with Python expertise.

The team must choose between:
1. Scala + Spark (native JVM integration, compile-time type safety)
2. Python + PySpark (broader team skillset, faster iteration, larger ecosystem)
3. Python + Dask/Ray (pure Python, no JVM overhead)

## Decision

Use **Python 3.12** as the implementation language with **PySpark 3.5.0** as the distributed execution runtime.

## Rationale

- **Team accessibility**: Python is the most widely known language in data engineering and data science teams. Choosing Python lowers the barrier to contribution and review.
- **Ecosystem breadth**: Python has the largest ecosystem for data tooling (pandas, Arrow, OpenLineage SDK, Pydantic, Hypothesis). All required integrations have mature Python libraries.
- **PySpark maturity**: PySpark 3.5.0 supports Spark Connect, pandas UDFs (Arrow-based), and the full DataFrame API. The Column expression API runs natively on the JVM with no Python serialization overhead.
- **Python 3.12 features**: Pattern matching (`match`/`case`) enables clean type dispatch. Performance improvements (PEP 684 per-interpreter GIL) benefit multi-threaded driver code.
- **Iteration speed**: Python's dynamic nature enables faster prototyping and testing cycles, which is valuable during initial development.

## Alternatives Considered

### Alternative 1: Scala 2.13 + Apache Spark

- **Pros**: Compile-time type safety via sealed traits, typeclass evidence, and phantom types. Native JVM Spark -- zero serialization overhead for all operations. Pattern matching with exhaustiveness checking.
- **Cons**: Smaller talent pool in many organizations. Longer compilation cycles. More complex build tooling (sbt). Steeper learning curve for teams without Scala experience.
- **Rejected because**: The CDME is intended for adoption across data engineering teams where Python is the dominant skill. The type safety advantages of Scala are partially compensated by mypy, Pydantic, and property-based testing.

### Alternative 2: Python + Dask

- **Pros**: Pure Python -- no JVM overhead. Simpler deployment. Native pandas integration.
- **Cons**: Less mature for enterprise-scale distributed processing. No equivalent to Spark's Catalyst optimizer. Smaller community for production data engineering. Does not satisfy REQ-NFR-DIST-001's explicit Spark requirement.
- **Rejected because**: The requirements specify Apache Spark as the primary distributed runtime. Dask cannot provide equivalent scale and optimization.

## Consequences

### Positive
- Broader team can contribute to and review the codebase
- Faster development iteration cycles
- Rich ecosystem for testing (Hypothesis), validation (Pydantic), and observability (OpenLineage)
- Poetry provides clean dependency management with optional groups (spark, lineage, dev)

### Negative
- **Python-JVM serialization overhead**: UDFs incur per-row serialization cost. Mitigated by using Column expressions where possible and `pandas_udf` (Arrow-based) for complex morphisms.
- **No compile-time type safety**: Type errors are caught later (mypy during CI, Pydantic at runtime, tests). Some errors that Scala catches at compile time will surface at test or runtime.
- **Monoid enforcement is weaker**: Without typeclass evidence, monoid law verification relies on property-based testing at registration time rather than compiler proof.
- **Performance ceiling**: For identical operations, Scala Spark will outperform PySpark due to eliminated serde overhead. The 1M records/hour target must be validated with benchmarks.
