# Gap Analysis: v2.1 (test03) vs v1.x (test02)

**Date**: 2026-02-20
**Purpose**: Compare CDME built with AI SDLC v2.1 (test03) against existing v1.x build (test02) to identify methodology gaps.

---

## 1. Quantitative Comparison

| Metric | test02 (v1.x) | test03 (v2.1) | Delta |
|--------|--------------|--------------|-------|
| **Intent files** | 2 | 1 | v2.1 consolidates into `docs/specification/` |
| **Requirements docs** | 3 (mapper_requirements + AISDLC_IMPLEMENTATION_REQUIREMENTS + appendix) | 1 (REQUIREMENTS.md) | v2.1 single doc with all REQ keys |
| **REQ keys** | ~50+ (REQ-LDM-01 format) | 43 (REQ-F-LDM-001 format) | v2.1 adds type prefix, better structured |
| **Design variants** | 3 (generic, spark, dbt) | 1 (cdme/Spark) | v2.1 supports this via Context[] parameterisation — run same edge with different context |
| **Design docs** | 5 (2 spark, 1 generic, 1 dbt, 2 early) | 1 | Agent error: should have iterated edge N times with N contexts |
| **ADRs** | ~29 across all variants | 5 | **GAP**: significantly fewer ADRs |
| **Source files (main)** | 14 Scala files | 14 Scala files | Comparable |
| **Test files** | 18+ (unit, UAT, data generators) | 0 | **GAP**: v2.1 didn't reach TDD edge |
| **Test data** | ~30+ dataset files (airline, tax agent) | 0 | **GAP**: no test data generation |
| **Traceability matrix** | 1 standalone doc | Embedded in design | v2.1 embeds in component traceability table |
| **Feature vectors** | 0 | 1 | v2.1 advantage: trajectory tracking |
| **Graph topology** | 0 (implicit) | 1 YAML + 10 edge configs | v2.1 advantage: explicit graph |
| **Projection profiles** | 0 | 6 | v2.1 advantage: named profiles |
| **Workspace config** | templates, context_history, config | graph, edges, profiles, features, context | Structurally different |

---

## 2. Structural Gaps (What v2.1 Missed)

### ~~GAP-001~~: Multiple Design Variants — NOT A GAP

**test02 produced**: 3 design variants — generic reference architecture, Spark-specific, and dbt-specific. Each had its own ADRs.

**test03 produced**: 1 design variant (Scala/Spark).

**Root cause**: This is NOT a methodology gap. Multi-variance is inherent in `iterate(Asset, Context[], Evaluators) → Asset'`. The same `requirements→design` edge run with different Context[] naturally produces different designs:

```
iterate(requirements, Context[Spark ADRs, Scala constraints], Evaluators) → design_spark
iterate(requirements, Context[dbt ADRs, SQL constraints], Evaluators)    → design_dbt
iterate(requirements, Context[generic, no framework], Evaluators)        → design_generic
```

Same source asset, same evaluators, different context = different output. This is what parameterised iteration means. The agent simply failed to recognise it should run the edge multiple times with different context bindings.

**Status**: Reclassified as agent execution error, not methodology gap. The graph already supports this — it's the defining feature of `iterate()`.

### GAP-002: ADR Depth and Coverage

**test02 produced**: ~29 ADRs covering granular decisions (Scala aggregation patterns, config parsing, Spark 4.0 migration, error-lineage integration, immutable run hierarchy, cross-domain fidelity, data quality monitoring).

**test03 produced**: 5 ADRs covering high-level decisions (adjoint vs dagger, schema registry, either monad, execution engine, language choice).

**Root cause**: The `requirements_design.yml` checklist has `adrs_for_decisions` as a single agent check — "ADRs exist for every significant decision." The agent produces enough ADRs to pass the check, but doesn't drill into implementation-level decisions. test02's ADRs came from iterating the design through code (design→code→tests feedback loop).

**Recommendation**: ADR depth naturally increases during the TDD co-evolution edge (code↔unit_tests). The gap will narrow as test03 progresses through more edges. Consider adding a `min_adr_count` threshold or ADR-per-component guideline to the design edge config.

### GAP-003: No Tests (TDD Edge Not Traversed)

**test02 produced**: 18+ test files including unit tests, UAT scenarios, data generators, airline system specs, tax agent system specs.

**test03 produced**: 0 test files.

**Root cause**: We stopped at the `design→code` edge. The `code↔unit_tests` TDD co-evolution edge and `design→test_cases`/`design→uat_tests` edges were not traversed.

**Recommendation**: This is not a methodology gap — the edges exist. Continue traversal to `code↔unit_tests`, `design→test_cases`, and `design→uat_tests` to close this gap.

### GAP-004: No Test Data Generation

