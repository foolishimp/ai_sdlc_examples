# Gap Analysis: test02 vs test03 vs test04 vs test05 vs test06

**Date**: 2026-02-23
**Purpose**: Cross-comparison of five CDME dogfood runs to track methodology evolution and identify remaining gaps.

---

## 1. Context

| | test02 | test03 | test04 | test05 | test06 |
|-|--------|--------|--------|--------|--------|
| **Methodology** | v1.x (7-stage pipeline) | v2.1 (Asset Graph, no gap detection) | v2.1+ (three-direction gap detection, event sourcing) | v2.3 (constraint dimensions, 3 design variants, parallel gen) | **v2.8** (IntentEngine, tolerances, functor encoding, full-auto profile) |
| **Intent source** | mapper_requirements.md v7.2 (human-authored) | Simplified from test02 | Full test02 intent (6 INT sections) | Full test02 intent (6 INT sections) | Full test02 intent (6 INT sections) |
| **Edges traversed** | intent→req→design(x3)→code→tests | intent→req→design→code | intent→req→design→code→unit_tests→uat_tests | intent→req→design(x3)→code (scala_spark only) | **intent→req→design→code→unit_tests→uat_tests (all 5 edges, full profile)** |
| **Design variants** | 3 (Spark, dbt, generic) | 1 | 1 | 3 (scala_spark, pyspark, dbt) | **1 (scala_spark — deliberate single-design)** |
| **Build approach** | Multiple iterations over weeks | Single-pass per edge | Single-pass per edge | Single-pass, parallel design (3 agents), parallel code gen (4 agents) | **Single-shot `/gen-start --auto`, sequential edges, full-auto profile** |
| **Build status** | Spark 2.12, compiles, 252 tests pass | Never compiled | All 8 modules compile, 252 tests pass | All 8 modules compile (Scala 2.13.12), 0 tests | **All 8 modules compile (Scala 2.13.12), 95 tests pass** |
| **Key innovation** | First complete run | Graph formalism | Gap detection + TDD co-evolution | Multi-design from same reqs + parallel code gen | **Uninterrupted single-shot build; full-auto profile (no human gates); v2.8 per-edge event emission** |

---

## 2. Quantitative Comparison

