# Genesis Monitor — User Guide

**Version**: 1.0.0
**Date**: 2026-03-03
**Applies to**: `imp_python_fastapi` implementation

---

## What Is Genesis Monitor?

Genesis Monitor is a real-time web dashboard that observes AI SDLC methodology
execution across one or more projects. It reads `.ai-workspace/` directories on the
filesystem and presents a live, browser-based view — no page refreshes needed.

It is a **pure observer**: it never writes to your project data. Every view is
derived from the files you already have.

---

## Quick Start

### Prerequisites

- Python 3.12+
- A project directory tree containing `.ai-workspace/` directories

### Install

```bash
cd local_projects/genisis_monitor/imp_python_fastapi
pip install -e ".[dev]"
```

### Run

```bash
python -m genesis_monitor --watch-dir /path/to/your/projects
```

The server starts on `http://0.0.0.0:8000` by default.

Multiple watch roots are supported:

```bash
python -m genesis_monitor \
  --watch-dir /Users/jim/src/apps \
  --watch-dir /Users/jim/work/clients
```

### Options

| Flag | Default | Description |
|------|---------|-------------|
| `--watch-dir PATH` | (required) | Root directory to scan for `.ai-workspace/` directories. Repeatable. |
| `--host HOST` | `0.0.0.0` | Interface to bind to. Use `127.0.0.1` for local-only. |
| `--port PORT` | `8000` | TCP port. |
| `--config FILE` | — | YAML config file (alternative to CLI flags). |

---

## Project Discovery

At startup, the monitor **recursively walks** every `--watch-dir` looking for
directories that contain a `.ai-workspace/` subdirectory. Each such directory is
registered as a project.

Discovery is live: the filesystem watcher (watchdog) detects new or removed
workspaces and updates the registry automatically. No restart needed.

**What counts as a project**: any directory with a `.ai-workspace/` child.
**What is ignored**: `.git`, `node_modules`, `__pycache__`, `.venv`, and any
directory starting with `.` are pruned from the walk.

---

## The Index Page (`/`)

The index page shows all discovered projects in a **hierarchical tree** that mirrors
the filesystem structure.

### Project Tree

Projects are grouped by their common ancestor directories. Single-child chains are
collapsed into a single node (e.g. `ai_sdlc_examples/local_projects` becomes one
label rather than two levels). All branches are **expanded by default** — every
project is visible without needing to click anything open.

Folder nodes (non-project directories that are parents of projects) show as
expandable `<details>` elements. You can click a folder name to collapse it and
reduce visual noise once you know where things are.

### Status Badges

Each project node in the tree shows:

- **Phase badge** — the most recently active edge (e.g. `req→design`) coloured
  amber for in-progress, green for converged.
- **Edge count** — `2/5 edges` showing how many graph edges have converged out
  of the total.
- **BL badge** — shown in blue when a Genesis Bootloader is installed in the
  project's `CLAUDE.md`.
- **no status** — shown in grey when no `STATUS.md` data is available yet.

### Live Updates

The tree auto-refreshes via SSE whenever a workspace changes on disk. You do not
need to reload the page.

---

## Project Detail Page (`/project/{id}`)

Click any project in the tree to open its detail dashboard. The page shows a
collection of cards, each updated independently via SSE.

### Header

The project name and filesystem path are shown at the top. If a Genesis Bootloader
is installed, a blue **Bootloader** badge appears next to the name.

### Design Tenant Selector

When a project's event log contains events from **multiple design implementations**
(e.g. `imp_claude`, `imp_gemini`, `imp_codex`), a tab bar appears below the header:

```
[ All tenants (342) ]  [ imp_claude (280) ]  [ imp_gemini (62) ]
```

- **All tenants** (default) — merged view, all events included.
- **imp_claude / imp_gemini / etc.** — filtered view showing only that
  implementation's events.

Clicking a tab does a full page navigation (not a partial swap) so that all cards
initialise cleanly for the chosen tenant. The event count `(N)` next to each tab
shows how many events belong to that tenant.

