#!/usr/bin/env python3
"""
demo_todo_lifecycle.py — Stream the Todo Dashboard lifecycle into a real workspace
so Genesis Monitor can observe it live in the browser.

Usage:
    python scripts/demo_todo_lifecycle.py [--target DIR] [--delay SECONDS] [--fast]

The script creates a .ai-workspace/ at the target directory and writes events
one by one with a configurable delay between each, so you can watch the Genesis
Monitor dashboard update in real-time.

Default target: ~/projects/todo-demo
Default delay:  3 seconds between events
Fast mode:      0.3 seconds (good for demos, still visible)
"""

import argparse
import json
import sys
import time
import uuid
from pathlib import Path

import yaml


# ── Timestamp helpers ─────────────────────────────────────────────────────────

def _ts(minutes: int) -> str:
    """Return an ISO timestamp N minutes after 08:00 on 2026-03-04."""
    h, m = divmod(minutes, 60)
    return f"2026-03-04T{8 + h:02d}:{m:02d}:00Z"


# ── OL v2 event builder ───────────────────────────────────────────────────────

_OL2_ET = {
    "edge_started": "START",
    "edge_converged": "COMPLETE",
    "iteration_abandoned": "ABORT",
    "command_error": "FAIL",
}


def _event(event_type: str, timestamp: str, project: str,
           edge: str | None = None, feature: str | None = None,
           delta: int | None = None, **fields) -> dict:
    ol_type = _OL2_ET.get(event_type, "OTHER")
    facets: dict = {
        "sdlc:event_type": {"_producer": "demo", "_schemaURL": "demo", "type": event_type}
    }
    if edge or feature:
        rk: dict = {"_producer": "demo", "_schemaURL": "demo"}
        if edge:
            rk["edge"] = edge
        if feature:
            rk["feature_id"] = feature
        facets["sdlc:req_keys"] = rk
    if delta is not None:
        facets["sdlc:delta"] = {"_producer": "demo", "_schemaURL": "demo", "delta": delta}

    original_data: dict = {"event_type": event_type, "project": project}
    if edge:
        original_data["edge"] = edge
    if feature:
        original_data["feature"] = feature
    if delta is not None:
        original_data["delta"] = delta
    original_data.update(fields)

    return {
        "eventType": ol_type,
        "eventTime": timestamp,
        "run": {"runId": str(uuid.uuid4()), "facets": facets},
        "job": {"namespace": f"aisdlc://{project}", "name": edge or "METHODOLOGY"},
        "_metadata": {"project": project, "original_data": original_data},
    }


# ── Lifecycle steps ───────────────────────────────────────────────────────────

