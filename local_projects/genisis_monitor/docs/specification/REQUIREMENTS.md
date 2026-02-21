# Genesis Monitor — Requirements Specification

**Version**: 2.0.0
**Date**: 2026-02-21
**Status**: Draft — iterate(intent→requirements) for INT-GMON-004
**Feature**: REQ-F-GMON-001, REQ-F-GMON-002
**Source Asset**: docs/specification/INTENT.md v2.0.0 (4 intent items, 24 outcomes)
**Methodology**: AI SDLC Asset Graph Model v2.5

---

## 1. Overview

### 1.1 System Purpose

Genesis Monitor is a real-time web dashboard that observes AI SDLC methodology execution across multiple projects by consuming `.ai-workspace/` filesystem data and presenting it through a browser-based interface.

### 1.2 Scope Boundaries

**In scope**: Reading `.ai-workspace/` data, parsing methodology artifacts, rendering dashboards, real-time filesystem watching, SSE push, HTMX partial updates.

**Out of scope**: Modifying target project data, running evaluators, triggering iterate operations, user authentication, persistent storage beyond filesystem.

### 1.3 Relationship to Intent

| Requirement Prefix | Traces To |
|-------------------|-----------|
| REQ-F-DISC-* | INT-GMON-001 (Discovery) |
| REQ-F-PARSE-* | INT-GMON-001 (Parsing) |
| REQ-F-DASH-* | INT-GMON-001 (Dashboard views) |
| REQ-F-STREAM-* | INT-GMON-001 (Real-time events) |
| REQ-F-WATCH-* | INT-GMON-001 (Filesystem watching) |
| REQ-F-TELEM-* | INT-GMON-001 (Telemetry aggregation) |
| REQ-NFR-* | INT-GMON-003 (Technology constraints) |
| REQ-F-DOG-* | INT-GMON-002 (Dogfood requirements) |
| REQ-F-REGIME-* | INT-GMON-004 (Processing regimes) |
| REQ-F-CONSC-* | INT-GMON-004 (Consciousness loop) |
| REQ-F-CDIM-* | INT-GMON-004 (Constraint dimensions) |
| REQ-F-PROF-* | INT-GMON-004 (Projection profiles) |
| REQ-F-VREL-* | INT-GMON-004 (Vector relationships) |
| REQ-F-TBOX-* | INT-GMON-004 (Time-boxing) |
| REQ-F-EVSCHEMA-* | INT-GMON-004 (Structured events) |
| REQ-F-PROTO-* | INT-GMON-004 (Protocol compliance) |

### 1.4 Target Implementation

- **Language**: Python 3.12+
- **Framework**: FastAPI + HTMX + SSE
- **Deployment**: Single-process uvicorn server

---

## 2. Terminology

| Term | Definition |
|------|-----------|
| **Asset** | A versioned artifact in the AI SDLC graph (intent, requirements, design, code, tests) |
| **Edge** | A transition between two asset types (e.g., intent→requirements) |
| **Feature Vector** | A tracked feature (REQ key) with per-edge convergence trajectory |
| **Convergence** | An edge has converged when all evaluators pass |
| **TELEM Signal** | A self-reflection observation recorded during methodology execution |
| **Projection** | A derived view of the asset graph (Gantt, convergence dashboard, feature matrix) |
| **Workspace** | A `.ai-workspace/` directory containing methodology metadata for a project |

---

## 3. Project Discovery

### REQ-F-DISC-001: Workspace Scanning

**Priority**: Critical
**Traces To**: INT-GMON-001 / OUT-001

The system MUST scan one or more configured root directories recursively to discover all directories containing a `.ai-workspace/` subdirectory.

**Acceptance Criteria**:
- AC-1: Given a root path, the scanner finds all `.ai-workspace/` directories at any depth
- AC-2: Results include the project path and basic metadata (name from STATUS.md or directory name)
- AC-3: Scanning completes within 5 seconds for a tree of up to 1000 directories