| Metric | test02 | test03 | test04 | test05 | test06 | Trend |
|--------|--------|--------|--------|--------|--------|-------|
| **Scala source files** | 25 | 14 | 71 | 66 (8 modules) | **30** (8 modules) | test06 leaner; fewer files per module |
| **Scala source lines** | ~3,500 | ~1,200 | ~8,000 | 7,455 | **1,578** | test06 most compact (single-shot, no over-engineering) |
| **Test files** | 11 | 0 | 16 unit + 11 BDD | 0 | **19** | test06 recovers tests (TDD + BDD edges traversed) |
| **Test lines** | ~2,500 | 0 | ~4,000 | 0 | **1,461** | test06 test:code ratio ~1:1 |
| **Tests passing** | ~252 (Spark) | 0 | 252/252 | 0 | **95/95 (0 failures)** | test06 recovers from test05's regression |
| **Compiles** | Yes (Spark 2.12) | No | Yes (Scala 2.13.12) | Yes (Scala 2.13.12) | **Yes (Scala 2.13.12, 352 .class files)** | 4 of 5 runs compile |
| **REQ keys defined** | ~50+ | 43 | 69 | 69 | **75** (in specification) | test06 highest (v7.2 spec fully utilized) |
| **REQ keys in code** | N/A | N/A | N/A | 64/69 (93%) | **29/30 files tagged (97%)** | test06 best file-level traceability |
| **Unique REQ keys in code** | N/A | N/A | N/A | N/A | **59** | First time measured per-key |
| **REQ key occurrences** | 1,168 | 307 | 523 | 881 | **124** | Lower count but higher consistency (fewer variants) |
| **Design variants** | 3 | 1 | 1 | 3 | **1** | Deliberate: avoids GAP-021 parallel mismatches |
| **Design doc lines** | ~2,000 | ~800 | ~1,200 | 4,194 (3 designs) | **850** | Compact single design; comparable per-variant to test05 |
| **Design components** | ~10 | 12 | 21 | 44 (scala_spark alone) | **42** | Comparable granularity to test05's best |
| **ADR count** | 22 | 7 | 13 | 15 (5 x 3 designs) | **7** | Improved depth over test05's 5-per-design |
| **Mermaid diagrams** | 4+ | 0 | 7 | 13 | **0** | **REGRESSION** — not generated |
| **BDD scenarios** | 0 | 0 | 215 | 0 | **14** (in AcceptanceCriteriaSpec) | Recovered; fewer than test04 but all 9 ACs covered |
| **ScalaCheck generators** | 4+ files | 0 | 0 | 1 (testkit) | **1** | Still minimal |
| **sbt modules** | 1 (flat) | 1 (flat) | 8 | 8 | **8** | Stable since test04 |
| **events.jsonl** | No | 3 events | 5 events | 1 event | **13 events** | **FIXED** — best observability yet |
| **Feature vector** | No | Yes (empty) | Converged (all 5 edges) | Yes (only req edge) | **Converged (all 5 edges)** | **FIXED** — matches test04 |
| **CLAUDE.md** | Yes | No | No | No | **Yes (pre-written)** | **FIXED** — first since test02 |
| **Appendices** | Yes (Frobenius) | No | No | No | **Yes (pre-copied)** | **FIXED** — first since test02 |
| **Constraint dimensions** | N/A | N/A | N/A | 8 defined | **8 defined + bound** | Constraints fully populated |
| **Profile** | N/A | N/A | N/A | N/A | **full-auto (custom)** | First use of custom projection profile |
| **Evaluator overrides** | N/A | N/A | N/A | N/A | **Agent-only (no human gates)** | First uninterrupted build |
| **Source findings** | N/A | N/A | 56 (5 edges) | 15 (1 edge) | **2** (INT-006 + header mismatch) | Lower — pre-seeded spec reduced ambiguity |
| **Bugs found during build** | N/A | N/A | 7 real bugs | ~100 cross-module mismatches | **0 compilation errors** | Sequential + single-design = clean build |

---

## 3. Gap Scorecard — What's Fixed, What's Open

### From test03 GAP_ANALYSIS.md (10 gaps identified)

| Gap | Description | Severity | test04 | test05 | test06 | Status |
|-----|-------------|----------|--------|--------|--------|--------|
| ~~GAP-001~~ | No multi-variant design | N/A | Reclassified | 3 designs | 1 design (deliberate) | **NOT A GAP** — validated in test05; test06 chooses single-design |
| GAP-002 | Shallow ADR generation | Low | 13 ADRs | 15 ADRs (5x3) | **7 ADRs** (improved topics) | **IMPROVED** — better topics than test05's 5-template |
| GAP-003 | No tests (edge not traversed) | Expected | 252 pass | 0 tests | **95 pass** | **FIXED** — TDD + BDD edges both traversed |
| GAP-004 | No test data generation | Low | evaluator added | 1 ScalaCheck | **1 ScalaCheck** | **PARTIALLY FIXED** — scaffold exists |
| GAP-005 | No appendix/supplementary docs | Low | No spawn | No spawn | **Appendix pre-copied** | **FIXED** (via pre-seeding, not spawn) |
| GAP-006 | No CLAUDE.md generation | Low | No | No | **Yes (pre-written)** | **FIXED** (via pre-seeding) |
| GAP-007 | No checkpoint/snapshot execution | Medium | No | No | **No** | **OPEN** |
| GAP-008 | No Mermaid diagrams | Low | 7 diagrams | 13 diagrams | **0 diagrams** | **REGRESSED** — test06 drops diagrams |
| GAP-009 | Requirements lose pedagogical structure | Medium | 33 terms | Same | **Same REQUIREMENTS.md** | **FIXED** (carried forward) |
| GAP-010 | Feature vector not updated | Low | Updated | Req edge only | **All edges converged** | **FIXED** |

**Score: 6 FIXED, 1 IMPROVED, 1 PARTIALLY FIXED, 1 OPEN, 1 REGRESSED**

