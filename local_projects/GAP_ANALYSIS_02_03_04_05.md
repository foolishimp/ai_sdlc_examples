# Gap Analysis: test02 vs test03 vs test04 vs test05

**Date**: 2026-02-21
**Purpose**: Cross-comparison of four CDME dogfood runs to track methodology evolution and identify remaining gaps.

---

## 1. Context

| | test02 | test03 | test04 | test05 |
|-|--------|--------|--------|--------|
| **Methodology** | v1.x (7-stage pipeline) | v2.1 (Asset Graph, no gap detection) | v2.1+ (three-direction gap detection, event sourcing) | **v2.3** (constraint dimensions, 3 design variants, parallel generation) |
| **Intent source** | mapper_requirements.md v7.2 (human-authored) | Simplified from test02 | Full test02 intent (6 INT sections) | Full test02 intent (6 INT sections) |
| **Edges traversed** | intent→req→design(x3)→code→tests | intent→req→design→code | intent→req→design→code→unit_tests→uat_tests | intent→req→**design(x3)**→code (scala_spark only) |
| **Design variants** | 3 (Spark, dbt, generic) | 1 | 1 | **3 (scala_spark, pyspark, dbt)** |
| **Build approach** | Multiple iterations over weeks | Single-pass per edge | Single-pass per edge | Single-pass per edge, **parallel design (3 agents), parallel code gen (4 agents)** |
| **Build status** | Spark 2.12, compiles, 252 tests pass | Never compiled | All 8 modules compile, 252 tests pass | **All 8 modules compile (Scala 2.13.12), 0 tests** |
| **Key innovation** | First complete run | Graph formalism | Gap detection + TDD co-evolution | **Multi-design from same reqs + parallel code gen** |

---

## 2. Quantitative Comparison

| Metric | test02 | test03 | test04 | test05 | Trend |
|--------|--------|--------|--------|--------|-------|
| **Scala source files** | 25 | 14 | 71 | **66** (8 modules) | test04/05 ~3x test02 |
| **Scala source lines** | ~3,500 | ~1,200 | ~8,000 | **7,455** | test04/05 ~2x test02 |
| **Test files** | 11 | 0 | 16 unit + 11 BDD | **0** | test05 TDD edge not yet traversed |
| **Tests passing** | ~252 (Spark) | 0 | 252/252 | **0** | test05 edge pending |
| **Compiles** | Yes (Spark 2.12) | No | Yes (Scala 2.13.12) | **Yes (Scala 2.13.12)** | test04+05 both clean |
| **REQ keys defined** | ~50+ | 43 | 69 | **69** | test04/05 match (same source material) |
| **REQ keys in code** | N/A | N/A | N/A | **64/69 (93%)** | test05 first to measure |
| **REQ key occurrences** | 1,168 | 307 | 523 | **881** | test05 second highest; test02 leads (tests contribute) |
| **Design variants** | 3 | 1 | 1 | **3** | test05 recovers multi-design |
| **Design doc lines** | ~2,000 | ~800 | ~1,200 | **4,194** (3 designs) | test05 2x test02 |
| **Design components** | ~10 | 12 | 21 | **44** (scala_spark alone) | test05 most granular |
| **ADR count** | 22 | 7 | 13 | **15** (5 × 3 designs) | test02 still leads total |
| **Mermaid diagrams** | 4+ | 0 | 7 | **13** | test05 most diagrams |
| **BDD scenarios** | 0 | 0 | 215 | **0** | test04 only (edge not traversed) |
| **ScalaCheck generators** | 4+ files | 0 | 0 | **1** (testkit scaffolded) | test05 has scaffold, not populated |
| **sbt modules** | 1 (flat) | 1 (flat) | 8 | **8** (root + 8) | test04/05 match design arch |
| **events.jsonl** | No | 3 events | 5 events | **1 event** | test05 under-instrumented |
| **STATUS.md** | No | Yes | Yes (with telemetry) | **No** | Regression from test04 |
| **Feature vectors** | No | Yes (empty criteria) | Converged (all 5 edges) | **Yes** (9 criteria, only req edge) | Initialised but incomplete |
| **Source findings** | N/A | N/A | 56 (across 5 edges) | **15** (6 ambig + 6 gaps + 3 underspec, 1 edge) | test05 per-edge similar density |
| **Process gaps** | N/A | N/A | 35 (across 5 edges) | **Not formally tracked** | Regression from test04 |
| **Bugs found during build** | N/A | N/A | 7 real bugs fixed | **~100 cross-module mismatches fixed** | Different bug class (parallel gen) |
| **CLAUDE.md** | Yes | No | No | **No** | Still missing |
| **Appendices** | Yes (Frobenius) | No | No | **No** | Still missing |
| **Constraint dimensions** | N/A | N/A | N/A | **8 defined** (v2.3 graph_topology) | test05 only |
| **dbt "Not Achievable"** | N/A | N/A | N/A | **4 reqs** (honest DA marking) | test05 only |