### REQ-F-DISC-002: Project Registry

**Priority**: Critical
**Traces To**: INT-GMON-001 / OUT-001

The system MUST maintain an in-memory registry of discovered projects, updated when workspaces are added or removed from the filesystem.

**Acceptance Criteria**:
- AC-1: Registry is populated on startup from initial scan
- AC-2: New workspaces detected by the watcher are added to the registry
- AC-3: Removed workspaces are pruned from the registry

### REQ-F-DISC-003: Configuration of Watch Roots

**Priority**: High
**Traces To**: INT-GMON-001 / OUT-001

The system MUST accept one or more root directories to monitor, configurable via command-line arguments or a YAML config file.

**Acceptance Criteria**:
- AC-1: CLI argument `--watch-dir` accepts one or more paths
- AC-2: Config file `config.yml` can specify `watch_dirs: [...]`
- AC-3: CLI arguments override config file values

---

## 4. Parsing

### REQ-F-PARSE-001: STATUS.md Parser

**Priority**: Critical
**Traces To**: INT-GMON-001 / OUT-002, OUT-005

The system MUST parse a project's `.ai-workspace/STATUS.md` to extract:
- Project name and version
- Phase completion summary (edge → status, iterations, evaluator results)
- Aggregate metrics
- TELEM signals

**Acceptance Criteria**:
- AC-1: Parser returns a structured model from valid STATUS.md content
- AC-2: Parser handles missing or incomplete STATUS.md gracefully (returns partial data)
- AC-3: Parser extracts the Gantt chart section as raw Mermaid text

### REQ-F-PARSE-002: Feature Vector Parser

**Priority**: Critical
**Traces To**: INT-GMON-001 / OUT-003

The system MUST parse `.ai-workspace/features/active/*.yml` files to extract feature vector data including:
- Feature ID, title, status
- Per-edge trajectory (status, iteration count, evaluator results)
- Acceptance criteria

**Acceptance Criteria**:
- AC-1: Parser returns a typed FeatureVector model from valid YAML
- AC-2: Parser handles feature vectors with missing edge data (partial trajectories)

### REQ-F-PARSE-003: Graph Topology Parser

**Priority**: High
**Traces To**: INT-GMON-001 / OUT-004

The system MUST parse `.ai-workspace/graph/graph_topology.yml` to extract:
- Asset types with descriptions
- Admissible transitions (edges)
- Profile definitions

**Acceptance Criteria**:
- AC-1: Parser returns a typed GraphTopology model
- AC-2: Parser validates that transitions reference defined asset types

### REQ-F-PARSE-004: Event Log Parser

**Priority**: High
**Traces To**: INT-GMON-001 / OUT-002

The system MUST parse `.ai-workspace/events/events.jsonl` (append-only event source) to extract timestamped methodology events.

**Acceptance Criteria**:
- AC-1: Parser reads JSONL line-by-line, returning a list of Event models
- AC-2: Parser handles empty or missing event log (returns empty list)
- AC-3: Parser supports incremental reading (seek to last-known position)

### REQ-F-PARSE-005: Active Tasks Parser

**Priority**: Medium
**Traces To**: INT-GMON-001 / OUT-002

The system MUST parse `.ai-workspace/tasks/active/ACTIVE_TASKS.md` to extract task list with status, priority, and assignment.

**Acceptance Criteria**:
- AC-1: Parser returns a list of Task models with id, title, status fields
- AC-2: Parser handles varied markdown table formats

### REQ-F-PARSE-006: Project Constraints Parser

**Priority**: Medium
**Traces To**: INT-GMON-001 / OUT-005

The system MUST parse `.ai-workspace/context/project_constraints.yml` to extract language, tools, thresholds, and architecture rules.

**Acceptance Criteria**:
- AC-1: Parser returns a structured ProjectConstraints model
- AC-2: Unknown fields are preserved (forward-compatible)

---