STEPS = [
    # (minutes_offset, label, event_kwargs)
    (0,   "Project initialized",
     dict(event_type="project_initialized", project="todo-demo")),

    (5,   "Feature spawned: REQ-F-TODO-001 Todo Item CRUD",
     dict(event_type="spawn_created", project="todo-demo", feature="REQ-F-TODO-001")),

    (10,  "Edge started: intent→requirements",
     dict(event_type="edge_started", project="todo-demo",
          edge="intent→requirements", feature="REQ-F-TODO-001")),

    (30,  "Iteration 1 complete — δ=1 (human review pending)",
     dict(event_type="iteration_completed", project="todo-demo",
          edge="intent→requirements", feature="REQ-F-TODO-001",
          delta=1, iteration=1,
          evaluator_details=[
              {"name": "human_review", "result": "pending"},
              {"name": "structure_check", "result": "pass"},
          ])),

    (45,  "Human review: decision=refined (add priority field, ISO dates)",
     dict(event_type="review_completed", project="todo-demo",
          edge="intent→requirements", feature="REQ-F-TODO-001",
          decision="refined",
          feedback="Add priority field (low/medium/high). Due date: ISO 8601 YYYY-MM-DD.")),

    (60,  "Iteration 2 complete — δ=0  ✓ requirements converging",
     dict(event_type="iteration_completed", project="todo-demo",
          edge="intent→requirements", feature="REQ-F-TODO-001",
          delta=0, iteration=2,
          evaluator_details=[
              {"name": "human_review", "result": "pass"},
              {"name": "structure_check", "result": "pass"},
          ])),

    (60,  "EDGE CONVERGED: intent→requirements  ✓",
     dict(event_type="edge_converged", project="todo-demo",
          edge="intent→requirements", feature="REQ-F-TODO-001")),

    (65,  "Edge started: requirements→design",
     dict(event_type="edge_started", project="todo-demo",
          edge="requirements→design", feature="REQ-F-TODO-001")),

    (90,  "Iteration 1 complete — δ=1 (human review pending)",
     dict(event_type="iteration_completed", project="todo-demo",
          edge="requirements→design", feature="REQ-F-TODO-001",
          delta=1, iteration=1,
          evaluator_details=[
              {"name": "human_review", "result": "pending"},
              {"name": "adr_coverage", "result": "pass"},
          ])),

    (105, "Human review: decision=approved",
     dict(event_type="review_completed", project="todo-demo",
          edge="requirements→design", feature="REQ-F-TODO-001",
          decision="approved", feedback="")),

    (110, "Iteration 2 complete — δ=0  ✓ design converging",
     dict(event_type="iteration_completed", project="todo-demo",
          edge="requirements→design", feature="REQ-F-TODO-001",
          delta=0, iteration=2,
          evaluator_details=[
              {"name": "human_review", "result": "pass"},
              {"name": "adr_coverage", "result": "pass"},
          ])),

    (110, "EDGE CONVERGED: requirements→design  ✓",
     dict(event_type="edge_converged", project="todo-demo",
          edge="requirements→design", feature="REQ-F-TODO-001")),

    (115, "Edge started: code↔unit_tests  [deterministic engine]",
     dict(event_type="edge_started", project="todo-demo",
          edge="code↔unit_tests", feature="REQ-F-TODO-001")),

    (135, "Engine run 1 — δ=3  ✗ FAILING: coverage 62% (<80%), lint, missing REQ tag",
     dict(event_type="iteration_completed", project="todo-demo",
          edge="code↔unit_tests", feature="REQ-F-TODO-001",
          delta=3, iteration=1,
          evaluator_details=[
              {"name": "tests_pass",      "result": "pass"},
              {"name": "test_coverage",   "result": "fail",
               "actual": "62%", "threshold": "80%"},
              {"name": "lint",            "result": "fail",
               "findings": "3 E501 violations in models.py"},
              {"name": "req_keys_tagged", "result": "fail",
               "files_missing": ["routes.py"]},
              {"name": "type_hints",      "result": "pass"},
          ])),

    (150, "Human: fix coverage, lint, add REQ tag to routes.py",
     dict(event_type="review_completed", project="todo-demo",
          edge="code↔unit_tests", feature="REQ-F-TODO-001",
          decision="refined",
          feedback="Fix lint. Add tests for priority_label and bulk_complete. Tag routes.py.")),

    (165, "Engine run 2 — δ=0  ✓ ALL CHECKS PASS: coverage 84%, lint clean, tags present",
     dict(event_type="iteration_completed", project="todo-demo",
          edge="code↔unit_tests", feature="REQ-F-TODO-001",
          delta=0, iteration=2,
          evaluator_details=[
              {"name": "tests_pass",      "result": "pass"},
              {"name": "test_coverage",   "result": "pass", "actual": "84%"},
              {"name": "lint",            "result": "pass"},
              {"name": "req_keys_tagged", "result": "pass"},
              {"name": "type_hints",      "result": "pass"},
          ])),

    (165, "EDGE CONVERGED: code↔unit_tests  ✓  — ALL DONE",
     dict(event_type="edge_converged", project="todo-demo",
          edge="code↔unit_tests", feature="REQ-F-TODO-001")),
]


# ── Workspace setup ───────────────────────────────────────────────────────────

