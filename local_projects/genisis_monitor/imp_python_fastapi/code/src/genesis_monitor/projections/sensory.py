# Implements: REQ-F-SENSE-004, REQ-F-SENSE-005
"""Build sensory dashboard from interoceptive/exteroceptive/affect_triage events."""

from __future__ import annotations

from genesis_monitor.models.events import (
    AffectTriageEvent,
    Event,
    ExteroceptiveSignalEvent,
    InteroceptiveSignalEvent,
)


def build_sensory_dashboard(events: list[Event]) -> dict:
    """Build a sensory system dashboard from v2.8 sensory events.

    Returns a dict with:
        interoceptive_count, exteroceptive_count, triage_count,
        interoceptive_signals (list[dict]), exteroceptive_signals (list[dict]),
        triage_results (list[dict]).
    """
    interoceptive: list[dict] = []
    exteroceptive: list[dict] = []
    triage: list[dict] = []

    for e in events:
        if isinstance(e, InteroceptiveSignalEvent):
            interoceptive.append({
                "timestamp": e.timestamp,
                "signal_type": e.signal_type,
                "measurement": e.measurement,
                "threshold": e.threshold,
                "project": e.project,
            })
        elif isinstance(e, ExteroceptiveSignalEvent):
            exteroceptive.append({
                "timestamp": e.timestamp,
                "source": e.source,
                "signal_type": e.signal_type,
                "payload": e.payload,
                "project": e.project,
            })
        elif isinstance(e, AffectTriageEvent):
            triage.append({
                "timestamp": e.timestamp,
                "signal_ref": e.signal_ref,
                "triage_result": e.triage_result,
                "rationale": e.rationale,
                "project": e.project,
            })

    return {
        "interoceptive_count": len(interoceptive),
        "exteroceptive_count": len(exteroceptive),
        "triage_count": len(triage),
        "interoceptive_signals": interoceptive,
        "exteroceptive_signals": exteroceptive,
        "triage_results": triage,
    }
