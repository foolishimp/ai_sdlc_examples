# Active Tasks

**Project**: Categorical Data Mapping & Computation Engine (CDME)
**Last Updated**: 2025-12-10 14:00

---

## Summary

| Status | Count |
|--------|-------|
| In Progress | 0 |
| Pending | 4 |
| Blocked | 0 |

---

## Design Variant Strategy

The CDME will have **3 design artifacts**:

| Design | Purpose | Technology |
|--------|---------|------------|
| `data_mapper` | Generic reference design | Abstract/implementation-agnostic |
| `design_spark` | Distributed batch/streaming | Apache Spark (Scala/PySpark) |
| `design_dbt` | SQL-first transformation | dbt (SQL + Jinja) |

Each variant implements the same 60 requirements with technology-specific ADRs.

---

## Tasks

### Task #2: Generic Reference Design (data_mapper)

**Status**: pending
**Priority**: High
**Implements**: All 60 REQ-* requirements (abstract)
**Traces To**: INT-001, INT-002, INT-003, INT-004, INT-005

**Description**:
Create the abstract/generic component architecture for CDME. This serves as the reference design that Spark and dbt variants will implement.

**Acceptance Criteria**:
- [ ] Abstract component interfaces defined (TopologicalCompiler, SheafManager, MorphismExecutor, etc.)
- [ ] `Adjoint<T,U>` interface defined with `forward`, `backward`, `compositionalConsistency`
- [ ] Data structures for LDM, PDM, Mapping artifacts defined (implementation-agnostic)
- [ ] Component interaction diagrams
- [ ] ADRs for implementation-agnostic decisions
- [ ] Design document: `docs/design/data_mapper/AISDLC_IMPLEMENTATION_DESIGN.md`

**Dependencies**:
- Requirements stage complete ✅

**Notes**:
- This is the "what" not the "how"
- Variants will provide technology-specific "how"

---

### Task #3: Spark Variant Design (design_spark)

**Status**: pending
**Priority**: High
**Implements**: All 60 REQ-* requirements (Spark-specific)
**Traces To**: INT-001, INT-002, INT-003, INT-004, INT-005

**Description**:
Create the Spark-specific implementation design for CDME. Maps abstract components to Spark primitives (DataFrames, Catalyst, etc.).

**Acceptance Criteria**:
- [ ] ADR-001: Execution Engine (Why Spark)
- [ ] ADR-002: Language Choice (Scala vs PySpark)
- [ ] ADR-003: Storage Format (Delta/Iceberg/Parquet)
- [ ] ADR-004: Lineage Backend (OpenLineage/Spline)
- [ ] ADR-005: Adjoint Metadata Storage (reverse-join tables)
- [ ] TopologicalCompiler → Spark Catalyst integration
- [ ] MorphismExecutor → DataFrame transformations
- [ ] SheafManager → Epoch/partition management
- [ ] ErrorDomain → Spark error handling patterns
- [ ] Adjoint wrappers for groupBy, filter, join operations
- [ ] Design document: `docs/design/design_spark/SPARK_IMPLEMENTATION_DESIGN.md`

**Dependencies**:
- Task #2 (Generic Reference Design) - can run in parallel for ADRs

**Notes**:
- Spark excels at: distributed aggregations, complex joins, streaming
- Adjoint backward via reverse-join DataFrames
- Consider Delta Lake for ACID + time travel

---

### Task #4: dbt Variant Design (design_dbt)

**Status**: pending
**Priority**: High
**Implements**: All 60 REQ-* requirements (dbt-specific)
**Traces To**: INT-001, INT-002, INT-003, INT-004, INT-005

**Description**:
Create the dbt-specific implementation design for CDME. Maps abstract components to dbt primitives (models, macros, tests, documentation).

**Acceptance Criteria**:
- [ ] ADR-001: Execution Engine (Why dbt)
- [ ] ADR-002: Warehouse Target (Snowflake/BigQuery/Databricks)
- [ ] ADR-003: Lineage Integration (dbt lineage + OpenLineage)
- [ ] ADR-004: Adjoint Strategy (SQL-based reverse lookups)
- [ ] ADR-005: Type System Mapping (dbt contracts)
- [ ] TopologicalCompiler → dbt model DAG + refs
- [ ] MorphismExecutor → SQL transformations + macros
- [ ] SheafManager → Incremental models + partitions
- [ ] ErrorDomain → dbt tests + accepted_values
- [ ] Adjoint backward via audit tables + window functions
- [ ] Design document: `docs/design/design_dbt/DBT_IMPLEMENTATION_DESIGN.md`

**Dependencies**:
- Task #2 (Generic Reference Design) - can run in parallel for ADRs

**Notes**:
- dbt excels at: SQL-first teams, warehouse-native, built-in testing
- Adjoint backward via audit/history tables
- dbt contracts for type enforcement (v1.5+)

---

### Task #5: Design Traceability Update

**Status**: pending
**Priority**: Medium
**Implements**: Traceability maintenance

**Description**:
Update the traceability matrix to reflect the multi-variant design approach and map requirements to specific design components in each variant.

**Acceptance Criteria**:
- [ ] Traceability matrix updated with variant columns
- [ ] Each REQ-* mapped to data_mapper, design_spark, design_dbt components
- [ ] Gap analysis per variant
- [ ] Design stage coverage updated

**Dependencies**:
- Tasks #2, #3, #4 (at least started)

---

## Recently Completed

- **Task #1**: Complete Requirements Stage for CDME
  - Archived: `.ai-workspace/tasks/finished/20251210_1200_requirements_stage_complete.md`
  - 60 requirements defined, 6 intents captured
  - Adjoint interface adopted for reverse transformations
  - Frobenius analysis deferred to appendix

---

## Directory Structure (Target)

```
docs/design/
├── data_mapper/                    # Generic reference
│   ├── AISDLC_IMPLEMENTATION_DESIGN.md
│   └── adrs/
│       ├── ADR-000-template.md
│       └── ADR-001-adjoint-interface.md
│
├── design_spark/                   # Spark variant
│   ├── SPARK_IMPLEMENTATION_DESIGN.md
│   └── adrs/
│       ├── ADR-001-execution-engine.md
│       ├── ADR-002-language-choice.md
│       ├── ADR-003-storage-format.md
│       ├── ADR-004-lineage-backend.md
│       └── ADR-005-adjoint-metadata.md
│
└── design_dbt/                     # dbt variant
    ├── DBT_IMPLEMENTATION_DESIGN.md
    └── adrs/
        ├── ADR-001-execution-engine.md
        ├── ADR-002-warehouse-target.md
        ├── ADR-003-lineage-integration.md
        ├── ADR-004-adjoint-strategy.md
        └── ADR-005-type-system.md
```

---

## Recovery Commands

If context is lost, run these commands to get back:
```bash
cat .ai-workspace/tasks/active/ACTIVE_TASKS.md  # This file
cat docs/TRACEABILITY_MATRIX.md                 # Full traceability
cat docs/requirements/INTENT.md                 # Intents
git status                                       # Current state
git log --oneline -5                            # Recent commits
/aisdlc-status                                   # Task queue status
```
