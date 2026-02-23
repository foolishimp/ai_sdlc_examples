# Validates: REQ-F-EVSCHEMA-001, REQ-F-VREL-001, REQ-F-TBOX-001, REQ-F-PROF-001, REQ-F-CDIM-001, REQ-F-REGIME-001
"""Tests for v2.5 data models."""

from datetime import datetime

from genesis_monitor.models.core import (
    EdgeTrajectory,
    FeatureVector,
    GraphTopology,
)
from genesis_monitor.models.events import (
    EVENT_TYPE_MAP,
    EdgeConvergedEvent,
    EvaluatorRanEvent,
    Event,
    FeatureFoldedBackEvent,
    FeatureSpawnedEvent,
    FindingRaisedEvent,
    IntentRaisedEvent,
    IterationCompletedEvent,
    SpecModifiedEvent,
    TelemetrySignalEmittedEvent,
)
from genesis_monitor.models.features import (
    ConstraintDimension,
    EvaluatorResult,
    ProjectionProfile,
    TimeBox,
)


# ── Typed Event Models ──────────────────────────────────────────


class TestTypedEvents:
    def test_event_type_map_has_v25_types(self):
        v25_types = {
            "iteration_completed", "edge_converged", "evaluator_ran",
            "finding_raised", "feature_spawned", "feature_folded_back",
            "intent_raised", "spec_modified", "telemetry_signal_emitted",
        }
        assert v25_types.issubset(set(EVENT_TYPE_MAP.keys()))

    def test_event_type_map_has_all_types(self):
        assert len(EVENT_TYPE_MAP) == 27

    def test_iteration_completed_event(self):
        e = IterationCompletedEvent(
            timestamp=datetime(2026, 2, 21),
            event_type="iteration_completed",
            project="test",
            data={},
            edge="design→code",
            feature="REQ-F-001",
            iteration=3,
            evaluators={"agent": "pass", "pytest": "pass"},
            context_hash="sha256:abc",
        )
        assert e.edge == "design→code"
        assert e.iteration == 3
        assert e.evaluators["pytest"] == "pass"

    def test_edge_converged_event(self):
        e = EdgeConvergedEvent(
            timestamp=datetime(2026, 2, 21),
            event_type="edge_converged",
            project="test",
            data={},
            edge="req→design",
            feature="REQ-F-001",
            convergence_time="2h",
        )
        assert e.convergence_time == "2h"

    def test_intent_raised_event_causal_chain(self):
        e = IntentRaisedEvent(
            timestamp=datetime(2026, 2, 21),
            event_type="intent_raised",
            project="test",
            data={},
            trigger="telemetry_deviation",
            signal_source="TELEM-001",
            prior_intents=["INT-001", "INT-042"],
        )
        assert e.prior_intents == ["INT-001", "INT-042"]
        assert e.trigger == "telemetry_deviation"

    def test_spec_modified_event(self):
        e = SpecModifiedEvent(
            timestamp=datetime(2026, 2, 21),
            event_type="spec_modified",
            project="test",
            data={},
            previous_hash="sha256:aaa",
            new_hash="sha256:bbb",
            delta="Added REQ-F-002",
            trigger_intent="INT-042",
        )
        assert e.previous_hash == "sha256:aaa"
        assert e.trigger_intent == "INT-042"

    def test_feature_spawned_event(self):
        e = FeatureSpawnedEvent(
            timestamp=datetime(2026, 2, 21),
            event_type="feature_spawned",
            project="test",
            data={},
            parent_vector="REQ-F-001",
            child_vector="REQ-F-002",
            reason="gap",
        )
        assert e.parent_vector == "REQ-F-001"
        assert e.reason == "gap"

    def test_feature_folded_back_event(self):
        e = FeatureFoldedBackEvent(
            timestamp=datetime(2026, 2, 21),
            event_type="feature_folded_back",
            project="test",
            data={},
            parent_vector="REQ-F-001",
            child_vector="REQ-F-002",
            outputs=["spike_report.md"],
        )
        assert e.outputs == ["spike_report.md"]

    def test_finding_raised_event(self):
        e = FindingRaisedEvent(
            timestamp=datetime(2026, 2, 21),
            event_type="finding_raised",
            project="test",
            data={},
            finding_type="backward",
            description="Missing security dimension",
            edge="req→design",
            feature="REQ-F-001",
        )
        assert e.finding_type == "backward"

    def test_evaluator_ran_event(self):
        e = EvaluatorRanEvent(
            timestamp=datetime(2026, 2, 21),
            event_type="evaluator_ran",
            project="test",
            data={},
            edge="code→unit_tests",
            evaluator_type="deterministic",
            result="pass",
            delta=None,
        )
        assert e.evaluator_type == "deterministic"

    def test_telemetry_signal_emitted_event(self):
        e = TelemetrySignalEmittedEvent(
            timestamp=datetime(2026, 2, 21),
            event_type="telemetry_signal_emitted",
            project="test",
            data={},
            signal_id="TELEM-003",
            category="consciousness",
            value="loop closed",
        )
        assert e.signal_id == "TELEM-003"

    def test_base_event_still_works(self):
        e = Event(
            timestamp=datetime(2026, 2, 21),
            event_type="unknown_future_type",
            project="test",
            data={"foo": "bar"},
        )
        assert e.data["foo"] == "bar"


