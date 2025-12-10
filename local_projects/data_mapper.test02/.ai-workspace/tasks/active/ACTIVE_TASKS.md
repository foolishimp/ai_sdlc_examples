# Active Tasks

**Project**: Categorical Data Mapping & Computation Engine (CDME)
**Last Updated**: 2025-12-11 00:47

---

## Summary

| Status | Count |
|--------|-------|
| In Progress | 2 |
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
- [x] Add Executor unit tests ✅ (18 tests in ExecutorSpec.scala)
- [x] Add UAT tests ✅ (33 BDD scenarios in UATSpec.scala)

**Acceptance Criteria**:
- [x] Unit tests pass for Compiler (8/8 tests passing)
- [x] Unit tests pass for Executor (18/18 tests passing) ✅
- [x] UAT tests pass (33/33 BDD scenarios passing) ✅
- [ ] Integration test: end-to-end mapping execution (requires Java 17)
- [ ] Error threshold checking implemented
- [x] Build passes: `sbt compile` ✅

**Build Environment**:
- sbt 1.11.7 installed via Homebrew
- Scala 2.12.18
- Spark 3.5.0 (provided scope)
- All 11 source files compile successfully
- 59 tests passing (CompilerSpec: 8, ExecutorSpec: 18, UATSpec: 33)

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

### Task #9: UAT Testing & Business Sign-off

**Status**: in_progress
**Priority**: High
**Implements**: AI SDLC UAT Stage
**Traces To**: REQ-INT-01, REQ-TRV-02, REQ-CFG-*, REQ-ERR-*

**Description**:
Complete User Acceptance Testing for CDME Spark implementation. Validate all business scenarios and obtain stakeholder sign-off.

**UAT Tests Implemented** (33 BDD scenarios in UATSpec.scala):
- [x] UAT-001: Schema Registry Setup (2 scenarios)
- [x] UAT-002: Relationship Path Validation (3 scenarios)
- [x] UAT-003: Simple Mapping Compilation (1 scenario)
- [x] UAT-004: Grain Safety Enforcement (3 scenarios)
- [x] UAT-005: Filter Morphism Definition (1 scenario)
- [x] UAT-006: Aggregation Mapping (1 scenario)
- [x] UAT-007: Clear Error Messages (2 scenarios)
- [x] UAT-008: Chained Morphisms (1 scenario)
- [x] UAT-009: Relationship Traversal (1 scenario)
- [x] UAT-010: Consistent Error Typing (2 scenarios)
- [x] UAT-011: Window Functions and Temporal Aggregations (2 scenarios) ✅
- [x] UAT-012: Data Quality Validations (3 scenarios) ✅
- [x] UAT-013: Performance with Large Datasets (3 scenarios) ✅
- [x] UAT-014: Error Recovery and Retry Logic (4 scenarios) ✅
- [x] UAT-015: Configuration Validation Edge Cases (4 scenarios) ✅

**All UAT Scenarios Complete** ✅

**Business Sign-off**:
- [ ] Data Engineers sign-off
- [ ] Business Analysts sign-off
- [ ] Compliance Team sign-off
- [ ] Product Owner sign-off

**Dependencies**:
- Task #6 (MVP Steel Thread) - Unit tests ✅
- Executor tests passing ✅

---

## Recently Completed

- **Task #1**: Complete Requirements Stage for CDME
  - Archived: `.ai-workspace/tasks/finished/20251210_1200_requirements_stage_complete.md`
  - 60 requirements defined, 6 intents captured

- **Task #2**: Generic Reference Design (data_mapper)
  - Archived: `.ai-workspace/tasks/finished/20251210_1400_generic_reference_design.md`
  - Design document: `docs/design/data_mapper/AISDLC_IMPLEMENTATION_DESIGN.md`
  - 11 ADRs (ADR-000 through ADR-011)

- **Task #3**: Spark Variant Design (design_spark)
  - Archived: `.ai-workspace/tasks/finished/20251210_1600_spark_variant_design.md`
  - Design document: `docs/design/design_spark/SPARK_IMPLEMENTATION_DESIGN.md`
  - 10 ADRs (ADR-001 through ADR-010)

- **Task #4**: dbt Variant Design (design_dbt)
  - Archived: `.ai-workspace/tasks/finished/20251210_1800_dbt_variant_design.md`
  - Design document: `docs/design/design_dbt/DBT_IMPLEMENTATION_DESIGN.md`
  - 5 ADRs (ADR-001 through ADR-005)

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
        ├── CompilerSpec.scala
        ├── ExecutorSpec.scala
        └── UATSpec.scala
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
