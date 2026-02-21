# Implements: REQ-F-EVSCHEMA-001
"""Typed event hierarchy for v2.5 event sourcing (§7.5.1)."""

from dataclasses import dataclass, field
from datetime import datetime


@dataclass
class Event:
    """Base event — all events share these fields."""

    timestamp: datetime = field(default_factory=datetime.now)
    event_type: str = ""
    project: str = ""
    data: dict = field(default_factory=dict)


@dataclass
class IterationCompletedEvent(Event):
    """Emitted every iterate() cycle."""

    edge: str = ""
    feature: str = ""
    iteration: int = 0
    evaluators: dict[str, str] = field(default_factory=dict)
    context_hash: str = ""


@dataclass
class EdgeConvergedEvent(Event):
    """Emitted when all evaluators pass for an edge."""

    edge: str = ""
    feature: str = ""
    convergence_time: str = ""


@dataclass
class EvaluatorRanEvent(Event):
    """Emitted for individual evaluator execution."""

    edge: str = ""
    evaluator_type: str = ""  # human | agent | deterministic
    result: str = ""
    delta: str | None = None


@dataclass
class FindingRaisedEvent(Event):
    """Emitted when a gap is detected."""

    finding_type: str = ""  # backward | forward | inward
    description: str = ""
    edge: str | None = None
    feature: str | None = None


@dataclass
class FeatureSpawnedEvent(Event):
    """Emitted when a new vector is spawned."""

    parent_vector: str = ""
    child_vector: str = ""
    reason: str = ""  # gap | risk | feasibility | incident | scope


@dataclass
class FeatureFoldedBackEvent(Event):
    """Emitted when child vector results fold back into parent."""

    parent_vector: str = ""
    child_vector: str = ""
    outputs: list[str] = field(default_factory=list)


@dataclass
class IntentRaisedEvent(Event):
    """Emitted when a new intent is raised from feedback."""

    trigger: str = ""  # telemetry_deviation | gap_found | ecosystem_change
    signal_source: str = ""
    prior_intents: list[str] = field(default_factory=list)


@dataclass
class SpecModifiedEvent(Event):
    """Emitted when the spec is updated (consciousness loop §7.7)."""

    previous_hash: str = ""
    new_hash: str = ""
    delta: str = ""
    trigger_intent: str = ""


@dataclass
class TelemetrySignalEmittedEvent(Event):
    """Emitted for self-observation signals."""

    signal_id: str = ""
    category: str = ""
    value: str = ""


EVENT_TYPE_MAP: dict[str, type[Event]] = {
    "iteration_completed": IterationCompletedEvent,
    "edge_converged": EdgeConvergedEvent,
    "evaluator_ran": EvaluatorRanEvent,
    "finding_raised": FindingRaisedEvent,
    "feature_spawned": FeatureSpawnedEvent,
    "feature_folded_back": FeatureFoldedBackEvent,
    "intent_raised": IntentRaisedEvent,
    "spec_modified": SpecModifiedEvent,
    "telemetry_signal_emitted": TelemetrySignalEmittedEvent,
}
