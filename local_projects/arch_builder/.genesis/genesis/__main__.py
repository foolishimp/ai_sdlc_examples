# Implements: REQ-ITER-001 (Universal Iterate), REQ-SUPV-003 (Failure Observability), REQ-F-FPC-005 (CLI Construct Mode), REQ-ROBUST-008 (Session Gap Detection)
"""CLI entry point for the genesis engine.

Usage:
    python -m genesis evaluate --edge "code\u2194unit_tests" --feature "REQ-F-ENGINE-001" --asset path/to/file.py
    python -m genesis run-edge --edge "code\u2194unit_tests" --feature "REQ-F-CALC-001" --asset src/calc.py --max-iterations 5
    python -m genesis construct --edge "intent\u2192requirements" --feature "REQ-F-AUTH-001" --asset spec/INTENT.md --output artifacts/req.md

evaluate: single iteration via iterate_edge() \u2014 same Level 4 events as before.
run-edge: loop until converge/spawn/budget via run_edge() \u2014 enables CLI spawn.
construct: construct + evaluate in one call \u2014 F_P builds, F_D gates (ADR-020).

The engine evaluates an asset against an edge's checklist and emits Level 4 events.
The LLM agent calls this for cross-validation (ADR-019).
"""

import argparse
import json
import sys
from pathlib import Path

from .config_loader import load_yaml
from .engine import EngineConfig, IterationRecord, iterate_edge, run_edge


def _find_workspace(start: Path) -> Path:
    """Walk up from start to find a directory containing .ai-workspace/."""
    current = start.resolve()
    for _ in range(10):
        if (current / ".ai-workspace").is_dir():
            return current
        parent = current.parent
        if parent == current:
            break
        current = parent
    return start.resolve()


def _find_constraints(workspace: Path) -> Path:
    """Find project_constraints.yml \u2014 try tenant paths then root."""
    candidates = [
        workspace / ".ai-workspace" / "claude" / "context" / "project_constraints.yml",
        workspace / ".ai-workspace" / "context" / "project_constraints.yml",
    ]
    for c in candidates:
        if c.exists():
            return c
    return candidates[0]  # return first even if missing \u2014 error will be clear


def _find_edge_params(workspace: Path) -> Path:
    """Find edge_params directory \u2014 try workspace then plugin."""
    candidates = [
        workspace / ".ai-workspace" / "graph" / "edges",
        workspace
        / "imp_claude"
        / "code"
        / ".claude-plugin"
        / "plugins"
        / "genesis"
        / "config"
        / "edge_params",
    ]
    for c in candidates:
        if c.is_dir():
            return c
    return candidates[0]


def _find_profiles(workspace: Path) -> Path:
    """Find profiles directory \u2014 try workspace then plugin."""
    candidates = [
        workspace / ".ai-workspace" / "profiles",
        workspace
        / "imp_claude"
        / "code"
        / ".claude-plugin"
        / "plugins"
        / "genesis"
        / "config"
        / "profiles",
    ]
    for c in candidates:
        if c.is_dir():
            return c
    return candidates[0]


def _find_graph_topology(workspace: Path) -> Path:
    """Find graph_topology.yml."""
    candidates = [
        workspace / ".ai-workspace" / "graph" / "graph_topology.yml",
        workspace
        / "imp_claude"
        / "code"
        / ".claude-plugin"
        / "plugins"
        / "genesis"
        / "config"
        / "graph_topology.yml",
    ]
    for c in candidates:
        if c.exists():
            return c
    return candidates[0]


def _emit_command_error(
    workspace: Path, project: str, command: str, category: str, detail: str
) -> None:
    """Emit a command_error event for REQ-SUPV-003 failure observability."""
    from .fd_emit import emit_event, make_event

    events_path = workspace / ".ai-workspace" / "events" / "events.jsonl"
    try:
        emit_event(
            events_path,
            make_event(
                "command_error",
                project or workspace.name,
                command=command,
                error_category=category,
                error_detail=detail,
            ),
        )
    except Exception:
        pass  # Observation failure must not block error reporting


def _check_session_gaps(workspace: Path, project: str) -> None:
    """Detect and emit events for abandoned iterations (REQ-ROBUST-008).

    Scans the event log for edge_started events with no subsequent completion.
    Emits iteration_abandoned events for each detected gap. Idempotent.
    """
    from .fd_emit import emit_event, make_event
    from .workspace_state import detect_abandoned_iterations, load_events

    events = load_events(workspace)
    abandoned = detect_abandoned_iterations(events)

    if not abandoned:
        return

    events_path = workspace / ".ai-workspace" / "events" / "events.jsonl"
    for gap in abandoned:
        try:
            emit_event(
                events_path,
                make_event(
                    "iteration_abandoned",
                    project,
                    feature=gap["feature"],
                    edge=gap["edge"],
                    last_event_timestamp=gap["last_event_timestamp"],
                ),
            )
        except Exception:
            pass  # Observation failure must not block engine startup

    print(
        f"genesis: detected {len(abandoned)} abandoned iteration(s) from prior session",
        file=sys.stderr,
    )


