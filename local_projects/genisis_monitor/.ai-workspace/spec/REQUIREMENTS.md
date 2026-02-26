# Genesis Monitor — Requirements Specification

**Version**: 3.0.0
**Date**: 2026-02-23
**Status**: Draft — iterate(intent→requirements) for INT-GMON-005
**Feature**: REQ-F-GMON-001, REQ-F-GMON-002
**Source Asset**: docs/specification/INTENT.md v3.0.0 (5 intent items, 32 outcomes)
**Methodology**: AI SDLC Asset Graph Model v2.8

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
| REQ-F-FUNC-* | INT-GMON-005 (Functor encoding) |
| REQ-F-SENSE-* | INT-GMON-005 (Sensory system) |
| REQ-F-MAGT-* | INT-GMON-005 (Multi-agent coordination) |
| REQ-F-IENG-* | INT-GMON-005 (IntentEngine classification) |
| REQ-F-ETIM-* | INT-GMON-005 (Edge timestamps) |
| REQ-F-CTOL-* | INT-GMON-005 (Constraint tolerances) |

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

### REQ-F-DASH-006: Project Tree Navigator

**Priority**: High
**Traces To**: INT-GMON-001 / OUT-001

The system MUST provide a hierarchical tree view on the project index page that groups discovered projects by their filesystem directory structure, replacing or augmenting the flat project list.

**Acceptance Criteria**:
- AC-1: Projects are grouped by common parent directories into a collapsible tree
- AC-2: Each tree node shows directory name; project nodes show status (phase badge, convergence count)
- AC-3: Non-project directories that are ancestors of projects appear as expandable folder nodes
- AC-4: Tree auto-updates via SSE when projects change
- AC-5: Clicking a project node navigates to its detail dashboard

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

## 19. Functor Encoding (v2.8)

### REQ-F-FUNC-001: Functor Encoding Parsing

**Priority**: High
**Traces To**: INT-GMON-005 / OUT-025

The system MUST parse the `encoding` block from feature vector YAML, extracting `mode`, `valence`, and `active_units` fields that represent the functor state of a feature vector.

**Acceptance Criteria**:
- AC-1: Parser extracts encoding dict with mode/valence/active_units from feature YAML
- AC-2: Missing encoding block defaults to None (backward compatible)

### REQ-F-FUNC-002: Functor Encoding Display

**Priority**: High
**Traces To**: INT-GMON-005 / OUT-025

The system MUST display the functor encoding state (mode, valence, active units) in the feature vector detail view.

**Acceptance Criteria**:
- AC-1: Feature detail view shows encoding block when present
- AC-2: Encoding fields are human-readable labels

---

## 20. Sensory System (v2.8)

### REQ-F-SENSE-001: Interoceptive Signal Parsing

**Priority**: High
**Traces To**: INT-GMON-005 / OUT-026

The system MUST parse `interoceptive_signal` events representing self-monitoring signals (e.g., convergence rate, iteration budget usage).

**Acceptance Criteria**:
- AC-1: Parser extracts signal_type, measurement, threshold fields
- AC-2: Events dispatched to InteroceptiveSignalEvent dataclass

### REQ-F-SENSE-002: Exteroceptive Signal Parsing

**Priority**: High
**Traces To**: INT-GMON-005 / OUT-026

The system MUST parse `exteroceptive_signal` events representing environment-monitoring signals (e.g., dependency changes, ecosystem updates).

**Acceptance Criteria**:
- AC-1: Parser extracts source, signal_type, payload fields
- AC-2: Events dispatched to ExteroceptiveSignalEvent dataclass

### REQ-F-SENSE-003: Affect Triage Parsing

**Priority**: High
**Traces To**: INT-GMON-005 / OUT-026

The system MUST parse `affect_triage` events representing the triage outcome of sensory signals (route to reflex, escalate, or ignore).

**Acceptance Criteria**:
- AC-1: Parser extracts signal_ref, triage_result, rationale fields
- AC-2: Events dispatched to AffectTriageEvent dataclass

### REQ-F-SENSE-004: Sensory Dashboard View

**Priority**: Medium
**Traces To**: INT-GMON-005 / OUT-026

The system MUST provide a sensory dashboard projection grouping interoceptive and exteroceptive signals with their triage outcomes.

**Acceptance Criteria**:
- AC-1: Dashboard shows signal counts by type (interoceptive/exteroceptive)
- AC-2: Affect triage results shown alongside originating signals

### REQ-F-SENSE-005: Sensory Event Backward Compatibility

**Priority**: High
**Traces To**: INT-GMON-005 / OUT-032

The system MUST handle workspaces without sensory events gracefully, returning empty sensory dashboard data.

**Acceptance Criteria**:
- AC-1: Empty sensory dashboard returned when no sensory events exist
- AC-2: No errors raised for v2.5 workspaces lacking sensory events

---

## 21. Multi-Agent Coordination (v2.8)

### REQ-F-MAGT-001: Claim/Release Event Parsing

**Priority**: High
**Traces To**: INT-GMON-005 / OUT-028