def setup_workspace(project_dir: Path) -> None:
    """Create the .ai-workspace/ skeleton (idempotent)."""
    ws = project_dir / ".ai-workspace"

    (ws / "graph").mkdir(parents=True, exist_ok=True)
    (ws / "features" / "active").mkdir(parents=True, exist_ok=True)
    (ws / "events").mkdir(parents=True, exist_ok=True)
    (ws / "context").mkdir(parents=True, exist_ok=True)

    (ws / "graph" / "graph_topology.yml").write_text(yaml.dump({
        "asset_types": {
            "intent": {"description": "Business intent"},
            "requirements": {"description": "Functional requirements"},
            "design": {"description": "Technical design"},
            "code": {"description": "Implementation"},
            "unit_tests": {"description": "Unit tests"},
        },
        "transitions": [
            {"source": "intent", "target": "requirements"},
            {"source": "requirements", "target": "design"},
            {"source": "code", "target": "unit_tests", "bidirectional": True},
        ],
    }))

    (ws / "features" / "active" / "REQ-F-TODO-001.yml").write_text(yaml.dump({
        "feature": "REQ-F-TODO-001",
        "title": "Todo Item CRUD",
        "status": "in_progress",
        "vector_type": "feature",
        "trajectory": {},
    }))

    (ws / "context" / "project_constraints.yml").write_text(yaml.dump({
        "language": {"primary": "python", "version": ">=3.12"},
        "tools": {
            "test_runner": {
                "command": "pytest",
                "args": "tests/ -q",
                "pass_criterion": "exit code 0",
            },
            "linter": {
                "command": "ruff check",
                "args": "src/",
                "pass_criterion": "exit code 0",
            },
        },
        "thresholds": {"test_coverage_minimum": "80%"},
    }))

    # Truncate the event log so reruns start fresh
    (ws / "events" / "events.jsonl").write_text("")


# ── Main replay loop ──────────────────────────────────────────────────────────

def run(target: Path, delay: float) -> None:
    print(f"\n{'='*60}")
    print(f"  Genesis Monitor — Todo Dashboard Demo")
    print(f"{'='*60}")
    print(f"  Target:  {target}")
    print(f"  Delay:   {delay}s between events")
    print(f"  Monitor: http://localhost:8000")
    print(f"{'='*60}\n")
    print("Setting up workspace...")
    setup_workspace(target)
    print(f"Workspace ready at {target / '.ai-workspace'}\n")

    events_file = target / ".ai-workspace" / "events" / "events.jsonl"

    print(f"Open http://localhost:8000 and find 'todo-demo' in the project tree.")
    print(f"Starting in 3 seconds...\n")
    time.sleep(3)

    for i, (minutes, label, kwargs) in enumerate(STEPS, 1):
        ts = _ts(minutes)
        ev = _event(timestamp=ts, **kwargs)

        # Append to event log
        with events_file.open("a") as f:
            f.write(json.dumps(ev) + "\n")

        marker = "✓" if "CONVERGED" in label else ("✗" in label and "✗" or "→")
        print(f"[{i:02d}/{len(STEPS)}]  T+{minutes:03d}m  {label}")

        if i < len(STEPS):
            time.sleep(delay)

    print(f"\n{'='*60}")
    print("  Demo complete — all edges converged!")
    print(f"  Use the temporal scrubber in the browser to replay history.")
    print(f"  To reset: re-run this script (event log is truncated on start).")
    print(f"{'='*60}\n")


# ── Entry point ───────────────────────────────────────────────────────────────

def main() -> None:
    parser = argparse.ArgumentParser(
        description="Stream the Todo Dashboard lifecycle into a Genesis Monitor workspace."
    )
    parser.add_argument(
        "--target",
        type=Path,
        default=Path.home() / "projects" / "todo-demo",
        help="Directory to create the demo project in (default: ~/projects/todo-demo)",
    )
    parser.add_argument(
        "--delay",
        type=float,
        default=3.0,
        help="Seconds between events (default: 3.0)",
    )
    parser.add_argument(
        "--fast",
        action="store_true",
        help="Set delay to 0.3s (fast demo mode)",
    )
    args = parser.parse_args()

    if args.fast:
        args.delay = 0.3

    # Ensure parent exists
    args.target.mkdir(parents=True, exist_ok=True)

    try:
        run(args.target, args.delay)
    except KeyboardInterrupt:
        print("\nDemo interrupted.")
        sys.exit(0)


if __name__ == "__main__":
    main()