## 5. Dashboard Views

### REQ-F-DASH-001: Project Index Page

**Priority**: Critical
**Traces To**: INT-GMON-001 / OUT-001

The system MUST render a landing page listing all discovered projects with:
- Project name
- Current phase (most recent active edge)
- Overall convergence status (how many edges complete)
- Last activity timestamp

**Acceptance Criteria**:
- AC-1: Page lists all projects from the registry
- AC-2: Each project links to its detail dashboard
- AC-3: Page auto-updates via SSE when projects change

### REQ-F-DASH-002: Project Detail Dashboard

**Priority**: Critical
**Traces To**: INT-GMON-001 / OUT-004, OUT-005

The system MUST render a per-project dashboard showing:
- Asset graph visualization (Mermaid diagram)
- Edge status table (iteration count, evaluator results, convergence)
- Feature vector list with per-edge trajectory
- Recent events feed

**Acceptance Criteria**:
- AC-1: Asset graph renders as a Mermaid diagram with node colour indicating status
- AC-2: Edge table shows all transitions from graph topology
- AC-3: Feature vectors show expanded trajectory on click/expand
- AC-4: Dashboard auto-updates via SSE on workspace changes

### REQ-F-DASH-003: Convergence View

**Priority**: High
**Traces To**: INT-GMON-001 / OUT-005

The system MUST render a convergence dashboard showing per-edge:
- Iteration count
- Evaluator pass/fail/skip breakdown
- Source findings count (ambiguities, gaps, underspecified)
- Process gaps count

**Acceptance Criteria**:
- AC-1: Data sourced from STATUS.md phase completion section
- AC-2: Visual indicators (colour coding) for pass/fail/in-progress

### REQ-F-DASH-004: Gantt Timeline View

**Priority**: High
**Traces To**: INT-GMON-001 / OUT-007

The system MUST render a Gantt chart showing asset creation and edge transition timeline.

**Acceptance Criteria**:
- AC-1: Gantt chart extracted from STATUS.md Mermaid section and rendered in browser
- AC-2: If no Gantt data available, show "No timeline data" placeholder

### REQ-F-DASH-005: TELEM Signal View

**Priority**: Medium
**Traces To**: INT-GMON-001 / OUT-006

The system MUST render TELEM signals extracted from STATUS.md with signal ID, category, and description.

**Acceptance Criteria**:
- AC-1: TELEM signals displayed in a table/card layout
- AC-2: Signals grouped by category if available

---

## 6. Real-Time Event Streaming

### REQ-F-STREAM-001: SSE Endpoint

**Priority**: Critical
**Traces To**: INT-GMON-001 / OUT-002, INT-GMON-003 / OUT-014

The system MUST expose an SSE endpoint (`/events/stream`) that pushes events to connected browsers when workspace data changes.

**Acceptance Criteria**:
- AC-1: Endpoint follows SSE protocol (text/event-stream content type)
- AC-2: Events include type (project_updated, file_changed, project_added) and project ID
- AC-3: Multiple clients can subscribe simultaneously
- AC-4: Client auto-reconnects on connection loss (SSE built-in)

### REQ-F-STREAM-002: HTMX Partial Updates

**Priority**: Critical
**Traces To**: INT-GMON-003 / OUT-013

The system MUST expose HTML fragment endpoints that HTMX can swap into the DOM on SSE events.

**Acceptance Criteria**:
- AC-1: Fragment endpoints return HTML (not JSON)
- AC-2: Each dashboard section has a corresponding fragment endpoint
- AC-3: HTMX `hx-sse` attributes trigger partial page updates without full reload

---

## 7. Filesystem Watching

### REQ-F-WATCH-001: Watchdog Observer

**Priority**: Critical
**Traces To**: INT-GMON-001 / OUT-008

The system MUST use the `watchdog` library to monitor configured root directories for filesystem changes within `.ai-workspace/` paths.

