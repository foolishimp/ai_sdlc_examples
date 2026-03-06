# Implements: REQ-EVENT-001 (OpenLineage event construction), ADR-S-011, ADR-S-012
"""
OpenLineage event constructor — builds spec-compliant RunEvents.

Callable two ways:
  1. Import:   from genesis.ol_event import make_ol_event, emit_ol_event
  2. CLI:      python -m genesis.ol_event --type IterationStarted --job "design→code" ...

Every event carries four universal fields (ADR-S-012):
  instance_id    — workflow instance scope
  actor          — who/what caused the event
  causation_id   — runId of the immediate parent event (ParentRunFacet)
  correlation_id — runId of the root event in the causal chain

Root events (no triggering parent) set causation_id = correlation_id = own runId.
"""

import argparse
import fcntl
import json
import uuid
from datetime import datetime, timezone
from pathlib import Path

PRODUCER = "https://github.com/foolishimp/ai_sdlc_method"
SCHEMA_URL = "https://openlineage.io/spec/1-0-5/OpenLineage.json"
FACET_SCHEMA_BASE = "https://github.com/foolishimp/ai_sdlc_method/spec/facets"

# ── Multi-tenancy: map file path prefix → design tenant (REQ-TOOL-012) ────────

_TENANT_PREFIXES = ("imp_claude", "imp_gemini", "imp_codex", "imp_bedrock")


def tenant_from_path(file_path: str | Path) -> str | None:
    """Return the design tenant name from a relative file path, or None.

    Maps the first path component to a tenant:
      imp_claude/...    → "imp_claude"
      imp_gemini/...    → "imp_gemini"
      specification/... → "specification"
      (anything else)   → None  (caller uses repo-level project name)

    Usage in event emitters:
      project = tenant_from_path(written_file) or workspace_project_name
    """
    first = Path(file_path).parts[0] if file_path else ""
    if first in _TENANT_PREFIXES or first == "specification":
        return first
    return None


# ADR-S-011: semantic event type → OL eventType
_OL_EVENT_TYPE = {
    "IterationStarted": "START",
    "IterationCompleted": "COMPLETE",
    "ConvergenceAchieved": "COMPLETE",
    "IterationFailed": "FAIL",
    "IterationAbandoned": "ABORT",
}


def make_ol_event(
    event_type: str,
    job_name: str,
    project: str,
    instance_id: str,
    actor: str,
    causation_id: str | None = None,
    correlation_id: str | None = None,
    payload: dict | None = None,
    inputs: list[str] | None = None,
    outputs: list[str] | None = None,
    project_root: str | None = None,
) -> dict:
    """
    Construct an OpenLineage RunEvent dict (ADR-S-011, ADR-S-012).

    Args:
        event_type:     Semantic type — "IterationStarted", "IterationFailed", etc.
        job_name:       Edge or job name — e.g. "design→code", "TRIAGE", "SPAWN:REQ-F-AUTH-001"
        project:        Project name — used as OL namespace "aisdlc://{project}"
        instance_id:    Workflow instance identifier
        actor:          Identity of the causal subject — human, agent id, or "local-user"
        causation_id:   runId of the immediate parent event. None → root (self-referential)
        correlation_id: runId of the root event in the chain. None → root (self-referential)
        payload:        Type-specific fields (e.g. {"edge": "design→code", "delta": 0})
        inputs:         List of file paths read (relative to project_root)
        outputs:        List of file paths written (relative to project_root)
        project_root:   Absolute project root for file:// namespace. Defaults to cwd.

    Returns:
        OpenLineage RunEvent dict — serialise with json.dumps() to append to events.jsonl
    """
    run_id = str(uuid.uuid4())
    root = project_root or str(Path.cwd())
    namespace = f"aisdlc://{project}"
    file_ns = f"file://{root}"

    # Root events are self-referential
    causation_id = causation_id or run_id
    correlation_id = correlation_id or run_id

    ol_type = _OL_EVENT_TYPE.get(event_type, "OTHER")

    # Universal facets
    run_facets: dict = {
        "sdlc:universal": {
            "_producer": PRODUCER,
            "_schemaURL": f"{FACET_SCHEMA_BASE}/sdlc_universal.json",
            "instance_id": instance_id,
            "actor": actor,
            "causation_id": causation_id,
            "correlation_id": correlation_id,
        },
        "parent": {
            "_producer": PRODUCER,
            "_schemaURL": "https://openlineage.io/spec/facets/ParentRunFacet.json",
            "run": {"runId": causation_id},
            "job": {"namespace": namespace, "name": job_name},
        },
    }

    # Semantic type facet (required on OTHER events, informational on typed events)
    if payload or ol_type == "OTHER":
        run_facets["sdlc:event_type"] = {
            "_producer": PRODUCER,
            "_schemaURL": f"{FACET_SCHEMA_BASE}/sdlc_event_type.json",
            "type": event_type,
        }

    # Payload facet
    if payload:
        run_facets["sdlc:payload"] = {
            "_producer": PRODUCER,
            "_schemaURL": f"{FACET_SCHEMA_BASE}/sdlc_payload.json",
            **payload,
        }

    def _dataset(path: str) -> dict:
        return {"namespace": file_ns, "name": path, "facets": {}}

    return {
        "eventType": ol_type,
        "eventTime": datetime.now(timezone.utc).isoformat(),
        "run": {"runId": run_id, "facets": run_facets},
        "job": {"namespace": namespace, "name": job_name, "facets": {}},
        "inputs": [_dataset(p) for p in (inputs or [])],
        "outputs": [_dataset(p) for p in (outputs or [])],
        "producer": PRODUCER,
        "schemaURL": SCHEMA_URL,
    }


