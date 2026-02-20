# Genesis Monitor — Intent

**Version**: 1.0.0
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
```
