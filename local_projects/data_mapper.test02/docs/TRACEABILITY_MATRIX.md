# Data Mapper - Traceability Matrix

**Project**: Categorical Data Mapping & Computation Engine (CDME)
**Version**: 1.1
**Date**: 2025-12-10
**Status**: Requirements Complete

---

## Purpose

Track requirement coverage across all SDLC stages: Intent → Requirements → Design → Tasks → Code → Test → UAT → Runtime.

---

## Coverage Summary

| Stage | Coverage | Status |
|-------|----------|--------|
| **0. Intent** | 6/6 (100%) | ✅ Complete |
| **1. Requirements** | 60/60 (100%) | ✅ Complete |
| **2. Design** | 0/60 (0%) | ⏳ Not Started |
| **3. Tasks** | 0/60 (0%) | ⏳ Not Started |
| **4. Code** | 0/60 (0%) | ⏳ Not Started |
| **5. System Test** | 0/60 (0%) | ⏳ Not Started |
| **6. UAT** | 0/60 (0%) | ⏳ Not Started |
| **7. Runtime** | 0/60 (0%) | ⏳ Not Started |

---

## Intent to Requirements Traceability

| Intent | Description | Requirements Count |
|--------|-------------|-------------------|
| **INT-001** | Categorical Data Mapping Engine | 35 |
| **INT-002** | Core Philosophy (10 Axioms) | 28 |
| **INT-003** | Universal Applicability | 6 |
| **INT-004** | AI Assurance Layer | 8 |
| **INT-005** | Adjoint Morphisms | 11 |
| **INT-006** | Frobenius Algebra (Speculative) | 0 (Appendix only) |

---

## Requirements by Category

### Logical Topology (LDM) - 7 Requirements

| Req ID | Description | Intent | Design | Tasks | Code | Test | Status |
|--------|-------------|--------|--------|-------|------|------|--------|
| REQ-LDM-01 | Strict Graph Structure | INT-001, INT-002 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |
| REQ-LDM-02 | Cardinality Types | INT-001, INT-002 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |
| REQ-LDM-03 | Strict Dot Hierarchy & Composition | INT-001, INT-002, INT-004 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |
| REQ-LDM-04 | Algebraic Aggregation (Monoid) | INT-001, INT-002 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |
| REQ-LDM-04-A | Empty Aggregation Behaviour | INT-001, INT-002 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |
| REQ-LDM-05 | Topological Access Control | INT-001, INT-002 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |
| REQ-LDM-06 | Grain & Type Metadata | INT-001, INT-002 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |

### Physical Binding (PDM) - 6 Requirements

| Req ID | Description | Intent | Design | Tasks | Code | Test | Status |
|--------|-------------|--------|--------|-------|------|------|--------|
| REQ-PDM-01 | Functorial Mapping | INT-001, INT-002 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |
| REQ-PDM-02 | Generation Grain | INT-001, INT-002 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |
| REQ-PDM-02-A | Generation Grain Semantics | INT-001, INT-002 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |
| REQ-PDM-03 | Boundary Definition | INT-001, INT-002 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |
| REQ-PDM-04 | Lookup Binding | INT-001, INT-002 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |
| REQ-PDM-05 | Temporal Binding | INT-001, INT-002 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |

### Traversal Engine (TRV/SHF) - 7 Requirements

| Req ID | Description | Intent | Design | Tasks | Code | Test | Status |
|--------|-------------|--------|--------|-------|------|------|--------|
| REQ-TRV-01 | Context Lifting (Kleisli) | INT-001, INT-002, INT-003 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |
| REQ-TRV-02 | Grain Safety | INT-001, INT-002, INT-004 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |
| REQ-TRV-03 | Boundary Alignment & Temporal | INT-001, INT-002 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |
| REQ-TRV-04 | Operational Telemetry | INT-001 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |
| REQ-TRV-05 | Deterministic Reproducibility | INT-001, INT-002 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |
| REQ-TRV-06 | Computational Cost Governance | INT-001 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |
| REQ-SHF-01 | Sheaf / Context Consistency | INT-001, INT-002 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |

### Integration & Synthesis (INT) - 8 Requirements

