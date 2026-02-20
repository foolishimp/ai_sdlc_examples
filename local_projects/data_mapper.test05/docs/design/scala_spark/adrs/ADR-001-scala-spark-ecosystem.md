# ADR-001: Scala 2.13 + Apache Spark 3.5 Ecosystem

**Status**: Accepted
**Date**: 2026-02-21
**Requirements Addressed**: REQ-NFR-DIST-001, REQ-NFR-PERF-001, REQ-NFR-PERF-002, REQ-F-LDM-004, REQ-F-TYP-001

---

## Context

The CDME requires a strongly typed language with mature distributed computing support. The domain model relies on sealed trait hierarchies, pattern matching, and parametric polymorphism to encode Category Theory constructs (morphisms, adjoint pairs, monoids). The execution layer must scale horizontally to meet the 1M records/hour throughput target on distributed clusters.

The project constraints specify Scala 2.13.12 as the primary language and Apache Spark 3.5.0 as the distributed runtime.

## Decision

Use **Scala 2.13.12** as the implementation language and **Apache Spark 3.5.0** (provided scope) as the distributed execution framework.

## Rationale

- Scala 2.13 provides sealed traits, case classes, and pattern matching that directly map to the CDME type system (CdmeType hierarchy, Morphism ADT, CdmeError ADT)
- Spark 3.5.x is the de facto standard for distributed data processing in the JVM ecosystem with mature DataFrame/Dataset APIs
- Spark 3.5.0 supports Scala 2.13 natively, providing binary compatibility
- The Cats ecosystem (available for 2.13) provides Monoid, Functor, and Kleisli abstractions that align with the domain model
- sbt multi-module builds are well-supported for Scala 2.13, enabling the strict dependency DAG required by the hexagonal architecture

## Alternatives Considered

### Alternative 1: Scala 3 + Spark

Rejected. Apache Spark 3.5.x does not support Scala 3. The Spark team has not committed to a Scala 3 release timeline. Using Scala 3 would require either a non-Spark execution engine or waiting for Spark compatibility, blocking delivery.

### Alternative 2: Apache Flink (Scala 2.13)

Rejected. Flink excels at stream processing but the CDME is primarily batch-oriented (epoch-based processing). Flink's Table API is less mature than Spark SQL for complex join/aggregation patterns. The Spark ecosystem has deeper integration with data lake formats (Delta, Parquet) that the PDM bindings require.

## Consequences

**Positive**:
- Direct access to Spark's optimized distributed execution (Catalyst optimizer, Tungsten memory management)
- Mature ecosystem of connectors for Parquet, Delta, CSV, JDBC, and other storage formats
- ScalaTest + ScalaCheck provide property-based testing aligned with monoid law verification
- `cdme-model` remains pure Scala with zero external dependencies, ensuring portability

**Negative**:
- Locked to Scala 2.13 until Spark supports Scala 3 (no union types, no enums, no significant whitespace)
- Spark's provided scope means the engine cannot run without a Spark runtime present for integration tests
- Spark version upgrades may require API adjustments in `cdme-spark`

**Mitigations**:
- The hexagonal architecture isolates Spark dependencies to `cdme-spark` only — a future Spark 4.x or Scala 3 migration affects only one module
- Local mode Spark sessions are used for integration testing without requiring a cluster
