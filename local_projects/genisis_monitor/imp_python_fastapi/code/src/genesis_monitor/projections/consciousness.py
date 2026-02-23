# Implements: REQ-F-CONSC-001, REQ-F-CONSC-002, REQ-F-CONSC-003
"""Build consciousness timeline from deliberative events."""

from __future__ import annotations

from genesis_monitor.models.events import (
    ConvergenceEscalatedEvent,
    Event,
    FeatureSpawnedEvent,
    FindingRaisedEvent,
    IntentRaisedEvent,
    ReviewCompletedEvent,
    SpecModifiedEvent,
)

# Event types that represent conscious/deliberative activity
CONSCIOUSNESS_EVENT_TYPES = frozenset({
    "intent_raised",
    "spec_modified",
    "finding_raised",
    "feature_spawned",
    "feature_folded_back",
    # v2.8 additions
    "convergence_escalated",
    "review_completed",
})


def build_consciousness_timeline(events: list[Event]) -> list[dict]:
    """Extract conscious/deliberative events into a chronological timeline.

    Filters to intent chains, spec modifications, findings, and spawn events.
    Returns a list of dicts sorted by timestamp with event-type-specific fields.
    """
    timeline = []

    for e in events:
        if e.event_type not in CONSCIOUSNESS_EVENT_TYPES:
            continue

        entry: dict = {
            "timestamp": e.timestamp,
            "event_type": e.event_type,
            "project": e.project,
        }

        if isinstance(e, IntentRaisedEvent):
            entry["trigger"] = e.trigger
            entry["signal_source"] = e.signal_source
        elif isinstance(e, SpecModifiedEvent):
            entry["delta"] = e.delta
            entry["trigger_intent"] = e.trigger_intent
        elif isinstance(e, FindingRaisedEvent):
            entry["finding_type"] = e.finding_type
            entry["description"] = e.description
        elif isinstance(e, FeatureSpawnedEvent):
            entry["parent_vector"] = e.parent_vector
            entry["child_vector"] = e.child_vector
            entry["reason"] = e.reason
        elif isinstance(e, ConvergenceEscalatedEvent):
            entry["edge"] = e.edge
            entry["reason"] = e.reason
            entry["escalated_to"] = e.escalated_to
        elif isinstance(e, ReviewCompletedEvent):
            entry["edge"] = e.edge
            entry["feature"] = e.feature
            entry["reviewer"] = e.reviewer
            entry["outcome"] = e.outcome

        timeline.append(entry)

    timeline.sort(key=lambda x: x["timestamp"])
    return timeline