| Req ID | Description | Intent | Design | Tasks | Code | Test | Status |
|--------|-------------|--------|--------|-------|------|------|--------|
| REQ-INT-01 | Isomorphic Synthesis | INT-001, INT-003 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |
| REQ-INT-02 | Subsequent Aggregation | INT-001, INT-002 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |
| REQ-INT-03 | Traceability | INT-001, INT-004 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |
| REQ-INT-04 | Complex Business Logic | INT-001, INT-003 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |
| REQ-INT-05 | Multi-Grain Formulation | INT-001, INT-002 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |
| REQ-INT-06 | Versioned Lookups | INT-001, INT-002 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |
| REQ-INT-07 | Identity Synthesis | INT-001 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |
| REQ-INT-08 | External Computational Morphisms | INT-001, INT-003, INT-004 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |

### Typing & Quality (TYP/ERROR) - 9 Requirements

| Req ID | Description | Intent | Design | Tasks | Code | Test | Status |
|--------|-------------|--------|--------|-------|------|------|--------|
| REQ-TYP-01 | Extended Type System | INT-001, INT-002 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |
| REQ-TYP-02 | Refinement Types | INT-001, INT-002 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |
| REQ-TYP-03 | Error Domain Semantics | INT-001, INT-002 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |
| REQ-TYP-03-A | Batch Failure Threshold | INT-001, INT-002 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |
| REQ-TYP-04 | Idempotency of Failure | INT-001, INT-002 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |
| REQ-TYP-05 | Semantic Casting | INT-001, INT-002, INT-004 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |
| REQ-TYP-06 | Type Unification Rules | INT-001, INT-002, INT-004 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |
| REQ-TYP-07 | Semantic Type Enforcement | INT-001, INT-002 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |
| REQ-ERROR-01 | Minimal Error Object Content | INT-001, INT-002 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |

### AI Assurance (AI) - 3 Requirements

| Req ID | Description | Intent | Design | Tasks | Code | Test | Status |
|--------|-------------|--------|--------|-------|------|------|--------|
| REQ-AI-01 | Topological Validity Check | INT-004, INT-002 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |
| REQ-AI-02 | Triangulation of Assurance | INT-004 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |
| REQ-AI-03 | Real-Time Dry Run | INT-004 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |

### Adjoint Morphisms (ADJ) - 11 Requirements

| Req ID | Description | Intent | Design | Tasks | Code | Test | Status |
|--------|-------------|--------|--------|-------|------|------|--------|
| REQ-ADJ-01 | Adjoint Interface Structure | INT-005, INT-002 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |
| REQ-ADJ-02 | Adjoint Classification | INT-005 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |
| REQ-ADJ-03 | Self-Adjoint Morphisms | INT-005 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |
| REQ-ADJ-04 | Adjoint Backward for Aggregations | INT-005 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |
| REQ-ADJ-05 | Adjoint Backward for Filters | INT-005 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |
| REQ-ADJ-06 | Adjoint Backward for Kleisli | INT-005 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |
| REQ-ADJ-07 | Adjoint Composition Validation | INT-005 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |
| REQ-ADJ-08 | Data Reconciliation via Adjoints | INT-005 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |
| REQ-ADJ-09 | Impact Analysis via Adjoints | INT-005 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |
| REQ-ADJ-10 | Bidirectional Sync Support | INT-005 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |
| REQ-ADJ-11 | Adjoint Metadata Storage | INT-005 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |

### Implementation Constraints (RIC) - 9 Requirements

| Req ID | Description | Intent | Design | Tasks | Code | Test | Status |
|--------|-------------|--------|--------|-------|------|------|--------|
| RIC-LIN-01 | Lineage Modes | INT-001 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |
| RIC-LIN-06 | Lossless vs Lossy Morphisms | INT-001 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |
| RIC-LIN-07 | Checkpointing Policy | INT-001 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |
| RIC-LIN-04 | Reconstructability Invariant | INT-001 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |
| RIC-SKW-01 | Skew Mitigation (Salted Joins) | INT-001 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |
| RIC-ERR-01 | Probabilistic Circuit Breakers | INT-001 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |
| RIC-AGG-01 | Sketch-Based Aggregations | INT-001 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |
| RIC-PHY-01 | Partition Homomorphism | INT-001 | ⏳ | ⏳ | ⏳ | ⏳ | 📋 Defined |