The active tenant is highlighted using Pico CSS's native `aria-current="page"`
styling — no custom CSS class.

### Temporal Scrubber (Time Travel)

A dual-handle range slider appears at the top of the page if the project has events.
It lets you **travel back in time** to inspect the project state at any historical
point.

```
[2026-02-01] ════════════════════════════════[ Now ]
              ▓▓▓░░░░▓▓▓▓▓▓▓░░▓▓▓░░░░░░▓▓▓
```

The coloured gradient behind the slider is an **activity heatmap** — brighter
sections mean more events fired in that time window.

- **Left handle** — start of the visible window (for future range-based views).
- **Right handle** — the "as-of" timestamp. Drag left to inspect the past.
- When the right handle is at the far right, the display shows **Live (Now)** in
  green and all cards show real-time state.
- When the right handle is anywhere else, all cards show the reconstructed
  state at that timestamp, and a blue timestamp label shows the selected date.

The scrubber preserves the active design tenant when changing timestamps.

---

## Dashboard Cards

### Asset Graph

A Mermaid diagram showing the project's AI SDLC graph topology with nodes coloured
by status:

- **Green** — converged
- **Amber / Yellow** — in progress
- **Grey** — not started

The diagram re-renders after each HTMX swap (including scrubber moves).

### Edge Convergence

A summary chart showing the convergence status of each graph edge:
iteration counts, evaluator pass/fail breakdown, and a delta curve
indicating how the gap closed over iterations.

### Edge Status

A table listing every admissible transition in the graph with:

| Column | Meaning |
|--------|---------|
| Edge | e.g. `intent → requirements` |
| Status | not started / in progress / converged |
| Iterations | number of iterate() calls completed |
| Started | ISO timestamp of first `edge_started` event |
| Converged | ISO timestamp of `edge_converged` event |
| Duration | wall-clock time from start to convergence |

### Feature Vectors

Cards for each tracked feature (REQ key) showing:

- Feature ID, title, and overall status
- Per-edge trajectory: which edges the feature has been through, current status,
  iteration count
- Functor encoding (mode, valence, active units) when present
- Time-box status (deadline, time remaining) for time-boxed vectors

### Timeline (Gantt)

A Mermaid Gantt chart extracted from the project's `STATUS.md` showing
asset creation and edge transition timing. If no Gantt data is available,
a placeholder is shown.

### TELEM Signals

Self-reflection signals captured during methodology execution, grouped by
category. These are the project's own "telemetry" about how iteration is going
(convergence rate, budget usage, etc.).

### Recent Events

A live feed of the last 50 events from `events.jsonl`. Each event shows:
timestamp, event type, edge (if applicable), feature (if applicable),
and delta value. Newest events appear at the top.

### Vector Relationships

A spawn tree showing parent/child relationships between feature vectors,
built from `feature_spawned` and `feature_folded_back` events. Each node
shows the vector type (Feature, Spike, PoC, Hotfix), status, and profile.

### Constraint Dimensions

A coverage matrix showing which mandatory constraint dimensions from the graph
topology have been resolved (via ADR or design section) and which remain open.
Unresolved mandatory dimensions are highlighted as warnings.

### Processing Phases

A summary of evaluator regime classification — which evaluator instances ran
as **reflex** (autonomic: deterministic tests, event emission) vs **conscious**
(deliberative: human review, agent evaluation).

### Consciousness Loop

A timeline of `intent_raised` events with their causal chains (`prior_intents`),
and `spec_modified` events showing spec evolution history. This view shows
which consciousness loop phase the project is in:

- **Phase 1** — signal → spec modification
- **Phase 2** — modification logged as event
- **Phase 3** — system detects consequences of its own modifications

### Protocol Compliance (v2.8)

A per-iteration compliance check: for each `iteration_completed` event, verifies
that all mandatory reflex side-effects were executed — event emission, feature
vector update, and STATUS regeneration. Non-compliant iterations are flagged.

