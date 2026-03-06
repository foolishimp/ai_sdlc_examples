# Implements: REQ-LIFE-001 (Recursive Spawn), REQ-LIFE-002 (Fold-Back)
"""F_D spawn — deterministic child vector creation, linkage, fold-back, time-box.

All operations are filesystem + YAML + events. No LLM calls.
The engine detects spawn conditions, creates child vectors on disk,
links parent↔child, emits events, and handles fold-back.
Actual child iteration happens via subsequent /gen-start invocations.
"""

import json
import re
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional

import yaml

from .fd_emit import emit_event, make_event
from .models import FoldBackResult, SpawnRequest, SpawnResult


# ── Profile defaults for child vectors ──────────────────────────────────

_VECTOR_TYPE_PROFILES = {
    "discovery": "minimal",
    "spike": "spike",
    "poc": "poc",
    "hotfix": "hotfix",
    "feature": "standard",
}

_VECTOR_TYPE_TIMEBOXES = {
    "discovery": "1 day",
    "spike": "1 week",
    "poc": "3 weeks",
    "hotfix": "4 hours",
}


# ── Spawn detection ────────────────────────────────────────────────────


def detect_spawn_condition(
    events: list[dict],
    feature: str,
    edge: str,
    threshold: int = 3,
) -> Optional[SpawnRequest]:
    """Check if a feature is stuck on an edge — same non-zero delta repeated.

    Scans iteration_completed events for this feature/edge.
    If the last `threshold` consecutive iterations have the same non-zero delta,
    the feature is stuck and a SpawnRequest is returned.
    """
    # Filter to iteration_completed events for this feature+edge
    relevant = [
        e
        for e in events
        if e.get("event_type") == "iteration_completed"
        and e.get("feature") == feature
        and e.get("edge") == edge
    ]

    if len(relevant) < threshold:
        return None

    # Check last N deltas
    last_n = relevant[-threshold:]
    deltas = [e.get("delta", -1) for e in last_n]

    # All same AND non-zero
    if len(set(deltas)) == 1 and deltas[0] > 0:
        # Build question from failing checks in last event
        last_event = relevant[-1]
        failing_checks = [
            c["name"]
            for c in last_event.get("checks", [])
            if c.get("outcome") in ("fail", "error") and c.get("required", True)
        ]
        check_names = ", ".join(failing_checks) if failing_checks else "unknown checks"
        question = (
            f"Why is {feature} stuck on {edge}? "
            f"Failing checks: {check_names}. "
            f"Delta has been {deltas[0]} for {threshold} consecutive iterations."
        )

        return SpawnRequest(
            question=question,
            vector_type="discovery",
            parent_feature=feature,
            triggered_at_edge=edge,
            context_hints={
                "stuck_delta": deltas[0],
                "stuck_iterations": threshold,
                "failing_checks": failing_checks,
            },
        )

    return None


# ── Child vector creation ──────────────────────────────────────────────


def _next_seq(features_dir: Path, type_prefix: str) -> int:
    """Scan features/active/*.yml for IDs matching REQ-F-{TYPE}-*, return max+1."""
    pattern = re.compile(rf"REQ-F-{type_prefix}-(\d+)", re.IGNORECASE)
    max_seq = 0

    if features_dir.exists():
        for yml_file in features_dir.glob("*.yml"):
            match = pattern.search(yml_file.stem)
            if match:
                max_seq = max(max_seq, int(match.group(1)))

    return max_seq + 1