# \u2500\u2500 Shared helpers \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500


_EDGE_MAP = {
    "code\u2194unit_tests": "tdd",
    "design\u2192test_cases": "design_tests",
    "design\u2192uat_tests": "bdd",
}


def _load_asset(args: argparse.Namespace, workspace: Path) -> str | None:
    """Load asset content from file or stdin. Returns None on error."""
    if args.asset == "-":
        return sys.stdin.read()
    asset_path = Path(args.asset)
    if not asset_path.exists():
        _emit_command_error(
            workspace,
            "",
            args.command,
            "missing_asset",
            f"Asset not found: {args.asset}",
        )
        print(json.dumps({"error": f"Asset not found: {args.asset}"}), file=sys.stderr)
        return None
    return asset_path.read_text()


def _build_config(args: argparse.Namespace, workspace: Path) -> EngineConfig | None:
    """Build EngineConfig from CLI args. Returns None on error."""
    constraints_path = (
        Path(args.constraints) if args.constraints else _find_constraints(workspace)
    )
    if not constraints_path.exists():
        _emit_command_error(
            workspace,
            "",
            args.command,
            "missing_constraints",
            f"Constraints not found: {constraints_path}",
        )
        print(
            json.dumps({"error": f"Constraints not found: {constraints_path}"}),
            file=sys.stderr,
        )
        return None
    constraints = load_yaml(constraints_path)

    topo_path = _find_graph_topology(workspace)
    graph_topology = load_yaml(topo_path) if topo_path.exists() else {}

    max_iters = getattr(args, "max_iterations", 1)

    return EngineConfig(
        project_name=constraints.get("project", {}).get("name", workspace.name),
        workspace_path=workspace,
        edge_params_dir=_find_edge_params(workspace),
        profiles_dir=_find_profiles(workspace),
        constraints=constraints,
        graph_topology=graph_topology,
        model=args.model,
        max_iterations_per_edge=max_iters,
        claude_timeout=getattr(args, "timeout", 300),
        deterministic_only=getattr(args, "deterministic_only", False),
        fd_timeout=getattr(args, "fd_timeout", 120),
        stall_timeout=getattr(args, "stall_timeout", 60),
        budget_usd=getattr(args, "budget_usd", 2.0),
    )


def _resolve_edge_config(edge: str, edge_params_dir: Path) -> Path | None:
    """Resolve edge name to config file path. Returns None if not found."""
    edge_filename = _EDGE_MAP.get(
        edge, edge.replace("\u2192", "_").replace("\u2194", "_").replace(" ", "")
    )
    edge_config_path = edge_params_dir / f"{edge_filename}.yml"
    if edge_config_path.exists():
        return edge_config_path
    return None


def _format_record(record: IterationRecord) -> dict:
    """Format an IterationRecord as a JSON-serializable dict."""
    ev = record.evaluation
    return {
        "iteration": record.iteration,
        "delta": ev.delta,
        "converged": ev.converged,
        "spawn_requested": ev.spawn_requested,
        "checks": [
            {
                "name": c.name,
                "type": c.check_type,
                "outcome": c.outcome.value,
                "required": c.required,
                "message": c.message[:200] if c.message else "",
            }
            for c in ev.checks
        ],
        "escalations": ev.escalations,
    }


# \u2500\u2500 Commands \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500


