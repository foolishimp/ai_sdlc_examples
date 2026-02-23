# Validates: REQ-F-FUNC-001, REQ-F-SENSE-001, REQ-F-SENSE-002, REQ-F-SENSE-003
# Validates: REQ-F-MAGT-001, REQ-F-MAGT-002, REQ-F-MAGT-003, REQ-F-IENG-001
# Validates: REQ-F-ETIM-001, REQ-F-ETIM-003, REQ-F-CTOL-001, REQ-F-CTOL-002
"""Tests for v2.8 data models."""

from datetime import datetime

from genesis_monitor.models.core import (
    EdgeConvergence,
    EdgeTrajectory,
    FeatureVector,
)
from genesis_monitor.models.events import (
    EVENT_TYPE_MAP,
    AffectTriageEvent,
    ArtifactModifiedEvent,
    CheckpointCreatedEvent,
    ClaimExpiredEvent,
    ClaimRejectedEvent,
    CommandErrorEvent,
    ConvergenceEscalatedEvent,
    EdgeReleasedEvent,
    EdgeStartedEvent,
    EncodingEscalatedEvent,
    EvaluatorDetailEvent,
    ExteroceptiveSignalEvent,
    GapsValidatedEvent,
    HealthCheckedEvent,
    InteroceptiveSignalEvent,
    IterationAbandonedEvent,
    IterationCompletedEvent,
    ProjectInitializedEvent,
    ReleaseCreatedEvent,
    ReviewCompletedEvent,
)
from genesis_monitor.models.features import ConstraintDimension


# ── EVENT_TYPE_MAP ────────────────────────────────────────────────


class TestEventTypeMapV28:
    def test_has_28_entries(self):
        assert len(EVENT_TYPE_MAP) == 28

    def test_v28_lifecycle_types_present(self):
        v28_lifecycle = {
            "edge_started", "project_initialized", "checkpoint_created",
            "review_completed", "gaps_validated", "release_created",
        }
        assert v28_lifecycle.issubset(set(EVENT_TYPE_MAP.keys()))

    def test_v28_sensory_types_present(self):
        v28_sensory = {
            "interoceptive_signal", "exteroceptive_signal", "affect_triage",
        }
        assert v28_sensory.issubset(set(EVENT_TYPE_MAP.keys()))

    def test_v28_failure_observability_types_present(self):
        v28_failure = {
            "evaluator_detail", "command_error", "health_checked",
            "iteration_abandoned", "encoding_escalated",
        }
        assert v28_failure.issubset(set(EVENT_TYPE_MAP.keys()))

    def test_v28_multi_agent_types_present(self):
        v28_agent = {
            "claim_rejected", "edge_released", "claim_expired",
            "convergence_escalated",
        }
        assert v28_agent.issubset(set(EVENT_TYPE_MAP.keys()))


# ── v2.8 Lifecycle Events ────────────────────────────────────────


class TestV28LifecycleEvents:
    def test_edge_started_event(self):
        e = EdgeStartedEvent(
            timestamp=datetime(2026, 2, 23),
            event_type="edge_started",
            project="test",
            data={},
            edge="design→code",
            feature="REQ-F-001",
        )
        assert e.edge == "design→code"
        assert e.feature == "REQ-F-001"

    def test_project_initialized_event(self):
        e = ProjectInitializedEvent(
            timestamp=datetime(2026, 2, 23),
            event_type="project_initialized",
            project="test",
            data={},
            profile="standard",
            graph_edges=["intent→req", "req→design"],
        )
        assert e.profile == "standard"
        assert len(e.graph_edges) == 2

    def test_checkpoint_created_event(self):
        e = CheckpointCreatedEvent(
            timestamp=datetime(2026, 2, 23),
            event_type="checkpoint_created",
            project="test",
            data={},
            checkpoint_id="chk-001",
            edge="design→code",
            feature="REQ-F-001",
        )
        assert e.checkpoint_id == "chk-001"

    def test_review_completed_event(self):
        e = ReviewCompletedEvent(
            timestamp=datetime(2026, 2, 23),
            event_type="review_completed",
            project="test",
            data={},
            edge="req→design",
            feature="REQ-F-001",
            reviewer="human",
            outcome="approved",
        )
        assert e.outcome == "approved"
        assert e.reviewer == "human"

    def test_gaps_validated_event(self):
        e = GapsValidatedEvent(
            timestamp=datetime(2026, 2, 23),
            event_type="gaps_validated",
            project="test",
            data={},
            total_gaps=10,
            resolved_gaps=8,
            unresolved_gaps=2,
        )
        assert e.total_gaps == 10
        assert e.unresolved_gaps == 2

    def test_release_created_event(self):
        e = ReleaseCreatedEvent(
            timestamp=datetime(2026, 2, 23),
            event_type="release_created",
            project="test",
            data={},
            version="1.0.0",
            req_coverage="95%",
            features_included=["REQ-F-001", "REQ-F-002"],
        )
        assert e.version == "1.0.0"
        assert len(e.features_included) == 2


