# Genesis Monitor — Intent

**Version**: 3.0.0
**Status**: Draft
**Date**: 2026-02-21
**Asset Type**: Intent

---

## INT-GMON-001: Real-Time Methodology Dashboard

### Problem

The AI SDLC v2.5 specification describes methodology self-observation (§7.6), the consciousness loop (§7.7), and the living system (§7.7.6) — but no tooling exists to make these observable. All visibility is currently:

- **Offline**: STATUS.md snapshots generated at session boundaries, never between sessions
- **Per-session**: No cross-project view; each Claude Code session sees only its own `.ai-workspace/`
- **Non-temporal**: No trend analysis, no convergence velocity, no iteration count over time
- **Silent on TELEM**: TELEM signals (§7.6) are specified but nothing aggregates or displays them
- **Open-loop**: The consciousness loop (observe → orient → decide → act) has no continuous observation instrument

### Expected Outcomes

| ID | Outcome | Measures |
|----|---------|----------|
| OUT-001 | Multi-project discovery | Scan filesystem for `.ai-workspace/` directories, present unified index |
| OUT-002 | Event stream visualization | Real-time feed of asset creation/update events across all monitored projects |
| OUT-003 | Feature vector tracking | Display feature vectors (REQ keys) with per-edge convergence status |
| OUT-004 | Asset graph visualization | Render each project's asset graph with node states (converged/iterating/blocked) |
| OUT-005 | Convergence dashboard | Per-edge iteration count, evaluator pass/fail history, convergence velocity |
| OUT-006 | TELEM aggregation | Collect and display TELEM signals from project telemetry artifacts |
| OUT-007 | Gantt schedule view | Timeline of asset creation and edge transitions, showing methodology execution cadence |
| OUT-008 | Filesystem watching | Live detection of `.ai-workspace/` changes via OS-level file watching |

---

## INT-GMON-002: Dogfood — Build Using the Methodology

### Problem

The AI SDLC methodology needs a non-trivial project built edge-by-edge using its own process, with the methodology execution itself being observable during development.

### Expected Outcomes

| ID | Outcome | Measures |
|----|---------|----------|
| OUT-009 | Edge-by-edge construction | Project proceeds intent → requirements → design → code → tests → UAT using AI SDLC |
| OUT-010 | Self-monitoring during development | The monitor's own `.ai-workspace/` is a monitored project during build |
| OUT-011 | Consciousness loop exemplar | The tool observes methodology execution while being methodology execution — the loop closes |

### Rationale

This is test06 in the ai_sdlc_examples dogfood series. The monitor is uniquely suited to dogfooding because it can observe its own construction process. This creates a concrete instance of the consciousness loop described in v2.5 §7.7.

---

## INT-GMON-003: Technology — FastAPI + HTMX + SSE

### Problem

The dashboard must be real-time, lightweight, and avoid heavy JavaScript framework dependencies. It must render methodology diagrams (Mermaid) in the browser and support partial page updates without full reloads.

### Expected Outcomes

| ID | Outcome | Measures |
|----|---------|----------|
| OUT-012 | FastAPI backend | Lightweight async Python server; parsers and models in Python |
| OUT-013 | HTMX partial updates | No JavaScript framework; HTMX handles DOM swaps from server-rendered HTML fragments |
| OUT-014 | SSE real-time push | Server-Sent Events driven by watchdog filesystem events — no polling |
| OUT-015 | Mermaid rendering | Asset graph diagrams rendered client-side via Mermaid.js CDN include |
| OUT-016 | Zero build step | No webpack, no npm, no transpilation — HTML templates + CDN scripts |

### Rationale

- **FastAPI**: async-native, minimal boilerplate, excellent for SSE streams
- **HTMX**: server-rendered HTML fragments eliminate SPA complexity; 14KB library
- **SSE over WebSocket**: unidirectional push is sufficient; SSE is simpler, auto-reconnects, works through proxies
- **watchdog**: OS-level filesystem events (inotify/kqueue/FSEvents) — no polling, instant detection
- **No build step**: HTML templates with Jinja2 + CDN includes for Mermaid.js and HTMX — deploy is `uvicorn`