---

## 3. Gap Scorecard — What's Fixed, What's Open

### From test03 GAP_ANALYSIS.md (10 gaps identified)

| Gap | Description | Severity | test03 | test04 | test05 | Status |
|-----|-------------|----------|--------|--------|--------|--------|
| ~~GAP-001~~ | No multi-variant design | N/A | Reclassified | Reclassified | **3 designs generated** | **NOT A GAP** (was reclassified; test05 validates multi-design works) |
| GAP-002 | Shallow ADR generation | Low | 7 ADRs | 13 ADRs | **15 ADRs** (5 per design) | **IMPROVED** — breadth up, depth comparable |
| GAP-003 | No tests (edge not traversed) | Expected | 0 tests | 252 pass | **0 tests** | **OPEN in test05** — TDD edge not yet traversed |
| GAP-004 | No test data generation | Low | No guidance | evaluator added | **1 ScalaCheck file** (testkit) | **PARTIALLY FIXED** — scaffold exists, not populated |
| GAP-005 | No appendix/supplementary docs | Low | No spawn | spawn recommended | **No spawn** | **OPEN** — 4 runs, never invoked |
| GAP-006 | No CLAUDE.md generation | Low | No CLAUDE.md | No CLAUDE.md | **No CLAUDE.md** | **OPEN** — 4 runs, never generated |
| GAP-007 | No checkpoint/snapshot execution | Medium | Empty snapshots/ | No snapshots/ | **No snapshots/** | **OPEN** |
| GAP-008 | No Mermaid diagrams in generated docs | Low | 0 diagrams | 7 diagrams | **13 diagrams** | **FIXED** — test05 best yet |
| GAP-009 | Requirements lose pedagogical structure | Medium | No terminology | 33 terms | **Same REQUIREMENTS.md** | **FIXED** (carried forward) |
| GAP-010 | Feature vector not updated during traversal | Low | Not updated | Updated at each edge | **Updated at req edge only** | **PARTIALLY FIXED** — only 1 edge traversed so far |

**Score: 4 FIXED, 2 IMPROVED/PARTIALLY FIXED, 4 OPEN**

---

### From test04 Gaps (GAP-011 through GAP-020)

| Gap | Description | Severity | test04 | test05 | Status |
|-----|-------------|----------|--------|--------|--------|
| GAP-011 | Evaluator bar too low — 1-iteration convergence | Medium | 7 bugs confirmed | **~100 cross-module mismatches** | **CONFIRMED WORSE** — parallel gen amplifies the problem |
| GAP-012 | No skip policy for deterministic checks | Medium | No policy | **No policy** | **OPEN** — lint/format still not run in test05 |
| GAP-013 | Source findings redundant across edges | Low | 56 findings, many duplicates | **Only 1 edge, can't assess** | **OPEN** |
| GAP-014 | No compilation validation | High | FIXED (252 tests pass) | **FIXED** (0 errors, sbt compile clean) | **FIXED** |
| GAP-015 | No human review on code generation | Low | Unchanged | **Unchanged** | **OPEN** |
| GAP-016 | Multi-module build vs flat layout | Low | FIXED (8 modules) | **FIXED** (8 modules, dependency DAG) | **FIXED** |
| GAP-017 | 11 missing evaluators | Medium | Identified | **Not addressed** | **OPEN** |
| GAP-018 | 4 vague evaluators | Low | Identified | **Not addressed** | **OPEN** |
| GAP-019 | Missing context (Scala 3 ref, Spark API, etc.) | Medium | Identified | **Not addressed** | **OPEN** |
| GAP-020 | Missing guidance (multi-intent, error formatting) | Low | Identified | **Not addressed** | **OPEN** |

---

## 4. New Gaps Discovered in test05

### From parallel generation and multi-design

| ID | Gap | Source | Severity | Recommendation |
|----|-----|--------|----------|----------------|
| GAP-021 | **Parallel code generation creates cross-module type mismatches** | ~100 compilation errors from 4 parallel agents not sharing type signatures | High | Add a "shared types contract" phase before parallel gen, or serialize module generation in dependency order |
| GAP-022 | **Architecture violations from parallel generation** | cdme-context imported from cdme-compiler (hexagonal DAG violation) | High | Post-gen `dependency_graph_acyclic` evaluator needed (was GAP-017 — now confirmed) |
| GAP-023 | **Feature vector not updated for design or code edges** | CDME.yml shows only `requirements: iterating`; design and code edges not recorded | Medium | Iterate agent should update feature vector at each edge traversal |
| GAP-024 | **events.jsonl only has 1 event (3 edges traversed)** | Design(x3) and code edges did not emit events | Medium | Event emission should be mandatory per-edge, not opt-in |
| GAP-025 | **STATUS.md not created** | test04 had it; test05 does not | Low | Should be auto-created by /aisdlc-init or first iterate |
| GAP-026 | **5 REQ keys missing from code** | REQ-BR-REG-{001,002,003}, REQ-DATA-LIN-004, REQ-NFR-PERF-001 have no `Implements:` tag | Medium | Post-gen traceability checker should flag missing keys |
| GAP-027 | **dbt design honestly marks 4 reqs as "Not Achievable"** | REQ-F-LDM-004 (monoid laws), 3 others lack dbt equivalent | N/A (positive signal) | **NOT A GAP** — this is the methodology working correctly; designs should flag technology mismatches |
| GAP-028 | **No cross-design consistency check** | 3 designs generated in parallel; no check that they interpret the same requirements consistently | Medium | Add `cross_design_consistency` evaluator when multi-design is used |
| GAP-029 | **project_constraints.yml has no constraint dimension tags** | v2.3 defines 8 dimensions but they weren't resolved into the project constraints file | Low | /aisdlc-init or first iterate should populate dimension bindings |
| GAP-030 | **TestKit scaffolded but empty** | CdmeGenerators.scala exists with ScalaCheck structure but test edge not traversed | Expected | Will resolve when TDD edge is run |
| **GAP-031** | **No ADR carry-forward protocol** | test05 drops 7/13 high-severity ADRs from test04 (type system, adjoint-as-trait, telemetry, versioning, circuit breaker, monoid algebra, reverse-join strategy). Code implements them but decisions are undocumented | **High** | Add `adr_carry_forward` evaluator: prior ADRs must be re-affirmed, superseded, or explicitly dropped with rationale |
| **GAP-032** | **No ADR minimum topic coverage** | `requirements_design.yml` requires "ADR Index" but specifies no minimum count or required topics. Agents satisfy the checklist with 5 and move on | **High** | Add required topic list to edge config: type system, error handling, observability, versioning, domain-specific algebra |
| **GAP-033** | **ADR depth not enforced** | test05 ADRs vary in depth; some lack Alternatives Considered or Consequences sections | **Medium** | Add depth evaluator: ≥2 alternatives, ≥1 positive + ≥1 negative consequence, REQ key traceability |
| **GAP-034** | **No shared ADR directory for multi-design** | Cross-variant decisions (Either monad, content hashing, sealed ADTs) are either duplicated or silently lost across 3 designs | **Medium** | Add `docs/design/common/adrs/` for variant-independent architectural decisions |

### Bug Classification: Parallel Generation (test05) vs Sequential (test04)

| Category | test04 (sequential) | test05 (parallel) | Implication |
|----------|--------------------|--------------------|-------------|
| **Import mismatches** | 0 | ~60 | Parallel agents don't see each other's imports |
| **Constructor sig mismatches** | 0 | ~20 | Epoch(4-param) vs Epoch(1-param) across modules |
| **Architecture violations** | 0 | 1 (context→compiler) | No shared dependency contract |
| **Sealed trait violations** | 0 | ~10 | Context tried to extend model's sealed CdmeError |
| **Field name mismatches** | 0 | ~10 | `.roles` vs `.allowedRoles`, constructor arity |
| **Logic bugs** | 7 | 0 (not tested) | Sequential finds logic bugs; parallel finds interface bugs |
| **Total** | **7** | **~100** | Different failure modes; both need evaluator coverage |

**Key insight**: Parallel generation trades interface consistency for speed. The ~100 mismatches were all mechanical (imports, signatures, sealed trait boundaries) and fixable by a single reconciliation pass. The 7 logic bugs from test04's sequential generation are arguably harder to find and fix. **Both modes need post-gen compilation as a mandatory evaluator.**

---

## 5. Evolution Across Runs

### What Got Better (test02 → test03 → test04 → test05)

| Dimension | test02→test03 | test03→test04 | test04→test05 |
|-----------|---------------|---------------|---------------|
| **Methodology formalism** | Implicit → explicit graph | Same graph + gap detection | **v2.3: constraint dimensions, Markov criteria** |
| **Multi-design** | 3 variants (manual) | 1 variant | **3 variants (parallel, from same reqs)** |
| **Design depth** | ~2,000 lines | ~1,200 lines | **4,194 lines (3 designs)** |
| **Mermaid diagrams** | 4+ (manual) | 7 (evaluator-enforced) | **13 (highest yet)** |
| **REQ traceability** | Not measured | Not measured | **93% (64/69 keys in code)** |
| **Code generation speed** | Weeks (iterative) | Single pass | **4 parallel agents, ~100 errors then 1 fix pass** |
| **Compilation** | Yes (2.12) | Yes (2.13) | **Yes (2.13, same)** |
| **Build architecture** | Flat | 8 modules | **8 modules (same design)** |
| **Technology honesty** | All variants assumed feasible | N/A | **dbt marks 4 reqs "Not Achievable"** |
| **ADR coverage** | 22 (one design) | 13 | **15 (5 × 3 designs)** |

### What Stayed the Same or Regressed

| Dimension | Status | Root Cause | Recommendation |
|-----------|--------|-----------|----------------|
| **Tests** | **Regressed** (252→0) | TDD edge not traversed in test05 | Run code↔unit_tests edge |
| **STATUS.md** | **Regressed** (present→absent) | Not generated during edge traversal | Make mandatory in iterate agent |
| **events.jsonl** | **Regressed** (5→1 events) | Design and code edges didn't emit | Make event emission mandatory |
| **Feature vector** | **Regressed** (converged→partial) | Only 1 of 4 edges updated it | Update at every edge |
| **Process gap tracking** | **Regressed** (35 gaps→0) | Inward gap detection not run | Include in iterate agent |
| **CLAUDE.md** | Still missing | Not in /aisdlc-init | Add to init scaffold |
| **Appendices** | Still missing | Spawn never invoked | Test spawn mechanism once |
| **Skip policy** | Still missing | No framework | Add to evaluator config |
| **Evaluator rigour** | **Worse** (7 bugs→~100 mismatches) | Parallel gen amplifies | Mandatory compilation check |

---

## 6. Multi-Design Analysis (test05 unique)

### Cross-Design Comparison

| Dimension | scala_spark | pyspark | dbt |
|-----------|-------------|---------|-----|
| **Design lines** | 1,188 | 1,676 | 1,330 |
| **ADRs** | 5 | 5 | 5 |
| **REQ coverage** | 69/69 (100%) | 69/69 (100%) | 65/69 (94%) — 4 "Not Achievable" |
| **Code generated** | Yes (66 files, compiles) | No | No |
| **Modules/packages** | 8 sbt modules | 8 Python packages | 5 dbt layers + 3 Python services |
| **Technology honesty** | Full coverage | Full coverage | **Honest limitations flagged** |

### dbt "Not Achievable" Requirements (GAP-027 — positive signal)

| REQ | Description | dbt Limitation |
|-----|-------------|---------------|
| REQ-F-LDM-004 | Monoid algebra with formal laws | No formal verification in SQL; uses built-in aggregation |
| REQ-F-ADJ-001 | Adjoint functor pairs | Category theory constructs have no dbt equivalent |
| REQ-F-ADJ-002 | Containment laws | Same as above |
| REQ-F-ADJ-003 | Self-adjoint detection | Same as above |

**This is the methodology working as designed**: the design edge should surface technology-requirement mismatches rather than silently papering over them.

### Missing from test02 Multi-Design

test02 had 3 design variants but they were generated sequentially over weeks with manual iteration. test05 generated 3 variants **in parallel from the same requirements in a single pass**. Key differences:

| | test02 | test05 |
|-|--------|--------|
| Generation method | Sequential, iterative | **Parallel, single-pass** |
| Shared requirements | Loose coupling | **Same REQUIREMENTS.md (69 keys)** |
| Cross-design checks | None | **None (GAP-028)** |
| Technology honesty | Assumed all feasible | **dbt flags 4 Not Achievable** |
| Code built from | All 3 | **1 of 3 (scala_spark)** |

---

## 7. Priority Recommendations

### Critical (blocks production use)

1. **GAP-021: Parallel generation type contract** — ~100 mismatches from parallel code gen. Either serialize in dependency order or add a shared interface extraction phase.
2. **GAP-011: Evaluator rigour (confirmed again)** — Now validated across 2 runs: test04 found 7 logic bugs, test05 found ~100 interface bugs. Mandatory `sbt compile` / `pytest` check before convergence.
3. **GAP-003/GAP-030: No tests in test05** — TDD edge pending. Without tests, the 66 source files are unvalidated beyond compilation.

### High (reduces value significantly)

4. **GAP-031: ADR carry-forward protocol** — test05 silently drops 7 of test04's 13 ADRs including type system (sealed ADTs), monoid algebra, Writer monad telemetry, content-addressed versioning, reverse-join strategy, and circuit breaker. The code implements all of these but the *decisions* are undocumented. A new team member reading test05's design would not understand why.
5. **GAP-032: ADR minimum topic coverage** — The edge config requires an "ADR Index" but no minimum count or topic list. Agents satisfy this with 5 platform-obvious decisions and skip the hard architectural ones.
6. **GAP-024: Event emission missing** — 3 of 4 edges produced no events. Event sourcing was a v2.1 innovation; test05 regresses.
7. **GAP-023: Feature vector stale** — Only req edge updated it. Design(x3) and code edges are untracked.
8. **GAP-026: 5 REQ keys missing from code** — 93% traceability is good but the 5 missing keys (3 business rules, 1 data, 1 NFR) should be explicitly addressed (implement or defer-with-rationale).
9. **GAP-028: No cross-design consistency** — 3 designs interpret the same requirements independently. A lightweight consistency check would catch divergent interpretations.
10. **GAP-022: Dependency DAG evaluator** — Architecture violation was caught manually but should be automated.

### Medium (nice-to-have for methodology completeness)

11. **GAP-033: ADR depth enforcement** — Some test05 ADRs lack Alternatives Considered or Consequences. Add evaluator requiring ≥2 alternatives, consequences, and REQ mapping.
12. **GAP-034: Shared ADR directory for multi-design** — Cross-variant decisions (Either monad, content hashing) need a `docs/design/common/adrs/` home.
13. **GAP-006: CLAUDE.md** — 4 runs, never generated. Add to /aisdlc-init.
14. **GAP-025: STATUS.md** — test04 had it, test05 doesn't. Should be auto-created.
15. **GAP-012: Skip policy** — Lint/format checks still not run in test05.
16. **GAP-029: Constraint dimensions** — v2.3 defines them but project_constraints.yml doesn't bind them.
17. **GAP-005: Spawn mechanism** — 4 runs, Frobenius appendix deferred every time. Test once to validate.

---

## 8. Methodology Health Dashboard

```
                        test02      test03      test04      test05
                        (v1.x)      (v2.1)      (v2.1+)     (v2.3)
                        ──────      ──────      ──────      ──────
Graph formalism         ░░░░░       █████       █████       █████
Constraint dimensions   ░░░░░       ░░░░░       ░░░░░       ████░
Multi-design            ████░       ░░░░░       ░░░░░       █████
Evaluator coverage      ██░░░       ███░░       ████░       ███░░
Document structure      ███░░       █░░░░       ████░       █████
Traceability            ███░░       ███░░       █████       █████
REQ coverage in code    ███░░       ░░░░░       ███░░       ████░
Observability           ░░░░░       ██░░░       █████       ██░░░
Gap detection           ░░░░░       ░░░░░       ████░       ██░░░
Test coverage           ████░       ░░░░░       █████       ░░░░░
Code completeness       ███░░       ██░░░       █████       █████
Build validation        █████       ░░░░░       █████       █████
ADR depth               ████░       ██░░░       █████       ██░░░
Feature tracking        ░░░░░       ██░░░       █████       ██░░░
Design depth            ███░░       ██░░░       ███░░       █████
Parallel generation     ░░░░░       ░░░░░       ░░░░░       █████
Technology honesty      ░░░░░       ░░░░░       ░░░░░       █████

Legend: ░ = gap/absent   █ = present/strong
```

---

## 9. ADR Deep-Dive: The Significant Drop to 5

### ADR Count Progression

| Run | Total ADRs | Per Design | TBD/Unresolved | Maturity |
|-----|-----------|------------|----------------|----------|
| test02 | **31** | 11 generic + 13 Spark + 5 dbt + 2 late additions | **5 TBD** | Discovery — exploring the decision space |
| test03 | **6** | 6 (single design) | 0 | Distilled — core categorical decisions only |
| test04 | **13** | 13 (single design) | 0 | Production — comprehensive, feature-mapped |
| test05 | **15** | 5 × 3 designs | 0 (but dbt 5 are "Proposed") | Pragmatic — breadth over depth |

### What test04 Covered That test05 Drops

test04's 13 ADRs represent the most architecturally complete set. test05's 5-per-design structure **implicitly inherits but never re-documents** 8 critical decisions:

| test04 ADR | Topic | Severity of Loss | test05 Status |
|-----------|-------|------------------|---------------|
| ADR-001 | Scala 3 opaque types for domain modelling | Low | Moot — ADR-013 superseded to Scala 2.13; test05 starts at 2.13 |
| **ADR-002** | **Sealed ADT for type system encoding** | **High** | **MISSING** — the entire CdmeType hierarchy design is undocumented |
| ADR-003 | Layered architecture, model-first | Medium | Partially covered by test05 ADR-002 (Hexagonal) but less specific |
| ADR-004 | Either monad for error handling | Medium | **MISSING** — error strategy assumed, not decided |
| **ADR-005** | **Adjoint-as-trait morphism design** | **High** | **MISSING** — core categorical modelling pattern undocumented |
| **ADR-006** | **Content-addressed versioning** | **High** | **MISSING** — reproducibility strategy undocumented |
| **ADR-007** | **Writer monad for telemetry** | **High** | **MISSING** — observability pattern undocumented |
| **ADR-008** | **Monoid typeclass for aggregation** | **High** | **MISSING** — the formal algebra that powers fold operations |
| ADR-009 | Spark as reference execution binding | Low | Covered by test05 ADR-001 |
| ADR-010 | Programmatic API over config files | Medium | **MISSING** — API design philosophy undocumented |
| **ADR-011** | **Reverse-join table strategy for adjoints** | **High** | **MISSING** — production-critical storage + accounting |
| **ADR-012** | **Circuit breaker with sampling pre-phase** | **High** | **MISSING** — operational safety pattern undocumented |
| ADR-013 | Scala 2.13 + Spark 3.5 migration | Low | Covered by test05 ADR-001 (starts at 2.13) |

**7 of 13 test04 ADRs are HIGH severity losses in test05.** These aren't minor gaps — they cover the type system, the categorical algebra, error handling, telemetry, versioning, adjoint storage, and operational safety. The code implements all of these patterns but the *decisions* are undocumented.

### What test05 Adds That test04 Lacked

| test05 ADR | Topic | Present in test04? |
|-----------|-------|--------------------|
| ADR-002 (all) | Architecture pattern per technology | No — test04 had ADR-003 (layered) but not hexagonal/protocol-based distinction |
| ADR-003 (Scala) | Validate-then-execute compiler pattern | Implicit in test04, now **explicit** |
| ADR-004 (all) | Deployment model (Docker Compose) | **NEW** — test04 had no deployment ADR |
| ADR-005 (Scala/PySpark) | RBAC on morphisms | **NEW** — test04 had access control in code but no ADR |
| ADR-003 (dbt) | "Descoped and Partially Satisfied" | **NEW** — honest technology limitation disclosure |

### The 5-ADR Template

test05 uses a fixed 5-slot template across all 3 designs:

| Slot | Purpose | Good For | Bad For |
|------|---------|----------|---------|
| 1 | **Platform choice** | Technology binding, version pinning | Assumed settled — no alternatives explored |
| 2 | **Architecture pattern** | Structural clarity | Drops detailed layer semantics |
| 3 | **Core validation/safety** | New in test05 (compiler pattern) | Doesn't cover error handling, telemetry, or accounting |
| 4 | **Deployment** | New in test05 | Operational but not architectural |
| 5 | **Access/Observability** | New in test05 | Conflates two separate concerns |

The template is **wide but shallow**: it covers breadth (3 designs × 5 ADRs = 15 decisions) but loses the depth that test04's 13 ADRs achieved for a single design.

### Root Cause Analysis

Why did test05 drop to 5? Three factors:

1. **Parallel design generation** — Each design was generated by a separate agent. Without explicit guidance on "carry forward all ADRs from prior runs", each agent independently decided what merited an ADR. The result: each picked the 5 most obvious decisions for its technology.

2. **No ADR carry-forward protocol** — The methodology has no mechanism for: "these ADRs from the previous design iteration are still valid; either re-affirm, supersede, or explain why dropped." Each design starts from scratch.

3. **Template pressure** — The `requirements_design.yml` edge config requires an "ADR Index" section but doesn't specify minimum count, coverage of prior decisions, or required topics. The agent satisfied the checklist with 5 and moved on.

### Recommendations

| ID | Fix | Severity | Effort |
|----|-----|----------|--------|
| **GAP-031** | Add `adr_carry_forward` evaluator: "all ADRs from prior iterations must be re-affirmed, superseded, or explicitly dropped with rationale" | **High** | Medium |
| **GAP-032** | Add `adr_minimum_topics` checklist to `requirements_design.yml`: require ADRs for type system, error handling, observability, versioning, and any domain-specific algebra | **High** | Low |
| **GAP-033** | Add `adr_depth_check` evaluator: each ADR must have Alternatives Considered (≥2), Consequences (≥1 positive, ≥1 negative), and REQ key mapping | **Medium** | Low |
| **GAP-034** | Multi-design ADR sharing: decisions that apply across all variants (e.g., Either monad, content-addressed versioning) should live in a shared `docs/design/common/adrs/` directory, with variant-specific ADRs in each design folder | **Medium** | Medium |

### Impact on Generated Code

The code **does implement** the patterns from test04's missing ADRs — the Scala source has sealed ADTs, Either monads, Writer telemetry, Monoid typeclasses, reverse-join metadata, and circuit breakers. The gap is purely in **documentation**: the architectural rationale exists in code but not in decisions. This means:

- A new team member reading test05's design docs would not understand *why* the code uses a Writer monad for telemetry
- The dbt variant has no guidance on which of these patterns to adapt vs skip (except ADR-003's honest descoping)
- Future iterations have no baseline to supersede — they'll re-derive the same decisions

---

## 10. Key Insights

### 1. Parallel Generation Is a New Failure Mode

test05 is the first run to use parallel code generation (4 agents building 8 modules simultaneously). The ~100 compilation errors are **qualitatively different** from test04's 7 logic bugs:

- **Parallel bugs**: Interface mismatches (imports, constructors, sealed trait boundaries). Mechanical, fixable by reconciliation pass.
- **Sequential bugs**: Logic errors (RBAC filtering, Unicode round-trip, property test generators). Harder to find, harder to fix.

The methodology needs **both** modes' error signals. Recommendation: parallel generation for speed, followed by mandatory compilation + reconciliation, then sequential logic validation via TDD.

### 2. Multi-Design from Same Requirements Works

test05 is the first run to generate 3 technology-bound designs from the same REQUIREMENTS.md in parallel. The dbt design honestly flagging 4 requirements as "Not Achievable" is exactly the signal the methodology should produce. This validates the spec/design separation thesis: one spec, many designs, each with honest technology-requirement gap analysis.

### 3. Observability Regressed Despite Methodology Improvement

v2.3 is formally superior to v2.1 (constraint dimensions, Markov criteria), yet test05 has **worse observability** than test04:
- 1 event vs 5 events
- No STATUS.md
- Feature vector stale after first edge
- No process gap tracking

Root cause: the iterate agent instructions were followed for the first edge but bypassed for design and code edges (which used direct parallel generation). The methodology's observability benefits only materialize when the iterate agent is invoked for every edge.

### 4. REQ Traceability Measurement Is New

test05 is the first run to measure REQ key coverage in generated code: 64/69 = 93%. The 5 missing keys are:
- 3 business rules (REQ-BR-REG-*) — regulatory requirements often don't map to specific code
- 1 data lineage (REQ-DATA-LIN-004) — may be in lineage module but not tagged
- 1 NFR (REQ-NFR-PERF-001) — performance requirements are architectural, not code-tagged

This measurement should become a standard post-code evaluator.

---

## 10. Run Comparison Summary

| | test02 | test03 | test04 | test05 |
|-|--------|--------|--------|--------|
| **Strongest at** | Test coverage, ADR count, REQ density | Graph formalism | Observability, gap detection, TDD | **Multi-design, parallel gen, design depth, technology honesty** |
| **Weakest at** | No formalism, no observability | No tests, no build | Design variants (only 1) | **No tests, poor observability, ADR depth regression, parallel gen bugs** |
| **Unique contribution** | Proved full pipeline works | Proved v2.1 formalism | Proved gap detection + TDD co-evolution | **Proved multi-design + parallel gen + honest tech limitations** |
| **Methodology lesson** | A complete pipeline beats a partial one | Formalism alone isn't enough | Observability + testing close the loop | **Parallelism needs reconciliation; ADRs need carry-forward; observability is opt-in** |