def create_child_vector(
    workspace: Path,
    spawn_request: SpawnRequest,
    project_name: str,
) -> SpawnResult:
    """Create a child feature vector YAML on disk.

    Generates child ID, populates from template structure,
    sets parent linkage and time box from vector_type defaults.
    """
    features_dir = workspace / ".ai-workspace" / "features" / "active"
    features_dir.mkdir(parents=True, exist_ok=True)

    # Generate child ID
    vtype = spawn_request.vector_type.upper()
    seq = _next_seq(features_dir, vtype)
    child_id = f"REQ-F-{vtype}-{seq:03d}"

    # Profile from vector type
    profile = _VECTOR_TYPE_PROFILES.get(spawn_request.vector_type, "standard")

    # Time box from vector type
    time_box_duration = _VECTOR_TYPE_TIMEBOXES.get(spawn_request.vector_type)
    now = datetime.now(timezone.utc).isoformat()

    child_vector = {
        "feature": child_id,
        "title": spawn_request.question[:80],
        "intent": f"SPAWN-{spawn_request.parent_feature}",
        "vector_type": spawn_request.vector_type,
        "profile": profile,
        "status": "pending",
        "convergence_type": "",
        "created": now,
        "updated": now,
        "parent": {
            "feature": spawn_request.parent_feature,
            "edge": spawn_request.triggered_at_edge,
            "reason": spawn_request.question,
        },
        "children": [],
        "time_box": {
            "enabled": time_box_duration is not None,
            "duration": time_box_duration or "",
            "started": "",
            "on_expiry": "fold_back",
        },
        "trajectory": {},
        "constraints": {
            "acceptance_criteria": [],
            "threshold_overrides": {},
            "additional_checks": [],
        },
    }

    child_path = features_dir / f"{child_id}.yml"
    with open(child_path, "w") as f:
        yaml.dump(child_vector, f, default_flow_style=False, sort_keys=False)

    return SpawnResult(
        child_id=child_id,
        child_path=str(child_path),
        parent_updated=False,  # link_parent_child does this
        event_emitted=False,  # emit_spawn_events does this
        profile=profile,
    )


# ── Parent-child linkage ──────────────────────────────────────────────


def link_parent_child(
    workspace: Path,
    parent_id: str,
    child_id: str,
    vector_type: str,
    spawn_request: SpawnRequest,
) -> bool:
    """Update parent feature vector: add child, block parent edge."""
    features_dir = workspace / ".ai-workspace" / "features" / "active"
    parent_path = features_dir / f"{parent_id}.yml"

    if not parent_path.exists():
        return False

    with open(parent_path) as f:
        parent = yaml.safe_load(f) or {}

    # Add to children list
    children = parent.get("children", []) or []
    children.append(
        {
            "feature": child_id,
            "vector_type": vector_type,
            "status": "pending",
            "fold_back_status": "pending",
            "fold_back_payload": "",
        }
    )
    parent["children"] = children

    # Block the parent's current edge
    trajectory = parent.get("trajectory", {})
    edge_key = (
        spawn_request.triggered_at_edge.replace("→", "_")
        .replace("↔", "_")
        .replace(" ", "")
    )
    if edge_key in trajectory:
        trajectory[edge_key]["status"] = "blocked"
        trajectory[edge_key]["blocked_by"] = child_id
    else:
        trajectory[edge_key] = {"status": "blocked", "blocked_by": child_id}
    parent["trajectory"] = trajectory

    parent["updated"] = datetime.now(timezone.utc).isoformat()

    with open(parent_path, "w") as f:
        yaml.dump(parent, f, default_flow_style=False, sort_keys=False)

    return True


# ── Event emission ────────────────────────────────────────────────────


def emit_spawn_events(
    workspace: Path,
    project_name: str,
    spawn_request: SpawnRequest,
    spawn_result: SpawnResult,
) -> None:
    """Emit spawn_created event."""
    events_path = workspace / ".ai-workspace" / "events" / "events.jsonl"
    emit_event(
        events_path,
        make_event(
            "spawn_created",
            project_name,
            parent_feature=spawn_request.parent_feature,
            child_feature=spawn_result.child_id,
            child_path=spawn_result.child_path,
            vector_type=spawn_request.vector_type,
            triggered_at_edge=spawn_request.triggered_at_edge,
            question=spawn_request.question,
            profile=spawn_result.profile,
        ),
    )


# ── Fold-back ─────────────────────────────────────────────────────────


