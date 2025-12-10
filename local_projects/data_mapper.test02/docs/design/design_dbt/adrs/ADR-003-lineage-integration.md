# ADR-003: Lineage Integration - dbt Lineage + OpenLineage

**Status**: Proposed
**Date**: 2025-12-10
**Deciders**: [TBD]
**Depends On**: ADR-001 (dbt as Execution Engine)
**Implements**: REQ-INT-03, RIC-LIN-01

---

## Context

CDME requires lineage capture for:
- Traceability (REQ-INT-03)
- AI assurance triangulation (REQ-AI-02)
- Impact analysis (REQ-ADJ-09)

dbt provides native lineage via `manifest.json`, but CDME may need extended capabilities.

---

## Decision

**[TBD - Options below for discussion]**

---

## Options

### Option A: dbt Native Lineage Only

Use `manifest.json` and dbt Docs for lineage.

**Pros**:
- Zero additional infrastructure
- Automatic from dbt runs
- Integrated with dbt Docs

**Cons**:
- Model-level only (no column lineage in manifest)
- No cross-platform integration
- No adjoint extensions

### Option B: dbt + OpenLineage Integration

Use dbt-openlineage integration to emit OpenLineage events.

**Pros**:
- Standard format
- Cross-platform (works with Spark variant)
- Extensible for adjoint metadata

**Cons**:
- Additional infrastructure (Marquez)
- Integration complexity

### Option C: dbt + Catalog Tool (DataHub/Atlan)

Use enterprise catalog with dbt integration.

**Pros**:
- Column-level lineage
- Business glossary integration
- Impact analysis UI

**Cons**:
- Vendor dependency
- Cost
- May not support adjoint extensions

### Option D: Hybrid (dbt Native + CDME Extension)

Use dbt manifest as base, extend with CDME-specific tables.

```sql
-- cdme_lineage_extension table
CREATE TABLE cdme_lineage (
  model_name VARCHAR,
  morphism_id VARCHAR,
  adjoint_model VARCHAR,
  classification VARCHAR,
  execution_id VARCHAR,
  created_at TIMESTAMP
);
```

**Pros**:
- No external dependencies
- Full control over adjoint metadata
- dbt-native feel

**Cons**:
- Custom implementation
- No ecosystem integration

---

## dbt Manifest Structure

```json
{
  "nodes": {
    "model.cdme.mart_customer_360": {
      "depends_on": {
        "nodes": [
          "model.cdme.int_customer_enriched",
          "model.cdme.stg_customers"
        ]
      },
      "columns": {...}
    }
  }
}
```

### CDME Extension (Custom Facets)

```json
{
  "model.cdme.mart_customer_360": {
    "meta": {
      "cdme": {
        "morphism_id": "customer_aggregation",
        "adjoint_model": "adj_customer_aggregation",
        "classification": "PROJECTION",
        "implements": ["REQ-INT-03", "REQ-ADJ-04"]
      }
    }
  }
}
```

---

## Evaluation Matrix

| Feature | Weight | dbt Only | OpenLineage | Catalog | Hybrid |
|---------|--------|----------|-------------|---------|--------|
| Setup Simplicity | Medium | 5 | 2 | 2 | 4 |
| Column Lineage | Medium | 2 | 3 | 5 | 2 |
| Adjoint Support | High | 1 | 3 | 2 | 5 |
| Cross-Platform | Medium | 1 | 5 | 4 | 2 |
| Ecosystem | Low | 3 | 4 | 5 | 2 |

---

## Consequences

### If dbt Native Only
- Simplest setup
- Limited to model-level lineage
- Must extend for adjoints

### If OpenLineage
- Standard format
- Works with Spark variant
- Requires Marquez

### If Catalog Tool
- Best column lineage
- Enterprise features
- Vendor lock-in

### If Hybrid
- Full control
- CDME-optimized
- Custom tooling

---

## References

- [dbt Docs](https://docs.getdbt.com/docs/collaborate/documentation)
- [dbt Artifacts](https://docs.getdbt.com/reference/artifacts/dbt-artifacts)
- [dbt-openlineage](https://github.com/OpenLineage/OpenLineage/tree/main/integration/dbt)
