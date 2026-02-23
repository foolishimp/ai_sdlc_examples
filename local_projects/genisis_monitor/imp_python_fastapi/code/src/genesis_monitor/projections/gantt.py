# Implements: REQ-F-DASH-004
"""Generate Mermaid Gantt charts from feature vector trajectories."""

from __future__ import annotations

from datetime import datetime, timezone

from genesis_monitor.models.core import FeatureVector, StatusReport


def build_gantt_mermaid(
    status: StatusReport | None,
    features: list[FeatureVector] | None = None,
) -> str | None:
    """Build a Mermaid gantt chart.

    Strategy:
    1. Generate from feature vector edge trajectories with real timestamps.
    2. Fall back to embedded gantt in STATUS.md.
    3. Generate a status-only gantt (no timestamps) as last resort.
    """
    if features:
        gantt = _gantt_from_features(features)
        if gantt:
            return gantt

    # Fallback: embedded mermaid in STATUS.md
    if status and status.gantt_mermaid:
        return status.gantt_mermaid

    return None


def _gantt_from_features(features: list[FeatureVector]) -> str | None:
    """Generate a Mermaid gantt chart from feature vector trajectories.

    Uses real started_at/converged_at timestamps when available.
    Falls back to status-only rendering when timestamps are missing.
    """
    features_with_traj = [f for f in features if f.trajectory]
    if not features_with_traj:
        return None

    # Check if any feature has real timestamps
    has_timestamps = any(
        traj.started_at is not None
        for f in features_with_traj
        for traj in f.trajectory.values()
    )

    if has_timestamps:
        return _gantt_with_timestamps(features_with_traj)
    else:
        return _gantt_status_only(features_with_traj)


def _gantt_with_timestamps(features: list[FeatureVector]) -> str:
    """Generate gantt with real datetime ranges from edge trajectories."""
    lines = [
        "gantt",
        "    dateFormat YYYY-MM-DDTHH:mm",
        "    axisFormat %m-%d %H:%M",
    ]

    task_counter = 0
    for feat in features:
        label = feat.feature_id
        if feat.title:
            label = f"{feat.feature_id} — {feat.title}"
        safe_label = label.replace(":", " -")
        lines.append(f"    section {safe_label}")

        for edge_name, traj in feat.trajectory.items():
            safe_edge = edge_name.replace("→", " to ").replace("↔", " and ")
            tag = _mermaid_task_tag(traj.status)
            task_id = f"t{task_counter}"
            task_counter += 1

            if traj.started_at and traj.converged_at:
                start = _fmt_dt(traj.started_at)
                end = _fmt_dt(traj.converged_at)
                lines.append(f"    {safe_edge} :{tag}{task_id}, {start}, {end}")
            elif traj.started_at:
                start = _fmt_dt(traj.started_at)
                # In-progress: show as starting from start, 30min default width
                lines.append(f"    {safe_edge} :{tag}{task_id}, {start}, 30m")
            else:
                # No timestamps but has status — skip in timestamp mode
                continue

    return "\n".join(lines)


def _gantt_status_only(features: list[FeatureVector]) -> str:
    """Generate a gantt showing edge status without real timestamps.

    Uses sequential positioning so edges appear in order with their status.
    """
    lines = [
        "gantt",
        "    dateFormat X",
        "    axisFormat  ",
    ]

    pos = 0
    for feat in features:
        label = feat.feature_id
        if feat.title:
            label = f"{feat.feature_id} — {feat.title}"
        safe_label = label.replace(":", " -")
        lines.append(f"    section {safe_label}")

        for edge_name, traj in feat.trajectory.items():
            safe_edge = edge_name.replace("→", " to ").replace("↔", " and ")
            tag = _mermaid_task_tag(traj.status)
            lines.append(f"    {safe_edge} :{tag}{pos}, {pos + 1}")
            pos += 1

    return "\n".join(lines)


def _mermaid_task_tag(status: str) -> str:
    """Map edge status to a Mermaid gantt task tag."""
    if status == "converged":
        return "done, "
    elif status == "in_progress":
        return "active, "
    else:
        return ""


def _fmt_dt(dt: datetime) -> str:
    """Format datetime for Mermaid gantt (YYYY-MM-DDTHH:mm)."""
    if dt.tzinfo is not None:
        dt = dt.astimezone(timezone.utc).replace(tzinfo=None)
    return dt.strftime("%Y-%m-%dT%H:%M")