---

### From test04 Gaps (GAP-011 through GAP-020)

| Gap | Description | Severity | test05 | test06 | Status |
|-----|-------------|----------|--------|--------|--------|
| GAP-011 | Evaluator bar too low — 1-iteration convergence | Medium | ~100 mismatches | **0 errors (sequential + single-design)** | **MITIGATED** — sequential avoids the class, but evaluator rigour still untested |
| GAP-012 | No skip policy for deterministic checks | Medium | No policy | **No policy** | **OPEN** |
| GAP-013 | Source findings redundant across edges | Low | Can't assess (1 edge) | **Only 2 findings (low ambiguity)** | **MITIGATED** — pre-seeded spec reduces noise |
| GAP-014 | No compilation validation | High | FIXED (sbt compiles) | **FIXED (352 .class files)** | **FIXED** |
| GAP-015 | No human review on code generation | Low | Unchanged | **By design (full-auto profile)** | **ACCEPTED** — deliberate for this run |
| GAP-016 | Multi-module build vs flat layout | Low | FIXED (8 modules) | **FIXED (8 modules)** | **FIXED** |
| GAP-017 | 11 missing evaluators | Medium | Not addressed | **Not addressed** | **OPEN** |
| GAP-018 | 4 vague evaluators | Low | Not addressed | **Not addressed** | **OPEN** |
| GAP-019 | Missing context (Scala 3 ref, Spark API) | Medium | Not addressed | **Partially (CLAUDE.md has arch guidance)** | **PARTIALLY FIXED** |
| GAP-020 | Missing guidance (multi-intent, error formatting) | Low | Not addressed | **Not addressed** | **OPEN** |

---

### From test05 Gaps (GAP-021 through GAP-034)

