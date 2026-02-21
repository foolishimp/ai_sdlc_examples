# Implements: REQ-F-REGIME-001, REQ-F-REGIME-002
"""Classify events into conscious vs reflex processing regimes."""

from __future__ import annotations

from genesis_monitor.models.events import Event

# Conscious regime: deliberative, human/agent-driven
CONSCIOUS_EVENT_TYPES = frozenset({
    "intent_raised",
    "spec_modified",
    "finding_raised",
    "feature_spawned",
    "feature_folded_back",
})

# Reflex regime: autonomic, deterministic
REFLEX_EVENT_TYPES = frozenset({
    "evaluator_ran",
    "iteration_completed",
    "edge_converged",
    "telemetry_signal_emitted",
})


def build_regime_summary(events: list[Event]) -> dict:
    """Classify events into conscious vs reflex processing regimes.

    Returns a dict with:
        conscious_count, reflex_count, unclassified_count, total,
        conscious_events (list), reflex_events (list).
    """
    conscious = []
    reflex = []
    unclassified = []

    for e in events:
        if e.event_type in CONSCIOUS_EVENT_TYPES:
            conscious.append(e)
        elif e.event_type in REFLEX_EVENT_TYPES:
            reflex.append(e)
        else:
            unclassified.append(e)

    return {
        "conscious_count": len(conscious),
        "reflex_count": len(reflex),
        "unclassified_count": len(unclassified),
        "total": len(events),
        "conscious_events": conscious,
        "reflex_events": reflex,
        "unclassified_events": unclassified,
    }