**Acceptance Criteria**:
- AC-1: Observer detects file creation, modification, and deletion
- AC-2: Events are filtered to only `.ai-workspace/` subtrees
- AC-3: Observer runs in a background thread, not blocking the async event loop

### REQ-F-WATCH-002: Debounced Refresh

**Priority**: High
**Traces To**: INT-GMON-001 / OUT-008

The system MUST debounce filesystem events to avoid excessive re-parsing during rapid writes (e.g., multiple files written in quick succession).

**Acceptance Criteria**:
- AC-1: Events within a 500ms window for the same project are coalesced into one refresh
- AC-2: Debounce interval is configurable

---

## 8. Telemetry Aggregation

### REQ-F-TELEM-001: TELEM Signal Collection

**Priority**: Medium
**Traces To**: INT-GMON-001 / OUT-006

The system MUST extract TELEM signals from STATUS.md across all monitored projects and present them in an aggregated view.

**Acceptance Criteria**:
- AC-1: TELEM signals from all projects collected into a unified list
- AC-2: Each signal attributed to its source project
- AC-3: Aggregated view accessible from the index page

---

## 9. Dogfood Requirements

### REQ-F-DOG-001: Self-Monitoring

**Priority**: High
**Traces To**: INT-GMON-002 / OUT-010

The genesis monitor project's own `.ai-workspace/` directory MUST be included as a monitored project when running during development.

**Acceptance Criteria**:
- AC-1: When the monitor's own project directory is under a watch root, it appears in the project index
- AC-2: Changes to its own methodology data are reflected in real-time

### REQ-F-DOG-002: Methodology Compliance

**Priority**: High
**Traces To**: INT-GMON-002 / OUT-009

The project MUST be built edge-by-edge following the AI SDLC methodology, with each artifact traceable via REQ-GMON-* keys.

**Acceptance Criteria**:
- AC-1: All code files include `# Implements: REQ-*` tags
- AC-2: All test files include `# Validates: REQ-*` tags
- AC-3: Commits reference REQ keys

---

## 10. Non-Functional Requirements

### REQ-NFR-001: Read-Only Operation

**Priority**: Critical
**Traces To**: INT-GMON-003

The system MUST NOT write to any target project's `.ai-workspace/` directory. It is a pure observer.

**Acceptance Criteria**:
- AC-1: No code path writes to monitored workspace directories
- AC-2: All file operations on workspace data use read-only mode

### REQ-NFR-002: Single Process Deployment

**Priority**: High
**Traces To**: INT-GMON-003

The system MUST run as a single process with no external dependencies (no database, no message queue, no separate worker processes).

**Acceptance Criteria**:
- AC-1: `uvicorn genesis_monitor.server.app:app` is the only command needed
- AC-2: All state held in memory, derived from filesystem

### REQ-NFR-003: Startup Performance

**Priority**: Medium
**Traces To**: INT-GMON-001

The system MUST complete initial workspace scanning and be serving HTTP within 5 seconds for up to 50 monitored projects.

**Acceptance Criteria**:
- AC-1: Startup measured from process start to first HTTP 200 response
- AC-2: Target: < 5 seconds with 50 projects

### REQ-NFR-004: Zero Build Step

**Priority**: High
**Traces To**: INT-GMON-003 / OUT-016

The frontend MUST require no build tooling — no webpack, npm, or transpilation. HTML templates with CDN-hosted JavaScript libraries only.

**Acceptance Criteria**:
- AC-1: No `package.json`, no `node_modules/`
- AC-2: Mermaid.js and HTMX loaded from CDN `<script>` tags
- AC-3: All frontend code is Jinja2 templates + inline CSS

### REQ-NFR-005: Python 3.12+ Compatibility

**Priority**: High
**Traces To**: INT-GMON-003

The system MUST use Python 3.12+ features and not support older Python versions.

**Acceptance Criteria**:
- AC-1: `requires-python = ">=3.12"` in pyproject.toml
- AC-2: Uses match statements, tomllib, modern type hints where appropriate

