#!/usr/bin/env python3
"""
migrate_events_v1_to_v2.py — Migrate events.jsonl from v1 (bespoke) to v2 (OpenLineage RunEvent).

Implements: ADR-S-011 (Unified OpenLineage Metadata Standard)

Usage:
    python migrate_events_v1_to_v2.py [events_file]
    python migrate_events_v1_to_v2.py .ai-workspace/events/events.jsonl

Behaviour:
    - Reads events.jsonl
    - Skips events already in v2 (eventType present)
    - Converts v1 events to OL RunEvent format
    - Backs up original to events.v1.jsonl
    - Writes migrated file to events.jsonl
    - Prints summary
"""

import json
import shutil
import sys
import uuid
from datetime import datetime, timezone
from pathlib import Path

# ── Constants ────────────────────────────────────────────────────────────────

PRODUCER = "https://github.com/foolishimp/ai_sdlc_method"
SCHEMA_URL = "https://openlineage.io/spec/1-0-5/OpenLineage.json"
FACET_BASE = PRODUCER


def _facet(name: str) -> str:
    """Schema URL for a custom sdlc facet."""
    return f"{PRODUCER}/spec/facets/{name.replace(':', '_')}.json"


# ── Event type → OL eventType mapping ────────────────────────────────────────

EVENT_TYPE_MAP: dict[str, str] = {
    "edge_started": "START",
    "edge_converged": "COMPLETE",
    "feature_converged": "COMPLETE",
    "iteration_abandoned": "ABORT",
    "command_error": "FAIL",
    # Everything else → OTHER (semantic type in sdlc:event_type facet)
}


def ol_event_type(v1_type: str) -> str:
    return EVENT_TYPE_MAP.get(v1_type, "OTHER")


# ── Job name derivation ───────────────────────────────────────────────────────


def job_name(v1: dict) -> str:
    """Derive OL job name from v1 event fields."""
    v1_type = v1.get("event_type", "")
    # Edge traversal events — use the edge as the job name
    edge = v1.get("edge") or v1.get("data", {}).get("edge", "")
    if edge and v1_type in (
        "edge_started",
        "edge_converged",
        "iteration_completed",
        "iteration_abandoned",
        "evaluator_ran",
        "edge_released",
        "claim_rejected",
        "claim_expired",
        "convergence_escalated",
    ):
        return edge

    # Homeostasis pipeline jobs
    jobs = {
        "intent_raised": "INTENT_ENGINE",
        "feature_proposal": "FEATURE_PROPOSAL",
        "feature_proposal_rejected": "FEATURE_PROPOSAL",
        "spec_modified": _spec_job(v1),
        "health_checked": "HEALTH_CHECK",
        "gaps_validated": _gaps_job(v1),
        "release_created": "RELEASE",
        "checkpoint_created": "CHECKPOINT",
        "finding_raised": "FINDING",
        "status_generated": "STATUS_GENERATION",
        "affect_triage": "TRIAGE",
        "interoceptive_signal": "SENSORY_PROBE:interoceptive",
        "exteroceptive_signal": "SENSORY_PROBE:exteroceptive",
        "telemetry_signal_emitted": "SENSORY_PROBE:telemetry",
        "artifact_modified": "SENSORY_PROBE:artifact",
        "feature_spawned": _spawn_job(v1),
        "feature_folded_back": _spawn_job(v1),
        "project_initialized": "PROJECT_INIT",
        "review_completed": "HUMAN_REVIEW",
        "encoding_escalated": "ENCODING_ESCALATION",
        "evaluator_detail": "EVALUATOR_DETAIL",
        "iteration_completed": edge or "ITERATE",
    }
    return jobs.get(v1_type, v1_type.upper())


def _spec_job(v1: dict) -> str:
    f = v1.get("file") or v1.get("data", {}).get("file", "")
    if f:
        return f"SPEC_EVOLUTION:{Path(f).name}"
    return "SPEC_EVOLUTION"


def _gaps_job(v1: dict) -> str:
    layers = v1.get("data", {}).get("layers_run", [])
    if layers:
        return f"GAP_VALIDATION:layer_{'_'.join(str(layer) for layer in layers)}"
    return "GAP_VALIDATION"


def _spawn_job(v1: dict) -> str:
    feature = (
        v1.get("feature")
        or v1.get("data", {}).get("child_vector", "")
        or v1.get("data", {}).get("feature", "")
    )
    if feature:
        return f"SPAWN:{feature}"
    return "SPAWN"


# ── Dataset (inputs/outputs) derivation ──────────────────────────────────────


