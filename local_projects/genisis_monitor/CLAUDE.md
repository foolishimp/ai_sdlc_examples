# CLAUDE.md — Genesis Monitor

## Project Overview

**Genesis Monitor** is a real-time dashboard for observing AI SDLC methodology execution across projects. It consumes `.ai-workspace/` data and presents asset graphs, convergence status, feature vectors, and TELEM signals via a web UI.

This is **test06** in the ai_sdlc_examples dogfood series — built edge-by-edge using the methodology it monitors.

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
| **Parsers** | Read STATUS.md, ACTIVE_TASKS.md, edge configs, TELEM artifacts | `src/genesis_monitor/parsers/` |
| **Models** | Typed dataclasses: Asset, Edge, FeatureVector, Project | `src/genesis_monitor/models/` |
| **Projections** | Derive views: convergence dashboard, Gantt, feature matrix | `src/genesis_monitor/projections/` |
| **Server** | FastAPI routes, SSE endpoints, Jinja2 templates | `src/genesis_monitor/server/` |
| **Watcher** | watchdog filesystem observer, event→SSE bridge | `src/genesis_monitor/watcher/` |

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

```bash
# Install in dev mode
pip install -e ".[dev]"

# Run server
uvicorn genesis_monitor.server.app:app --reload

# Run tests
pytest

# Lint
ruff check src/ tests/
```

## AI SDLC Asset Status

| Edge | Status |
|------|--------|
| Intent | Draft |
| Requirements | Not started |
| Design | Not started |
| Code | Not started |
| Unit Tests | Not started |
| UAT Tests | Not started |
