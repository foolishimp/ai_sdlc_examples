# Implements: REQ-F-PARSE-004, REQ-F-EVSCHEMA-001, REQ-F-IENG-001
"""Parse .ai-workspace/events/events.jsonl into typed Event models."""

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
    """Parse the append-only event log with v2.5 typed dispatch.

    Returns an empty list if the file doesn't exist.
    Reads at most max_events from the end of the file.
    """
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
                event = _parse_one(data)
                events.append(event)
            except json.JSONDecodeError:
                continue
    except OSError:
        return []

    return events


def _parse_one(data: dict) -> Event:
    """Dispatch to typed event or fall back to generic Event."""
    event_type = str(data.get("event_type", data.get("type", "unknown")))
    timestamp = _parse_timestamp(data.get("timestamp", ""))
    project = str(data.get("project", ""))

    base_kwargs = {
        "timestamp": timestamp,
        "event_type": event_type,
        "project": project,
        "data": data,
    }

    cls = EVENT_TYPE_MAP.get(event_type)
    if cls is None:
        return Event(**base_kwargs)

    # Extract typed fields from data (top-level or nested in data["data"])
    nested = data.get("data") if isinstance(data.get("data"), dict) else {}
    typed_kwargs = dict(base_kwargs)
    for f in dataclasses.fields(cls):
        if f.name in base_kwargs:
            continue
        if f.name in data:
            typed_kwargs[f.name] = data[f.name]
        elif f.name in nested:
            typed_kwargs[f.name] = nested[f.name]

    try:
        return cls(**typed_kwargs)
    except TypeError:
        logger.warning("Failed to construct %s from data, falling back to Event", cls.__name__)
        return Event(**base_kwargs)


def _parse_timestamp(ts: str) -> datetime:
    """Parse ISO timestamp, falling back to now()."""
    if not ts:
        return datetime.now()
    try:
        return datetime.fromisoformat(ts)
    except (ValueError, TypeError):
        return datetime.now()


# ── IntentEngine output classification (v2.8 §4.6) ──────────────

# Reflex events: autonomic logging, no human attention needed
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

# Spec-level events: modify the spec or feature graph
_SPEC_EVENT_LOG_TYPES = frozenset(
    {
        "spec_modified",
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

# Escalation events: require human attention
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
    """Classify an event type by IntentEngine output category.

    Returns one of: 'reflex.log', 'specEventLog', 'escalate', 'unclassified'.
    """
    if event_type in _REFLEX_LOG_TYPES:
        return "reflex.log"
    if event_type in _SPEC_EVENT_LOG_TYPES:
        return "specEventLog"
    if event_type in _ESCALATE_TYPES:
        return "escalate"
    return "unclassified"
