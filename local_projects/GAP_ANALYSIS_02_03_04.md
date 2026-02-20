# Gap Analysis: test02 vs test03 vs test04

**Date**: 2026-02-20
**Purpose**: Cross-comparison of three CDME dogfood runs to track methodology evolution and identify remaining gaps.

---

## 1. Context

| | test02 | test03 | test04 |
|-|--------|--------|--------|
| **Methodology** | v1.x (7-stage pipeline) | v2.1 (Asset Graph, no gap detection) | v2.1+ (three-direction gap detection, event sourcing) |
| **Intent source** | mapper_requirements.md v7.2 (human-authored) | Simplified from test02 | Full test02 intent (6 INT sections) |
| **Edges traversed** | intent→req→design(x3)→code→tests | intent→req→design→code | intent→req→design→code |
| **Build approach** | Multiple iterations over weeks | Single-pass per edge | Single-pass per edge |

---

## 2. Quantitative Comparison

| Metric | test02 | test03 | test04 | Trend |
|--------|--------|--------|--------|-------|
| **Scala source files** | 25 | 14 | 71 | test04 5x test02 |
| **Test files** | 11 | 0 | 0 | Only test02 has tests |
| **REQ keys defined** | ~50+ | 43 | 69 | test04 most comprehensive |
| **REQ key occurrences** | 1,168 | 307 | 523 | test02 highest density (tests contribute) |
| **Design components** | ~10 | 12 | 21 | test04 most granular |
| **ADR count** | 22 | 7 | 12 | test02 still leads |
| **Design variants** | 3 (Spark, dbt, generic) | 1 | 1 | test02 only |
| **Mermaid diagrams** | 4+ in requirements | 0 | 5 in design, 2 in requirements | test04 recovered this |
| **Test data generators** | 4+ | 0 | 0 | Only test02 |
| **events.jsonl** | No | Yes (3 events) | Yes (3 events) | v2.1 feature |
| **STATUS.md** | No | Yes | Yes (with telemetry) | v2.1 feature |
| **Feature vectors** | No | Yes (empty criteria) | Yes (11 bound criteria) | test04 fixed |
| **Source findings** | N/A | N/A | 46 (across 3 edges) | test04 only |
| **Process gaps** | N/A | N/A | 27 (across 3 edges) | test04 only |
| **CLAUDE.md** | Yes | No | No | Still missing |
| **Appendices** | Yes (Frobenius) | No | No (spawn recommended) | Still missing |

---

## 3. Gap Scorecard — What's Fixed, What's Open

### From test03 GAP_ANALYSIS.md (10 gaps identified)

| Gap | Description | Severity | test03 | test04 | Status |
|-----|-------------|----------|--------|--------|--------|
| ~~GAP-001~~ | No multi-variant design | N/A | Reclassified | Reclassified | **NOT A GAP** — inherent in iterate() with different Context[] |
| GAP-002 | Shallow ADR generation | Low | 7 ADRs | 12 ADRs | **IMPROVED** — added `adr_depth_adequate` check (min count, alternatives, consequences) |
| GAP-003 | No tests (edge not traversed) | Expected | 0 tests | 0 tests | **OPEN** — TDD edge exists but not yet traversed |
| GAP-004 | No test data generation | Low | No guidance | `test_data_strategy` check added to tdd.yml | **PARTIALLY FIXED** — evaluator added, not yet exercised |
| GAP-005 | No appendix/supplementary docs | Low | No spawn | spawn recommended in event | **OPEN** — spawn mechanism exists but never invoked |
| GAP-006 | No CLAUDE.md generation | Low | No CLAUDE.md | No CLAUDE.md | **OPEN** — not added to /aisdlc-init |
| GAP-007 | No checkpoint/snapshot execution | Medium | Empty snapshots/ | No snapshots/ | **OPEN** — /aisdlc-checkpoint not implemented |
| GAP-008 | No Mermaid diagrams in generated docs | Low | 0 diagrams | 7 diagrams (5 design + 2 req) | **FIXED** — added `diagrams_present` and `architecture_diagrams_present` evaluator checks |
| GAP-009 | Requirements lose pedagogical structure | Medium | No terminology, no success criteria | Terminology (33 terms), Success Criteria, 8 required sections | **FIXED** — added `document_structure` with required sections, `terminology_defines_domain_terms`, `success_criteria_measurable` checks |
| GAP-010 | Feature vector not updated during traversal | Low | Not updated | Updated at each convergence + bound acceptance criteria | **FIXED** — event sourcing drives derived view updates |

**Score: 4 FIXED, 1 IMPROVED, 1 PARTIALLY FIXED, 4 OPEN**

---

## 4. New Gaps Discovered in test04

### From TELEM signals (self-reflection)

| ID | Gap | Source | Severity | Recommendation |
|----|-----|--------|----------|---------------|
| GAP-011 | Evaluator bar too low — all edges converge in 1 iteration | TELEM-001 (test03 + test04) | Medium | Add inter-artifact consistency checks; consider minimum iteration requirement on standard profile |
| GAP-012 | No skip policy for deterministic checks | TELEM-002/003 (test03 + test04) | Medium | Add `skip_policy: {block, warn, allow}` to evaluator config |
| GAP-013 | Source findings redundant across edges | TELEM-003 (test04) | Low | Add `back_propagate` disposition; track new vs inherited findings |
| GAP-014 | No compilation validation | TELEM-005 (test04) | High | Require at minimum syntax parse; consider `scalac -Xonly-parse` |
| GAP-015 | No human review on code generation | TELEM-006 (test04) | Low | Optional human check for first-iteration code gen on complex systems |
| GAP-016 | Multi-module build vs flat source layout | TELEM-004 (test04) | Low | Design doc should include module→package mapping table |