**test02 produced**: Rich test data generators (AirlineDataGenerator, ErrorInjector, TaxAgentDataGenerator) with ~30+ dataset files.

**test03 produced**: Nothing.

**Root cause**: Test data generation is part of the TDD co-evolution, not explicitly called out in any edge config. The iterate agent doesn't have a specific checklist item for "test data generation strategy."

**Recommendation**: Add a checklist item to the `tdd.yml` edge: `test_data_strategy` — "Tests use representative data, either generated or fixture-based. Data generators are tagged with REQ keys they exercise."

### GAP-005: No Appendices / Supplementary Specs

**test02 produced**: `APPENDIX_A_FROBENIUS_ALGEBRAS.md` — a deep theoretical analysis.

**test03 produced**: INT-006 references Frobenius but defers to design. No appendix was generated.

**Root cause**: The `intent→requirements` edge config doesn't have a mechanism for supplementary/appendix documents. The agent generates the main requirements doc but doesn't create supporting appendices for speculative/exploratory content.

**Recommendation**: This maps naturally to v2.1's **spawn** mechanism. INT-006 (Frobenius) should spawn a Discovery vector: `/aisdlc-spawn --type discovery --parent REQ-F-CDME-001 --reason "Frobenius algebra feasibility"`. The discovery vector produces the appendix and folds back.

### GAP-006: No CLAUDE.md / Project Guide

**test02 produced**: `CLAUDE.md` (6.7KB project guide for Claude Code).

**test03 produced**: No CLAUDE.md.

**Root cause**: CLAUDE.md generation is not part of any edge config. It's a project convention, not a methodology artifact.

**Recommendation**: Add to `/aisdlc-init` Step 8: generate a project CLAUDE.md that summarises the workspace structure, active features, and development workflow. Or create a separate `/aisdlc-guide` command.

### GAP-007: No Context History

**test02 produced**: `.ai-workspace/context_history/20251216_1530_spark_mvp_implementation.md` — session checkpoints.

**test03 produced**: `.ai-workspace/snapshots/` directory exists but is empty.

**Root cause**: The v2.1 methodology has the `snapshots/` directory and context manifest concept, but no command actually creates snapshots during iteration. The planned `/aisdlc-checkpoint` command doesn't exist yet.

**Recommendation**: Implement the `/aisdlc-checkpoint` command, or add automatic snapshotting to the iterate agent after each convergence.

---

## 3. Structural Advantages (What v2.1 Did Better)

### ADV-001: Explicit Graph Topology

v2.1 has `graph_topology.yml` with 10 asset types and 10 transitions — making the methodology machine-readable. v1.x had implicit stages.

### ADV-002: Feature Vector Tracking

v2.1 tracks each feature's trajectory through the graph with iteration counts, evaluator results, and context hashes. v1.x had no equivalent.

### ADV-003: Projection Profiles

v2.1 supports 6 named profiles (full, standard, poc, spike, hotfix, minimal) that control graph subset, evaluator composition, and convergence criteria. v1.x was one-size-fits-all.

### ADV-004: Formal REQ Key Format

v2.1 uses `REQ-{TYPE}-{DOMAIN}-{SEQ}` with explicit type classification (F, NFR, DATA, BR). v1.x used simpler `REQ-{DOMAIN}-{SEQ}` without type prefix.

### ADV-005: Edge Parameterisation with Checklists

v2.1 has concrete, countable evaluator checklists per edge with $variable resolution from project constraints. v1.x relied on the methodology reference document and human interpretation.

### ADV-006: Spec/Design Separation

v2.1 explicitly separates `docs/specification/` (WHAT, tech-agnostic) from `docs/design/` (HOW, tech-bound). v1.x mixed them in `docs/requirements/`.

### ADV-007: Spawn/Fold-Back Mechanism

v2.1 can spawn discovery, spike, PoC, and hotfix child vectors with fold-back. v1.x had no equivalent for handling uncertainty during iteration.

### ADV-008: Traceability as Formal Evaluator

v2.1 has 3-layer traceability checks composed into edge configs. v1.x had a standalone traceability matrix without formal evaluation.

---

## 4. Quality Comparison (Same Edge Output)

### Intent (Same input — identical)

Both use the same CDME intent. v2.1 format adds explicit methodology version tag.

### Requirements

| Aspect | test02 | test03 | Winner |
|--------|--------|--------|--------|
| REQ key format | `REQ-LDM-01` | `REQ-F-LDM-001` | test03 (type prefix, zero-padded) |
| Acceptance criteria | Present | Present, more structured | test03 |
| Intent tracing | Implicit in section structure | Explicit `Traces To: INT-*` per REQ | test03 |
| Appendices | Yes (Frobenius) | No | test02 |
| Appendix A (Implementation Constraints) | Yes (lineage modes, skew, circuit breaker) | Incorporated into main REQ keys | Draw |
| Section 5 (Architecture) | Detailed abstract machine | Not in requirements (deferred to design) | test02 (richer spec) |
| Terminology dictionary | Yes (Section 4) | No | test02 |
| Mermaid diagrams | 4+ diagrams | 0 | test02 |
| Success criteria | 13 detailed criteria (Section 9) | Summary table only | test02 |