---

## 11. Processing Regimes (v2.5)

### REQ-F-REGIME-001: Evaluator Regime Classification

**Priority**: High
**Traces To**: INT-GMON-004 / OUT-017

The system MUST classify each evaluator instance as either **conscious** (deliberative — human, agent) or **reflex** (autonomic — deterministic tests, event emission, status regeneration) per the two processing regimes defined in v2.5 §4.3.

**Acceptance Criteria**:
- AC-1: Evaluator results in feature vectors and status reports are tagged with regime (conscious/reflex)
- AC-2: Dashboard displays regime classification per evaluator in edge detail views

### REQ-F-REGIME-002: Reflex Completeness Verification

**Priority**: High
**Traces To**: INT-GMON-004 / OUT-017, OUT-024

The system MUST verify that all mandatory reflex side-effects (event emission, feature vector update, STATUS regeneration) were executed at each iteration, by checking for corresponding events in events.jsonl.

**Acceptance Criteria**:
- AC-1: For each `iteration_completed` event, verify a corresponding feature vector update and STATUS regeneration occurred
- AC-2: Missing reflexes are displayed as warnings in the convergence view

---

## 12. Consciousness Loop (v2.5)

### REQ-F-CONSC-001: Intent Causal Chain Parsing

**Priority**: Critical
**Traces To**: INT-GMON-004 / OUT-018

The system MUST parse `intent_raised` events that include a `prior_intents` field (causal chain), displaying the lineage of intents that led to each new intent.

**Acceptance Criteria**:
- AC-1: Parser extracts `prior_intents` list from `intent_raised` events
- AC-2: Dashboard renders intent causal chain as a linked list or tree
- AC-3: Clicking an intent in the chain navigates to the corresponding feature vector

### REQ-F-CONSC-002: Spec Modification Event Parsing

**Priority**: Critical
**Traces To**: INT-GMON-004 / OUT-018

The system MUST parse `spec_modified` events containing `previous_hash`, `new_hash`, `delta`, and `trigger_intent`, and display spec evolution history.

**Acceptance Criteria**:
- AC-1: Parser extracts all `spec_modified` fields
- AC-2: Dashboard displays spec modification timeline with trigger intent links
- AC-3: Delta descriptions are displayed for each modification

### REQ-F-CONSC-003: Consciousness Phase Display

**Priority**: Medium
**Traces To**: INT-GMON-004 / OUT-018

The system MUST display which consciousness loop phase a project is in: Phase 1 (signal → spec modification), Phase 2 (modification logged as event), Phase 3 (system detects consequences of own modifications).

**Acceptance Criteria**:
- AC-1: Phase determined by presence of `spec_modified` events and intent chains with `prior_intents`
- AC-2: Phase displayed on project detail dashboard

---

## 13. Constraint Dimensions (v2.5)

### REQ-F-CDIM-001: Constraint Dimension Parsing

**Priority**: High
**Traces To**: INT-GMON-004 / OUT-019

The system MUST parse `constraint_dimensions` from graph topology YAML, extracting dimension name, mandatory flag, and resolution method (adr/design_section).

**Acceptance Criteria**:
- AC-1: Parser returns typed ConstraintDimension models
- AC-2: Parser handles topologies without constraint_dimensions (backward compatible)

### REQ-F-CDIM-002: Dimension Coverage Matrix

**Priority**: High
**Traces To**: INT-GMON-004 / OUT-019

The system MUST display a matrix showing which mandatory constraint dimensions have been resolved (via ADR or design section) and which remain unresolved.

**Acceptance Criteria**:
- AC-1: Matrix shows each dimension, its mandatory flag, resolution method, and resolved status
- AC-2: Unresolved mandatory dimensions are highlighted as warnings

---

## 14. Projection Profiles (v2.5)

### REQ-F-PROF-001: Profile Parsing

