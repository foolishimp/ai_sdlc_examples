# Active Tasks

**Project**: Categorical Data Mapping & Computation Engine (CDME)
**Last Updated**: 2025-12-10 23:05

---

## Summary

| Status | Count |
|--------|-------|
| In Progress | 1 |
| Pending | 3 |
| Blocked | 0 |

---

## Current Stage: Code (Implementation)

Design stage complete. Now implementing the Spark variant with TDD.

---

## Tasks

### Task #6: Spark Implementation - MVP Steel Thread

**Status**: in_progress
**Priority**: High
**Implements**: Core pipeline (CONFIG → INIT → COMPILE → EXECUTE → OUTPUT)
**Traces To**: REQ-INT-01, REQ-TYP-*, REQ-CFG-*, ADR-006, ADR-007, ADR-010

**Description**:
Implement the Spark MVP steel thread following TDD. Core pipeline is implemented, needs testing and gap closure.

**Implementation Status** (from code review):
- [x] Error Domain (Types.scala) - CdmeError sealed trait, 8 error variants
- [x] Domain Model (Domain.scala) - Entity, Grain, Morphism, Cardinality
- [x] Algebra (Algebra.scala) - Aggregator trait, MonoidInstances
- [x] Config Parsing (ConfigModel.scala, ConfigLoader.scala) - circe-yaml
- [x] Schema Registry (SchemaRegistry.scala) - Path validation
- [x] Compiler (Compiler.scala) - Plan generation, grain validation
- [x] Executor (Executor.scala) - DataFrame transformations
- [x] Morphisms (FilterMorphism.scala, AggregateMorphism.scala)
- [x] Main entry point (Main.scala) - Steel thread

**Gaps Identified** (design vs implementation):
- [ ] Upgrade DataFrame to Dataset[T] (ADR-006)
- [ ] Wire Algebra.scala Aggregator to Executor
- [ ] Add SparkAdjointWrapper for reverse-join capture
- [ ] Add accumulator-based error collection (SparkErrorDomain)
- [ ] Add Executor unit tests

**Acceptance Criteria**:
- [x] Unit tests pass for Compiler (8/8 tests passing)
- [ ] Unit tests pass for Executor
- [ ] Integration test: end-to-end mapping execution
- [ ] Error threshold checking implemented
- [x] Build passes: `sbt compile` ✅

**Build Environment**:
- sbt 1.11.7 installed via Homebrew
- Scala 2.12.18
- Spark 3.5.0 (provided scope)
- All 11 source files compile successfully
- 8 tests passing (SchemaRegistry, GrainValidator, Compiler)

**Dependencies**:
- Design stage complete ✅
- Build tools installed ✅

---

### Task #7: Spark Implementation - Adjoint Capture

**Status**: pending
**Priority**: Medium
**Implements**: REQ-ADJ-01 through REQ-ADJ-07
**Traces To**: ADR-005-adjoint-metadata, SPARK_IMPLEMENTATION_DESIGN.md

**Description**:
Implement SparkAdjointWrapper to capture reverse-join metadata for aggregations, filters, and explode operations.

**Acceptance Criteria**:
- [ ] `groupByWithAdjoint` captures group_key → source_keys mapping
- [ ] `filterWithAdjoint` captures filtered-out keys (optional)
- [ ] `explodeWithAdjoint` captures parent-child mapping
- [ ] Adjoint metadata stored to Delta tables
- [ ] Tests for backward reconstruction

**Dependencies**:
- Task #6 (MVP Steel Thread)

---

### Task #8: Spark Implementation - Lineage & Error Domain

**Status**: pending
**Priority**: Medium
**Implements**: REQ-INT-03, RIC-LIN-*, REQ-ERR-*
**Traces To**: ADR-004-lineage-backend, ADR-008-openlineage-standard

**Description**:
Implement SparkLineageCollector for OpenLineage events and SparkErrorDomain for accumulator-based error handling.

**Acceptance Criteria**:
- [ ] SparkErrorDomain with Spark accumulators
- [ ] Error threshold checking (absolute + percentage)
- [ ] Dead-letter queue (DLQ) writes
- [ ] SparkLineageCollector with FULL/KEY_DERIVABLE/SAMPLED modes
- [ ] OpenLineage event emission

**Dependencies**:
- Task #6 (MVP Steel Thread)

---

### Task #5: Design Traceability Update

**Status**: pending
**Priority**: Medium
**Implements**: Traceability maintenance

**Description**:
Update the traceability matrix to reflect completed design stage and map requirements to implementation components.

**Acceptance Criteria**:
- [ ] Traceability matrix updated with implementation columns
- [ ] Each REQ-* mapped to Scala source files
- [ ] Gap analysis for unimplemented requirements
- [ ] Design stage marked complete

**Dependencies**:
- Task #6 (at least started)

---

## Recently Completed

- **Task #1**: Complete Requirements Stage for CDME
  - Archived: `.ai-workspace/tasks/finished/20251210_1200_requirements_stage_complete.md`
  - 60 requirements defined, 6 intents captured

- **Task #2**: Generic Reference Design (data_mapper)
  - Design document: `docs/design/data_mapper/AISDLC_IMPLEMENTATION_DESIGN.md`
  - 11 ADRs (ADR-000 through ADR-011)
  - Abstract component interfaces defined

- **Task #3**: Spark Variant Design (design_spark)
  - Design document: `docs/design/design_spark/SPARK_IMPLEMENTATION_DESIGN.md`
  - 10 ADRs (ADR-001 through ADR-010)
  - Scala-specific patterns: Type system, Either monad, Aggregation, Config

- **Task #4**: dbt Variant Design (design_dbt)
  - Design document: `docs/design/design_dbt/DBT_IMPLEMENTATION_DESIGN.md`
  - 5 ADRs (ADR-001 through ADR-005)
  - SQL-first transformation patterns

---

## Implementation Structure

```
src/data_mapper.spark.scala/
├── build.sbt
├── project/
└── src/
    ├── main/scala/cdme/
    │   ├── Main.scala              # Entry point
    │   ├── core/
    │   │   ├── Types.scala         # Error domain
    │   │   ├── Domain.scala        # Entity, Morphism
    │   │   └── Algebra.scala       # Aggregator, Monoid
    │   ├── config/
    │   │   ├── ConfigModel.scala   # Case classes
    │   │   └── ConfigLoader.scala  # YAML parsing
    │   ├── registry/
    │   │   └── SchemaRegistry.scala
    │   ├── compiler/
    │   │   └── Compiler.scala
    │   └── executor/
    │       ├── Executor.scala
    │       └── morphisms/
    │           ├── FilterMorphism.scala
    │           └── AggregateMorphism.scala
    └── test/scala/cdme/
        └── CompilerSpec.scala
```

---

## TDD Workflow

```
RED    → Write failing test first
GREEN  → Implement minimal solution
REFACTOR → Improve code quality
COMMIT → Save with REQ tags
```

---

## Recovery Commands

```bash
cat .ai-workspace/tasks/active/ACTIVE_TASKS.md  # This file
cat docs/TRACEABILITY_MATRIX.md                 # Full traceability
git status                                       # Current state
git log --oneline -5                            # Recent commits
sbt compile                                      # Build Spark project
sbt test                                         # Run tests
```
