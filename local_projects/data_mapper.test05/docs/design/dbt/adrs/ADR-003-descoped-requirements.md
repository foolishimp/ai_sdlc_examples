# ADR-003: Descoped and Partially Satisfied Requirements

**Status**: Proposed
**Date**: 2026-02-21
**Requirements**: REQ-F-TYP-001, REQ-F-TYP-004, REQ-F-LDM-004, REQ-F-ADJ-001, REQ-F-LDM-001, REQ-F-TRV-002, REQ-F-TRV-004

## Context

The CDME requirements were written for a programmatic engine with compile-time guarantees, a custom type system, and formal algebraic structures. dbt is a SQL-first declarative tool that delegates type checking to the warehouse and has no native concept of monoids, adjoint pairs, or refinement types.

After thorough analysis, several requirements cannot be fully satisfied in dbt. This ADR documents which requirements are partially satisfied or not achievable, why, and what the practical impact is. This is a feature of the methodology: demonstrating that a technology binding can honestly report its coverage gaps.

## Decision

We will classify requirements into three categories and document each honestly:

### Category 1: Formally Weakened (Partial Satisfaction)

| REQ Key | Requirement | What is Achieved | What is Lost |
|---------|------------|-----------------|-------------|
| REQ-F-TYP-001 | Extended type system (sum, product, refinement types) | Warehouse primitive types + dbt tests for constraint predicates | No sum types (tagged unions), no product types (tuples), no refinement types. dbt tests catch violations post-execution, not at compile time. |
| REQ-F-TYP-004 | Type unification rules | Validation service checks join column types from catalog.json | No formal subtype hierarchy. No compile-time type unifier. Warehouse may implicitly coerce types without detection. |
| REQ-F-LDM-004 | Monoidal aggregation laws | SQL built-in aggregations (SUM, COUNT) are associative by definition. Custom aggregations checked via data tests. | No formal proof of associativity. No compile-time rejection of non-associative aggregations. A developer could write a non-monoidal UDF and dbt would not prevent it. |
| REQ-F-LDM-001 | Directed multigraph structure | dbt's model DAG is a directed graph. Multiple refs between models approximate multi-edges. | Not a formal multigraph data structure. Cannot query the graph programmatically from within dbt. No identity morphism enforcement. |
| REQ-F-TRV-002 | Grain safety enforcement at compile time | Validation service checks grain compatibility pre-run. dbt tests check post-run. | Not compile-time enforcement. Invalid grain combinations can execute and produce data before tests catch them. |
| REQ-F-TRV-004 | Operational telemetry via Writer Effect | dbt run_results provide per-model timing and row counts. Lineage service propagates these. | Not a side-channel Writer Effect. Telemetry is captured after model execution, not accumulated during traversal. Per-record telemetry is not available. |

### Category 2: Structurally Different (Adapted Satisfaction)

| REQ Key | Requirement | dbt Adaptation | Structural Difference |
|---------|------------|---------------|----------------------|
| REQ-F-ADJ-001 | Adjoint interface on every morphism | Adjoint service computes backward metadata post-run | Forward and backward are not co-located on the same entity. Backward is computed retroactively, not declared alongside forward. |
| REQ-F-ERR-001 | Either monad error routing | Conditional SQL routing via _cdme_either column | Not monadic. Error routing is a SQL pattern, not a type-system guarantee. A developer could forget to include the pattern. |
| REQ-F-TRV-001 | Kleisli context lifting | SQL LEFT JOIN / LATERAL FLATTEN for 1:N | No monadic context switch. The SQL engine handles cardinality implicitly. No formal "lifting" operation. |

### Category 3: Not Achievable

| REQ Key | Requirement | Why Not Achievable |
|---------|------------|-------------------|
| REQ-F-TYP-001 (sum types) | User-definable tagged unions | SQL has no sum type construct. CASE WHEN approximates branching but does not create a new type. |
| REQ-F-LDM-004 (formal verification) | Compile-time proof that aggregation is associative | Neither dbt nor SQL can prove algebraic properties. This requires a proof assistant or a language with algebraic type classes. |

## Rationale

Honest reporting of coverage gaps is more valuable than pretending SQL + dbt tests = a formal type system. The CDME methodology benefits from this analysis because:

1. It demonstrates that the requirements are technology-agnostic — the same requirements produce different coverage profiles with different technology bindings.
2. It helps teams choose the right technology: if formal CT enforcement is critical, use the Scala/Spark binding. If SQL accessibility is more important, use the dbt binding with acknowledged gaps.
3. It identifies where additional engineering (custom tests, validation services) must compensate for platform limitations.

### Alternatives Considered

1. **Claim full satisfaction via dbt tests**: Argue that dbt tests provide equivalent guarantees to compile-time checks. Rejected because this is dishonest. dbt tests run after data is written; compile-time checks prevent invalid data from ever being produced. The failure mode is fundamentally different.

2. **Exclude partially-satisfiable requirements entirely**: Remove all requirements that dbt cannot fully satisfy from the design scope. Rejected because partial satisfaction is still valuable. A refinement-type test that catches violations post-run is better than no check at all. The design must document what level of satisfaction is achieved.

## Consequences

### Positive
- Honest assessment enables informed technology selection
- Partially satisfied requirements still receive engineering attention via tests and validation services
- The methodology demonstrates its value by producing this coverage analysis automatically
- Teams can make risk-based decisions about which gaps are acceptable

### Negative
- The dbt binding provides weaker guarantees than the Scala binding for type safety, grain safety, and algebraic correctness
- Some regulatory compliance arguments (BCBS 239 Accuracy) are weaker when type enforcement is post-execution rather than compile-time
- Developers must be disciplined about using CDME patterns (macros, tests) — the platform does not force correctness

### Mitigations
- The cdme_validation_service provides pre-run topological validation, closing the gap between "no check" and "compile-time check"
- Custom generic tests enforce refinement predicates, catching violations before downstream consumption
- CI/CD pipelines must enforce `dbt test` as a mandatory gate — models that fail tests must not be promoted
- Documentation clearly marks which requirements are partially satisfied, preventing false confidence

## References

- REQUIREMENTS.md v1.0.0 (full requirements list)
- DESIGN.md section 3 (Requirements Feasibility in dbt)
- ADR-001 (dbt ecosystem selection and its inherent limitations)
