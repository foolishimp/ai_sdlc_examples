# Implements: REQ-F-IENG-001, REQ-F-IENG-002
"""Build IntentEngine output classification view."""

from __future__ import annotations

from genesis_monitor.models.events import Event
from genesis_monitor.parsers.events import classify_intent_engine_output


def build_intent_engine_view(events: list[Event]) -> dict:
    """Classify events by IntentEngine output type.

    Returns a dict with:
        reflex_log_count, spec_event_log_count, escalate_count, unclassified_count,
        reflex_log_events (list), spec_event_log_events (list),
        escalate_events (list), unclassified_events (list).
    """
    buckets: dict[str, list[Event]] = {
        "reflex.log": [],
        "specEventLog": [],
        "escalate": [],
        "unclassified": [],
    }

    for e in events:
        category = classify_intent_engine_output(e.event_type)
        buckets[category].append(e)

    return {
        "reflex_log_count": len(buckets["reflex.log"]),
        "spec_event_log_count": len(buckets["specEventLog"]),
        "escalate_count": len(buckets["escalate"]),
        "unclassified_count": len(buckets["unclassified"]),
        "reflex_log_events": buckets["reflex.log"],
        "spec_event_log_events": buckets["specEventLog"],
        "escalate_events": buckets["escalate"],
        "unclassified_events": buckets["unclassified"],
    }