# ── v2.8 Sensory Events ─────────────────────────────────────────


class TestV28SensoryEvents:
    def test_interoceptive_signal_event(self):
        e = InteroceptiveSignalEvent(
            timestamp=datetime(2026, 2, 23),
            event_type="interoceptive_signal",
            project="test",
            data={},
            signal_type="convergence_rate",
            measurement="0.3 iterations/hour",
            threshold="0.5 iterations/hour",
        )
        assert e.signal_type == "convergence_rate"
        assert e.measurement == "0.3 iterations/hour"

    def test_exteroceptive_signal_event(self):
        e = ExteroceptiveSignalEvent(
            timestamp=datetime(2026, 2, 23),
            event_type="exteroceptive_signal",
            project="test",
            data={},
            source="npm_audit",
            signal_type="dependency_vulnerability",
            payload="lodash@4.17.20 has CVE-2021-23337",
        )
        assert e.source == "npm_audit"
        assert e.signal_type == "dependency_vulnerability"

    def test_affect_triage_event(self):
        e = AffectTriageEvent(
            timestamp=datetime(2026, 2, 23),
            event_type="affect_triage",
            project="test",
            data={},
            signal_ref="intero-001",
            triage_result="escalate",
            rationale="Convergence rate below threshold",
        )
        assert e.triage_result == "escalate"
        assert e.signal_ref == "intero-001"


# ── v2.8 Multi-Agent Events ─────────────────────────────────────


class TestV28MultiAgentEvents:
    def test_claim_rejected_event(self):
        e = ClaimRejectedEvent(
            timestamp=datetime(2026, 2, 23),
            event_type="claim_rejected",
            project="test",
            data={},
            agent_id="agent-002",
            edge="design→code",
            reason="already claimed by agent-001",
        )
        assert e.agent_id == "agent-002"
        assert e.reason == "already claimed by agent-001"

    def test_edge_released_event(self):
        e = EdgeReleasedEvent(
            timestamp=datetime(2026, 2, 23),
            event_type="edge_released",
            project="test",
            data={},
            agent_id="agent-001",
            edge="design→code",
        )
        assert e.agent_id == "agent-001"

    def test_claim_expired_event(self):
        e = ClaimExpiredEvent(
            timestamp=datetime(2026, 2, 23),
            event_type="claim_expired",
            project="test",
            data={},
            agent_id="agent-001",
            edge="design→code",
            expiry_reason="timeout after 30m",
        )
        assert e.expiry_reason == "timeout after 30m"

    def test_convergence_escalated_event(self):
        e = ConvergenceEscalatedEvent(
            timestamp=datetime(2026, 2, 23),
            event_type="convergence_escalated",
            project="test",
            data={},
            edge="code→tests",
            reason="3 iterations without progress",
            escalated_to="human_reviewer",
        )
        assert e.escalated_to == "human_reviewer"


# ── Enriched IterationCompletedEvent ─────────────────────────────


class TestEnrichedIterationCompleted:
    def test_v28_fields_default_empty(self):
        e = IterationCompletedEvent(
            event_type="iteration_completed",
            edge="design→code",
        )
        assert e.encoding is None
        assert e.source_findings == []
        assert e.process_gaps == []
        assert e.convergence_type == ""
        assert e.intent_engine_output == ""
        assert e.delta is None

    def test_v28_fields_populated(self):
        e = IterationCompletedEvent(
            event_type="iteration_completed",
            edge="design→code",
            encoding={"mode": "constructive", "valence": "+", "active_units": 3},
            source_findings=["gap in auth spec"],
            process_gaps=["missing test for edge case"],
            convergence_type="delta_zero",
            intent_engine_output="reflex.log",
            delta=8,
        )
        assert e.encoding["mode"] == "constructive"
        assert len(e.source_findings) == 1
        assert e.convergence_type == "delta_zero"
        assert e.delta == 8


