# Implements: REQ-F-EVSCHEMA-001, REQ-F-SENSE-001, REQ-F-SENSE-002, REQ-F-SENSE-003
# Implements: REQ-F-MAGT-001, REQ-F-MAGT-002, REQ-F-MAGT-003
# Implements: REQ-F-ETIM-001, REQ-F-FUNC-001, REQ-F-IENG-001
"""Typed event hierarchy for v2.5/v2.8 event sourcing."""

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
    # v2.8 additions
    encoding: dict | None = None
    source_findings: list = field(default_factory=list)
    process_gaps: list = field(default_factory=list)
    convergence_type: str = ""
    intent_engine_output: str = ""
    delta: int | None = None


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


# ── v2.8 Lifecycle Events ────────────────────────────────────────


@dataclass
class EdgeStartedEvent(Event):
    """Emitted when work begins on an edge."""

    edge: str = ""
    feature: str = ""


@dataclass
class ProjectInitializedEvent(Event):
    """Emitted when a project workspace is initialized."""

    profile: str = ""
    graph_edges: list[str] = field(default_factory=list)


@dataclass
class CheckpointCreatedEvent(Event):
    """Emitted when a session checkpoint is saved."""

    checkpoint_id: str = ""
    edge: str = ""
    feature: str = ""


@dataclass
class ReviewCompletedEvent(Event):
    """Emitted when a human evaluator review completes."""

    edge: str = ""
    feature: str = ""
    reviewer: str = ""
    outcome: str = ""  # approved | changes_requested | deferred


@dataclass
class GapsValidatedEvent(Event):
    """Emitted when traceability gap validation completes."""

    total_gaps: int = 0
    resolved_gaps: int = 0
    unresolved_gaps: int = 0


@dataclass
class ReleaseCreatedEvent(Event):
    """Emitted when a release is created."""

    version: str = ""
    req_coverage: str = ""
    features_included: list[str] = field(default_factory=list)


# ── v2.8 Sensory Events ─────────────────────────────────────────


@dataclass
class InteroceptiveSignalEvent(Event):
    """Self-monitoring signal (convergence rate, budget usage, etc.)."""

    signal_type: str = ""
    measurement: str = ""
    threshold: str = ""


@dataclass
class ExteroceptiveSignalEvent(Event):
    """Environment-monitoring signal (dependency changes, ecosystem updates)."""

    source: str = ""
    signal_type: str = ""
    payload: str = ""


@dataclass
class AffectTriageEvent(Event):
    """Triage outcome of a sensory signal."""

    signal_ref: str = ""
    triage_result: str = ""  # reflex | escalate | ignore
    rationale: str = ""


# ── v2.8 Multi-Agent Events ─────────────────────────────────────


@dataclass
class ClaimRejectedEvent(Event):
    """Emitted when an agent's edge claim is rejected."""

    agent_id: str = ""
    edge: str = ""
    reason: str = ""


@dataclass
class EdgeReleasedEvent(Event):
    """Emitted when an agent releases an edge."""

    agent_id: str = ""
    edge: str = ""


@dataclass
class ClaimExpiredEvent(Event):
    """Emitted when an agent's edge claim times out."""

    agent_id: str = ""
    edge: str = ""
    expiry_reason: str = ""


@dataclass
class ConvergenceEscalatedEvent(Event):
    """Emitted when edge convergence requires human escalation."""

    edge: str = ""
    reason: str = ""
    escalated_to: str = ""


# ── v2.8 Failure Observability Events ──────────────────────────────


@dataclass
class EvaluatorDetailEvent(Event):
    """Emitted for each failed evaluator check (REQ-SUPV-003)."""

    edge: str = ""
    feature: str = ""
    iteration: int = 0
    check_name: str = ""
    check_type: str = ""  # deterministic | agent
    result: str = ""      # fail | error


@dataclass
class CommandErrorEvent(Event):
    """Emitted when a command execution fails."""

    command: str = ""
    error: str = ""
    edge: str = ""


@dataclass
class HealthCheckedEvent(Event):
    """Emitted on health-check probe of workspace."""

    workspace: str = ""
    status: str = ""  # healthy | degraded | unreachable


@dataclass
class IterationAbandonedEvent(Event):
    """Emitted when an iteration is abandoned (max retries, timeout)."""

    edge: str = ""
    feature: str = ""
    iteration: int = 0
    reason: str = ""


@dataclass
class EncodingEscalatedEvent(Event):
    """Emitted when encoding valence escalates (e.g. medium→high)."""

    edge: str = ""
    feature: str = ""
    previous_valence: str = ""
    new_valence: str = ""
    trigger: str = ""


EVENT_TYPE_MAP: dict[str, type[Event]] = {
    # v2.5 events
    "iteration_completed": IterationCompletedEvent,
    "edge_converged": EdgeConvergedEvent,
    "evaluator_ran": EvaluatorRanEvent,
    "finding_raised": FindingRaisedEvent,
    "feature_spawned": FeatureSpawnedEvent,
    "feature_folded_back": FeatureFoldedBackEvent,
    "intent_raised": IntentRaisedEvent,
    "spec_modified": SpecModifiedEvent,
    "telemetry_signal_emitted": TelemetrySignalEmittedEvent,
    # v2.8 lifecycle
    "edge_started": EdgeStartedEvent,
    "project_initialized": ProjectInitializedEvent,
    "checkpoint_created": CheckpointCreatedEvent,
    "review_completed": ReviewCompletedEvent,
    "gaps_validated": GapsValidatedEvent,
    "release_created": ReleaseCreatedEvent,
    # v2.8 sensory
    "interoceptive_signal": InteroceptiveSignalEvent,
    "exteroceptive_signal": ExteroceptiveSignalEvent,
    "affect_triage": AffectTriageEvent,
    # v2.8 multi-agent
    "claim_rejected": ClaimRejectedEvent,
    "edge_released": EdgeReleasedEvent,
    "claim_expired": ClaimExpiredEvent,
    "convergence_escalated": ConvergenceEscalatedEvent,
    # v2.8 failure observability
    "evaluator_detail": EvaluatorDetailEvent,
    "command_error": CommandErrorEvent,
    "health_checked": HealthCheckedEvent,
    "iteration_abandoned": IterationAbandonedEvent,
    "encoding_escalated": EncodingEscalatedEvent,
}
