# Intent: Build a Data Mapping Engine from First Principles

**Intent ID**: INT-CDME-001
**Date**: 2025-01-22
**Submitted By**: Architecture Team
**Priority**: Critical

---

## The Problem

Our current ETL tools treat data as "buckets of bytes" with no formal guarantees. This causes:

1. **Debugging Hell**: Hours spent tracing why a calculation is wrong, only to find we accidentally mixed daily aggregates with atomic trades.

2. **Lineage Gaps**: Regulators ask "which trades contributed to this risk number?" and we can't answer without re-running the entire pipeline with custom logging.

3. **AI Hallucinations**: AI generates a pipeline that looks correct but references relationships that don't exist. We only find out in production when joins return empty.

4. **Impedance Mismatch**: Business analyst says "sum cashflows per portfolio" but the data engineer has to manually figure out the grain transformations, lookup versions, and boundary semantics.

---

## What We Want

**Build a data mapping engine where:**

> If the mapping compiles, the pipeline is mathematically guaranteed to be correct.

**Core Idea**: Use Category Theory to model data transformations
- Schemas are Topologies (graphs of entities and relationships)
- Transformations are Morphisms (functions with type signatures)
- Composition follows mathematical laws (no grain mixing, no invalid paths)

**Like SQL but:**
- ✅ Catches grain violations at compile time (not runtime)
- ✅ Generates full lineage automatically (not manual logging)
- ✅ Verifies AI-generated code (rejects invalid paths)
- ✅ Separates logical intent from physical storage

---

## Success Looks Like

**Week 1** (Proof of Concept):
- Define: `Trade → Legs → Cashflows` (1:N relationship)
- Reject: `sum(Trade.amount, Portfolio.total)` (grain mixing)
- Emit: Lineage graph showing which trades → which cashflows

**Month 3** (MVP):
- Run a real regulatory report end-to-end
- Generate OpenLineage events with full traceability
- Audit package passes external regulator review

**Year 1** (Production):
- 10+ production pipelines migrated
- Zero grain violations in production
- AI-generated mappings validated before deployment

---

## Business Value

**Regulatory Compliance**:
- BCBS 239: Complete lineage (Principle 3, 4, 6)
- FRTB: Granular risk factor attribution
- EU AI Act: Human oversight of AI-generated pipelines

**Cost Reduction**:
- 80% reduction in debugging time (shift errors left to compile time)
- 90% reduction in audit prep time (automated lineage)

**AI Enablement**:
- AI can generate pipelines, but engine verifies correctness
- Prevents hallucinations from reaching production

---

## Constraints

**Must Have**:
- Mathematically rigorous (Category Theory foundation)
- Full lineage traceability
- Compile-time error detection (grain, type, path violations)
- Works with existing data (Parquet, Delta Lake, etc.)

**Nice to Have**:
- Real-time streaming support (can come later)
- Auto-optimization of physical execution plan
- Visual topology editor

**Out of Scope**:
- Machine learning model training
- Unstructured data (text, images)
- Interactive query interfaces (this is for pipelines, not BI tools)

---

## Next Steps

1. **Requirements Stage**: Transform this intent into structured requirements (REQ-F-*, REQ-NFR-*, etc.)
2. **Design Stage**: Create component architecture, select tech stack
3. **Proof of Concept**: Implement Success Criteria 1-5 from requirements

---

**Related Documents**:
- Requirements: `docs/requirements/mapper_requirements.md` (Requirements Stage output)
- Design: `docs/design/design_CT_01.md`, `design_sql_01.md` (Design Stage output)

---

*"We're tired of debugging data pipelines. Let's build one that can't fail."*
