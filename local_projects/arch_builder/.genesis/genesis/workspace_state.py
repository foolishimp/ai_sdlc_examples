# Implements: REQ-UX-001, REQ-UX-005, REQ-SUPV-002, REQ-ROBUST-003, REQ-ROBUST-008
"""Pure-function workspace state detection utilities.

These functions operate on filesystem paths (workspace directories) and return
derived state. No stored state variables — same input always produces same
output.

Provenance: Originally authored by Gemini in
imp_gemini/gemini_cli/internal/workspace_state.py,
ported to imp_claude and adapted for Claude tenant conventions.
"""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
from typing import Any, Optional, Union

import yaml


# ═══════════════════════════════════════════════════════════════════════
# CONSTANTS
# ═══════════════════════════════════════════════════════════════════════

WORKSPACE_STATES = (
    "UNINITIALISED",
    "NEEDS_CONSTRAINTS",
    "NEEDS_INTENT",
    "NO_FEATURES",
    "IN_PROGRESS",
    "ALL_CONVERGED",
    "STUCK",
    "ALL_BLOCKED",
)

STANDARD_PROFILE_EDGES = [
    "intent→requirements",
    "requirements→design",
    "design→code",
    "code↔unit_tests",
]

BOOTLOADER_START_MARKER = "<!-- GENESIS_BOOTLOADER_START -->"


# ═══════════════════════════════════════════════════════════════════════
# CONTEXT & VIEW UTILITIES
# ═══════════════════════════════════════════════════════════════════════


def _workspace_dir(workspace: Path) -> Path:
    """Normalize a path to the actual ``.ai-workspace`` directory."""
    if workspace.name == ".ai-workspace":
        return workspace
    return workspace / ".ai-workspace"