def emit_ol_event(events_path: Path, event: dict) -> str:
    """
    Append an OL event dict to events.jsonl. Returns the runId.

    Uses fcntl.flock for advisory locking (single-machine safety).
    Creates parent directories if they don't exist.
    """
    events_path.parent.mkdir(parents=True, exist_ok=True)
    line = json.dumps(event, separators=(",", ":")) + "\n"

    with open(events_path, "a") as f:
        fcntl.flock(f, fcntl.LOCK_EX)
        try:
            f.write(line)
            f.flush()
        finally:
            fcntl.flock(f, fcntl.LOCK_UN)

    return event["run"]["runId"]


# ---------------------------------------------------------------------------
# Convenience constructors — one per ADR-S-012 event type
# ---------------------------------------------------------------------------


def iteration_started(
    project, instance_id, actor, edge, context_refs=None, **kw
) -> dict:
    return make_ol_event(
        "IterationStarted",
        edge,
        project,
        instance_id,
        actor,
        payload={"edge": edge, "context_refs": context_refs or []},
        **kw,
    )


def iteration_completed(project, instance_id, actor, edge, delta, **kw) -> dict:
    return make_ol_event(
        "IterationCompleted",
        edge,
        project,
        instance_id,
        actor,
        payload={"edge": edge, "delta": delta},
        **kw,
    )


def iteration_failed(project, instance_id, actor, edge, reason, **kw) -> dict:
    return make_ol_event(
        "IterationFailed",
        edge,
        project,
        instance_id,
        actor,
        payload={"edge": edge, "reason": reason},
        **kw,
    )


def evaluator_voted(
    project, instance_id, actor, evaluator_type, result, evidence, **kw
) -> dict:
    return make_ol_event(
        "EvaluatorVoted",
        "EVALUATOR",
        project,
        instance_id,
        actor,
        payload={
            "evaluator_type": evaluator_type,
            "result": result,
            "evidence": evidence,
        },
        **kw,
    )


def convergence_achieved(project, instance_id, actor, edge, delta, **kw) -> dict:
    return make_ol_event(
        "ConvergenceAchieved",
        edge,
        project,
        instance_id,
        actor,
        payload={"edge": edge, "delta": delta},
        **kw,
    )