---

## INT-GMON-004: Align with Genesis v2.5 Asset Graph Model

### Problem

The genesis monitor was built against v2.1 of the Asset Graph Model specification. The spec has since evolved to v2.5, introducing structural concepts that the monitor cannot observe:

- **Two processing regimes** (§4.3) — conscious (deliberative) vs reflex (autonomic). The monitor cannot distinguish or verify that reflexes fire unconditionally.
- **Consciousness loop** (§7.7) — intent causal chains (`prior_intents`), spec modification events (`spec_modified`), three-phase self-observation. The monitor has no awareness of these event types.
- **Constraint dimensions** (§2.6.1) — mandatory disambiguation categories at the design edge (ecosystem, security, performance, etc.). The monitor doesn't parse or validate dimension coverage.
- **Projection profiles** (§7, PROJECTIONS_AND_INVARIANTS.md) — named profiles (full, standard, poc, spike, hotfix, minimal) that parameterise graph, evaluators, convergence, and context density. The monitor has no concept of profiles.
- **Vector spawning and fold-back** (§5, PROJECTIONS_AND_INVARIANTS.md) — parent/child vector relationships, spawn triggers, fold-back of results from child to parent. The monitor tracks vectors in isolation with no relationships.
- **Time-boxing** (§6, PROJECTIONS_AND_INVARIANTS.md) — discovery/spike/PoC vectors have deadlines and check-in cadences. The monitor cannot distinguish "converged because done" from "converged because timed out".
- **Structured event schemas** (§7.5.1) — 9 typed event schemas with specific fields. The monitor uses a generic `Event(data: dict)` with no schema validation.
- **Protocol enforcement hooks** (§7.8) — mandatory side effects (event emission, feature vector update, STATUS regeneration). The monitor cannot verify hook compliance.

### Expected Outcomes

| ID | Outcome | Measures |
|----|---------|----------|
| OUT-017 | Reflex/conscious regime display | Dashboard shows which evaluators are reflex vs conscious per edge; verifies reflex completeness |
| OUT-018 | Consciousness loop tracking | Parse `intent_raised` events with `prior_intents` causal chains; parse `spec_modified` events; display intent lineage |
| OUT-019 | Constraint dimension coverage | Parse constraint dimensions from topology; validate design resolves all mandatory dimensions; display coverage matrix |
| OUT-020 | Projection profile awareness | Parse active profile per vector; validate vector constraints match profile; display profile in feature view |
| OUT-021 | Vector relationship graph | Track parent/child spawn relationships; display spawn tree; show fold-back state |
| OUT-022 | Time-box tracking | Display deadline, check-in cadence, time remaining for time-boxed vectors |
| OUT-023 | Structured event parsing | Schema-validate events against 9 typed schemas; extract structured fields for cross-event analysis |
| OUT-024 | Protocol compliance view | Verify that reflex side-effects (events, feature vector, STATUS) were emitted at each iteration |

### Rationale

The monitor's value is proportional to its fidelity to the spec it observes. A v2.1 monitor observing v2.5 projects is structurally blind to the new concepts — it sees data it cannot interpret. This creates a false sense of observability: the dashboard appears complete but misses the processing regimes, consciousness loop, and vector relationships that define v2.5.

This is itself a telemetry signal (TELEM-004): the methodology evolved but its observer did not, creating a drift between spec and runtime.

---

## INT-GMON-005: Align with Genesis v2.8/v3.0 Asset Graph Model

### Problem

The monitor was aligned to v2.5 but the Asset Graph Model has evolved to v2.8/v3.0, introducing:

- **Functor encoding** (§4.4) — mode/valence/active_units encoding per feature vector. The monitor cannot display functor state.
- **Sensory system** (§4.6.7) — interoceptive (self-monitoring) and exteroceptive (environment-monitoring) signals with affect triage. The monitor has no sensory dashboard.
- **IntentEngine classification** (§4.6) — typed outputs: reflex.log, specEventLog, escalate. The monitor cannot classify intent engine output.
- **Multi-agent coordination** (ADR-013) — claim/release/expiry events for edge ownership. The monitor ignores agent coordination events.
- **Constraint tolerances** (§4.6.9) — every constraint needs a measurable threshold and breach status. The monitor does not display tolerances.
- **Edge timestamps** — started_at/converged_at/duration/convergence_type fields on edge trajectories. The monitor lacks temporal edge data.
- **22 event types** — expanded from 9 to 22 typed event schemas. The monitor only recognises 9.

### Expected Outcomes

| ID | Outcome | Measures |
|----|---------|----------|
| OUT-025 | Functor encoding display | Parse and display encoding block (mode/valence/active_units) per feature vector |
| OUT-026 | Sensory system dashboard | Parse interoceptive/exteroceptive/affect_triage events; display sensory signal feed |
| OUT-027 | IntentEngine classification | Classify events by IntentEngine output type (reflex.log, specEventLog, escalate) |
| OUT-028 | Multi-agent coordination events | Parse claim_rejected, edge_released, claim_expired events; display agent coordination |
| OUT-029 | Constraint tolerance display | Parse tolerance thresholds and breach status per constraint dimension |
| OUT-030 | Edge timestamp tracking | Parse started_at/converged_at/duration/convergence_type from edge trajectory data |
| OUT-031 | Convergence type classification | Display convergence type (delta_zero, timeout, escalated) per edge |
| OUT-032 | Backward compatibility | v2.5 workspaces still parse correctly; all new fields default to None/empty |

### Rationale

The v2.8/v3.0 spec formalises the IntentEngine as a composition law over the four primitives, introduces constraint tolerances for homeostasis, and expands the event vocabulary to 22 types covering sensory, multi-agent, and lifecycle events. A v2.5 monitor observing v2.8 projects misses these concepts entirely.

---

## Constraints

- **Read-only**: The monitor MUST NOT write to any target project's `.ai-workspace/`. It is a pure observer.
- **Python 3.12+**: Leverage modern Python features (type hints, match statements, tomllib).
- **Single process**: No external databases, no message queues. All state derived from filesystem on startup; watchdog for incremental updates.

---

## Traceability

This document is the root asset. All subsequent requirements MUST trace back to INT-GMON-001, INT-GMON-002, or INT-GMON-003 via REQ keys.

```
INT-GMON-001 → REQ-GMON-* (dashboard requirements)
INT-GMON-002 → REQ-GMON-* (dogfood requirements)
INT-GMON-003 → REQ-GMON-* (technology requirements)
INT-GMON-004 → REQ-GMON-* (v2.5 alignment requirements)
INT-GMON-005 → REQ-GMON-* (v2.8/v3.0 alignment requirements)
```

--- 

## INT-GMON-006: Executive Presentation & Drill-Down

### Problem
The current dashboard lacks intuitive explanations for data derivation and aesthetic polish required for executive stakeholders. The Gantt chart is compressed and non-interactive, and the lineage from dashboard metrics back to underlying data is not visible.

### Expected Outcomes

| ID | Outcome | Measures |
|----|---------|----------|
| OUT-033 | Explanatory Hovers | "?" icons with hover text explaining data derivation and sources |
| OUT-034 | Real-Time Status | "Last Updated" timestamps on all data fragments |
| OUT-035 | Presentation UX | Executive-grade CSS styling and layout improvements |
| OUT-036 | Interactive Gantt | Clickable Gantt bars that drill down into edge details |
| OUT-037 | Data Lineage | "Explain this" view showing the raw underlying artifact data |

--- 