def fold_back_child(
    workspace: Path,
    parent_id: str,
    child_id: str,
    project_name: str,
    payload_summary: str = "",
) -> FoldBackResult:
    """Fold a child's results back to the parent.

    Writes fold-back payload, updates parent's children entry,
    unblocks parent edge, emits event.
    """
    features_dir = workspace / ".ai-workspace" / "features" / "active"

    # Load child to get status
    child_path = features_dir / f"{child_id}.yml"
    child_status = "unknown"
    if child_path.exists():
        with open(child_path) as f:
            child = yaml.safe_load(f) or {}
        child_status = child.get("status", "unknown")

    # Write fold-back payload
    fold_back_dir = workspace / ".ai-workspace" / "features" / "fold-back"
    fold_back_dir.mkdir(parents=True, exist_ok=True)
    payload_path = fold_back_dir / f"{child_id}.md"

    payload_content = (
        f"# Fold-Back: {child_id} → {parent_id}\n\n"
        f"**Status**: {child_status}\n"
        f"**Summary**: {payload_summary or 'No summary provided.'}\n"
    )
    payload_path.write_text(payload_content)

    # Update parent
    parent_path = features_dir / f"{parent_id}.yml"
    parent_unblocked = False
    if parent_path.exists():
        with open(parent_path) as f:
            parent = yaml.safe_load(f) or {}

        # Update children entry
        children = parent.get("children", []) or []
        for child_entry in children:
            if child_entry.get("feature") == child_id:
                child_entry["status"] = child_status
                child_entry["fold_back_status"] = "folded_back"
                child_entry["fold_back_payload"] = str(payload_path)
                break
        parent["children"] = children

        # Unblock parent edge — find the edge blocked by this child
        trajectory = parent.get("trajectory", {})
        for edge_key, edge_data in trajectory.items():
            if isinstance(edge_data, dict) and edge_data.get("blocked_by") == child_id:
                edge_data["status"] = "iterating"
                del edge_data["blocked_by"]
                parent_unblocked = True
                break
        parent["trajectory"] = trajectory
        parent["updated"] = datetime.now(timezone.utc).isoformat()

        with open(parent_path, "w") as f:
            yaml.dump(parent, f, default_flow_style=False, sort_keys=False)

    # Emit event
    events_path = workspace / ".ai-workspace" / "events" / "events.jsonl"
    emit_event(
        events_path,
        make_event(
            "spawn_folded_back",
            project_name,
            parent_feature=parent_id,
            child_feature=child_id,
            child_status=child_status,
            payload_path=str(payload_path),
        ),
    )

    return FoldBackResult(
        parent_id=parent_id,
        child_id=child_id,
        payload_path=str(payload_path),
        parent_unblocked=parent_unblocked,
        event_emitted=True,
    )


# ── Time-box checking ────────────────────────────────────────────────


def check_time_box(feature_vector: dict) -> str:
    """Check time-box status for a feature vector.

    Returns: "active" | "expiring" | "expired" | "disabled"
    """
    tb = feature_vector.get("time_box", {})
    if not tb.get("enabled"):
        return "disabled"

    started = tb.get("started", "")
    if not started:
        return "active"  # not yet started

    duration_str = tb.get("duration", "")
    if not duration_str:
        return "active"

    # Parse duration
    duration_hours = _parse_duration_hours(duration_str)
    if duration_hours <= 0:
        return "active"

    # Compute elapsed
    try:
        start_dt = datetime.fromisoformat(started)
        now = datetime.now(timezone.utc)
        if start_dt.tzinfo is None:
            start_dt = start_dt.replace(tzinfo=timezone.utc)
        elapsed_hours = (now - start_dt).total_seconds() / 3600
    except (ValueError, TypeError):
        return "active"

    if elapsed_hours >= duration_hours:
        return "expired"
    elif elapsed_hours >= duration_hours * 0.75:
        return "expiring"
    else:
        return "active"


def _parse_duration_hours(duration_str: str) -> float:
    """Parse a human-readable duration to hours."""
    duration_str = duration_str.strip().lower()
    match = re.match(
        r"(\d+(?:\.\d+)?)\s*(hour|hours|h|day|days|d|week|weeks|w)", duration_str
    )
    if not match:
        return 0
    value = float(match.group(1))
    unit = match.group(2)
    if unit.startswith("h"):
        return value
    elif unit.startswith("d"):
        return value * 24
    elif unit.startswith("w"):
        return value * 24 * 7
    return 0


# ── Event loading helper ─────────────────────────────────────────────


def load_events(workspace: Path) -> list[dict]:
    """Load all events from events.jsonl."""
    events_path = workspace / ".ai-workspace" / "events" / "events.jsonl"
    if not events_path.exists():
        return []
    events = []
    for line in events_path.read_text().strip().split("\n"):
        if line.strip():
            try:
                events.append(json.loads(line))
            except json.JSONDecodeError:
                continue
    return events