# ── EdgeTrajectory v2.8 Fields ───────────────────────────────────


class TestEdgeTrajectoryV28:
    def test_v28_fields_default(self):
        et = EdgeTrajectory()
        assert et.started_at is None
        assert et.converged_at is None
        assert et.convergence_type == ""
        assert et.escalations == []

    def test_v28_fields_populated(self):
        et = EdgeTrajectory(
            status="converged",
            iteration=3,
            started_at=datetime(2026, 2, 20, 10, 0),
            converged_at=datetime(2026, 2, 21, 14, 30),
            convergence_type="delta_zero",
            escalations=["human review requested"],
        )
        assert et.started_at == datetime(2026, 2, 20, 10, 0)
        assert et.converged_at == datetime(2026, 2, 21, 14, 30)
        assert et.convergence_type == "delta_zero"
        assert len(et.escalations) == 1


# ── EdgeConvergence v2.8 Fields ──────────────────────────────────


class TestEdgeConvergenceV28:
    def test_v28_fields_default(self):
        ec = EdgeConvergence()
        assert ec.started_at is None
        assert ec.converged_at is None
        assert ec.duration == ""
        assert ec.convergence_type == ""
        assert ec.delta_curve == []

    def test_v28_fields_populated(self):
        ec = EdgeConvergence(
            edge="design→code",
            started_at=datetime(2026, 2, 20),
            converged_at=datetime(2026, 2, 21),
            duration="1d",
            convergence_type="delta_zero",
            delta_curve=[8, 0],
        )
        assert ec.duration == "1d"
        assert ec.convergence_type == "delta_zero"
        assert ec.delta_curve == [8, 0]


# ── FeatureVector v2.8 Fields ────────────────────────────────────


class TestFeatureVectorV28:
    def test_encoding_default_none(self):
        fv = FeatureVector(feature_id="REQ-001")
        assert fv.encoding is None

    def test_encoding_populated(self):
        fv = FeatureVector(
            feature_id="REQ-001",
            encoding={"mode": "constructive", "valence": "+", "active_units": 3},
        )
        assert fv.encoding["mode"] == "constructive"
        assert fv.encoding["active_units"] == 3

    def test_backward_compat_v25_fields_still_work(self):
        fv = FeatureVector(
            feature_id="REQ-001",
            profile="standard",
            parent_id="REQ-000",
            spawned_by="gap",
            children=["REQ-002"],
            fold_back_status="pending",
        )
        assert fv.profile == "standard"
        assert fv.parent_id == "REQ-000"
        assert fv.encoding is None  # v2.8 field defaults to None


# ── ConstraintDimension v2.8 Fields ──────────────────────────────


class TestConstraintDimensionV28:
    def test_tolerance_default_empty(self):
        cd = ConstraintDimension(name="security", mandatory=True)
        assert cd.tolerance == ""
        assert cd.breach_status == ""

    def test_tolerance_populated(self):
        cd = ConstraintDimension(
            name="performance",
            mandatory=True,
            resolves_via="adr",
            tolerance="≤ 5% degradation",
            breach_status="ok",
        )
        assert cd.tolerance == "≤ 5% degradation"
        assert cd.breach_status == "ok"

    def test_breached_dimension(self):
        cd = ConstraintDimension(
            name="latency",
            mandatory=True,
            tolerance="< 200ms p99",
            breach_status="breached",
        )
        assert cd.breach_status == "breached"


# ── Artifact Write Observation (REQ-SENSE-006) ───────────────────


class TestArtifactModifiedEvent:
    def test_artifact_modified_in_map(self):
        assert "artifact_modified" in EVENT_TYPE_MAP
        assert EVENT_TYPE_MAP["artifact_modified"] is ArtifactModifiedEvent

    def test_artifact_modified_event(self):
        e = ArtifactModifiedEvent(
            timestamp=datetime(2026, 2, 23),
            event_type="artifact_modified",
            project="test",
            data={},
            file_path="imp_scala/code/src/main.scala",
            asset_type="code",
            tool="Write",
        )
        assert e.file_path == "imp_scala/code/src/main.scala"
        assert e.asset_type == "code"
        assert e.tool == "Write"

    def test_artifact_modified_defaults(self):
        e = ArtifactModifiedEvent(event_type="artifact_modified")
        assert e.file_path == ""
        assert e.asset_type == ""
        assert e.tool == ""