### Test Traceability

Shows REQ key coverage across code and test files, scanned via
`# Implements: REQ-*` and `# Validates: REQ-*` tags:

| Status | Meaning |
|--------|---------|
| full | REQ key has both code and test coverage |
| partial | code coverage only, or tests only |
| none | REQ key appears in spec but has no tags in code or tests |

Summary statistics show total REQ keys, full/partial/no coverage counts.

### Feature → Module Map

A bipartite mapping showing which code modules implement which features,
built by joining feature vector REQ keys with the traceability scan output.
Untraced keys (in the feature spec but not tagged in code) are highlighted
as gaps.

---

## URL Scheme & Query Parameters

All page and fragment routes accept the following optional query parameters:

| Parameter | Example | Effect |
|-----------|---------|--------|
| `?t=ISO8601` | `?t=2026-02-15T10:30:00` | Show project state as of this timestamp. All projections use events up to and including `t`. |
| `?design=NAME` | `?design=imp_claude` | Filter all views to events from this design tenant only. |
| Both together | `?design=imp_claude&t=2026-02-15T10:30:00` | Historical state for a specific tenant. |

These parameters are accepted on:
- `/project/{id}` — the main project detail page
- All `/fragments/project/{id}/*` endpoints

The temporal scrubber and design tenant selector write these parameters into the
URL automatically — you can bookmark or share any historical / tenant-filtered state.

---

## API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/` | GET | Project index (HTML) |
| `/project/{id}` | GET | Project detail page (HTML) |
| `/events/stream` | GET | SSE event stream (text/event-stream) |
| `/api/health` | GET | JSON health check: `{"status":"ok","projects":N}` |
| `/lineage/source/feature/{feature_id}` | GET | Raw source data for a feature vector |

---

## SSE Event Types

The `/events/stream` endpoint pushes these event names to connected browsers:

| Event | Triggered when |
|-------|---------------|
| `project_updated` | A project's workspace files changed |
| `project_added` | A new workspace was discovered |
| `project_removed` | A workspace was deleted or moved |

HTMX `hx-trigger="sse:project_updated"` attributes on each card cause
the relevant fragment to reload automatically.

---

## Running Multiple Instances

If you start two instances of genesis_monitor pointing at overlapping directories,
**only one can own port 8000**. The second will fail to bind. To run multiple
instances, give each a different port:

```bash
python -m genesis_monitor --watch-dir /path/a --port 8000
python -m genesis_monitor --watch-dir /path/b --port 8001
```

If you see fewer projects than expected, check for a stale background process:

```bash
lsof -i :8000 -n -P
```

Kill any unexpected processes before starting fresh.

---

## Dogfooding

Genesis Monitor monitors itself. When run with a `--watch-dir` that includes the
`genisis_monitor` project directory, the monitor appears in its own project tree
and its own `.ai-workspace/` events are reflected in real-time.

---

## Project Constraints & Engine Evaluation

The monitor's `.ai-workspace/python_fastapi_genesis/project_constraints.yml`
configures the genesis engine for deterministic evaluation of the `code↔unit_tests`
edge:

```bash
# Run engine evaluation from project root
python -m genesis evaluate \
  --edge "code↔unit_tests" \
  --feature "REQ-F-TRACE-001" \
  --constraints .ai-workspace/python_fastapi_genesis/project_constraints.yml \
  --deterministic-only
```

When the engine reports `delta=0`, all deterministic checks pass (tests, lint,
coverage) and the edge has converged at the reflex level.

---

## Architecture Notes

- **No database** — all state is derived from the filesystem at startup and kept
  in memory. Restart the server to force a full re-scan.
- **No build step** — the frontend is Jinja2 templates + HTMX + Mermaid.js from
  CDN. No webpack, no npm.
- **Read-only** — the monitor never writes to any monitored `.ai-workspace/`
  directory.
- **Single process** — one uvicorn process handles HTTP, SSE, and filesystem
  watching (watchdog runs in a background thread).
