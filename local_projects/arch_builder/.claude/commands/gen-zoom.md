# /gen-zoom - Graph Edge Zoom

Zoom into or out of graph edges, revealing sub-structure within an edge traversal or aggregating across edges for a high-level view. The graph is zoomable (§1) — this command navigates zoom levels.

<!-- Implements: REQ-GRAPH-002 (Zoomable Graph), REQ-UX-007 (Edge Zoom Management) -->
<!-- Reference: AI_SDLC_ASSET_GRAPH_MODEL.md §1, §2.5 (Graph Scaling), ADR-009 (Graph Topology as Config) -->

## Usage

```
/gen-zoom [in|out|show] --edge "{source}→{target}" [--feature "REQ-F-*"] [--depth {n}]
```

| Option | Description |
|--------|-------------|
| `in` | Zoom into an edge — show sub-steps and internal structure |
| `out` | Zoom out — show aggregated view across multiple edges |
| `show` | Show current zoom level and available zoom targets (default) |
| `--edge` | The edge to zoom into/out of |
| `--feature` | Scope to a specific feature (optional) |
| `--depth` | How many levels to zoom (default: 1) |

## Instructions

### Step 1: Load Graph Context

1. Read `config/graph_topology.yml` — the current topology
2. Read the feature vector if `--feature` is provided
3. Read edge configuration from `config/edge_params/{edge}.yml`

### Step 2: Show Current Zoom Level (default / `show`)

Display the current graph topology with indicators of which edges have sub-structure:

```
═══ GRAPH ZOOM — Current View ═══

intent → requirements → design → code ↔ unit_tests → uat_tests → cicd → running_system → telemetry
                                                                                          ↓
                                                                                        intent (feedback)

Zoomable edges (have internal sub-structure):
  [+] intent→requirements     3 sub-steps: capture, structure, validate
  [+] requirements→design     4 sub-steps: ADR decisions, constraint resolution, architecture, design doc
  [+] design→code             3 sub-steps: scaffold, implement, integrate
  [+] code↔unit_tests         3 sub-steps: red, green, refactor (TDD cycle)
  [ ] uat_tests               1 step (flat)
  [ ] cicd                    1 step (flat)

Current feature zoom:
  REQ-F-AUTH-001: at design→code (sub-step: implement)
  REQ-F-API-001: at requirements (sub-step: validate)

═══════════════════════════
```

### Step 3: Zoom In

Reveal the internal structure of an edge. Each edge decomposes into sub-steps that are themselves mini-iterations:

#### intent→requirements

```
═══ ZOOM: intent→requirements ═══

Sub-steps:
  1. CAPTURE    — Extract requirements from intent (functional + non-functional)
                  Input: INTENT.md
                  Output: Draft requirements list
                  Evaluator: agent (completeness check)

  2. STRUCTURE  — Organise into REQ-F-* and REQ-NFR-* keys
                  Input: Draft requirements
                  Output: Structured requirements with IDs
                  Evaluator: deterministic (format check) + agent (gap check)

  3. VALIDATE   — Human confirms requirements capture intent faithfully
                  Input: Structured requirements
                  Output: Approved requirements
                  Evaluator: human (gradient check via /gen-spec-review)

{Feature zoom if --feature provided:}
  REQ-F-AUTH-001:
    [✓] capture    — 5 functional, 2 non-functional extracted
    [✓] structure  — REQ-F-AUTH-001, REQ-F-AUTH-002, REQ-NFR-SEC-001
    [●] validate   — pending human review

═══════════════════════════
```

#### requirements→design

```
═══ ZOOM: requirements→design ═══

Sub-steps:
  1. ADR_DECISIONS     — Make technology binding decisions
                         Output: ADR documents
                         Evaluator: human (design approval)

  2. CONSTRAINT_RESOLVE — Resolve mandatory constraint dimensions
                          Output: project_constraints.yml populated
                          Evaluator: deterministic (all mandatory fields filled)

  3. ARCHITECTURE       — Define module decomposition and integration points
                          Output: Architecture diagrams, module map
                          Evaluator: agent (coherence with requirements)

  4. DESIGN_DOC         — Produce consolidated design document
                          Output: Design document with REQ key traceability
                          Evaluator: agent + human (gradient check)

═══════════════════════════
```

#### design→code

```
═══ ZOOM: design→code ═══

Sub-steps:
  1. SCAFFOLD    — Create file structure, module boundaries, build config
                   Evaluator: deterministic (builds, lints)

  2. IMPLEMENT   — Write production code with Implements: REQ-* tags
                   Evaluator: deterministic (compile, lint) + agent (design alignment)

  3. INTEGRATE   — Wire modules together, verify cross-module contracts
                   Evaluator: deterministic (integration tests) + agent (architecture check)

═══════════════════════════
```

#### code↔unit_tests (TDD cycle)

```
═══ ZOOM: code↔unit_tests (TDD co-evolution) ═══

Sub-steps (cyclical):
  1. RED       — Write failing test for next requirement
                 Validates: REQ-* tag in test
                 Evaluator: deterministic (test fails as expected)

  2. GREEN     — Write minimal code to pass the test
                 Implements: REQ-* tag in code
                 Evaluator: deterministic (test passes)

  3. REFACTOR  — Improve code structure without changing behaviour
                 Evaluator: deterministic (all tests still pass) + agent (structural quality)

  Cycle repeats until all REQ keys for this feature have tests.

═══════════════════════════
```

### Step 4: Zoom Out

Aggregate multiple edges into a higher-level view:

```
═══ ZOOM OUT — Feature-Level View ═══

REQ-F-AUTH-001 "User authentication"
  Specification:  ████████████████████ 100% (intent + req + design converged)
  Construction:   █████████░░░░░░░░░░░  45% (code in progress, tests started)
  Validation:     ░░░░░░░░░░░░░░░░░░░░   0% (UAT + CI/CD pending)
  Operations:     ░░░░░░░░░░░░░░░░░░░░   0% (telemetry pending)

REQ-F-DB-001 "Database schema"
  Specification:  ████████████████████ 100%
  Construction:   ████████████████████ 100%
  Validation:     ██████████░░░░░░░░░░  50%
  Operations:     ░░░░░░░░░░░░░░░░░░░░   0%

═══════════════════════════
```

The four aggregation bands map to graph regions:
- **Specification**: intent + requirements + design
- **Construction**: code + unit_tests
- **Validation**: uat_tests + cicd
- **Operations**: running_system + telemetry

### Step 5: Graph Discovery Connection (§2.5)

When zooming in reveals that an edge's sub-structure is consistently too complex (many sub-steps, high iteration counts), this is a signal for graph discovery — the edge may need to be split into separate edges in the topology.

If zoom-in reveals:
- More than 5 sub-steps within a single edge
- Sub-steps that are independently convergence-tested
- Sub-steps that have different evaluator compositions

Then surface a recommendation:

```
Graph Discovery Signal:
  Edge design→code has 5 sub-steps with independent convergence.
  Consider splitting into: design→scaffold, scaffold→implement, implement→integrate
  Run /gen-escalate to capture as intent if warranted.
```

This is the tolerance pressure (ADR-016) operating on graph topology complexity.

## Event Emission

Zoom is a **read-only view** — it does not emit events or modify workspace state. It is F_D(Route) — a deterministic projection of the existing graph state.

Exception: If zoom reveals a graph discovery signal (Step 5), it surfaces the signal but does NOT emit an intent. The human decides whether to escalate via `/gen-escalate`.
