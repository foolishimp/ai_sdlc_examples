# Implements: REQ-F-DASH-003
"""Build convergence table from events (edge_started, iteration_completed, edge_converged)."""

from __future__ import annotations

from collections import defaultdict

from genesis_monitor.models.core import EdgeConvergence, StatusReport
from genesis_monitor.models.events import Event


def build_convergence_table_from_events(events: list[Event]) -> list[EdgeConvergence]:
    """Derive convergence view from events.jsonl data.

    Aggregates edge_started, iteration_completed, and edge_converged events
    per unique edge to build the convergence table.
    """
    if not events:
        return []

    # Track per-edge state
    edge_state: dict[str, dict] = defaultdict(lambda: {
        "iterations": 0,
        "status": "not_started",
        "started_at": None,
        "converged_at": None,
        "features": set(),
        "convergence_type": "",
        "delta_curve": [],
    })

    for e in events:
        edge = getattr(e, "edge", None) or e.data.get("edge", "")
        if not edge:
            continue

        feature = getattr(e, "feature", None) or e.data.get("feature", "")

        if e.event_type == "edge_started":
            state = edge_state[edge]
            if state["status"] == "not_started":
                state["status"] = "in_progress"
                state["started_at"] = e.timestamp
            if feature:
                state["features"].add(feature)

        elif e.event_type == "iteration_completed":
            state = edge_state[edge]
            state["iterations"] += 1
            state["status"] = "in_progress"
            if feature:
                state["features"].add(feature)
            delta = getattr(e, "delta", None)
            if delta is None:
                delta = e.data.get("delta")
            if delta is not None:
                state["delta_curve"].append(int(delta))

        elif e.event_type == "edge_converged":
            state = edge_state[edge]
            state["status"] = "converged"
            state["converged_at"] = e.timestamp
            conv_type = getattr(e, "convergence_type", "") or e.data.get("convergence_type", "")
            if conv_type:
                state["convergence_type"] = conv_type
            if feature:
                state["features"].add(feature)

    # Build rows sorted by first appearance order
    rows: list[EdgeConvergence] = []
    for edge, state in edge_state.items():
        duration = ""
        if state["started_at"] and state["converged_at"]:
            delta = state["converged_at"] - state["started_at"]
            hours = delta.total_seconds() / 3600
            if hours < 1:
                duration = f"{int(delta.total_seconds() / 60)}m"
            else:
                duration = f"{hours:.1f}h"

        summary = f"{len(state['features'])} feature{'s' if len(state['features']) != 1 else ''}"

        rows.append(EdgeConvergence(
            edge=edge,
            iterations=state["iterations"],
            evaluator_summary=summary,
            status=state["status"],
            started_at=state["started_at"],
            converged_at=state["converged_at"],
            duration=duration,
            convergence_type=state["convergence_type"],
            delta_curve=state["delta_curve"],
        ))

    return rows


def build_convergence_table(
    status: StatusReport | None,
    events: list[Event] | None = None,
) -> list[EdgeConvergence]:
    """Derive convergence view — prefer events, fall back to status report.

    The events-based path provides richer data (timestamps, duration,
    convergence type). Falls back to STATUS.md parsing when events
    are unavailable.
    """
    # Prefer events-based derivation
    if events:
        result = build_convergence_table_from_events(events)
        if result:
            return result

    # Fallback: STATUS.md phase_summary
    if not status or not status.phase_summary:
        return []

    rows: list[EdgeConvergence] = []
    for entry in status.phase_summary:
        if "summary" in entry.evaluator_results:
            summary = entry.evaluator_results["summary"]
        elif entry.evaluator_results:
            total = len(entry.evaluator_results)
            passed = sum(1 for v in entry.evaluator_results.values() if v.lower() == "pass")
            summary = f"{passed}/{total} pass"
        else:
            summary = "no evaluators"

        rows.append(EdgeConvergence(
            edge=entry.edge,
            iterations=entry.iterations,
            evaluator_summary=summary,
            source_findings=entry.source_findings,
            process_gaps=entry.process_gaps,
            status=entry.status,
        ))

    return rows