**Priority**: High
**Traces To**: INT-GMON-004 / OUT-020

The system MUST parse projection profile definitions from graph topology or profile configuration, extracting graph subset, evaluator types, convergence criteria, context density, iteration budget, and supported vector types.

**Acceptance Criteria**:
- AC-1: Parser returns typed ProjectionProfile models
- AC-2: Each profile specifies which edges are active and which evaluator types apply

### REQ-F-PROF-002: Profile Display Per Vector

**Priority**: High
**Traces To**: INT-GMON-004 / OUT-020

The system MUST display the active projection profile for each feature vector and validate that the vector's trajectory conforms to its profile constraints.

**Acceptance Criteria**:
- AC-1: Feature vector view shows active profile name
- AC-2: Profile violations (e.g., spike using full graph) are flagged

---

## 15. Vector Relationships (v2.5)

### REQ-F-VREL-001: Spawn Relationship Tracking

**Priority**: Critical
**Traces To**: INT-GMON-004 / OUT-021

The system MUST parse `feature_spawned` events to build a parent/child tree of feature vectors, tracking spawn trigger (gap, risk, feasibility, incident, scope expansion).

**Acceptance Criteria**:
- AC-1: FeatureVector model includes `parent_id` and `spawned_by` fields
- AC-2: Parser builds parent/child relationships from spawn events
- AC-3: Spawn trigger classification is preserved

### REQ-F-VREL-002: Fold-Back State Tracking

**Priority**: Critical
**Traces To**: INT-GMON-004 / OUT-021

The system MUST parse `feature_folded_back` events and track when a child vector's results are folded back into its parent, updating the parent's context.

**Acceptance Criteria**:
- AC-1: Dashboard shows fold-back state (pending/complete) for spawned vectors
- AC-2: Parent vector shows which child outputs have been absorbed

### REQ-F-VREL-003: Vector Relationship Visualization

**Priority**: High
**Traces To**: INT-GMON-004 / OUT-021

The system MUST render a spawn tree showing parent/child vector relationships with status indicators.

**Acceptance Criteria**:
- AC-1: Tree rendered as Mermaid diagram or HTML nested list
- AC-2: Each node shows vector type, status, and profile

---

## 16. Time-Boxing (v2.5)

### REQ-F-TBOX-001: Time-Box Parsing

**Priority**: High
**Traces To**: INT-GMON-004 / OUT-022

The system MUST parse time-box configuration from feature vector YAML: duration, check-in cadence, on-expiry action, and partial results flag.

**Acceptance Criteria**:
- AC-1: FeatureVector model includes `time_box` fields
- AC-2: Parser handles vectors with and without time-box configuration

### REQ-F-TBOX-002: Time-Box Display

**Priority**: High
**Traces To**: INT-GMON-004 / OUT-022

The system MUST display time-box status for time-boxed vectors: deadline, time remaining, next check-in, and whether convergence was by completion or expiry.

**Acceptance Criteria**:
- AC-1: Time-boxed vectors show countdown or expired status
- AC-2: Convergence reason (completed vs timed_out) is displayed

---

## 17. Structured Event Schemas (v2.5)

### REQ-F-EVSCHEMA-001: Typed Event Parsing

**Priority**: High
**Traces To**: INT-GMON-004 / OUT-023

The system MUST parse events according to their type-specific schemas as defined in v2.5 §7.5.1. The 9 event types are: `iteration_completed`, `edge_converged`, `evaluator_ran`, `finding_raised`, `context_added`, `feature_spawned`, `feature_folded_back`, `telemetry_signal_emitted`, `spec_modified`.

**Acceptance Criteria**:
- AC-1: Each event type is parsed into a type-specific model with validated fields
- AC-2: Unknown event types are preserved as generic events (forward compatible)
- AC-3: Missing required fields generate parser warnings

### REQ-F-EVSCHEMA-002: Cross-Event Linkage