def _datasets(v1: dict, project_root: str) -> tuple[list[dict], list[dict]]:
    """Return (inputs, outputs) as OL Dataset objects."""
    ns = f"file://{project_root}" if project_root else "file://"
    v1_type = v1.get("event_type", "")
    data = v1.get("data", {})

    inputs: list[dict] = []
    outputs: list[dict] = []

    # Spec evolution: file written = output
    if v1_type == "spec_modified":
        f = v1.get("file") or data.get("file", "")
        if f:
            outputs.append({"namespace": ns, "name": f, "facets": {}})

    # Artifact modified: file written = output
    elif v1_type == "artifact_modified":
        f = v1.get("file_path") or data.get("file_path", "")
        if f:
            outputs.append({"namespace": ns, "name": f, "facets": {}})

    # Feature spawned: source feature = input, new feature vector = output
    elif v1_type == "feature_spawned":
        parent = data.get("parent_vector", "")
        child = data.get("child_vector", "")
        if parent:
            inputs.append(
                {
                    "namespace": f"aisdlc://{v1.get('project', '')}",
                    "name": parent,
                    "facets": {},
                }
            )
        if child:
            outputs.append(
                {
                    "namespace": f"aisdlc://{v1.get('project', '')}",
                    "name": child,
                    "facets": {},
                }
            )

    return inputs, outputs


# ── Facet builders ────────────────────────────────────────────────────────────


def _facet_event_type(v1_type: str) -> dict:
    return {
        "_producer": FACET_BASE,
        "_schemaURL": _facet("sdlc:event_type"),
        "type": v1_type,
    }


def _facet_delta(v1: dict) -> dict | None:
    delta = v1.get("delta")
    if delta is None:
        delta = v1.get("data", {}).get("delta")
    if delta is None:
        return None
    # delta may be an integer (convergence count) or a string description
    # (e.g. spec_modified uses it as a human-readable summary)
    try:
        delta_int = int(delta)
    except (ValueError, TypeError):
        # String delta — store as annotation, not a convergence count
        return {
            "_producer": FACET_BASE,
            "_schemaURL": _facet("sdlc:delta"),
            "delta": 0,
            "annotation": str(delta),
        }
    evaluators = v1.get("evaluators", {})
    passed = evaluators.get("passed", 0) if isinstance(evaluators, dict) else 0
    total = evaluators.get("total", 0) if isinstance(evaluators, dict) else 0
    return {
        "_producer": FACET_BASE,
        "_schemaURL": _facet("sdlc:delta"),
        "delta": delta_int,
        "required_failing": delta_int,
        "passed": passed,
        "total_checks": total,
    }


def _facet_req_keys(v1: dict) -> dict | None:
    """Build sdlc:req_keys facet, also carries feature_id and edge for monitor."""
    feature = v1.get("feature") or v1.get("data", {}).get("feature", "")
    edge = v1.get("edge") or v1.get("data", {}).get("edge", "")
    reqs = v1.get("requirements", []) or v1.get("data", {}).get("satisfies", [])
    if not any([feature, edge, reqs]):
        return None
    return {
        "_producer": FACET_BASE,
        "_schemaURL": _facet("sdlc:req_keys"),
        "feature_id": feature,
        "edge": edge,
        "implements": reqs if isinstance(reqs, list) else [],
        "validates": [],
        "telemetry": [],
    }


def _facet_valence(v1: dict) -> dict | None:
    """Build sdlc:valence from available signal data."""
    v1_type = v1.get("event_type", "")
    # Reflex events: low severity, autonomic
    if v1_type in (
        "artifact_modified",
        "edge_started",
        "edge_converged",
        "evaluator_ran",
        "telemetry_signal_emitted",
        "health_checked",
        "iteration_completed",
        "status_generated",
    ):
        return {
            "_producer": FACET_BASE,
            "_schemaURL": _facet("sdlc:valence"),
            "regime": "reflex",
            "severity": 1,
            "urgency": 1,
        }
    # Conscious events: human gate required
    if v1_type in (
        "intent_raised",
        "convergence_escalated",
        "review_completed",
        "spec_modified",
        "feature_proposal",
        "release_created",
    ):
        return {
            "_producer": FACET_BASE,
            "_schemaURL": _facet("sdlc:valence"),
            "regime": "conscious",
            "severity": 7,
            "urgency": 5,
        }
    # Affect events: triage required
    if v1_type in (
        "finding_raised",
        "gaps_validated",
        "interoceptive_signal",
        "exteroceptive_signal",
        "affect_triage",
        "feature_spawned",
        "encoding_escalated",
    ):
        return {
            "_producer": FACET_BASE,
            "_schemaURL": _facet("sdlc:valence"),
            "regime": "affect",
            "severity": 5,
            "urgency": 3,
        }
    return None


# ── Core conversion ───────────────────────────────────────────────────────────