| Gap | Description | Severity | test06 | Status |
|-----|-------------|----------|--------|--------|
| GAP-021 | Parallel code gen creates cross-module type mismatches | High | **N/A** — single-design, sequential gen | **AVOIDED** (by design choice, not fixed) |
| GAP-022 | Architecture violations from parallel gen | High | **0 violations** (sequential respects DAG) | **AVOIDED** (by design choice) |
| GAP-023 | Feature vector not updated for design/code edges | Medium | **All 5 edges converged** | **FIXED** |
| GAP-024 | events.jsonl only 1 event (3 edges traversed) | Medium | **13 events (5 edge_started + 5 edge_converged + more)** | **FIXED** |
| GAP-025 | STATUS.md not created | Low | **No STATUS.md** | **OPEN** |
| GAP-026 | 5 REQ keys missing from code | Medium | **59/75 unique REQ keys in code (79%)** | **PARTIALLY FIXED** — higher file coverage (97%) but 16 spec REQs not code-tagged |
| GAP-027 | dbt "Not Achievable" (positive signal) | N/A | N/A (no dbt design) | **NOT A GAP** |
| GAP-028 | No cross-design consistency check | Medium | N/A (single design) | **AVOIDED** |
| GAP-029 | project_constraints.yml has no dimension tags | Low | **8 dimensions fully bound** | **FIXED** |
| GAP-030 | TestKit scaffolded but empty | Expected | **1 ScalaCheck file in use** | **PARTIALLY FIXED** |
| GAP-031 | No ADR carry-forward protocol | High | **7 ADRs (better topics than test05's 5)** | **PARTIALLY FIXED** — better coverage but no formal carry-forward |
| GAP-032 | No ADR minimum topic coverage | High | **7 ADRs cover: category theory, compiler pattern, errors, adjoints, execution, Spark binding, build** | **IMPROVED** — 7 distinct topics vs test05's 5-template |
| GAP-033 | ADR depth not enforced | Medium | **Depth not measured** | **OPEN** |
| GAP-034 | No shared ADR directory for multi-design | Medium | N/A (single design) | **AVOIDED** |

---

## 4. New Gaps Discovered in test06

| ID | Gap | Source | Severity | Recommendation |
|----|-----|--------|----------|----------------|
| GAP-035 | **No Mermaid diagrams in design doc** | test04 had 7, test05 had 13, test06 has 0 | Low | Add Mermaid evaluator check to `requirements_design.yml` edge config |
| GAP-036 | **Source code is compact but shallow** | 1,578 lines across 30 files (53 LOC/file average) vs test04's 8,000 lines across 71 files | Medium | May indicate skeleton implementations; deeper code review needed to assess completeness vs test04 |
| GAP-037 | **16 specification REQ keys not found in code** | 59/75 unique REQ keys traced (79%); missing keys likely in NFR, regulatory, performance domains | Medium | Post-gen `req_coverage_checker` evaluator should list untraced keys and require disposition (implemented, deferred, N/A) |
| GAP-038 | **`evaluator_overrides` in project_constraints.yml is unimplemented** | Template suggests override mechanism but iterate agent only reads profile-level overrides | Low | Either implement the consumption logic in iterate agent or remove the placeholder from template to avoid confusion |
| GAP-039 | **Custom profile discovery undocumented** | The `full-auto` profile works when placed in `.ai-workspace/profiles/` but this mechanism isn't documented in any command or agent | Low | Document profile discovery path in gen-iterate.md and project_constraints_template.yml |
| GAP-040 | **95 tests vs test04's 252** | Fewer total tests despite both traversing TDD + BDD edges | Medium | May be acceptable given single-shot vs iterative; compare test density (tests per source file) rather than absolute count |
| GAP-041 | **REQ key format inconsistency** | Code uses mixed formats: `REQ-F-LDM-001` (spec format) alongside `REQ-LDM-01` (short format) | Low | Normalize to spec format (`REQ-{TYPE}-{DOMAIN}-{SEQ}`) or add format validator |
| GAP-042 | **Installer doesn't scaffold `graph_topology.yml`** | `gen-setup.py` creates `.ai-workspace/` but not `.ai-workspace/graph/graph_topology.yml`. Genesis Monitor can't see the project (no asset graph, no constraint dimensions, no profiles). The monitor expects this file for all dashboard sections. | **High** | `gen-setup.py` should copy `graph_topology.yml` from plugin `config/` into `.ai-workspace/graph/` during workspace scaffolding. This is the integration contract between the methodology and the monitor. |
| GAP-043 | **Code generated at project root instead of `imp_<design>/`** | All 8 sbt modules (`cdme-model/`, `cdme-compiler/`, etc.) and `build.sbt` generated at `./` instead of `./imp_scala_spark/`. Violates the multi-tenant standard: `specification/` (shared WHAT) + `imp_<name>/` (self-contained HOW). Makes it impossible to later add `imp_pyspark/` or `imp_dbt/` without restructuring. | **High** | Add `output_directory` constraint to `design→code` edge config: `"All generated code MUST be placed under imp_{design_variant}/ — the design tenant directory. This directory is self-contained and independently buildable."` Also enforce in CLAUDE.md and project_constraints. |
| GAP-044 | **No constraint enforcing multi-tenant folder structure** | Neither `project_constraints.yml`, `CLAUDE.md`, nor the `design→code` edge checklist mentions the `imp_<name>/` convention. The agent has no signal that root-level code is wrong. | **High** | Add to `project_constraints_template.yml` a `structure.design_tenants` section specifying the `imp_{variant}/` pattern. Add a deterministic evaluator check: `"No source files exist outside specification/ and imp_*/."` |

---

## 5. Evolution Across Runs

### What Got Better (test02 → test03 → test04 → test05 → test06)

| Dimension | test02→03 | test03→04 | test04→05 | test05→06 |
|-----------|-----------|-----------|-----------|-----------|
| **Methodology formalism** | Implicit → explicit graph | + gap detection | **v2.3: constraint dims, Markov** | **v2.8: IntentEngine, tolerances, functor encoding** |
| **Build automation** | Manual iterations | Single-pass | Parallel gen | **Full single-shot, zero human gates** |
| **Observability** | None | 5 events | 1 event (regressed) | **13 events (best yet)** |
| **Feature tracking** | None | Converged | Partial (1 edge) | **All 5 edges converged** |
| **Test coverage** | 252 tests | 252 tests | 0 tests (regressed) | **95 tests (recovered)** |
| **CLAUDE.md** | Present | Missing | Missing | **Present (pre-seeded)** |
| **Constraint binding** | None | None | 8 dims defined | **8 dims defined + fully bound** |
| **Build errors** | N/A | 0 | ~100 (parallel) | **0 (sequential + single-design)** |
| **Profile system** | N/A | N/A | N/A | **Custom full-auto profile** |

### What Stayed the Same or Regressed

| Dimension | test06 Status | Root Cause | Recommendation |
|-----------|---------------|-----------|----------------|
| **Mermaid diagrams** | **Regressed** (13→0) | Not in single-shot agent priority | Add to evaluator checklist |
| **Source code volume** | **Lower** (1,578 vs 8,000 lines) | Single-shot generates skeleton, not production depth | May need iterate cycles for depth |
| **Test count** | **Lower** (95 vs 252) | Single-shot TDD produces fewer tests | Iterate on TDD edge for more coverage |
| **ADR carry-forward** | Still no protocol | No mechanism in methodology | Add `adr_carry_forward` evaluator |
| **STATUS.md** | Still missing | Not in gen-start | Add to init scaffold |
| **Skip policy** | Still missing | No framework | Add to evaluator config |
| **Evaluator rigour** | Not stress-tested | Sequential avoided parallel bugs | Need both modes validated |
| **Folder structure** | **VIOLATED** — code at root, not `imp_<design>/` | No constraint enforces the standard | Add `structure.design_tenants` to project_constraints + edge checklist |
| **Monitor integration** | **BROKEN** — blank dashboard | Installer doesn't scaffold `graph_topology.yml` | Add to `gen-setup.py` workspace scaffolding |

---

## 6. ADR Analysis: test06 vs Prior Runs

### ADR Count Progression

| Run | Total ADRs | Topics | Maturity |
|-----|-----------|--------|----------|
| test02 | **31** | 11 generic + 13 Spark + 5 dbt + 2 late | Discovery — exploring the decision space |
| test03 | **6** | 6 (single design) | Distilled — core categorical decisions only |
| test04 | **13** | 13 (single design, comprehensive) | Production — comprehensive, feature-mapped |
| test05 | **15** | 5 x 3 designs (template pattern) | Pragmatic — breadth over depth |
| test06 | **7** | 7 (single design, improved topic selection) | **Focused — better topic coverage than test05** |

### test06's 7 ADRs vs test04's 13

| test06 ADR | Topic | test04 Equivalent | Coverage |
|-----------|-------|-------------------|----------|
| ADR-001 | Category Theory as Foundation | ADR-003 (layered arch) | **NEW framing** — category theory explicit |
| ADR-002 | Compile-then-Execute Pattern | Implicit in test04 | **NEW** — compiler pattern now an explicit decision |
| ADR-003 | Errors as Data (Either Monad) | ADR-004 (Either monad) | **MATCHED** |
| ADR-004 | Adjoint Pairs for All Morphisms | ADR-005 (Adjoint-as-trait) | **MATCHED** |
| ADR-005 | Immutable Run Hierarchy | ADR-006 (Content-addressed versioning) | **PARTIALLY MATCHED** — different angle |
| ADR-006 | Spark as Default Runtime | ADR-009 (Spark binding) | **MATCHED** |
| ADR-007 | sbt Multi-Module DAG | Implicit in test04 | **NEW** — build structure now explicit |

### Still Missing from test04's Set

| test04 ADR | Topic | Severity | test06 Status |
|-----------|-------|----------|---------------|
| ADR-002 | Sealed ADT for type system | High | Code has sealed traits but no ADR |
| ADR-007 | Writer monad for telemetry | High | **MISSING** |
| ADR-008 | Monoid typeclass for aggregation | High | Code has Monoid but no ADR |
| ADR-010 | Programmatic API over config | Medium | **MISSING** |
| ADR-011 | Reverse-join table strategy | High | Code has reverse-join but no ADR |
| ADR-012 | Circuit breaker with sampling | High | **MISSING** |

**Improvement over test05**: test06 has 7 ADRs with meaningful topic diversity (category theory, compiler pattern, errors, adjoints, versioning, Spark, build) vs test05's 5-slot template repeated 3x. However, 3 high-severity test04 ADRs remain undocumented (Writer monad, reverse-join strategy, circuit breaker).

---

## 7. Structural Violations: Folder Layout and Monitor Integration

### 7.1 Multi-Tenant Folder Structure Violated (GAP-043, GAP-044)

The methodology's standard is `specification/` (shared, tech-agnostic) + `imp_<design>/` (self-contained, independently buildable). This is the same pattern used in the methodology repository itself:

```
ai_sdlc_method/
├── specification/        # shared WHAT
├── imp_claude/           # Claude Code: design + code + tests
├── imp_gemini/           # Gemini: design + code + tests
└── imp_codex/            # Codex: design + code + tests
```

test06 should have produced:
```
data_mapper.test06/
├── specification/                    # shared spec (correct)
│   ├── INTENT.md
│   ├── REQUIREMENTS.md
│   └── mapper_requirements.md
├── imp_scala_spark/                  # self-contained design tenant
│   ├── design/
│   │   ├── DESIGN.md
│   │   └── adrs/
│   ├── build.sbt
│   ├── project/
│   ├── cdme-model/
│   ├── cdme-compiler/
│   ├── cdme-runtime/
│   ├── cdme-spark/
│   ├── cdme-lineage/
│   ├── cdme-adjoint/
│   ├── cdme-ai-assurance/
│   └── cdme-api/
├── .ai-workspace/
└── CLAUDE.md
```

Instead test06 produced everything at root — 8 sbt module directories, `build.sbt`, and `specification/DESIGN.md` all at the same level. This means:

- **Cannot add `imp_pyspark/`** without first moving all scala_spark files
- **Cannot run independent parallel builds** — the root is polluted
- **Design doc in wrong location** — `specification/DESIGN.md` conflates spec (WHAT) with design (HOW)
- **No isolation** — a future `imp_dbt/` would share the root `.scalafmt.conf`, `.gitignore`, etc.

### Root Cause Chain

```
1. project_constraints.yml has no `structure.design_tenants` constraint
2. CLAUDE.md mentions "8 sbt modules" but not WHERE they go
3. design→code edge checklist has no output directory rule
4. The agent defaults to root (simplest path)
5. No post-gen evaluator checks for "files outside specification/ and imp_*/"
```

### 7.2 Monitor Integration Broken at Installer Level (GAP-042)

`gen-setup.py` creates:
```
.ai-workspace/
├── events/events.jsonl       ✓
├── features/active/          ✓
├── context/project_constraints.yml  ✓
├── tasks/                    ✓
├── agents/                   ✓
└── spec/                     ✓
```

Genesis Monitor expects:
```
.ai-workspace/
├── events/events.jsonl       ✓ (matches)
├── features/active/*.yml     ✓ (matches)
├── graph/graph_topology.yml  ✗ MISSING — no asset graph, no constraint dims, no profiles
├── context/project_constraints.yml  ✓ (matches)
├── STATUS.md                 ✗ MISSING — no status report
└── tasks/active/ACTIVE_TASKS.md     ✓ (matches)
```

Without `graph_topology.yml`, the monitor shows: no asset graph, no edge convergence, no feature vectors, no constraint dimensions — effectively a blank dashboard despite 13 events and a fully converged feature vector.

**Fix**: `gen-setup.py` should copy `graph_topology.yml` from the plugin's `config/` directory into `.ai-workspace/graph/` during workspace scaffolding. This is the same file that defines the methodology's asset types, transitions, and constraint dimensions — it's the integration contract between the methodology plugin and the observability layer.

---

## 8. The Single-Shot Paradigm: What test06 Proves (continued)

### 7.1 Uninterrupted Build Works

test06 is the first run where `/gen-start --auto` traversed all edges without pausing for human review. The `full-auto` custom profile eliminated all human evaluator gates while preserving full edge traversal. This validates that:

- The projection profile system works for customizing evaluator composition
- Agent + deterministic evaluators are sufficient for a complete build
- Per-edge event emission (v2.8) works — 13 events logged, one per edge transition

### 7.2 Pre-Seeding Spec Reduces Ambiguity

By copying test02's proven spec docs into test06's `specification/` directory, the build started with high-quality input material. Result:
- Only 2 source findings (vs test04's 56 and test05's 15)
- Zero compilation errors (vs test05's ~100)
- 97% file-level REQ traceability

### 7.3 Sequential + Single-Design Avoids Parallel Failure Mode

test06 deliberately chose single-design to avoid GAP-021 (parallel type mismatches). This worked — zero compilation errors, clean builds throughout. But it means GAP-021 remains **unfixed** in the methodology; it's just **avoided** by not using parallel generation.

### 7.4 Compact Code: Feature or Bug?

test06 produced 1,578 source lines vs test04's 8,000. This could mean:
- **Feature**: Single-shot is more focused, avoids over-engineering
- **Bug**: Skeleton implementations that lack production depth

The 95 passing tests suggest the code is functional, but the lower line count warrants investigation. The 1:1 test-to-code line ratio is actually healthy.

---

## 8. Methodology Health Dashboard

```
                        test02      test03      test04      test05      test06
                        (v1.x)      (v2.1)      (v2.1+)     (v2.3)      (v2.8)
                        ──────      ──────      ──────      ──────      ──────
Graph formalism         ░░░░░       █████       █████       █████       █████
Constraint dimensions   ░░░░░       ░░░░░       ░░░░░       ████░       █████
Multi-design            ████░       ░░░░░       ░░░░░       █████       ░░░░░  (deliberate)
Evaluator coverage      ██░░░       ███░░       ████░       ███░░       ████░
Document structure      ███░░       █░░░░       ████░       █████       ████░
Traceability            ███░░       ███░░       █████       █████       █████
REQ coverage in code    ███░░       ░░░░░       ███░░       ████░       ████░
Observability           ░░░░░       ██░░░       █████       ██░░░       █████
Gap detection           ░░░░░       ░░░░░       ████░       ██░░░       ███░░
Test coverage           ████░       ░░░░░       █████       ░░░░░       ████░
Code completeness       ███░░       ██░░░       █████       █████       ███░░
Build validation        █████       ░░░░░       █████       █████       █████
ADR depth               ████░       ██░░░       █████       ██░░░       ███░░
Feature tracking        ░░░░░       ██░░░       █████       ██░░░       █████
Design depth            ███░░       ██░░░       ███░░       █████       ███░░
Parallel generation     ░░░░░       ░░░░░       ░░░░░       █████       ░░░░░  (N/A)
Technology honesty      ░░░░░       ░░░░░       ░░░░░       █████       ░░░░░  (N/A)
Build automation        ██░░░       ███░░       ███░░       ████░       █████
Profile customization   ░░░░░       ░░░░░       ░░░░░       ░░░░░       █████
Event emission          ░░░░░       ██░░░       ████░       █░░░░       █████

Legend: ░ = gap/absent   █ = present/strong
```

---

## 9. Priority Recommendations

### Critical (blocks production use)

1. **GAP-043: Code at root instead of `imp_<design>/`** — All 8 sbt modules and `build.sbt` generated at project root. This violates the multi-tenant standard (`specification/` + `imp_<name>/`) and makes it impossible to add a second design variant without restructuring. The agent had no constraint telling it where to place code.
2. **GAP-044: No constraint enforcing multi-tenant folder structure** — The root cause of GAP-043. Nothing in `project_constraints.yml`, `CLAUDE.md`, or the `design→code` edge checklist specifies the `imp_<name>/` convention. Without this constraint, every run will default to root-level code.
3. **GAP-042: Installer doesn't scaffold `graph_topology.yml`** — Genesis Monitor can't see the project at all. The integration contract between methodology and monitor is broken at the installer level.
4. **GAP-021: Parallel generation type contract** — Still unfixed. test06 avoids it with single-design. Next test should try parallel gen with the v2.8 methodology to see if better event emission + iterate agent helps.

### High (reduces value significantly)

5. **GAP-011: Evaluator rigour** — Sequential build means test06 didn't stress-test evaluator convergence. Need a run that deliberately introduces errors to validate evaluators catch them.
6. **GAP-031/032: ADR carry-forward + minimum topics** — test06 improved from 5 to 7 ADRs with better topic diversity, but still missing 3 high-severity architectural decisions from test04. Formal carry-forward protocol needed.
7. **GAP-037: 16 REQ keys not in code** — 59/75 spec REQs traced to code (79%). The 16 missing keys need disposition (implemented but untagged, deferred, or N/A for NFR/regulatory).
8. **GAP-036: Code depth vs skeleton** — 1,578 lines is lean for 8 modules with 75 requirements. Compare functional completeness per module vs test04.
9. **GAP-040: Test depth** — 95 tests vs test04's 252. Sufficient for validation but may miss edge cases. Iterate TDD edge for more coverage.

### Medium (nice-to-have for methodology completeness)

7. **GAP-035: Mermaid diagrams** — Regressed from 13→0. Add to evaluator checklist.
8. **GAP-038: evaluator_overrides placeholder** — Template suggests feature that doesn't exist. Implement or remove.
9. **GAP-039: Profile discovery undocumented** — The full-auto profile mechanism works but isn't documented.
10. **GAP-041: REQ key format inconsistency** — Mixed formats in code. Add format validator.
11. **GAP-025: STATUS.md** — Still missing after 4 runs. Add to init scaffold.
12. **GAP-007: Checkpoint mechanism** — 5 runs, never used.
13. **GAP-012: Skip policy** — Lint/format checks still not run.

---

## 10. Run Comparison Summary

| | test02 | test03 | test04 | test05 | test06 |
|-|--------|--------|--------|--------|--------|
| **Strongest at** | Test coverage, ADR count, REQ density | Graph formalism | Observability, gap detection, TDD | Multi-design, parallel gen, design depth, technology honesty | **Build automation, observability, feature tracking, constraint binding, uninterrupted execution** |
| **Weakest at** | No formalism, no observability | No tests, no build | Design variants (only 1) | No tests, poor observability, ADR depth regression | **Code depth, Mermaid diagrams, ADR carry-forward, test count** |
| **Unique contribution** | Proved full pipeline works | Proved v2.1 formalism | Proved gap detection + TDD co-evolution | Proved multi-design + parallel gen + honest tech limitations | **Proved single-shot uninterrupted build; custom projection profiles; v2.8 per-edge event emission** |
| **Methodology lesson** | A complete pipeline beats a partial one | Formalism alone isn't enough | Observability + testing close the loop | Parallelism needs reconciliation; ADRs need carry-forward | **Pre-seeded spec + custom profile + sequential edges = cleanest build yet; but skeleton depth needs iteration** |

---

## 11. Composite Best-of-All-Runs (Hypothetical test07)

If we could combine the best of each run:

| Dimension | Best Run | Value |
|-----------|----------|-------|
| Methodology | test06 | v2.8 (IntentEngine, tolerances, functors) |
| Build automation | test06 | `/gen-start --auto` with full-auto profile |
| Observability | test06 | 13 events, all edges tracked |
| Test coverage | test04 | 252 tests, TDD + BDD |
| Code depth | test04 | 8,000 lines, 71 files |
| ADR depth | test04 | 13 ADRs, comprehensive |
| Multi-design | test05 | 3 variants, technology honesty |
| Design richness | test05 | 4,194 lines, 13 Mermaid diagrams |
| Traceability | test06 | 97% file coverage |
| Pre-seeded spec | test06 | Reduces ambiguity, clean builds |
| Feature tracking | test04/06 | All edges converged |

**test07 recipe**: v2.8 methodology + pre-seeded spec + full-auto profile + **`imp_<design>/` folder constraint** + `graph_topology.yml` in installer + iterate on TDD edge for depth + ADR carry-forward from test04 + Mermaid evaluator + multi-design (if parallel gen fixed).