# ── Feature Vector v2.5 Fields ──────────────────────────────────


class TestFeatureVectorV25:
    def test_v25_fields_default_none(self):
        fv = FeatureVector(feature_id="REQ-001")
        assert fv.profile is None
        assert fv.parent_id is None
        assert fv.spawned_by is None
        assert fv.children == []
        assert fv.fold_back_status is None
        assert fv.time_box is None

    def test_v25_fields_set(self):
        tb = TimeBox(
            duration="3 weeks",
            check_in="weekly",
            on_expiry="fold_back",
            partial_results=True,
        )
        fv = FeatureVector(
            feature_id="REQ-002",
            vector_type="spike",
            profile="spike",
            parent_id="REQ-001",
            spawned_by="risk",
            time_box=tb,
        )
        assert fv.profile == "spike"
        assert fv.parent_id == "REQ-001"
        assert fv.time_box.duration == "3 weeks"
        assert fv.time_box.on_expiry == "fold_back"

    def test_children_populated(self):
        fv = FeatureVector(feature_id="REQ-001", children=["REQ-002", "REQ-003"])
        assert len(fv.children) == 2


class TestTimeBox:
    def test_defaults(self):
        tb = TimeBox(duration="1 week", on_expiry="fold_back", partial_results=True)
        assert tb.check_in is None
        assert tb.deadline is None
        assert tb.convergence_reason is None

    def test_full(self):
        tb = TimeBox(
            duration="2 days",
            check_in="daily",
            on_expiry="terminate",
            partial_results=False,
            deadline=datetime(2026, 3, 1),
            convergence_reason="timed_out",
        )
        assert tb.convergence_reason == "timed_out"


# ── Evaluator Result with Regime ────────────────────────────────


class TestEvaluatorResult:
    def test_conscious_evaluator(self):
        er = EvaluatorResult(name="agent_gap_analysis", result="pass", regime="conscious")
        assert er.regime == "conscious"

    def test_reflex_evaluator(self):
        er = EvaluatorResult(name="pytest", result="pass", regime="reflex")
        assert er.regime == "reflex"

    def test_edge_trajectory_with_evaluator_results(self):
        et = EdgeTrajectory(
            status="converged",
            iteration=1,
            evaluator_results={
                "agent": EvaluatorResult(name="agent", result="pass", regime="conscious"),
                "pytest": EvaluatorResult(name="pytest", result="pass", regime="reflex"),
            },
        )
        assert et.evaluator_results["agent"].regime == "conscious"


# ── Constraint Dimensions ───────────────────────────────────────


class TestConstraintDimension:
    def test_mandatory_dimension(self):
        cd = ConstraintDimension(
            name="ecosystem_compatibility",
            mandatory=True,
            resolves_via="adr",
        )
        assert cd.mandatory is True
        assert cd.resolved is None

    def test_resolved_dimension(self):
        cd = ConstraintDimension(
            name="security_model",
            mandatory=True,
            resolves_via="adr",
            resolved=True,
        )
        assert cd.resolved is True


# ── Projection Profiles ─────────────────────────────────────────


class TestProjectionProfile:
    def test_full_profile(self):
        pp = ProjectionProfile(
            name="full",
            graph_edges=["all"],
            evaluator_types=["human", "agent", "deterministic"],
            convergence="delta_zero",
            context_density="full",
            iteration_budget=None,
            vector_types=["feature", "discovery", "spike"],
        )
        assert pp.name == "full"
        assert "human" in pp.evaluator_types

    def test_spike_profile(self):
        pp = ProjectionProfile(
            name="spike",
            graph_edges=["hypothesis→experiment", "experiment→assessment"],
            evaluator_types=["agent", "human"],
            convergence="risk_assessed_or_timeout",
            context_density="intent+technical",
            iteration_budget="1 week",
            vector_types=["spike", "discovery"],
        )
        assert pp.iteration_budget == "1 week"


# ── GraphTopology v2.5 Fields ───────────────────────────────────


class TestGraphTopologyV25:
    def test_v25_fields_default_empty(self):
        gt = GraphTopology()
        assert gt.constraint_dimensions == []
        assert gt.profiles == []

    def test_with_dimensions_and_profiles(self):
        gt = GraphTopology(
            constraint_dimensions=[
                ConstraintDimension(name="security", mandatory=True, resolves_via="adr"),
            ],
            profiles=[
                ProjectionProfile(
                    name="standard",
                    graph_edges=["req→design", "design→code"],
                    evaluator_types=["agent", "deterministic"],
                    convergence="required_checks_pass",
                    context_density="project+adrs",
                    iteration_budget="per-sprint",
                    vector_types=["feature", "hotfix"],
                ),
            ],
        )
        assert len(gt.constraint_dimensions) == 1
        assert gt.profiles[0].name == "standard"