---

## Regulatory Compliance Traceability

### BCBS 239 (Risk Data Aggregation & Reporting)

| Principle | Requirements | Status |
|-----------|-------------|--------|
| Principle 3 (Accuracy & Integrity) | REQ-TYP-01, REQ-TYP-02, REQ-TYP-06, REQ-TRV-05 | 📋 Defined |
| Principle 4 (Completeness) | REQ-LDM-03, REQ-TRV-02, REQ-INT-03, REQ-ERROR-01 | 📋 Defined |
| Principle 6 (Adaptability) | REQ-PDM-01 | 📋 Defined |

### FRTB (Fundamental Review of the Trading Book)

| Requirement | Requirements | Status |
|-------------|-------------|--------|
| Granular Risk Attribution | REQ-INT-03, REQ-TRV-05, REQ-TRV-02 | 📋 Defined |

### GDPR / CCPA (Data Privacy)

| Requirement | Requirements | Status |
|-------------|-------------|--------|
| Right to be Forgotten | REQ-INT-07, REQ-PDM-01 | 📋 Defined |

### EU AI Act (Artificial Intelligence)

| Article | Requirements | Status |
|---------|-------------|--------|
| Article 14 (Human Oversight) | REQ-AI-01, REQ-AI-02, REQ-AI-03 | 📋 Defined |
| Article 15 (Robustness) | REQ-LDM-03, REQ-TYP-06, REQ-TRV-05 | 📋 Defined |

---

## Design Component Mapping

| Component | Requirements | Status |
|-----------|-------------|--------|
| **TopologicalCompiler** | REQ-LDM-01, REQ-LDM-02, REQ-LDM-03, REQ-LDM-05, REQ-LDM-06, REQ-TRV-02, REQ-TRV-06, REQ-TYP-01, REQ-TYP-02, REQ-TYP-05, REQ-TYP-06, REQ-TYP-07, REQ-AI-01, REQ-AI-03 | ⏳ Not Started |
| **SheafManager** | REQ-PDM-03, REQ-TRV-03, REQ-SHF-01 | ⏳ Not Started |
| **MorphismExecutor** | REQ-LDM-04, REQ-LDM-04-A, REQ-TRV-01, REQ-TRV-04, REQ-TRV-05, REQ-INT-01, REQ-INT-02, REQ-INT-04, REQ-INT-06, REQ-INT-07, REQ-INT-08, REQ-ADJ-04, REQ-ADJ-05, REQ-ADJ-06 | ⏳ Not Started |
| **ErrorDomain** | REQ-TYP-03, REQ-TYP-03-A, REQ-TYP-04, REQ-ERROR-01, RIC-ERR-01 | ⏳ Not Started |
| **ImplementationFunctor** | REQ-PDM-01, REQ-PDM-02, REQ-PDM-02-A, REQ-PDM-04, REQ-PDM-05, RIC-PHY-01 | ⏳ Not Started |
| **ResidueCollector** | REQ-INT-03, REQ-AI-02, RIC-LIN-01, RIC-LIN-07, RIC-LIN-04, REQ-ADJ-11 | ⏳ Not Started |
| **AdjointCompiler** | REQ-ADJ-01, REQ-ADJ-02, REQ-ADJ-03, REQ-ADJ-07 | ⏳ Not Started |
| **ReconciliationEngine** | REQ-ADJ-08 | ⏳ Not Started |
| **ImpactAnalyzer** | REQ-ADJ-09 | ⏳ Not Started |
| **BidirectionalSyncManager** | REQ-ADJ-10 | ⏳ Not Started |

---

## Architecture Decision Records (ADRs)

### Spark Implementation ADRs

| ADR | Title | Requirements | Status |
|-----|-------|--------------|--------|
| **ADR-001** | Apache Spark as Execution Engine | All REQ-* (Spark variant) | Proposed |
| **ADR-002** | Language Choice - Scala vs PySpark | - | Proposed |
| **ADR-003** | Storage Format | - | Proposed |
| **ADR-004** | Lineage Backend | RIC-LIN-01, REQ-INT-03 | Proposed |
| **ADR-005** | Adjoint Metadata Storage | REQ-ADJ-04, REQ-ADJ-05, REQ-ADJ-06, REQ-ADJ-11 | Proposed |