**Priority**: Medium
**Traces To**: INT-GMON-004 / OUT-023

The system MUST support linking related events: `finding_raised` → `intent_raised` → `feature_spawned` → `feature_folded_back`, enabling causal chain visualization.

**Acceptance Criteria**:
- AC-1: Events with shared feature IDs or intent IDs are linkable
- AC-2: Event detail view shows related events

---

## 18. Protocol Compliance (v2.5)

### REQ-F-PROTO-001: Protocol Compliance View

**Priority**: Medium
**Traces To**: INT-GMON-004 / OUT-024

The system MUST display per-iteration compliance with the iterate protocol: whether mandatory side-effects (event emission, feature vector update, STATUS regeneration) were completed.

**Acceptance Criteria**:
- AC-1: Compliance derived from event log analysis (presence/absence of expected reflex events)
- AC-2: Non-compliant iterations are flagged in the convergence view

---

## 19. Source Findings (v2.5 Iteration)

| # | Type | Finding | Resolution |
|---|------|---------|------------|
| 1 | GAP | INT-GMON-001 does not specify error handling for corrupted workspace data | Added graceful handling in REQ-F-PARSE-001 AC-2, REQ-F-PARSE-002 AC-2 |
| 2 | GAP | INT-GMON-001 does not specify how many projects can be monitored | Added REQ-NFR-003 with 50-project target |
| 3 | AMBIGUITY | "Gantt schedule view" — source unclear (STATUS.md vs computed) | Resolved: extract from STATUS.md Mermaid section (REQ-F-DASH-004) |
| 4 | GAP | No error page / fallback UI specified | Deferred — parser graceful degradation covers data issues |
| 5 | UNDERSPEC | TELEM aggregation format not defined in intent | Resolved: extract from STATUS.md self-reflection section (REQ-F-TELEM-001) |
| 6 | GAP | No configuration format specified | Added REQ-F-DISC-003 with CLI + YAML config |
| 7 | DRIFT | Monitor built against v2.1; methodology now at v2.5 | INT-GMON-004 raised; 18 new requirements added |
| 8 | GAP | INT-GMON-004 does not specify Markov criteria validation display | Deferred — covered implicitly by convergence view + protocol compliance |
| 9 | AMBIGUITY | Constraint dimension "resolved" status — how to detect from artifacts | Resolved: check for ADR files matching dimension name or design section grep |
| 10 | GAP | INT-GMON-004 does not specify how vector nesting depth is bounded | Deferred — display depth, but enforcement is the methodology's responsibility |

---

## 20. Requirements Summary

| Category | Count | Critical | High | Medium |
|----------|-------|----------|------|--------|
| Discovery (DISC) | 3 | 2 | 1 | 0 |
| Parsing (PARSE) | 6 | 2 | 2 | 2 |
| Dashboard (DASH) | 5 | 2 | 2 | 1 |
| Streaming (STREAM) | 2 | 2 | 0 | 0 |
| Watching (WATCH) | 2 | 1 | 1 | 0 |
| Telemetry (TELEM) | 1 | 0 | 0 | 1 |
| Dogfood (DOG) | 2 | 0 | 2 | 0 |
| Non-Functional (NFR) | 5 | 1 | 3 | 1 |
| Processing Regimes (REGIME) | 2 | 0 | 2 | 0 |
| Consciousness Loop (CONSC) | 3 | 2 | 0 | 1 |
| Constraint Dimensions (CDIM) | 2 | 0 | 2 | 0 |
| Projection Profiles (PROF) | 2 | 0 | 2 | 0 |
| Vector Relationships (VREL) | 3 | 2 | 1 | 0 |
| Time-Boxing (TBOX) | 2 | 0 | 2 | 0 |
| Structured Events (EVSCHEMA) | 2 | 0 | 1 | 1 |
| Protocol Compliance (PROTO) | 1 | 0 | 0 | 1 |
| **Total** | **43** | **13** | **19** | **11** |
