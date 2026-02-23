# Implements: REQ-F-REGIME-001, REQ-F-REGIME-002, REQ-F-IENG-001
"""Classify events into conscious vs reflex processing regimes."""

from __future__ import annotations

from genesis_monitor.models.events import Event
from genesis_monitor.parsers.events import classify_intent_engine_output

# Conscious regime: deliberative, human/agent-driven
CONSCIOUS_EVENT_TYPES = frozenset({
    "intent_raised",
    "spec_modified",
    "finding_raised",
    "feature_spawned",
    "feature_folded_back",
    # v2.8 additions
    "convergence_escalated",
    "review_completed",
    "affect_triage",
})

# Reflex regime: autonomic, deterministic
REFLEX_EVENT_TYPES = frozenset({
    "evaluator_ran",
    "iteration_completed",
    "edge_converged",
    "telemetry_signal_emitted",
    # v2.8 additions
    "edge_started",
    "checkpoint_created",
    "edge_released",
    "interoceptive_signal",
    "project_initialized",
    "gaps_validated",
    "release_created",
    "exteroceptive_signal",
    "claim_rejected",
    "claim_expired",
})


def build_regime_summary(events: list[Event]) -> dict:
    """Classify events into conscious vs reflex processing regimes.

    Returns a dict with:
        conscious_count, reflex_count, unclassified_count, total,
        conscious_events (list), reflex_events (list),
        intent_engine_breakdown (dict) — v2.8 IntentEngine output type counts.
    """
    conscious = []
    reflex = []
    unclassified = []

    # v2.8: IntentEngine output classification
    ie_breakdown: dict[str, int] = {
        "reflex.log": 0,
        "specEventLog": 0,
        "escalate": 0,
        "unclassified": 0,
    }

    for e in events:
        if e.event_type in CONSCIOUS_EVENT_TYPES:
            conscious.append(e)
        elif e.event_type in REFLEX_EVENT_TYPES:
            reflex.append(e)
        else:
            unclassified.append(e)

        # IntentEngine classification
        ie_cat = classify_intent_engine_output(e.event_type)
        ie_breakdown[ie_cat] = ie_breakdown.get(ie_cat, 0) + 1

    return {
        "conscious_count": len(conscious),
        "reflex_count": len(reflex),
        "unclassified_count": len(unclassified),
        "total": len(events),
        "conscious_events": conscious,
        "reflex_events": reflex,
        "unclassified_events": unclassified,
        "intent_engine_breakdown": ie_breakdown,
    }