def cmd_evaluate(args: argparse.Namespace) -> int:
    """Evaluate an asset against an edge's checklist. Emit Level 4 events."""
    workspace = Path(args.workspace) if args.workspace else _find_workspace(Path.cwd())

    asset_content = _load_asset(args, workspace)
    if asset_content is None:
        return 1

    config = _build_config(args, workspace)
    if config is None:
        return 1

    _check_session_gaps(workspace, config.project_name)

    edge_config_path = _resolve_edge_config(args.edge, config.edge_params_dir)
    if edge_config_path is None:
        _emit_command_error(
            workspace,
            config.project_name,
            "evaluate",
            "missing_edge_config",
            f"Edge config not found for: {args.edge}",
        )
        print(
            json.dumps({"error": f"Edge config not found for: {args.edge}"}),
            file=sys.stderr,
        )
        return 1

    edge_config = load_yaml(edge_config_path)

    record = iterate_edge(
        edge=args.edge,
        edge_config=edge_config,
        config=config,
        feature_id=args.feature,
        asset_content=asset_content,
        context=args.context or "",
        iteration=args.iteration,
    )

    # Build output \u2014 preserve the same schema callers expect
    formatted = _format_record(record)
    ev = record.evaluation
    passed = sum(1 for c in ev.checks if c.outcome.value == "pass")
    failed = sum(1 for c in ev.checks if c.outcome.value in ("fail", "error"))
    skipped = sum(1 for c in ev.checks if c.outcome.value == "skip")

    output = {
        "edge": args.edge,
        "feature": args.feature,
        "iteration": args.iteration,
        "delta": ev.delta,
        "converged": ev.converged,
        "evaluators": {
            "passed": passed,
            "failed": failed,
            "skipped": skipped,
            "total": len(ev.checks),
        },
        "checks": formatted["checks"],
        "escalations": ev.escalations,
        "event_emitted": record.event_emitted,
        "source": "engine_cli",
    }

    print(json.dumps(output, indent=2))
    return 0 if ev.converged else 1


def cmd_run_edge(args: argparse.Namespace) -> int:
    """Loop on an edge until converge/spawn/budget. Enables CLI spawn."""
    workspace = Path(args.workspace) if args.workspace else _find_workspace(Path.cwd())

    asset_content = _load_asset(args, workspace)
    if asset_content is None:
        return 1

    config = _build_config(args, workspace)
    if config is None:
        return 1

    _check_session_gaps(workspace, config.project_name)

    construct = getattr(args, "construct", False)
    output_file = Path(args.output) if getattr(args, "output", None) else None

    # run_edge does its own edge config file lookup
    profile_path = config.profiles_dir / "standard.yml"
    profile = load_yaml(profile_path) if profile_path.exists() else {}

    records = run_edge(
        edge=args.edge,
        config=config,
        feature_id=args.feature,
        profile=profile,
        asset_content=asset_content,
        context=args.context or "",
        construct=construct,
        output_path=output_file,
    )

    last = records[-1] if records else None
    output = {
        "edge": args.edge,
        "feature": args.feature,
        "total_iterations": len(records),
        "final_delta": last.evaluation.delta if last else -1,
        "converged": last.evaluation.converged if last else False,
        "spawn_requested": last.evaluation.spawn_requested if last else "",
        "iterations": [_format_record(r) for r in records],
    }

    if last and last.evaluation.spawn_requested:
        child_id = last.evaluation.spawn_requested
        output["child_path"] = str(
            config.workspace_path
            / ".ai-workspace"
            / "features"
            / "active"
            / f"{child_id}.yml"
        )

    if last and last.fp_result and not last.fp_result.audit.skipped:
        fp = last.fp_result
        output["fp_actor"] = {
            "transport": fp.audit.transport,
            "converged": fp.converged,
            "cost_usd": fp.cost_usd,
            "duration_ms": fp.duration_ms,
            "artifacts": len(fp.artifacts),
        }

    print(json.dumps(output, indent=2))

    if last and (last.evaluation.converged or last.evaluation.spawn_requested):
        return 0
    return 1


def cmd_construct(args: argparse.Namespace) -> int:
    """Construct + evaluate in one call. F_P builds, F_D gates (ADR-020)."""
    workspace = Path(args.workspace) if args.workspace else _find_workspace(Path.cwd())

    asset_content = _load_asset(args, workspace)
    if asset_content is None:
        return 1

    config = _build_config(args, workspace)
    if config is None:
        return 1

    _check_session_gaps(workspace, config.project_name)

    output_file = Path(args.output) if args.output else None

    edge_config_path = _resolve_edge_config(args.edge, config.edge_params_dir)
    if edge_config_path is None:
        _emit_command_error(
            workspace,
            config.project_name,
            "construct",
            "missing_edge_config",
            f"Edge config not found for: {args.edge}",
        )
        print(
            json.dumps({"error": f"Edge config not found for: {args.edge}"}),
            file=sys.stderr,
        )
        return 1

    edge_config = load_yaml(edge_config_path)

    record = iterate_edge(
        edge=args.edge,
        edge_config=edge_config,
        config=config,
        feature_id=args.feature,
        asset_content=asset_content,
        context=args.context or "",
        iteration=args.iteration,
        construct=True,
        output_path=output_file,
    )

    # Build output
    formatted = _format_record(record)
    ev = record.evaluation
    passed = sum(1 for c in ev.checks if c.outcome.value == "pass")
    failed = sum(1 for c in ev.checks if c.outcome.value in ("fail", "error"))
    skipped = sum(1 for c in ev.checks if c.outcome.value == "skip")

    output = {
        "edge": args.edge,
        "feature": args.feature,
        "iteration": args.iteration,
        "delta": ev.delta,
        "converged": ev.converged,
        "evaluators": {
            "passed": passed,
            "failed": failed,
            "skipped": skipped,
            "total": len(ev.checks),
        },
        "checks": formatted["checks"],
        "escalations": ev.escalations,
        "event_emitted": record.event_emitted,
        "source": "engine_cli",
    }

    if record.fp_result and not record.fp_result.audit.skipped:
        fp = record.fp_result
        output["fp_actor"] = {
            "transport": fp.audit.transport,
            "converged": fp.converged,
            "cost_usd": fp.cost_usd,
            "duration_ms": fp.duration_ms,
            "artifacts": len(fp.artifacts),
        }
        if output_file:
            output["output_path"] = str(output_file)

    print(json.dumps(output, indent=2))
    return 0 if ev.converged else 1


