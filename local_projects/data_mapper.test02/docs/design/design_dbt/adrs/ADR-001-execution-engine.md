# ADR-001: dbt as Execution Engine

**Status**: Proposed
**Date**: 2025-12-10
**Deciders**: [TBD]
**Implements**: All 60 REQ-* requirements (dbt variant)

---

## Context

CDME requires an execution engine for SQL-first teams that:
- Supports declarative transformations
- Provides built-in testing
- Enables lineage tracking
- Works with modern data warehouses

---

## Decision

**Use dbt-core 1.7.x+ as the execution engine for the dbt variant**

---

## Rationale

| Requirement | dbt Capability |
|-------------|----------------|
| Declarative transforms | SQL models with Jinja |
| DAG validation | `ref()` enforces dependencies |
| Type enforcement | Contracts (1.5+) |
| Testing | Built-in + custom tests |
| Lineage | manifest.json + docs |
| Incremental | Incremental materializations |

### dbt vs Alternatives for SQL Teams

| Tool | Pros | Cons |
|------|------|------|
| **dbt** | Declarative, tested, documented | SQL-only transforms |
| Raw SQL | Maximum flexibility | No DAG, testing, docs |
| SQLMesh | Virtual environments | Smaller ecosystem |
| Dataform | Google-native | GCP lock-in |

---

## CDME Mapping to dbt Concepts

| CDME Concept | dbt Implementation |
|--------------|-------------------|
| Morphism | SQL transformation + macros |
| Entity | Model (table/view) |
| Topology | Model DAG via `ref()` |
| Type System | Contracts + tests |
| Error Domain | Tests + error capture models |
| Lineage | manifest.json |
| Adjoint | Companion audit models |

---

## Consequences

### Positive
- SQL-native for analyst teams
- Built-in testing framework
- Excellent documentation
- Warehouse-agnostic (with adapters)

### Negative
- SQL-only (complex logic requires Python models)
- No native adjoint support (must build)
- Batch/incremental only (no streaming)

### Technology Choices Enabled
- ADR-002: Warehouse Target
- ADR-003: Lineage Integration
- ADR-004: Adjoint Strategy
- ADR-005: Type System Mapping

---

## Alternatives Considered

1. **Raw SQL Scripts**: No testing, DAG, or documentation
2. **SQLMesh**: Smaller ecosystem, less mature
3. **Stored Procedures**: Database-specific, harder to test

---

## References

- [dbt Documentation](https://docs.getdbt.com/)
- [dbt Contracts](https://docs.getdbt.com/docs/collaborate/govern/model-contracts)
- [dbt Best Practices](https://docs.getdbt.com/guides/best-practices)
