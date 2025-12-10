# ADR-002: Warehouse Target - Snowflake vs BigQuery vs Databricks

**Status**: Proposed
**Date**: 2025-12-10
**Deciders**: [TBD]
**Depends On**: ADR-001 (dbt as Execution Engine)
**Implements**: REQ-PDM-01, REQ-PDM-02

---

## Context

The dbt variant requires a target data warehouse that supports:
- Complex SQL transformations
- Type enforcement
- Time travel (for temporal semantics)
- Incremental processing
- Cost-effective scaling

---

## Decision

**[TBD - Options below for discussion]**

---

## Options

### Option A: Snowflake

**Pros**:
- Excellent dbt integration
- Time Travel (90 days on Enterprise)
- Strong type system
- Streams and Tasks for CDC
- Widely adopted

**Cons**:
- Compute costs can escalate
- Proprietary SQL extensions

### Option B: BigQuery

**Pros**:
- Serverless, auto-scaling
- Strong ML integration (BQML)
- Time Travel (7 days default)
- Nested/repeated fields (STRUCT, ARRAY)
- Cost-effective for sporadic workloads

**Cons**:
- GCP lock-in
- Less flexible concurrency control

### Option C: Databricks SQL

**Pros**:
- Unity Catalog for governance
- Consistent with Spark variant
- Delta Lake time travel
- Cross-platform (AWS, Azure, GCP)

**Cons**:
- Newer SQL warehouse offering
- DBU costs

### Option D: Multiple Targets (Adapter-Agnostic)

**Pros**:
- Maximum flexibility
- Customer choice
- dbt handles abstraction

**Cons**:
- Testing complexity
- Warehouse-specific optimizations lost

---

## CDME-Specific Requirements

### Time Travel for Temporal Semantics (REQ-TRV-03)

| Warehouse | Default | Max | Query Syntax |
|-----------|---------|-----|--------------|
| Snowflake | 1 day | 90 days | `AT(TIMESTAMP => ...)` |
| BigQuery | 7 days | 7 days | `FOR SYSTEM_TIME AS OF` |
| Databricks | 30 days | Configurable | `VERSION AS OF` |

### Type Enforcement (REQ-TYP-01)

| Warehouse | Type Safety |
|-----------|-------------|
| Snowflake | Strong (with VARIANT for semi-structured) |
| BigQuery | Strong (with STRUCT/ARRAY) |
| Databricks | Strong (with Delta schema enforcement) |

### Incremental for Epochs (REQ-SHF-01)

All three support incremental strategies via dbt.

---

## Evaluation Matrix

| Feature | Weight | Snowflake | BigQuery | Databricks |
|---------|--------|-----------|----------|------------|
| dbt Integration | High | 5 | 5 | 4 |
| Time Travel | High | 5 | 3 | 5 |
| Type System | High | 5 | 5 | 5 |
| Cost Model | Medium | 3 | 4 | 3 |
| Ecosystem | Medium | 5 | 5 | 4 |

---

## Consequences

### If Snowflake
- Excellent dbt experience
- Best time travel
- Higher compute costs

### If BigQuery
- Serverless simplicity
- GCP ecosystem benefits
- Limited time travel

### If Databricks
- Spark + dbt consistency
- Delta Lake benefits
- Newer offering

### If Multiple
- Maximum flexibility
- Higher testing burden

---

## References

- [dbt Snowflake Adapter](https://docs.getdbt.com/docs/core/connect-data-platform/snowflake-setup)
- [dbt BigQuery Adapter](https://docs.getdbt.com/docs/core/connect-data-platform/bigquery-setup)
- [dbt Databricks Adapter](https://docs.getdbt.com/docs/core/connect-data-platform/databricks-setup)
