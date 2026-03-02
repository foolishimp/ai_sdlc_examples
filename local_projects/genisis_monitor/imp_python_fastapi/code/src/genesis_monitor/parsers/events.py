# Implements: REQ-F-PARSE-004, REQ-F-EVSCHEMA-001, REQ-F-IENG-001
"""Parse .ai-workspace/events/events.jsonl into typed Event models using OpenLineage v2 schema."""

import dataclasses
import json
import logging
from datetime import datetime
from pathlib import Path

from genesis_monitor.models.events import (
    EVENT_TYPE_MAP,
    Event,
)

logger = logging.getLogger(__name__)


def parse_events(workspace: Path, max_events: int = 100000) -> list[Event]:
    """Parse the append-only event log with OpenLineage v2 dispatch."""
    events_path = workspace / "events" / "events.jsonl"
    if not events_path.exists():
        return []

    events: list[Event] = []
    try:
        lines = events_path.read_text(encoding="utf-8").strip().splitlines()
        for line in lines:
            line = line.strip()
            if not line:
                continue
            try:
                data = json.loads(line)
                if "eventType" in data:
                    event = _parse_one(data)
                    events.append(event)
            except json.JSONDecodeError:
                continue
    except OSError:
        return []

    return events


def _parse_one(data: dict) -> Event:
    """Dispatch to typed event using ADR-S-011 OpenLineage facets.

    Facets use colon notation (sdlc:event_type) per ADR-S-011.
    Falls back to underscore notation (sdlc_event_type) for pre-ADR events.
    """

    ol_type = data.get("eventType")
    run_facets = data.get("run", {}).get("facets", {})

    def _get_facet(colon_name: str) -> dict:
        """Get facet by colon name, fall back to underscore name."""
        return run_facets.get(colon_name) or run_facets.get(colon_name.replace(":", "_")) or {}

    req_facet = _get_facet("sdlc:req_keys")
    type_facet = _get_facet("sdlc:event_type")
    delta_facet = _get_facet("sdlc:delta")

    # Determine methodology event type from OL eventType + sdlc:event_type facet
    if ol_type == "START":
        event_type = "edge_started"
    elif ol_type == "COMPLETE":
        event_type = "edge_converged"
    elif ol_type == "ABORT":
        event_type = "iteration_abandoned"
    elif ol_type == "FAIL":
        event_type = type_facet.get("type", "command_error")
    else:  # OTHER
        event_type = type_facet.get("type", "unknown")

    timestamp = _parse_timestamp(data.get("eventTime", ""))
    project = data.get("_metadata", {}).get("project", "")
    if not project:
        ns = data.get("job", {}).get("namespace", "")
        if ns.startswith("aisdlc://"):
            project = ns[len("aisdlc://"):]

    base_kwargs = {
        "timestamp": timestamp,
        "event_type": event_type,
        "project": project,
        "data": data,
    }

    cls = EVENT_TYPE_MAP.get(event_type)
    if cls is None:
        return Event(**base_kwargs)

    typed_kwargs = dict(base_kwargs)
    field_names = {f.name for f in dataclasses.fields(cls)}

    if "feature" in field_names:
        typed_kwargs["feature"] = req_facet.get("feature_id", "")
    if "edge" in field_names:
        edge = req_facet.get("edge", "")
        if not edge and ol_type in ("START", "COMPLETE", "ABORT"):
            edge = data.get("job", {}).get("name", "")
        typed_kwargs["edge"] = edge
    if "delta" in field_names:
        # Migration writes sdlc:delta.delta (int); pre-spec legacy used .value
        # annotation holds the original string description when delta=0 (e.g. spec_modified)
        annotation = delta_facet.get("annotation")
        d = delta_facet.get("delta")
        if d is None:
            d = delta_facet.get("value")
        # Prefer annotation for string-typed delta fields (spec_modified, etc.)
        if annotation and d == 0:
            d = annotation
        typed_kwargs["delta"] = d
    
    # Map any remaining fields from original_data metadata
    orig = data.get("_metadata", {}).get("original_data", {})
    for f in dataclasses.fields(cls):
        if f.name in typed_kwargs: continue
        if f.name in orig: typed_kwargs[f.name] = orig[f.name]
        
    return cls(**typed_kwargs)


def _parse_timestamp(ts: str) -> datetime:
    """Parse ISO timestamp."""
    if not ts:
        return datetime.now()
    try:
        if ts.endswith("Z"):
            ts = ts.replace("Z", "+00:00")
        return datetime.fromisoformat(ts)
    except (ValueError, TypeError):
        return datetime.now()


# ── IntentEngine output classification (v2.8 §4.6) ──────────────

_REFLEX_LOG_TYPES = frozenset(
    {
        "iteration_completed",
        "edge_converged",
        "evaluator_ran",
        "telemetry_signal_emitted",
        "edge_started",
        "checkpoint_created",
        "edge_released",
        "interoceptive_signal",
        "evaluator_detail",
        "command_error",
        "health_checked",
        "artifact_modified",
    }
)

_SPEC_EVENT_LOG_TYPES = frozenset(
    {
        "spec_modified",
        "feature_proposal",
        "feature_spawned",
        "feature_folded_back",
        "finding_raised",
        "project_initialized",
        "gaps_validated",
        "release_created",
        "exteroceptive_signal",
        "affect_triage",
        "encoding_escalated",
    }
)

_ESCALATE_TYPES = frozenset(
    {
        "intent_raised",
        "convergence_escalated",
        "review_completed",
        "claim_rejected",
        "claim_expired",
        "iteration_abandoned",
    }
)


def classify_intent_engine_output(event_type: str) -> str:
    """Classify an event type by IntentEngine output category."""
    if event_type in _REFLEX_LOG_TYPES:
        return "reflex.log"
    if event_type in _SPEC_EVENT_LOG_TYPES:
        return "specEventLog"
    if event_type in _ESCALATE_TYPES:
        return "escalate"
    return "unclassified"