The system MUST parse `claim_rejected` and `edge_released` events from the multi-agent coordination protocol (ADR-013).

**Acceptance Criteria**:
- AC-1: Parser extracts agent_id, edge, reason fields from claim_rejected
- AC-2: Parser extracts agent_id, edge fields from edge_released

### REQ-F-MAGT-002: Claim Expiry Event Parsing

**Priority**: High
**Traces To**: INT-GMON-005 / OUT-028

The system MUST parse `claim_expired` events indicating agent claim timeouts.

**Acceptance Criteria**:
- AC-1: Parser extracts agent_id, edge, expiry_reason fields
- AC-2: Events dispatched to ClaimExpiredEvent dataclass

### REQ-F-MAGT-003: Convergence Escalation Event Parsing

**Priority**: High
**Traces To**: INT-GMON-005 / OUT-028

The system MUST parse `convergence_escalated` events indicating edge convergence required human escalation.

**Acceptance Criteria**:
- AC-1: Parser extracts edge, reason, escalated_to fields
- AC-2: Events dispatched to ConvergenceEscalatedEvent dataclass

---

## 22. IntentEngine Classification (v2.8)

### REQ-F-IENG-001: IntentEngine Output Type Classification

**Priority**: High
**Traces To**: INT-GMON-005 / OUT-027

The system MUST classify events by their IntentEngine output type: `reflex.log` (autonomic logging), `specEventLog` (spec-level event), or `escalate` (requires human attention).

**Acceptance Criteria**:
- AC-1: Classification function maps event types to IntentEngine output categories
- AC-2: Projection view shows breakdown by output type

### REQ-F-IENG-002: IntentEngine View Projection

**Priority**: Medium
**Traces To**: INT-GMON-005 / OUT-027

The system MUST provide an IntentEngine view projection showing event distribution across output types.

**Acceptance Criteria**:
- AC-1: Projection returns counts and event lists per output type
- AC-2: Empty events produce zero-count results

---

## 23. Edge Timestamps (v2.8)

### REQ-F-ETIM-001: Edge Start/Converge Timestamp Parsing

**Priority**: High
**Traces To**: INT-GMON-005 / OUT-030

The system MUST parse `started_at` and `converged_at` timestamps from edge trajectory data in feature vector YAML files.

**Acceptance Criteria**:
- AC-1: Parser extracts ISO datetime strings and converts to datetime objects
- AC-2: Missing timestamps default to None (backward compatible)

### REQ-F-ETIM-002: Edge Duration Display

**Priority**: High
**Traces To**: INT-GMON-005 / OUT-030

The system MUST compute and display edge duration from started_at/converged_at timestamps in the convergence view.

**Acceptance Criteria**:
- AC-1: Duration computed as converged_at - started_at when both present
- AC-2: Duration shown as human-readable string (e.g., "2h 15m")

### REQ-F-ETIM-003: Convergence Type Tracking

**Priority**: High
**Traces To**: INT-GMON-005 / OUT-031

The system MUST parse and display `convergence_type` (delta_zero, timeout, escalated) from edge trajectory data.

**Acceptance Criteria**:
- AC-1: Convergence type extracted from feature vector YAML
- AC-2: Convergence type displayed in convergence table and feature detail views

---

## 24. Constraint Tolerances (v2.8)

### REQ-F-CTOL-001: Tolerance Threshold Parsing

**Priority**: High
**Traces To**: INT-GMON-005 / OUT-029

The system MUST parse `tolerance` thresholds from constraint dimension definitions in graph topology YAML.

**Acceptance Criteria**:
- AC-1: Parser extracts tolerance string (e.g., "≤ 5% degradation") per dimension
- AC-2: Missing tolerance defaults to empty string (backward compatible)

### REQ-F-CTOL-002: Breach Status Display

**Priority**: High
**Traces To**: INT-GMON-005 / OUT-029

The system MUST parse and display `breach_status` for constraint dimensions, indicating whether tolerance thresholds are currently exceeded.

**Acceptance Criteria**:
- AC-1: Breach status extracted from topology YAML per dimension
- AC-2: Breached dimensions highlighted as warnings in the dimension coverage view

---

## 25. Source Findings (v2.5/v2.8 Iteration)

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

## 26. Requirements Summary

| Category | Count | Critical | High | Medium |
|----------|-------|----------|------|--------|
| Discovery (DISC) | 3 | 2 | 1 | 0 |
| Parsing (PARSE) | 6 | 2 | 2 | 2 |
| Dashboard (DASH) | 6 | 2 | 3 | 1 |
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
| Functor Encoding (FUNC) | 2 | 0 | 2 | 0 |
| Sensory System (SENSE) | 5 | 0 | 4 | 1 |
| Multi-Agent Coordination (MAGT) | 3 | 0 | 3 | 0 |
| IntentEngine Classification (IENG) | 2 | 0 | 1 | 1 |
| Edge Timestamps (ETIM) | 3 | 0 | 3 | 0 |
| Constraint Tolerances (CTOL) | 2 | 0 | 2 | 0 |
| **Total** | **61** | **13** | **33** | **15** |