def compute_context_hash(context: dict[str, Any]) -> str:
    """Deterministically hash context content for reproducibility checks."""
    canonical = json.dumps(context, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


def deep_merge(base: dict[str, Any], override: dict[str, Any]) -> dict[str, Any]:
    """Recursively merge two dictionaries (override wins)."""
    result = dict(base)
    for key, value in override.items():
        existing = result.get(key)
        if isinstance(existing, dict) and isinstance(value, dict):
            result[key] = deep_merge(existing, value)
        else:
            result[key] = value
    return result


def resolve_context_hierarchy(contexts: list[dict[str, Any]]) -> dict[str, Any]:
    """Fold a list of context layers from global→local using deep merge."""
    resolved: dict[str, Any] = {}
    for layer in contexts:
        resolved = deep_merge(resolved, layer)
    return resolved


def compute_feature_view(workspace: Path, feature_id: str) -> dict[str, Any]:
    """Return a per-feature derived view from the active feature YAML."""
    ws_dir = _workspace_dir(workspace)
    fv_path = ws_dir / "features" / "active" / f"{feature_id}.yml"
    if not fv_path.exists():
        return {"feature": feature_id, "status": "missing", "edges": {}}
    with open(fv_path) as f:
        data = yaml.safe_load(f) or {}
    return {
        "feature": data.get("feature", feature_id),
        "status": data.get("status", "pending"),
        "edges": data.get("trajectory", {}),
    }


def compute_aggregated_view(workspace: Path) -> dict[str, Any]:
    """Aggregate high-level progress counters across active feature vectors."""
    ws_dir = _workspace_dir(workspace)
    features_dir = ws_dir / "features" / "active"
    totals = {
        "total": 0,
        "in_progress": 0,
        "pending": 0,
        "converged": 0,
        "blocked": 0,
        "edges_total": 0,
    }
    if not features_dir.exists():
        return totals

    for path in sorted(features_dir.glob("*.yml")):
        with open(path) as f:
            data = yaml.safe_load(f) or {}
        totals["total"] += 1
        status = data.get("status", "pending")
        if status in totals:
            totals[status] += 1
        traj = data.get("trajectory", {})
        if isinstance(traj, dict):
            totals["edges_total"] += len(traj)

    return totals


def classify_tolerance_breach(
    observed_value: float,
    threshold: float,
    severity: Union[str, None] = None,
) -> str:
    """Classify tolerance pressure into IntentEngine output classes."""
    if observed_value <= threshold:
        return "reflex.log"
    if severity == "critical":
        return "escalate"
    if observed_value <= (threshold * 2):
        return "specEventLog"
    return "escalate"


# ═══════════════════════════════════════════════════════════════════════
# EVENT UTILITIES
# ═══════════════════════════════════════════════════════════════════════


def load_events(workspace: Path) -> list[dict[str, Any]]:
    """Parse events.jsonl from a workspace, returning list of event dicts."""
    events_file = _workspace_dir(workspace) / "events" / "events.jsonl"
    if not events_file.exists():
        return []

    events: list[dict[str, Any]] = []
    for i, line in enumerate(events_file.read_text().splitlines(), 1):
        line = line.strip()
        if not line:
            continue
        try:
            events.append(json.loads(line))
        except json.JSONDecodeError:
            continue
    return events


def get_converged_edges(events: list[dict[str, Any]], feature: str) -> set[str]:
    """Return set of edge names that have ``edge_converged`` events for *feature*."""
    return {
        ev.get("edge", "")
        for ev in events
        if ev.get("event_type") == "edge_converged" and ev.get("feature") == feature
    }


def compute_current_delta(
    events: list[dict[str, Any]],
    feature: str,
    edge: str,
) -> Union[int, None]:
    """Return the most recent delta value for a feature/edge pair, or None."""
    delta: Union[int, None] = None
    for ev in events:
        if (
            ev.get("event_type") == "iteration_completed"
            and ev.get("feature") == feature
            and ev.get("edge") == edge
        ):
            d = ev.get("delta")
            if d is not None:
                delta = int(d)
    return delta


def get_iteration_count(
    events: list[dict[str, Any]],
    feature: str,
    edge: str,
) -> int:
    """Count iteration_completed events for a feature/edge pair."""
    return sum(
        1
        for ev in events
        if ev.get("event_type") == "iteration_completed"
        and ev.get("feature") == feature
        and ev.get("edge") == edge
    )


# ═══════════════════════════════════════════════════════════════════════
# SESSION GAP DETECTION (REQ-ROBUST-003, REQ-ROBUST-008)
# ═══════════════════════════════════════════════════════════════════════


def detect_abandoned_iterations(
    events: list[dict[str, Any]],
) -> list[dict[str, Any]]:
    """Detect edge_started events with no subsequent completion.

    Scans the event log for ``edge_started`` events that were never followed
    by an ``edge_converged`` or ``iteration_completed`` for the same
    feature/edge pair. Also filters out pairs that already have an
    ``iteration_abandoned`` event (idempotency).

    Returns a list of dicts: {feature, edge, last_event_timestamp}.
    """
    started: dict[tuple[str, str], str] = {}  # (feature, edge) → timestamp
    completed: set[tuple[str, str]] = set()
    already_abandoned: set[tuple[str, str]] = set()

    for ev in events:
        et = ev.get("event_type", "")
        feature = ev.get("feature", "")
        edge = ev.get("edge", "")
        key = (feature, edge)

        if et == "edge_started" and feature and edge:
            started[key] = ev.get("timestamp", "")
        elif et in ("edge_converged", "iteration_completed") and feature and edge:
            completed.add(key)
        elif et == "iteration_abandoned" and feature and edge:
            already_abandoned.add(key)

    abandoned = []
    for key, timestamp in started.items():
        if key not in completed and key not in already_abandoned:
            abandoned.append(
                {
                    "feature": key[0],
                    "edge": key[1],
                    "last_event_timestamp": timestamp,
                }
            )

    return abandoned


# ═══════════════════════════════════════════════════════════════════════
# FEATURE UTILITIES
# ═══════════════════════════════════════════════════════════════════════


def get_active_features(workspace: Path) -> list[dict[str, Any]]:
    """Load all active feature vector YAML files."""
    features_dir = _workspace_dir(workspace) / "features" / "active"
    if not features_dir.exists():
        return []

    features: list[dict[str, Any]] = []
    for path in sorted(features_dir.glob("*.yml")):
        with open(path) as f:
            data = yaml.safe_load(f)
        if data:
            features.append(data)
    return features


def detect_stuck_features(
    workspace: Path,
    threshold: int = 3,
) -> list[dict[str, Any]]:
    """Detect features where delta has not decreased for *threshold*
    consecutive iterations."""
    events = load_events(workspace)
    stuck: list[dict[str, Any]] = []

    sequences: dict[tuple[str, str], list[int]] = {}
    for ev in events:
        if ev.get("event_type") != "iteration_completed":
            continue
        feat = ev.get("feature", "")
        edge = ev.get("edge", "")
        delta = ev.get("delta")
        if feat and edge and delta is not None:
            sequences.setdefault((feat, edge), []).append(int(delta))

    for (feat, edge), deltas in sequences.items():
        if len(deltas) < threshold:
            continue
        tail = deltas[-threshold:]
        if len(set(tail)) == 1 and tail[0] > 0:
            stuck.append(
                {
                    "feature": feat,
                    "edge": edge,
                    "delta": tail[0],
                    "iterations": len(deltas),
                    "reason": f"delta={tail[0]} unchanged for {threshold} iterations",
                }
            )

    return stuck


def _has_pending_human_review(
    workspace: Path,
    feature_id: str,
    events: list[dict[str, Any]],
) -> bool:
    """Check if a feature has a pending human review."""
    features = get_active_features(workspace)
    for fv in features:
        if fv.get("feature") != feature_id:
            continue
        traj = fv.get("trajectory", {})
        for _edge_name, edge_data in traj.items():
            if (
                isinstance(edge_data, dict)
                and edge_data.get("status") == "pending_review"
            ):
                return True
    return False


def _has_blocked_dependency(
    workspace: Path,
    feature_id: str,
    events: list[dict[str, Any]],
) -> bool:
    """Check if a feature is blocked by an unconverged dependency (spawn)."""
    features = get_active_features(workspace)
    for fv in features:
        if fv.get("feature") != feature_id:
            continue
        deps = fv.get("dependencies", [])
        for dep in deps:
            dep_id = dep if isinstance(dep, str) else dep.get("feature", "")
            if dep_id:
                dep_converged = get_converged_edges(events, dep_id)
                if not dep_converged:
                    return True
    return False


# ═══════════════════════════════════════════════════════════════════════
# FEATURE SELECTION & EDGE WALK
# ═══════════════════════════════════════════════════════════════════════


def select_next_feature(
    features: list[dict[str, Any]],
) -> Optional[dict[str, Any]]:
    """Select the feature closest to completion (fewest unconverged edges)."""
    best: Optional[dict[str, Any]] = None
    best_remaining = float("inf")

    for fv in features:
        if fv.get("status") == "converged":
            continue
        traj = fv.get("trajectory", {})
        converged_count = sum(
            1
            for _k, v in traj.items()
            if isinstance(v, dict) and v.get("status") == "converged"
        )
        total_edges = max(len(traj), 1)
        remaining = total_edges - converged_count
        if remaining < best_remaining:
            best_remaining = remaining
            best = fv

    return best


def get_next_edge(
    feature: dict[str, Any],
    graph_topology: Optional[dict[str, Any]] = None,
) -> Union[str, None]:
    """Walk edges in topological order, return first unconverged edge."""
    traj = feature.get("trajectory", {})
    edges = STANDARD_PROFILE_EDGES
    if graph_topology:
        transitions = graph_topology.get("transitions", [])
        if transitions:
            edges = [t.get("name", "") for t in transitions if t.get("name")]

    for edge in edges:
        parts = edge.replace("↔", "→").split("→")
        for part in parts:
            part = part.strip()
            traj_entry = traj.get(part)
            if traj_entry:
                if isinstance(traj_entry, dict):
                    status = traj_entry.get("status", "pending")
                    if status not in ("converged",):
                        return edge
            else:
                return edge
    return None


# ═══════════════════════════════════════════════════════════════════════
# GENESIS SELF-COMPLIANCE (REQ-SUPV-002, REQ-UX-005)
# ═══════════════════════════════════════════════════════════════════════


def verify_genesis_compliance(workspace: Path) -> dict[str, Any]:
    """Verify methodology invariants and bootloader presence.

    Returns a dict with: passed (int), failed (int), results (list of dicts).
    """
    results = []
    passed = 0
    failed = 0

    # 1. Bootloader Presence
    claude_md = workspace / "CLAUDE.md"
    if claude_md.exists() and BOOTLOADER_START_MARKER in claude_md.read_text():
        results.append(
            {
                "name": "bootloader_present",
                "status": "pass",
                "description": "CLAUDE.md contains Bootloader",
            }
        )
        passed += 1
    else:
        results.append(
            {
                "name": "bootloader_present",
                "status": "fail",
                "description": "Bootloader markers missing in CLAUDE.md",
            }
        )
        failed += 1

    # 2. Graph Invariant
    topology_path = workspace / ".ai-workspace" / "graph" / "graph_topology.yml"
    topology = None
    if topology_path.exists():
        try:
            with open(topology_path) as f:
                topology = yaml.safe_load(f)
            nodes = len(topology.get("asset_types", {}))
            edges = len(topology.get("transitions", []))
            if nodes > 0 and edges > 0:
                results.append(
                    {
                        "name": "graph_invariant",
                        "status": "pass",
                        "description": f"Graph has {nodes} nodes, {edges} edges",
                    }
                )
                passed += 1
            else:
                results.append(
                    {
                        "name": "graph_invariant",
                        "status": "fail",
                        "description": "Graph topology is empty",
                    }
                )
                failed += 1
        except Exception as e:
            results.append(
                {
                    "name": "graph_invariant",
                    "status": "fail",
                    "description": f"Cannot parse graph: {e}",
                }
            )
            failed += 1
    else:
        results.append(
            {
                "name": "graph_invariant",
                "status": "fail",
                "description": "graph_topology.yml missing",
            }
        )
        failed += 1

    # 3. Iterate & Evaluator Invariant
    if topology:
        edges = topology.get("transitions", [])
        edge_configs_present = 0
        evaluators_found = 0
        missing_configs = []
        zero_evaluator_edges = []

        for edge in edges:
            name = edge.get("name", "")
            config_rel = edge.get("edge_config", "")
            if not name or not config_rel:
                continue

            # Local path check
            config_path = workspace / ".ai-workspace" / "graph" / config_rel
            if config_path.exists():
                edge_configs_present += 1
                try:
                    with open(config_path) as f:
                        cfg = yaml.safe_load(f)
                    # Support both evaluators: and checklist: keys
                    evs = cfg.get("evaluators", cfg.get("checklist", []))
                    if isinstance(
                        evs, dict
                    ):  # some schemas use dict mapping type -> list
                        count = sum(
                            len(v) if isinstance(v, list) else 1 for v in evs.values()
                        )
                    else:
                        count = len(evs)

                    if count > 0:
                        evaluators_found += count
                    else:
                        zero_evaluator_edges.append(name)
                except Exception:
                    zero_evaluator_edges.append(name)
            else:
                missing_configs.append(name)

        if not missing_configs:
            results.append(
                {
                    "name": "iterate_invariant",
                    "status": "pass",
                    "description": "All edges have evaluator configs",
                }
            )
            passed += 1
        else:
            results.append(
                {
                    "name": "iterate_invariant",
                    "status": "fail",
                    "description": (
                        f"Missing config for edges: {', '.join(missing_configs)}"
                    ),
                }
            )
            failed += 1

        if not zero_evaluator_edges:
            results.append(
                {
                    "name": "evaluator_invariant",
                    "status": "pass",
                    "description": (
                        f"All edges have >=1 evaluator ({evaluators_found} total)"
                    ),
                }
            )
            passed += 1
        else:
            results.append(
                {
                    "name": "evaluator_invariant",
                    "status": "fail",
                    "description": (
                        f"Edges with 0 evaluators: {', '.join(zero_evaluator_edges)}"
                    ),
                }
            )
            failed += 1

    # 4. Tolerance Check (REQ-SUPV-002)
    constraints_path = (
        workspace / ".ai-workspace" / "context" / "project_constraints.yml"
    )
    if constraints_path.exists():
        try:
            with open(constraints_path) as f:
                constraints = yaml.safe_load(f)
            dims = constraints.get("constraint_dimensions", {})
            wishes = []
            for name, val in dims.items():
                if isinstance(val, dict):
                    # Check for non-placeholder values
                    resolved = [
                        v
                        for k, v in val.items()
                        if k
                        not in ("mandatory", "description", "resolves_via", "examples")
                        and v
                    ]
                    if resolved:
                        # Check if it looks like a "wish"
                        # (no numbers, versions, or booleans)
                        flat_val = str(resolved).lower()
                        if not any(char.isdigit() for char in flat_val) and not any(
                            kw in flat_val
                            for kw in [
                                "true",
                                "false",
                                "yes",
                                "no",
                                "active",
                                "enabled",
                            ]
                        ):
                            wishes.append(name)

            if wishes:
                results.append(
                    {
                        "name": "tolerance_check",
                        "status": "warn",
                        "description": (
                            f"{len(wishes)} constraints lack measurable"
                            f" thresholds (wishes): {', '.join(wishes)}"
                        ),
                    }
                )
            else:
                results.append(
                    {
                        "name": "tolerance_check",
                        "status": "pass",
                        "description": (
                            "All resolved constraints have measurable thresholds"
                        ),
                    }
                )
                passed += 1
        except Exception:
            pass

    return {"passed": passed, "failed": failed, "results": results}


# ═══════════════════════════════════════════════════════════════════════
# INTEGRITY & HEALTH CHECKS
# ═══════════════════════════════════════════════════════════════════════


def detect_corrupted_events(workspace: Path) -> list[dict[str, Any]]:
    """Return list of corruption reports for events.jsonl."""
    events_file = _workspace_dir(workspace) / "events" / "events.jsonl"
    if not events_file.exists():
        return []

    corruptions: list[dict[str, Any]] = []
    for i, line in enumerate(events_file.read_text().splitlines(), 1):
        stripped = line.strip()
        if not stripped:
            continue
        try:
            json.loads(stripped)
        except json.JSONDecodeError as exc:
            corruptions.append({"line": i, "raw": stripped, "error": str(exc)})
    return corruptions


def detect_orphaned_spawns(workspace: Path) -> list[dict[str, Any]]:
    """Find spawns whose parent feature doesn't exist."""
    features = get_active_features(workspace)
    feature_ids = {fv.get("feature", "") for fv in features}

    orphans: list[dict[str, Any]] = []
    for fv in features:
        parent = fv.get("parent", {})
        if not parent:
            continue
        parent_id = (
            parent.get("feature", "") if isinstance(parent, dict) else str(parent)
        )
        if parent_id and parent_id not in feature_ids:
            orphans.append(
                {
                    "feature": fv.get("feature", ""),
                    "parent": parent_id,
                    "reason": f"parent {parent_id} not in active features",
                }
            )
    return orphans


def get_unactioned_escalations(
    events: list[dict[str, Any]],
) -> list[dict[str, Any]]:
    """Find escalation/intent_raised events that have no corresponding action."""
    escalations = [
        ev for ev in events if ev.get("event_type") in ("intent_raised", "escalation")
    ]
    actioned_intents: set[str] = set()
    for ev in events:
        if ev.get("event_type") in (
            "spawn_created",
            "review_completed",
            "spec_modified",
        ):
            iid = ev.get("intent_id", "") or ev.get("data", {}).get("intent_id", "")
            if iid:
                actioned_intents.add(iid)

    unactioned: list[dict[str, Any]] = []
    for esc in escalations:
        iid = esc.get("intent_id", "") or esc.get("data", {}).get("intent_id", "")
        if iid and iid not in actioned_intents:
            unactioned.append(esc)
        elif not iid:
            unactioned.append(esc)

    return unactioned


# ═══════════════════════════════════════════════════════════════════════
# STATE DETECTION
# ═══════════════════════════════════════════════════════════════════════


def detect_workspace_state(workspace: Path) -> str:
    """Detect the current workspace state from filesystem + event log."""
    # Ensure we are looking at the actual .ai-workspace directory
    if workspace.name == ".ai-workspace":
        ws_dir = workspace
        project_root = workspace.parent
    else:
        ws_dir = workspace / ".ai-workspace"
        project_root = workspace

    if not ws_dir.exists() or not ws_dir.is_dir():
        return "UNINITIALISED"

    constraints_candidates = [
        ws_dir / "claude" / "context" / "project_constraints.yml",
        ws_dir / "context" / "project_constraints.yml",
    ]
    has_constraints = False
    for cp in constraints_candidates:
        if cp.exists():
            # print(f"DEBUG: Found constraint file at {cp}")
            try:
                with open(cp) as f:
                    data = yaml.safe_load(f)
                if data and data.get("project"):
                    has_constraints = True
                    break
            except (yaml.YAMLError, OSError):
                pass

    if has_constraints:
        for cp in constraints_candidates:
            if cp.exists():
                try:
                    with open(cp) as f:
                        data = yaml.safe_load(f)
                    dims = data.get("constraint_dimensions", {})
                    mandatory_filled = 0
                    mandatory_total = 0
                    for _name, dim_val in dims.items():
                        if isinstance(dim_val, dict):
                            if dim_val.get("mandatory") is False:
                                continue
                            values = [
                                v
                                for k, v in dim_val.items()
                                if k
                                not in (
                                    "mandatory",
                                    "description",
                                    "resolves_via",
                                    "examples",
                                    "notes",
                                )
                                and v
                            ]
                            if values:
                                mandatory_filled += 1
                            mandatory_total += 1
                    if mandatory_total > 0 and mandatory_filled == 0:
                        return "NEEDS_CONSTRAINTS"
                except Exception:
                    pass
                break

    if not has_constraints:
        return "NEEDS_CONSTRAINTS"

    intent_candidates = [
        project_root / "specification" / "INTENT.md",
        ws_dir / "spec" / "INTENT.md",
    ]
    has_intent = any(p.exists() and p.stat().st_size > 10 for p in intent_candidates)
    if not has_intent:
        return "NEEDS_INTENT"

    features = get_active_features(workspace)
    if not features:
        return "NO_FEATURES"

    events = load_events(workspace)

    all_converged = True
    any_stuck = False
    all_blocked = True

    for fv in features:
        feat_id = fv.get("feature", "")
        status = fv.get("status", "pending")

        if status == "converged":
            continue

        all_converged = False

        stuck_list = detect_stuck_features(workspace, threshold=3)
        if any(s["feature"] == feat_id for s in stuck_list):
            any_stuck = True
            continue

        is_blocked = _has_blocked_dependency(
            workspace, feat_id, events
        ) or _has_pending_human_review(workspace, feat_id, events)
        if not is_blocked:
            all_blocked = False

    if all_converged:
        return "ALL_CONVERGED"

    if any_stuck:
        return "STUCK"

    if all_blocked:
        return "ALL_BLOCKED"

    return "IN_PROGRESS"