### From process gap detection (inward)

| ID | Gap | Edge | Type | Count Across Edges |
|----|-----|------|------|-------------------|
| GAP-017 | Missing evaluators (dependency DAG, domain coverage, regulatory completeness, negative requirements, build structure, inter-component cycles, test strategy, config defaults, package-component match) | All | EVALUATOR_MISSING | 11 unique across 3 edges |
| GAP-018 | Vague evaluators (compound requirements for math structures, specificity rubric, design rationale vs spec ambiguity, ADR min count basis) | intent→req, req→design | EVALUATOR_VAGUE | 4 unique |
| GAP-019 | Missing context (source document v7.2, Appendix A Frobenius, test strategy template, Scala 3 reference, Spark API reference, OpenLineage spec) | All | CONTEXT_MISSING | 7 unique |
| GAP-020 | Missing guidance (multi-intent tracing, design pattern preferences, error message formatting, TypeUnifier integration, ProductConstructor context injection) | All | GUIDANCE_MISSING | 5 unique |

---

## 5. Evolution Across Runs

### What Got Better (test02 → test03 → test04)

| Dimension | test02 → test03 | test03 → test04 |
|-----------|----------------|----------------|
| **Methodology formalism** | Implicit stages → explicit graph topology | Same graph + three-direction gap detection |
| **Observability** | None | STATUS.md → STATUS.md + event sourcing + telemetry + self-reflection |
| **REQ key format** | REQ-LDM-01 → REQ-F-LDM-001 | Same (already good) |
| **Traceability** | Standalone matrix → embedded | Same + 100% coverage tracking |
| **Document structure** | N/A | Enforced via required sections (terminology, success criteria) |
| **Diagrams** | Human-authored | None → Auto-generated (evaluator-enforced) |
| **Feature tracking** | None → feature vector (empty) | Feature vector with 11 bound acceptance criteria |
| **Gap detection** | None | None → Three-direction (backward + forward + inward) |
| **ADR quality** | Many but shallow/template-driven | Few → More with depth check (alternatives, consequences) |
| **Code volume** | 25 files | 14 files → 71 files (full architecture) |

### What Stayed the Same or Regressed

| Dimension | Status | Root Cause |
|-----------|--------|-----------|
| **Tests** | Still 0 in v2.1 runs | TDD edge not traversed |
| **Test data** | Still 0 in v2.1 runs | TDD edge not traversed |
| **CLAUDE.md** | Still missing | Not in /aisdlc-init |
| **Appendices** | Still missing | Spawn never invoked |
| **Design variants** | Still 1 (was 3 in test02) | iterate() supports it but agent doesn't invoke it |
| **1-iteration convergence** | Persists | Evaluator bar not raised |
| **Deterministic checks** | Still skipping | No build environment + no skip policy |
| **context_hash** | Still "sha256:pending" | Hash computation not implemented |

---

## 6. Priority Recommendations

### Critical (blocks production use)

1. **GAP-014: No compilation validation** — 71 Scala files never compiled. Add syntax parse as minimum.
2. **GAP-012: Skip policy** — deterministic checks silently skipped. Must block or warn explicitly.
3. **GAP-003: TDD edge** — traverse code↔unit_tests to validate the full loop.

### High (reduces value significantly)

4. **GAP-011: Evaluator rigour** — 1-iteration convergence on a 69-REQ system is suspicious. Add cross-artifact consistency checks.
5. **GAP-013: Redundant findings** — same gaps rediscovered at each edge wastes context. Add `back_propagate` disposition.
6. **GAP-017: 11 missing evaluators** — most impactful: `dependency_graph_acyclic`, `test_strategy_present`, `build_structure_valid`.

### Medium (nice-to-have for methodology completeness)

7. **GAP-006: CLAUDE.md** — easy win, add to /aisdlc-init.
8. **GAP-007: Checkpoints** — needed for long-running sessions.
9. **GAP-019: Missing context** — Scala 3 reference, Spark API, OpenLineage spec should be in recommended context.
10. **GAP-005: Spawn mechanism** — Frobenius appendix deferred for 3 runs. Invoke spawn once to validate it works.

---

## 7. Methodology Health Dashboard

```
                        test02      test03      test04
                        (v1.x)      (v2.1)      (v2.1+)
                        ──────      ──────      ──────
Graph formalism         ░░░░░       █████       █████
Evaluator coverage      ██░░░       ███░░       ████░
Document structure      ███░░       █░░░░       ████░
Traceability            ███░░       ███░░       █████
Observability           ░░░░░       ██░░░       █████
Gap detection           ░░░░░       ░░░░░       ████░
Test coverage           ████░       ░░░░░       ░░░░░
Code completeness       ███░░       ██░░░       █████
ADR depth               ████░       ██░░░       ███░░
Feature tracking        ░░░░░       ██░░░       ████░

Legend: ░ = gap/absent   █ = present/strong
```

**Overall**: test04 is strongest on structure, formalism, and observability. test02 is strongest on test coverage and ADR depth. The methodology is converging toward a complete system but the TDD loop and evaluator rigour remain the critical missing pieces.