# \u2500\u2500 Shared CLI args \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500


def _add_shared_args(parser: argparse.ArgumentParser) -> None:
    """Add arguments shared by evaluate and run-edge."""
    parser.add_argument(
        "--edge", required=True, help="Edge name (e.g., 'code\u2194unit_tests')"
    )
    parser.add_argument(
        "--feature", required=True, help="Feature ID (e.g., 'REQ-F-ENGINE-001')"
    )
    parser.add_argument(
        "--asset", required=True, help="Path to asset file, or '-' for stdin"
    )
    parser.add_argument(
        "--workspace", default=None, help="Workspace root (auto-detected if omitted)"
    )
    parser.add_argument(
        "--constraints", default=None, help="Path to project_constraints.yml"
    )
    parser.add_argument("--context", default="", help="Additional context string")
    parser.add_argument(
        "--model", default="sonnet", help="Model for agent checks (default: sonnet)"
    )
    parser.add_argument(
        "--timeout", type=int, default=120, help="Timeout for agent checks in seconds"
    )
    parser.add_argument(
        "--deterministic-only",
        action="store_true",
        help="Only run F_D checks (skip agent and human). Fast, free, Level 4.",
    )
    parser.add_argument(
        "--fd-timeout",
        type=int,
        default=60,
        help=(
            "Stall timeout for F_D subprocess checks in seconds (default: 60). "
            "Kills the subprocess if it produces NO output for this many seconds. "
            "NOT a wall-clock timeout — a healthy slow process (e.g. long test suite) "
            "will run to completion. Wall ceiling = stall_timeout × 20."
        ),
    )
    parser.add_argument(
        "--stall-timeout",
        type=int,
        default=60,
        help="Stall detection timeout for F_P calls in seconds (default: 60, 0=disable)",
    )
    parser.add_argument(
        "--budget-usd",
        type=float,
        default=2.0,
        dest="budget_usd",
        help="Max spend per construct invocation in USD (default: 2.0)",
    )


def main() -> int:
    parser = argparse.ArgumentParser(
        prog="genesis",
        description="Genesis F_D engine \u2014 deterministic evaluation with Level 4 events",
    )
    subparsers = parser.add_subparsers(dest="command")

    # evaluate subcommand
    eval_parser = subparsers.add_parser(
        "evaluate",
        help="Evaluate an asset against an edge's checklist (single iteration)",
    )
    _add_shared_args(eval_parser)
    eval_parser.add_argument(
        "--iteration", type=int, default=1, help="Iteration number for event"
    )

    # run-edge subcommand
    run_parser = subparsers.add_parser(
        "run-edge",
        help="Loop on edge until converge/spawn/budget",
    )
    _add_shared_args(run_parser)
    run_parser.add_argument(
        "--max-iterations",
        type=int,
        default=10,
        help="Maximum iterations before stopping (default: 10)",
    )
    run_parser.add_argument(
        "--construct",
        action="store_true",
        help="Enable F_P construct before evaluate (ADR-020)",
    )
    run_parser.add_argument(
        "--output",
        default=None,
        help="Path to write constructed artifact",
    )

    # construct subcommand (ADR-020)
    construct_parser = subparsers.add_parser(
        "construct",
        help="Construct artifact + evaluate in one call (F_P builds, F_D gates)",
    )
    _add_shared_args(construct_parser)
    construct_parser.add_argument(
        "--iteration", type=int, default=1, help="Iteration number for event"
    )
    construct_parser.add_argument(
        "--output",
        default=None,
        help="Path to write constructed artifact",
    )

    args = parser.parse_args()

    if args.command == "evaluate":
        return cmd_evaluate(args)
    elif args.command == "run-edge":
        return cmd_run_edge(args)
    elif args.command == "construct":
        return cmd_construct(args)
    else:
        parser.print_help()
        return 1


if __name__ == "__main__":
    sys.exit(main())