def compensation_triggered(
    project, instance_id, actor, failed_edge, target_edge, **kw
) -> dict:
    return make_ol_event(
        "CompensationTriggered",
        f"COMPENSATE:{target_edge}",
        project,
        instance_id,
        actor,
        payload={"failed_edge": failed_edge, "target_edge": target_edge},
        **kw,
    )


def compensation_completed(
    project, instance_id, actor, target_edge, restored_projection_hash, **kw
) -> dict:
    return make_ol_event(
        "CompensationCompleted",
        f"COMPENSATE:{target_edge}",
        project,
        instance_id,
        actor,
        payload={
            "target_edge": target_edge,
            "restored_projection_hash": restored_projection_hash,
        },
        **kw,
    )


def context_arrived(
    project, instance_id, actor, source_type, payload_ref, **kw
) -> dict:
    return make_ol_event(
        "ContextArrived",
        "CONTEXT",
        project,
        instance_id,
        actor,
        payload={"source_type": source_type, "payload_ref": payload_ref},
        **kw,
    )


def transition_authorized(project, instance_id, actor, edge, permissions, **kw) -> dict:
    return make_ol_event(
        "TransitionAuthorized",
        edge,
        project,
        instance_id,
        actor,
        payload={"edge": edge, "permissions": permissions},
        **kw,
    )


def transition_denied(project, instance_id, actor, edge, reason, **kw) -> dict:
    return make_ol_event(
        "TransitionDenied",
        edge,
        project,
        instance_id,
        actor,
        payload={"edge": edge, "reason": reason},
        **kw,
    )


# ---------------------------------------------------------------------------
# CLI — callable from a Claude Code skill or engine subprocess
#
# Usage:
#   python -m genesis.ol_event \
#     --type IterationStarted \
#     --job "design→code" \
#     --project my-service \
#     --instance-id abc123 \
#     --actor "claude" \
#     --events-path .ai-workspace/events/events.jsonl \
#     --payload '{"delta": 0}' \
#     --causation-id <parent-run-id>   # omit for root events
#
# Prints the emitted runId to stdout.
# ---------------------------------------------------------------------------


def _cli():
    p = argparse.ArgumentParser(description="Emit an OpenLineage event to events.jsonl")
    p.add_argument(
        "--type",
        required=True,
        dest="event_type",
        help="Semantic event type (IterationStarted, IterationFailed, …)",
    )
    p.add_argument(
        "--job",
        required=True,
        dest="job_name",
        help="Job/edge name (e.g. 'design→code')",
    )
    p.add_argument("--project", required=True)
    p.add_argument("--instance-id", required=True)
    p.add_argument("--actor", required=True)
    p.add_argument(
        "--events-path", required=True, type=Path, help="Path to events.jsonl"
    )
    p.add_argument(
        "--causation-id",
        default=None,
        help="runId of triggering event (omit for root events)",
    )
    p.add_argument(
        "--correlation-id",
        default=None,
        help="runId of chain root (omit for root events)",
    )
    p.add_argument(
        "--payload", default=None, help="JSON object of type-specific fields"
    )
    p.add_argument(
        "--inputs",
        nargs="*",
        default=None,
        help="Input file paths (relative to project root)",
    )
    p.add_argument(
        "--outputs",
        nargs="*",
        default=None,
        help="Output file paths (relative to project root)",
    )
    p.add_argument(
        "--project-root",
        default=None,
        help="Absolute project root for file:// namespace",
    )

    args = p.parse_args()
    payload = json.loads(args.payload) if args.payload else None

    event = make_ol_event(
        event_type=args.event_type,
        job_name=args.job_name,
        project=args.project,
        instance_id=args.instance_id,
        actor=args.actor,
        causation_id=args.causation_id,
        correlation_id=args.correlation_id,
        payload=payload,
        inputs=args.inputs,
        outputs=args.outputs,
        project_root=args.project_root,
    )

    run_id = emit_ol_event(args.events_path, event)
    print(run_id)  # caller captures runId for use as causation_id of next event


if __name__ == "__main__":
    _cli()
