# ADR-001: dbt as Transformation Ecosystem

**Status**: Proposed
**Date**: 2026-02-21
**Requirements**: REQ-F-PDM-001, REQ-F-PDM-002, REQ-F-SYN-001, REQ-F-SYN-004, REQ-F-TRV-005, REQ-NFR-DIST-001, REQ-NFR-VER-001

## Context

The CDME requirements define a data mapping and computation engine built on Category Theory principles. The original project constraints specify Scala 2.13 + Apache Spark as the implementation stack. However, this design explores an alternative technology binding: **dbt (data build tool)**, a SQL-first declarative transformation framework.

The motivation for exploring dbt is threefold:
1. **Team accessibility**: Many data teams have SQL skills but not Scala/functional programming skills. dbt lowers the barrier to adoption.
2. **Ecosystem maturity**: dbt has a mature ecosystem for data transformation (sources, refs, tests, documentation, lineage artifacts) that addresses many CDME requirements out of the box.
3. **Warehouse-agnostic**: dbt adapters exist for Spark, Snowflake, BigQuery, Redshift, and others, meaning the same project can target multiple warehouses.

The tension is that dbt is fundamentally a **declarative SQL templating tool**, not a **programmatic framework**. Many CDME requirements assume the ability to define custom type systems, enforce compile-time guarantees, and implement formal algebraic structures — capabilities that dbt does not natively provide.

## Decision

We will use **dbt-core 1.8+** as the primary transformation engine for the CDME dbt implementation. All data transformations, materializations, and SQL-expressible validations must be implemented as dbt models, tests, and macros. Capabilities that dbt cannot satisfy must be provided by companion Python services (see ADR-002).

## Rationale

dbt's core strengths align well with the data-plane requirements of CDME:
- **Source abstraction** (REQ-F-PDM-001): dbt's `source()` function cleanly separates logical references from physical tables.
- **Declarative transformations** (REQ-F-SYN-001): SQL expressions in dbt models implement synthesis functions.
- **Deterministic execution** (REQ-F-TRV-005): SQL is inherently deterministic when non-deterministic functions are excluded.
- **Native lineage** (REQ-F-SYN-003): dbt's manifest.json provides model-level and column-level lineage.
- **Version control** (REQ-NFR-VER-001): dbt projects are git-managed; every artifact is versioned.
- **Distributed execution** (REQ-NFR-DIST-001): dbt-spark adapter delegates compute to Spark.

### Alternatives Considered

1. **Scala 2.13 + Spark (programmatic engine)**: This was the original design target. It provides compile-time type safety, formal algebraic structures (monoids, functors), and a programmatic API. Rejected for this design because the purpose is to explore whether a SQL-first tool can satisfy the same requirements, and to demonstrate the methodology working with a partially-covering technology. The Scala design is the correct choice when formal CT enforcement is mandatory.

2. **Apache Beam + SQL transforms**: Beam provides a unified batch/streaming model with SQL support. Rejected because Beam's SQL layer is less mature than dbt's, the development experience is more complex, and the lineage/testing ecosystem is weaker. Beam would be a better choice if streaming requirements were in scope.

## Consequences

### Positive
- Dramatically lower barrier to entry for data teams (SQL vs Scala)
- Rich ecosystem of dbt packages, tests, and documentation tooling
- Native lineage artifacts reduce the need for custom lineage infrastructure
- Warehouse-agnostic execution via adapter system
- Strong community support and tooling (dbt docs, dbt test, dbt source freshness)

### Negative
- No compile-time type system — type violations are caught at test time, not before execution
- No formal algebraic structures — monoid laws, adjoint properties are tested, not proven
- Limited programmability — complex logic must be expressed in SQL/Jinja, which can become unwieldy
- dbt tests run after models execute, meaning invalid data is written before being flagged
- Several requirements can only be partially satisfied (see ADR-003)

### Mitigations
- Companion Python services provide pre-run validation (approximating compile-time checks)
- Custom generic tests enforce refinement-type predicates at test time
- The cdme_validation_service provides topological validation before dbt run
- Clear documentation of which requirements are partially satisfied prevents false confidence

## References

- REQUIREMENTS.md v1.0.0 (source requirements)
- ADR-002 (hybrid architecture to compensate for dbt limitations)
- ADR-003 (descoped requirements analysis)
- dbt-core documentation: https://docs.getdbt.com
