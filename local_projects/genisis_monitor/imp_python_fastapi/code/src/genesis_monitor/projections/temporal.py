# Implements: REQ-F-NAV-001, REQ-F-NAV-003
"""Temporal projection engine. Reconstructs state from the event log."""

from datetime import datetime
from genesis_monitor.models.core import FeatureVector, EdgeTrajectory, StatusReport
from genesis_monitor.models.events import Event


def reconstruct_features(events: list[Event], timestamp_limit: datetime) -> list[FeatureVector]:
    """Reconstruct feature vectors as they existed at timestamp_limit."""
    features_dict = {}

    # Filter events
    past_events = [e for e in events if e.timestamp <= timestamp_limit]

    for ev in past_events:
        fid = getattr(ev, "feature", None)
        if not fid and ev.event_type == "feature_spawned":
            fid = getattr(ev, "data", {}).get("feature")

        if not fid:
            # Maybe nested in data
            if isinstance(getattr(ev, "data", None), dict):
                fid = ev.data.get("feature")

        if not fid:
            continue

        if fid not in features_dict:
            features_dict[fid] = FeatureVector(feature_id=fid, title=fid)

        feat = features_dict[fid]

        if ev.event_type == "feature_spawned":
            feat.vector_type = getattr(ev, "data", {}).get("vector_type", "feature")
            feat.parent_id = getattr(ev, "data", {}).get("parent")

        edge = getattr(ev, "edge", None)
        if edge:
            # normalize edge
            edge = edge.replace("->", "→")
            target_node = edge.split("→")[-1].strip()

            if target_node not in feat.trajectory:
                feat.trajectory[target_node] = EdgeTrajectory()

            traj = feat.trajectory[target_node]

            if ev.event_type == "edge_started":
                traj.status = "in_progress"
                if not traj.started_at:
                    traj.started_at = ev.timestamp
            elif ev.event_type == "iteration_completed":
                traj.iteration += 1
                if getattr(ev, "delta", None) == 0:
                    traj.status = "converged"
                    if not traj.converged_at:
                        traj.converged_at = ev.timestamp
                else:
                    traj.status = "in_progress"
            elif ev.event_type == "edge_converged":
                traj.status = "converged"
                if not traj.converged_at:
                    traj.converged_at = ev.timestamp

        # Overall status derivation
        if feat.trajectory:
            if all(t.status == "converged" for t in feat.trajectory.values()):
                feat.status = "converged"
            else:
                feat.status = "in_progress"

    return list(features_dict.values())


from genesis_monitor.models.core import PhaseEntry


def reconstruct_status(events: list[Event], timestamp_limit: datetime) -> StatusReport:
    """Reconstruct project status (phase summary) from events."""
    from collections import defaultdict

    edge_states = defaultdict(
        lambda: {"status": "not_started", "iterations": 0, "delta_curve": [], "features": set()}
    )

    past_events = [e for e in events if e.timestamp <= timestamp_limit]

    for ev in past_events:
        edge = getattr(ev, "edge", None)
        if not edge and isinstance(getattr(ev, "data", None), dict):
            edge = ev.data.get("edge")

        if not edge:
            continue
        edge = edge.replace("->", "→")

        state = edge_states[edge]
        feat = getattr(ev, "feature", None)
        if feat:
            state["features"].add(feat)

        if ev.event_type == "edge_started":
            if state["status"] == "not_started":
                state["status"] = "in_progress"
        elif ev.event_type == "iteration_completed":
            state["iterations"] += 1
            state["status"] = "in_progress"
            delta = getattr(ev, "delta", None)
            if delta is not None:
                state["delta_curve"].append(int(delta))
            if delta == 0:
                state["status"] = "converged"
        elif ev.event_type == "edge_converged":
            state["status"] = "converged"

    summary = []
    for edge, data in edge_states.items():
        summary.append(
            PhaseEntry(
                edge=edge,
                status=data["status"],
                iterations=data["iterations"],
                delta_curve=data["delta_curve"],
                evaluator_results={"summary": f"{len(data['features'])} features"},
            )
        )

    return StatusReport(phase_summary=summary)


def get_event_density(events: list[Event], buckets: int = 100) -> list[float]:
    """Compute normalized activity density scores for heatmap visualization."""
    if not events:
        return [0.0] * buckets

    # Sort events by timestamp
    sorted_events = sorted(events, key=lambda e: e.timestamp)
    start_ts = sorted_events[0].timestamp.timestamp()
    end_ts = sorted_events[-1].timestamp.timestamp()
    duration = end_ts - start_ts
    if duration == 0:
        return [1.0] * buckets

    counts = [0] * buckets
    for ev in sorted_events:
        pos = (ev.timestamp.timestamp() - start_ts) / duration
        bucket_idx = min(buckets - 1, int(pos * buckets))
        counts[bucket_idx] += 1

    max_count = max(counts) if any(counts) else 1
    return [c / max_count for c in counts]
