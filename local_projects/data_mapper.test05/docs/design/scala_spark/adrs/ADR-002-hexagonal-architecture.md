# ADR-002: Hexagonal Architecture (Ports and Adapters)

**Status**: Accepted
**Date**: 2026-02-21
**Requirements Addressed**: REQ-F-PDM-001, REQ-NFR-DIST-001, REQ-F-TRV-005, REQ-F-LDM-003

---

## Context

The CDME must cleanly separate its domain model (Category Theory constructs) from its execution engine (Spark), its storage bindings (PDM), and its observability layer (OpenLineage). The requirements demand that changing a PDM binding must not require changing business logic or LDM definitions (REQ-F-PDM-001). The system must also support deterministic reproducibility independent of the execution backend (REQ-F-TRV-005).

The project constraints mandate that `cdme-model` has zero external dependencies and that no Spark API usage is permitted in `cdme-model` or `cdme-compiler`.

## Decision

Adopt a **hexagonal (ports-and-adapters) architecture** with strict dependency rules:

- **Core ports**: `cdme-model` defines domain types; `cdme-compiler` defines validation logic; `cdme-runtime` defines abstract execution interfaces (`ExecutionBackend[F[_]]`)
- **Adapters**: `cdme-spark` implements `ExecutionBackend` for Spark; `cdme-lineage` implements observability; `cdme-context` implements epoch/fiber management
- **Dependency direction**: Adapters depend on ports. Ports never depend on adapters.

## Rationale

- The hexagonal pattern enforces the functorial separation between LDM and PDM required by REQ-F-PDM-001 — the PDM binding is an adapter, and swapping it does not touch the core
- The `ExecutionBackend[F[_]]` trait abstracts over the execution context, enabling the same compiled plan to run on Spark, a local in-memory backend, or a future Flink binding
- The strict dependency DAG (model -> compiler -> runtime -> spark) prevents accidental coupling between the pure domain logic and Spark internals
- Testing becomes straightforward: `cdme-model` and `cdme-compiler` are tested with pure unit tests, no Spark session required

## Alternatives Considered

### Alternative 1: Traditional Layered Architecture

Rejected. Layered architecture permits transitive dependencies (UI -> Service -> Data), which would allow `cdme-compiler` to accidentally depend on Spark types. The hexagonal model enforces that dependencies point inward, not downward through layers.

### Alternative 2: Microservices Architecture

Rejected. The CDME is a library/framework (Assumption A-001), not a distributed service. Microservices would introduce network boundaries, serialization overhead, and operational complexity that conflict with the sub-millisecond morphism execution targets. The compile-then-execute pattern requires all validation to occur in a single process.

## Consequences

**Positive**:
- `cdme-model` is testable, portable, and reusable without any framework dependency
- PDM rebinding (e.g., switching from Parquet to Delta) requires changes only in `cdme-spark`
- A local test backend can implement `ExecutionBackend` for fast unit tests without Spark
- Future execution backends (Flink, pure Scala, GPU) can be added without touching core logic

**Negative**:
- The `ExecutionBackend[F[_]]` abstraction adds indirection compared to direct Spark calls
- 8 sbt sub-projects increase build complexity and initial setup time
- Cross-module changes (e.g., adding a new morphism type) require coordinated updates across model, compiler, and runtime

**Mitigations**:
- Build-time overhead is managed via sbt incremental compilation and parallel module builds
- The `ExecutionBackend` trait is narrow (5-6 methods), limiting abstraction cost
- `cdme-testkit` provides shared test infrastructure to reduce cross-module test duplication
