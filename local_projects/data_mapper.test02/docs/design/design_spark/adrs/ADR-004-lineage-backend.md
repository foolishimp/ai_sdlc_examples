# ADR-004: Lineage Backend - OpenLineage vs Spline

**Status**: Proposed
**Date**: 2025-12-10
**Deciders**: [TBD]
**Depends On**: ADR-001 (Spark as Execution Engine)
**Implements**: REQ-INT-03, REQ-TRV-04, RIC-LIN-01

---

## Context

CDME requires lineage capture for:
- Regulatory compliance (BCBS 239, FRTB)
- AI assurance triangulation (REQ-AI-02)
- Impact analysis (REQ-ADJ-09)
- Data reconciliation (REQ-ADJ-08)

---

## Decision

**[TBD - Options below for discussion]**

---

## Options

### Option A: OpenLineage

**Pros**:
- Open standard (Linux Foundation)
- Multi-engine support (Spark, Airflow, dbt)
- Marquez as reference implementation
- Growing ecosystem (DataHub, Atlan integration)

**Cons**:
- Column-level lineage still maturing
- Requires additional infrastructure (Marquez)
- Less Spark-native than Spline

### Option B: Spline

**Pros**:
- Purpose-built for Spark
- Automatic lineage capture (Spark listener)
- Column-level lineage out of box
- Lightweight deployment

**Cons**:
- Spark-only (no cross-platform)
- Smaller community
- Less enterprise integration

### Option C: Custom Implementation

**Pros**:
- Full control over schema
- Optimized for CDME adjoint metadata
- No external dependencies

**Cons**:
- Development effort
- No ecosystem benefits
- Maintenance burden

### Option D: Hybrid (OpenLineage + CDME Extension)

**Pros**:
- Standard base format
- CDME-specific extensions for adjoints
- Best of both worlds

**Cons**:
- Complexity of dual model
- Custom tooling needed

---

## CDME-Specific Requirements

### Standard Lineage (REQ-INT-03)
- Input/output datasets
- Job metadata
- Execution timestamp

### Extended for Adjoints (REQ-ADJ-11)
- Reverse-join table references
- Adjoint classification per morphism
- Containment bounds

### OpenLineage Custom Facets

```json
{
  "eventType": "COMPLETE",
  "job": { "name": "cdme_order_aggregation" },
  "inputs": [...],
  "outputs": [...],
  "run": {
    "facets": {
      "cdme_adjoint": {
        "morphismId": "order_aggregation",
        "classification": "PROJECTION",
        "reverseJoinTable": "adj_order_aggregation",
        "lowerClosure": true,
        "upperClosure": false
      }
    }
  }
}
```

---

## Evaluation Matrix

| Feature | Weight | OpenLineage | Spline | Custom |
|---------|--------|-------------|--------|--------|
| Cross-Platform | Medium | 5 | 1 | 3 |
| Column Lineage | High | 3 | 5 | 5 |
| Adjoint Support | High | 3 | 2 | 5 |
| Ecosystem | Medium | 5 | 3 | 1 |
| Maintenance | Medium | 4 | 4 | 2 |

---

## Consequences

### If OpenLineage
- Standard format for interoperability
- Custom facets for adjoint metadata
- Requires Marquez or compatible backend

### If Spline
- Automatic Spark lineage
- Column-level out of box
- Spark-only, no dbt integration

### If Custom
- Optimized for CDME
- Full control
- No ecosystem benefits

---

## References

- [OpenLineage Specification](https://openlineage.io/)
- [Spline Documentation](https://absaoss.github.io/spline/)
- [Marquez](https://marquezproject.ai/)