### Scala Implementation ADRs

| ADR | Title | Requirements | Status |
|-----|-------|--------------|--------|
| **ADR-006** | Scala Type System for CDME | REQ-TYP-01, REQ-TYP-02, REQ-LDM-03, REQ-TRV-02 | Accepted |
| **ADR-007** | Error Handling with Either Monad | REQ-TYP-03, REQ-ERR-01, REQ-ERR-02, REQ-ERR-03 | Accepted |
| **ADR-008** | Scala Aggregation Patterns | REQ-ADJ-01, REQ-ADJ-02, REQ-ADJ-03, REQ-TYP-04 | Accepted |
| **ADR-009** | Scala Project Structure | - (Best practices) | Accepted |
| **ADR-010** | YAML Configuration Parsing | REQ-CFG-01, REQ-CFG-02, REQ-CFG-03 | Accepted |

---

## Priority Summary

| Priority | Count | Requirements |
|----------|-------|--------------|
| **Critical** | 18 | REQ-LDM-01, REQ-LDM-02, REQ-LDM-03, REQ-LDM-04, REQ-LDM-06, REQ-TRV-01, REQ-TRV-02, REQ-TRV-05, REQ-SHF-01, REQ-INT-03, REQ-INT-06, REQ-TYP-01, REQ-TYP-03, REQ-TYP-06, REQ-AI-01, REQ-ADJ-04 |
| **High** | 25 | REQ-LDM-04-A, REQ-LDM-05, REQ-PDM-01, REQ-PDM-02, REQ-PDM-02-A, REQ-PDM-03, REQ-TRV-03, REQ-TRV-06, REQ-INT-01, REQ-INT-04, REQ-INT-05, REQ-INT-08, REQ-TYP-02, REQ-TYP-03-A, REQ-TYP-04, REQ-TYP-05, REQ-ERROR-01, REQ-AI-02, REQ-AI-03, REQ-ADJ-01, REQ-ADJ-02, REQ-ADJ-03, REQ-ADJ-05, REQ-ADJ-06, REQ-ADJ-07, REQ-ADJ-08, REQ-ADJ-09 |
| **Medium** | 15 | REQ-PDM-04, REQ-PDM-05, REQ-TRV-04, REQ-INT-02, REQ-INT-07, REQ-TYP-07, REQ-ADJ-10, REQ-ADJ-11, RIC-LIN-01, RIC-LIN-06, RIC-LIN-07, RIC-LIN-04, RIC-SKW-01, RIC-ERR-01, RIC-PHY-01 |
| **Low** | 2 | RIC-AGG-01 |

---

## Gap Analysis

### Requirements without Design
*All 60 requirements - Design stage not started*

### Requirements without Tests
*All 60 requirements - Test stage not started*

### Requirements without Code
*All 60 requirements - Code stage not started*

### Critical Path (MVP)

The following requirements form the critical path for minimum viable implementation:

1. **Foundation**: REQ-LDM-01, REQ-LDM-02, REQ-LDM-03 (Graph topology)
2. **Type System**: REQ-TYP-01, REQ-TYP-06, REQ-LDM-06 (Types and grain)
3. **Execution**: REQ-TRV-01, REQ-TRV-02, REQ-LDM-04 (Traversal and aggregation)
4. **Error Handling**: REQ-TYP-03, REQ-ERROR-01 (Either monad, error objects)
5. **Lineage**: REQ-INT-03 (Traceability)
6. **AI Assurance**: REQ-AI-01 (Hallucination prevention)
7. **Adjoint Core**: REQ-ADJ-01, REQ-ADJ-04 (Backward transformations)

---

## Appendices (Speculative)

| Appendix | Topic | Status |
|----------|-------|--------|
| [Appendix A](requirements/appendices/APPENDIX_A_FROBENIUS_ALGEBRAS.md) | Frobenius Algebras | Speculative |

---

## Legend

- ✅ Complete
- 🚧 In Progress
- ⏳ Not Started
- 📋 Defined (requirements captured)
- ❌ Blocked

---

**Last Updated**: 2025-12-10
**Next Stage**: Design
