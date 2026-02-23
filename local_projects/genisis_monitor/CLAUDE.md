# CLAUDE.md — Genesis Monitor

## Project Overview

**Genesis Monitor** is a real-time dashboard for observing AI SDLC methodology execution across projects. It consumes `.ai-workspace/` data and presents asset graphs, convergence status, feature vectors, and TELEM signals via a web UI.

This is **test06** in the ai_sdlc_examples dogfood series — built edge-by-edge using the methodology it monitors.

## Multi-Tenant Layout

This repository follows the **ai_sdlc_method** multi-tenant structure:

- `specification/` — shared, tech-agnostic (WHAT the system does)
- `imp_<name>/` — per-platform implementation (HOW it's built)

Future implementations (cloud EventBridge, stream Kafka, etc.) go in sibling `imp_<name>/` directories.

```
genisis_monitor/
├── CLAUDE.md                          ← this file
├── specification/                     ← shared spec (tech-agnostic)
│   ├── INTENT.md
│   └── REQUIREMENTS.md
├── imp_python_fastapi/                ← Python + FastAPI + HTMX implementation
│   ├── design/
│   │   ├── DESIGN.md
│   │   └── adrs/  (ADR-001..003)
│   ├── code/
│   │   └── src/genesis_monitor/       ← 34 Python modules
│   │       ├── parsers/
│   │       ├── models/
│   │       ├── projections/
│   │       ├── server/
│   │       └── watcher/
│   ├── tests/                         ← 16 test files, 240+ tests
│   └── pyproject.toml
├── .ai-workspace/                     ← runtime workspace state
└── .pytest_cache/
```

## Architecture

```
Filesystem (.ai-workspace/)
    │
    ▼
┌──────────┐     ┌──────────┐     ┌────────────┐     ┌───────┐
│ watchdog  │────▶│ parsers  │────▶│ projections│────▶│  SSE  │
│ (events)  │     │ (models) │     │  (views)   │     │ push  │
└──────────┘     └──────────┘     └────────────┘     └───┬───┘
                                                         │
                                                    ┌────▼────┐
                                                    │  HTMX   │
                                                    │ (browser)│
                                                    └─────────┘
```

### Layers

| Layer | Responsibility | Location |
|-------|---------------|----------|
| **Parsers** | Read STATUS.md, ACTIVE_TASKS.md, edge configs, TELEM artifacts | `imp_python_fastapi/code/src/genesis_monitor/parsers/` |
| **Models** | Typed dataclasses: Asset, Edge, FeatureVector, Project | `imp_python_fastapi/code/src/genesis_monitor/models/` |
| **Projections** | Derive views: convergence dashboard, Gantt, feature matrix | `imp_python_fastapi/code/src/genesis_monitor/projections/` |
| **Server** | FastAPI routes, SSE endpoints, Jinja2 templates | `imp_python_fastapi/code/src/genesis_monitor/server/` |
| **Watcher** | watchdog filesystem observer, event-to-SSE bridge | `imp_python_fastapi/code/src/genesis_monitor/watcher/` |

## Data Sources

The monitor reads `.ai-workspace/` directories with this structure:

```
.ai-workspace/
├── tasks/
│   ├── active/ACTIVE_TASKS.md
│   └── finished/*.md
├── STATUS.md
├── graphs/              # Asset graph snapshots
├── edge_history/        # Iteration logs per edge
└── telem/               # TELEM signal artifacts
```

## Critical Constraints

- **Read-only contract**: NEVER write to any target project's `.ai-workspace/`. The monitor is a pure observer.
- **Single process**: No external databases. State derived from filesystem on startup; watchdog for incremental updates.
- **No JS framework**: HTMX for DOM updates, Mermaid.js (CDN) for diagrams. No build step.

## REQ Key Convention

All requirements use prefix `REQ-GMON-*`. Traceability:

```
Code:    # Implements: REQ-GMON-*
Tests:   # Validates: REQ-GMON-*
Commits: Include REQ-GMON-* in message
```

## Development

All dev commands run from the `imp_python_fastapi/` directory:

```bash
# Install in dev mode
cd imp_python_fastapi
pip install -e ".[dev]"

# Run server
uvicorn genesis_monitor.server.app:app --reload

# Run tests
pytest

# Lint
ruff check code/src/ tests/
```

**Note**: There is no root-level `pyproject.toml`. Each implementation tenant is self-contained.

## AI SDLC Asset Status

| Edge | Status |
|------|--------|
| Intent | Draft |
| Requirements | Not started |
| Design | Not started |
| Code | Not started |
| Unit Tests | Not started |
| UAT Tests | Not started |
