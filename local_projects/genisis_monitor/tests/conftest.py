# Validates: REQ-F-DISC-001, REQ-F-PARSE-001 through REQ-F-PARSE-006
"""Shared test fixtures for Genesis Monitor tests."""

import json
from pathlib import Path

import pytest
import yaml


@pytest.fixture
def tmp_workspace(tmp_path: Path) -> Path:
    """Create a minimal .ai-workspace/ structure for testing."""
    ws = tmp_path / "test_project" / ".ai-workspace"

    # STATUS.md
    (ws).mkdir(parents=True)
    (ws / "STATUS.md").write_text("""\
# CDME — Project Status

**Project**: Test CDME Project

## Phase Completion Summary

| Edge | Status | Iterations | Agent | Human | Deterministic | Source Findings | Process Gaps |
|------|--------|------------|-------|-------|---------------|----------------|-------------|
| intent→requirements | converged | 1 | pass | skip | pass | 3 | 0 |
| requirements→design | converged | 1 | pass | pass | skip | 2 | 1 |
| design→code | in_progress | 2 | pass | skip | fail | 1 | 0 |

```mermaid
gantt
    title CDME Methodology Timeline
    dateFormat YYYY-MM-DD
    section Edges
    intent→requirements :done, ir, 2026-02-01, 1d
    requirements→design :done, rd, 2026-02-02, 1d
    design→code         :active, dc, 2026-02-03, 2d
```

## Self-Reflection (TELEM Signals)

### TELEM-001: Test signal one
**Signal**: This is a test signal body

### TELEM-002: Test signal two
**Signal**: Second test signal body

## Aggregate Metrics

- **Total REQ keys**: 26
- **Coverage**: 85%
""")

    # Feature vectors
    features_dir = ws / "features" / "active"
    features_dir.mkdir(parents=True)
    (features_dir / "REQ-F-GMON-001.yml").write_text(yaml.dump({
        "feature": "REQ-F-GMON-001",
        "title": "Genesis Monitor Dashboard",
        "status": "in_progress",
        "vector_type": "feature",
        "trajectory": {
            "requirements": {"status": "converged", "iteration": 1, "evaluator_results": {"agent": "pass"}},
            "design": {"status": "converged", "iteration": 1, "evaluator_results": {"agent": "pass"}},
            "code": {"status": "in_progress", "iteration": 2, "evaluator_results": {}},
        },
    }))

    # Graph topology
    graph_dir = ws / "graph"
    graph_dir.mkdir()
    (graph_dir / "graph_topology.yml").write_text(yaml.dump({
        "asset_types": {
            "intent": {"description": "Business intent"},
            "requirements": {"description": "Functional requirements"},
            "design": {"description": "Technical design"},
            "code": {"description": "Implementation"},
        },
        "transitions": [
            {"source": "intent", "target": "requirements", "edge_type": "intent_requirements"},
            {"source": "requirements", "target": "design", "edge_type": "requirements_design"},
            {"source": "design", "target": "code", "edge_type": "design_code"},
        ],
    }))

    # Events
    events_dir = ws / "events"
    events_dir.mkdir()
    events = [
        {"timestamp": "2026-02-01T10:00:00", "event_type": "edge_started", "project": "test"},
        {"timestamp": "2026-02-01T10:05:00", "event_type": "edge_converged", "project": "test"},
    ]
    (events_dir / "events.jsonl").write_text(
        "\n".join(json.dumps(e) for e in events) + "\n"
    )

    # Tasks
    tasks_dir = ws / "tasks" / "active"
    tasks_dir.mkdir(parents=True)
    (tasks_dir / "ACTIVE_TASKS.md").write_text("""\
# Active Tasks

| # | Title | Status | Priority |
|---|-------|--------|----------|
| 1 | Write requirements | completed | high |
| 2 | Write design | in_progress | high |
| 3 | Implement code | pending | medium |
""")

    # Constraints
    context_dir = ws / "context"
    context_dir.mkdir()
    (context_dir / "project_constraints.yml").write_text(yaml.dump({
        "language": {"primary": "python"},
        "tools": {"test_runner": {"command": "pytest", "args": [], "pass_criterion": "exit 0"}},
        "thresholds": {"test_coverage_minimum": "80%"},
    }))

    return tmp_path / "test_project"


@pytest.fixture
def workspace_path(tmp_workspace: Path) -> Path:
    """Return the .ai-workspace path."""
    return tmp_workspace / ".ai-workspace"