## INT-GMON-006: Executive Presentation & Drill-Down

### Problem
The current dashboard lacks intuitive explanations for data derivation and aesthetic polish required for executive stakeholders. The Gantt chart is compressed and non-interactive, and the lineage from dashboard metrics back to underlying data is not visible.

### Expected Outcomes

| ID | Outcome | Measures |
|----|---------|----------|
| OUT-033 | Explanatory Hovers | "?" icons with hover text explaining data derivation and sources |
| OUT-034 | Real-Time Status | "Last Updated" timestamps on all data fragments |
| OUT-035 | Presentation UX | Executive-grade CSS styling and layout improvements |
| OUT-036 | Interactive Gantt | Clickable Gantt bars that drill down into edge details |
| OUT-037 | Data Lineage | "Explain this" view showing the raw underlying artifact data |

--- 

## INT-GMON-007: Interactive Temporal Navigator

### Problem
The dashboard is currently locked to the "now" state. Understanding system evolution requires the ability to scan backwards through the event log, zoom into specific periods of high activity, and observe the state of the graph as it existed at any historical timestamp.

### Expected Outcomes

| ID | Outcome | Measures |
|----|---------|----------|
| OUT-038 | Temporal Scrubber | A UI slider to navigate the entire history of the events.jsonl |
| OUT-039 | Zoomable Execution View | Variable-scale visualization of event density over time |
| OUT-040 | State Reconstruction | Dashboard fragments update to reflect state at selected timestamp T |
| OUT-041 | Causal Event Tracing | Visual links between historical events (e.g. intent → spawn → convergence) |

--- 

## INT-GMON-007: Interactive Temporal Navigator

### Problem
The dashboard is currently locked to the "now" state. Understanding system evolution requires the ability to scan backwards through the event log, zoom into specific periods of high activity, and observe the state of the graph as it existed at any historical timestamp.

### Expected Outcomes

| ID | Outcome | Measures |
|----|---------|----------|
| OUT-038 | Temporal Scrubber | A UI slider to navigate the entire history of the events.jsonl |
| OUT-039 | Zoomable Execution View | Variable-scale visualization of event density over time |
| OUT-040 | State Reconstruction | Dashboard fragments update to reflect state at selected timestamp T |
| OUT-041 | Causal Event Tracing | Visual links between historical events (e.g. intent → spawn → convergence) |

--- 

## INT-GMON-008: Static Global Control Plane & Zoomable Scrubber

### Problem
The temporal controls are currently lost when scrolling, and the single-point slider does not allow for viewing a window of time or understanding where activity is clustered. Executives need a persistent control plane that allows them to "zoom in" on specific periods of intense methodology execution.

### Expected Outcomes

| ID | Outcome | Measures |
|----|---------|----------|
| OUT-042 | Fixed Global Footer | Scrubber and versioning are persistent at the bottom of the viewport |
| OUT-043 | Dual-Handle Zoom | Ability to select a Start and End timestamp to define a view period |
| OUT-044 | Activity Clustering | Visual markers or heatmap on the slider showing event density |
| OUT-045 | Contextual Metadata | Footer displays event count range and total duration |

--- 

## INT-GMON-008: Static Global Control Plane & Zoomable Scrubber

### Problem
The temporal controls are currently lost when scrolling, and the single-point slider does not allow for viewing a window of time or understanding where activity is clustered. Executives need a persistent control plane that allows them to "zoom in" on specific periods of intense methodology execution.

### Expected Outcomes

| ID | Outcome | Measures |
|----|---------|----------|
| OUT-042 | Fixed Global Footer | Scrubber and versioning are persistent at the bottom of the viewport |
| OUT-043 | Dual-Handle Zoom | Ability to select a Start and End timestamp to define a view period |
| OUT-044 | Activity Clustering | Visual markers or heatmap on the slider showing event density |
| OUT-045 | Contextual Metadata | Footer displays event count range and total duration |