**Verdict**: test02 has a richer, more complete requirements document. The v1.x requirements were the input spec written by a human expert. The v2.1 requirements were generated from intent by an agent — they correctly capture the functional requirements but lose the pedagogical structure (terminology dictionary, diagrams, architecture section, success criteria).

### Design

| Aspect | test02 | test03 | Winner |
|--------|--------|--------|--------|
| Component count | ~10 | 12 | test03 (more explicit) |
| ADR count | 11 (generic) + 13 (spark) + 5 (dbt) | 5 | test02 |
| Multiple variants | 3 (generic, spark, dbt) | 1 | test02 |
| Traceability table | Separate doc | Embedded in design | test03 (co-located) |
| Data models | In design doc | In design doc | Draw |
| Package structure | Defined in ADR-009 | In design doc | Draw |
| Mermaid diagrams | Multiple | None | test02 |

### Code

| Aspect | test02 | test03 | Winner |
|--------|--------|--------|--------|
| Source file count | 14 | 14 | Draw |
| REQ tags in code | Yes (via `// Implements: REQ-*`) | Yes (`// Implements: REQ-*`) | Draw |
| Test files | 18+ | 0 | test02 |
| Test data generators | 4+ | 0 | test02 |
| Config system | YAML config loader + model | Not yet | test02 |
| Main entry point | Yes (Main.scala) | No | test02 |
| SchemaRegistry | Yes (separate component) | No (in topology model) | test02 |
| Specific morphism impls | AggregateMorphism, FilterMorphism | Abstract interfaces | test02 |

---

## 5. Methodology Gaps Summary

| ID | Gap | Severity | Fix |
|----|-----|----------|-----|
| ~~GAP-001~~ | ~~No multi-variant design~~ — **NOT A GAP** | N/A | `iterate()` with different Context[] already produces variants. Agent execution error. |
| GAP-002 | Shallow ADR generation | Low | Naturally deepens during TDD; consider min count |
| GAP-003 | No tests (edge not traversed) | Expected | Continue to TDD edge |
| GAP-004 | No test data generation guidance | Low | Add checklist item to tdd.yml |
| GAP-005 | No appendix/supplementary doc support | Low | Use spawn/discovery for exploratory content |
| GAP-006 | No CLAUDE.md generation | Low | Add to /aisdlc-init |
| GAP-007 | No checkpoint/snapshot execution | Medium | Implement /aisdlc-checkpoint |
| GAP-008 | No Mermaid diagrams in generated docs | Low | Add agent guidance for diagrams |
| GAP-009 | Requirements lose pedagogical structure | Medium | Enhance intent→requirements agent guidance with sections for terminology, success criteria, diagrams |
| GAP-010 | Feature vector not updated during traversal | Low | Automate feature vector update at each step |

---

## 6. Conclusion

**v2.1 methodology is structurally sound** for the edges it traverses. The formal graph topology, edge parameterisation, evaluator checklists, and feature vector tracking are genuine improvements over v1.x.

**Multi-variance is not a gap — it's a core feature**: `iterate(Asset, Context[], Evaluators) → Asset'` naturally produces different outputs when Context[] differs. Running `requirements→design` with {Spark context} vs {dbt context} vs {generic context} produces 3 design variants through the same edge. This was an agent execution failure (running the edge once instead of N times), not a methodology gap. The graph model's separation of Asset from Context[] is precisely what makes this possible.

**Key gaps are in depth, not structure**:
- v1.x produced richer artifacts because it iterated through more edges (including TDD) and had human expert input shaping the requirements
- v2.1 produced correctly structured but shallower artifacts in fewer passes
- All remaining gaps are low severity, relating to agent guidance quality rather than methodology structure

**What v2.1 adds over v1.x**:
- Machine-readable methodology (YAML graph, edge configs, profiles)
- Formal evaluation (countable checklists, not human judgment)
- Feature trajectory tracking (knows where each feature is in the graph)
- Projection profiles (adapt methodology to project needs)
- Spawn/fold-back (handle uncertainty during iteration)
- Context[] parameterisation enabling multi-variant design through the same graph edge

**Recommendation**: The 9 genuine gaps are all low-to-medium severity and relate to agent guidance rather than methodology structure. The most impactful fix is GAP-009 (requirements depth/pedagogical structure).