def convert_v1_to_v2(v1: dict, project_root: str = "") -> dict:
    """Convert a single v1 event dict to OL v2 format."""
    v1_type = str(v1.get("event_type", v1.get("type", "unknown")))
    project = str(v1.get("project", ""))
    timestamp = v1.get("timestamp", datetime.now(timezone.utc).isoformat())

    # Build facets
    facets: dict = {}
    ol_type = ol_event_type(v1_type)

    # sdlc:event_type — always present on OTHER events
    if ol_type == "OTHER":
        facets["sdlc:event_type"] = _facet_event_type(v1_type)

    # sdlc:delta — when delta/evaluators present
    delta_facet = _facet_delta(v1)
    if delta_facet:
        facets["sdlc:delta"] = delta_facet

    # sdlc:req_keys — carries feature_id and edge for monitor consumption
    req_facet = _facet_req_keys(v1)
    if req_facet:
        facets["sdlc:req_keys"] = req_facet

    # sdlc:valence — processing regime
    valence_facet = _facet_valence(v1)
    if valence_facet:
        facets["sdlc:valence"] = valence_facet

    # ParentRunFacet — preserve causal links if present
    parent_id = (
        v1.get("data", {}).get("trigger_event_id")
        or v1.get("data", {}).get("trigger_intent")
        or v1.get("data", {}).get("parent_run_id")
    )
    if parent_id and parent_id != "manual":
        facets["parent"] = {
            "_producer": FACET_BASE,
            "_schemaURL": "https://openlineage.io/spec/facets/ParentRunFacet.json",
            "run": {"runId": parent_id},
            "job": {"namespace": f"aisdlc://{project}", "name": "UNKNOWN"},
        }

    # Build datasets
    inputs, outputs = _datasets(v1, project_root)

    # _metadata: backward compat for monitor + raw preservation
    metadata = {
        "project": project,
        "original_data": v1,  # full v1 event preserved for monitor field extraction
    }

    return {
        "eventType": ol_type,
        "eventTime": timestamp,
        "run": {
            "runId": str(uuid.uuid4()),
            "facets": facets,
        },
        "job": {
            "namespace": f"aisdlc://{project}" if project else "aisdlc://unknown",
            "name": job_name(v1),
        },
        "inputs": inputs,
        "outputs": outputs,
        "producer": PRODUCER,
        "schemaURL": SCHEMA_URL,
        "_metadata": metadata,
    }


# ── File migration ────────────────────────────────────────────────────────────


def migrate_file(events_path: Path) -> None:
    if not events_path.exists():
        print(f"Error: {events_path} does not exist")
        sys.exit(1)

    # Backup
    backup_path = events_path.parent / "events.v1.jsonl"
    shutil.copy2(events_path, backup_path)
    print(f"Backup written to: {backup_path}")

    # Read all lines
    lines = events_path.read_text(encoding="utf-8").strip().splitlines()
    print(f"Reading {len(lines)} events from {events_path}")

    # Infer project root from path (walk up to find .ai-workspace)
    project_root = ""
    p = events_path
    for _ in range(6):
        p = p.parent
        if (p / ".git").exists():
            project_root = str(p)
            break

    # Convert
    converted = []
    v1_count = 0
    v2_count = 0
    skip_count = 0

    for i, line in enumerate(lines, 1):
        line = line.strip()
        if not line:
            continue
        try:
            data = json.loads(line)
        except json.JSONDecodeError:
            print(f"  Line {i}: invalid JSON, skipping")
            skip_count += 1
            continue

        if "eventType" in data:
            # Already v2 — preserve as-is
            converted.append(line)
            v2_count += 1
        else:
            # Convert v1 → v2
            v2 = convert_v1_to_v2(data, project_root)
            converted.append(json.dumps(v2, ensure_ascii=False))
            v1_count += 1

    # Write migrated file
    events_path.write_text("\n".join(converted) + "\n", encoding="utf-8")

    print("\nMigration complete:")
    print(f"  Converted v1→v2: {v1_count}")
    print(f"  Already v2:      {v2_count}")
    print(f"  Skipped (bad JSON): {skip_count}")
    print(f"  Total written:   {len(converted)}")
    print(f"\nOutput: {events_path}")
    print(f"Backup: {backup_path}")


def main() -> None:
    if len(sys.argv) > 1:
        path = Path(sys.argv[1])
    else:
        # Default: look for .ai-workspace relative to cwd
        candidates = [
            Path(".ai-workspace/events/events.jsonl"),
            Path("../.ai-workspace/events/events.jsonl"),
        ]
        path = next((p for p in candidates if p.exists()), None)
        if path is None:
            print("Could not find events.jsonl. Pass path as argument.")
            sys.exit(1)

    migrate_file(path.resolve())


if __name__ == "__main__":
    main()
