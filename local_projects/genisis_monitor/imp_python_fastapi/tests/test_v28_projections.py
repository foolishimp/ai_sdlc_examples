# Validates: REQ-F-SENSE-004, REQ-F-SENSE-005, REQ-F-IENG-001, REQ-F-IENG-002
# Validates: REQ-F-REGIME-001, REQ-F-CONSC-001, REQ-F-CTOL-001, REQ-F-CTOL-002
# Validates: REQ-F-CDIM-002, REQ-F-PROTO-001
"""Tests for v2.8 projection functions."""

from datetime import datetime

from genesis_monitor.models.core import (
    EdgeTrajectory,
    FeatureVector,
    GraphTopology,
    Project,
    StatusReport,
)
from genesis_monitor.models.events import (
    AffectTriageEvent,
    ClaimExpiredEvent,
    ConvergenceEscalatedEvent,
    EdgeStartedEvent,
    Event,
    ExteroceptiveSignalEvent,
    IntentRaisedEvent,
    InteroceptiveSignalEvent,
    IterationCompletedEvent,
    ReviewCompletedEvent,
)
from genesis_monitor.models.features import ConstraintDimension, ProjectionProfile
from genesis_monitor.projections.consciousness import build_consciousness_timeline
from genesis_monitor.projections.compliance import build_compliance_report
from genesis_monitor.projections.dimensions import build_dimension_coverage
from genesis_monitor.projections.intent_engine import build_intent_engine_view
from genesis_monitor.projections.regimes import build_regime_summary
from genesis_monitor.projections.sensory import build_sensory_dashboard


def _ts(minute: int = 0) -> datetime:
    return datetime(2026, 2, 23, 10, minute, 0)


# ── Sensory Dashboard Tests ──────────────────────────────────────


class TestBuildSensoryDashboard:
    def test_mixed_sensory_events(self):
        events = [
            InteroceptiveSignalEvent(
                timestamp=_ts(0), event_type="interoceptive_signal",
                project="p1", data={}, signal_type="convergence_rate",
                measurement="0.3/h", threshold="0.5/h",
            ),
            ExteroceptiveSignalEvent(
                timestamp=_ts(5), event_type="exteroceptive_signal",
                project="p1", data={}, source="npm",
                signal_type="vuln", payload="CVE-001",
            ),
            AffectTriageEvent(
                timestamp=_ts(10), event_type="affect_triage",
                project="p1", data={}, signal_ref="intero-001",
                triage_result="escalate", rationale="below threshold",
            ),
        ]
        result = build_sensory_dashboard(events)
        assert result["interoceptive_count"] == 1
        assert result["exteroceptive_count"] == 1
        assert result["triage_count"] == 1
        assert result["interoceptive_signals"][0]["signal_type"] == "convergence_rate"
        assert result["exteroceptive_signals"][0]["source"] == "npm"
        assert result["triage_results"][0]["triage_result"] == "escalate"

    def test_empty_events(self):
        result = build_sensory_dashboard([])
        assert result["interoceptive_count"] == 0
        assert result["exteroceptive_count"] == 0
        assert result["triage_count"] == 0
        assert result["interoceptive_signals"] == []

    def test_non_sensory_events_ignored(self):
        events = [
            IterationCompletedEvent(
                timestamp=_ts(0), event_type="iteration_completed",
                project="p1", data={}, edge="design→code",
            ),
        ]
        result = build_sensory_dashboard(events)
        assert result["interoceptive_count"] == 0
        assert result["exteroceptive_count"] == 0

    def test_multiple_interoceptive(self):
        events = [
            InteroceptiveSignalEvent(
                timestamp=_ts(0), event_type="interoceptive_signal",
                project="p1", data={}, signal_type="budget_usage",
                measurement="80%", threshold="90%",
            ),
            InteroceptiveSignalEvent(
                timestamp=_ts(5), event_type="interoceptive_signal",
                project="p1", data={}, signal_type="convergence_rate",
                measurement="0.1/h", threshold="0.5/h",
            ),
        ]
        result = build_sensory_dashboard(events)
        assert result["interoceptive_count"] == 2


# ── IntentEngine View Tests ──────────────────────────────────────


class TestBuildIntentEngineView:
    def test_mixed_classification(self):
        events = [
            IterationCompletedEvent(
                timestamp=_ts(0), event_type="iteration_completed",
                project="p1", data={}, edge="d→c",
            ),
            IntentRaisedEvent(
                timestamp=_ts(5), event_type="intent_raised",
                project="p1", data={}, trigger="gap",
            ),
            ExteroceptiveSignalEvent(
                timestamp=_ts(10), event_type="exteroceptive_signal",
                project="p1", data={}, source="npm",
            ),
        ]
        result = build_intent_engine_view(events)
        assert result["reflex_log_count"] == 1
        assert result["escalate_count"] == 1
        assert result["spec_event_log_count"] == 1
        assert result["unclassified_count"] == 0

    def test_empty_events(self):
        result = build_intent_engine_view([])
        assert result["reflex_log_count"] == 0
        assert result["spec_event_log_count"] == 0
        assert result["escalate_count"] == 0
        assert result["unclassified_count"] == 0

    def test_unknown_event_type(self):
        events = [
            Event(timestamp=_ts(0), event_type="custom_future",
                  project="p1", data={}),
        ]
        result = build_intent_engine_view(events)
        assert result["unclassified_count"] == 1


# ── Updated Regime Summary Tests ─────────────────────────────────


class TestRegimeSummaryV28:
    def test_v28_conscious_events(self):
        events = [
            ConvergenceEscalatedEvent(
                timestamp=_ts(0), event_type="convergence_escalated",
                project="p1", data={}, edge="d→c",
                reason="stuck", escalated_to="human",
            ),
            ReviewCompletedEvent(
                timestamp=_ts(5), event_type="review_completed",
                project="p1", data={}, edge="d→c",
                reviewer="human", outcome="approved",
            ),
            AffectTriageEvent(
                timestamp=_ts(10), event_type="affect_triage",
                project="p1", data={}, signal_ref="s1",
                triage_result="escalate", rationale="urgent",
            ),
        ]
        result = build_regime_summary(events)
        assert result["conscious_count"] == 3
        assert result["reflex_count"] == 0

    def test_v28_reflex_events(self):
        events = [
            EdgeStartedEvent(
                timestamp=_ts(0), event_type="edge_started",
                project="p1", data={}, edge="d→c",
            ),
            InteroceptiveSignalEvent(
                timestamp=_ts(5), event_type="interoceptive_signal",
                project="p1", data={}, signal_type="rate",
            ),
        ]
        result = build_regime_summary(events)
        assert result["reflex_count"] == 2
        assert result["conscious_count"] == 0

    def test_intent_engine_breakdown_present(self):
        events = [
            IterationCompletedEvent(
                timestamp=_ts(0), event_type="iteration_completed",
                project="p1", data={}, edge="d→c",
            ),
            IntentRaisedEvent(
                timestamp=_ts(5), event_type="intent_raised",
                project="p1", data={}, trigger="gap",
            ),
        ]
        result = build_regime_summary(events)
        assert "intent_engine_breakdown" in result
        assert result["intent_engine_breakdown"]["reflex.log"] == 1
        assert result["intent_engine_breakdown"]["escalate"] == 1


# ── Updated Consciousness Timeline Tests ─────────────────────────


class TestConsciousnessTimelineV28:
    def test_includes_convergence_escalated(self):
        events = [
            ConvergenceEscalatedEvent(
                timestamp=_ts(0), event_type="convergence_escalated",
                project="p1", data={}, edge="d→c",
                reason="stuck", escalated_to="human",
            ),
        ]
        result = build_consciousness_timeline(events)
        assert len(result) == 1
        assert result[0]["event_type"] == "convergence_escalated"
        assert result[0]["escalated_to"] == "human"

    def test_includes_review_completed(self):
        events = [
            ReviewCompletedEvent(
                timestamp=_ts(0), event_type="review_completed",
                project="p1", data={}, edge="req→design",
                feature="REQ-F-001", reviewer="human", outcome="approved",
            ),
        ]
        result = build_consciousness_timeline(events)
        assert len(result) == 1
        assert result[0]["outcome"] == "approved"
        assert result[0]["reviewer"] == "human"


# ── Updated Dimension Coverage Tests ─────────────────────────────


class TestDimensionCoverageV28:
    def test_includes_tolerance_and_breach(self):
        topology = GraphTopology(
            constraint_dimensions=[
                ConstraintDimension(
                    name="performance", mandatory=True,
                    resolves_via="adr",
                    tolerance="≤ 5% degradation",
                    breach_status="ok",
                ),
                ConstraintDimension(
                    name="security", mandatory=True,
                    resolves_via="adr",
                    tolerance="zero CVEs",
                    breach_status="breached",
                ),
            ],
        )
        result = build_dimension_coverage(topology, [])
        assert result[0]["tolerance"] == "≤ 5% degradation"
        assert result[0]["breach_status"] == "ok"
        assert result[1]["breach_status"] == "breached"

    def test_missing_tolerance_defaults_empty(self):
        topology = GraphTopology(
            constraint_dimensions=[
                ConstraintDimension(name="legacy", mandatory=False),
            ],
        )
        result = build_dimension_coverage(topology, [])
        assert result[0]["tolerance"] == ""
        assert result[0]["breach_status"] == ""


# ── Updated Compliance Report Tests ──────────────────────────────


class TestComplianceReportV28:
    def test_tolerance_check_present(self):
        project = Project(
            project_id="p1", name="Test",
            topology=GraphTopology(
                asset_types=[],
                constraint_dimensions=[
                    ConstraintDimension(
                        name="perf", mandatory=True,
                        tolerance="≤ 5%", breach_status="ok",
                    ),
                ],
            ),
            features=[],
            events=[],
        )
        report = build_compliance_report(project)
        tol_checks = [c for c in report if "tolerance" in c["check"].lower()]
        assert len(tol_checks) == 1
        assert tol_checks[0]["status"] == "pass"

    def test_no_tolerance_warns(self):
        project = Project(
            project_id="p1", name="Test",
            topology=GraphTopology(
                asset_types=[],
                constraint_dimensions=[
                    ConstraintDimension(name="perf", mandatory=True),
                ],
            ),
            features=[],
            events=[],
        )
        report = build_compliance_report(project)
        tol_checks = [c for c in report if "tolerance" in c["check"].lower()]
        assert len(tol_checks) == 1
        assert tol_checks[0]["status"] == "warn"

    def test_sensory_events_check(self):
        project = Project(
            project_id="p1", name="Test",
            topology=GraphTopology(asset_types=[]),
            features=[],
            events=[
                InteroceptiveSignalEvent(
                    timestamp=_ts(0), event_type="interoceptive_signal",
                    project="p1", data={}, signal_type="rate",
                ),
            ],
        )
        report = build_compliance_report(project)
        sensory_checks = [c for c in report if "sensory" in c["check"].lower()]
        assert len(sensory_checks) == 1
        assert sensory_checks[0]["status"] == "pass"

    def test_no_sensory_events_warns(self):
        project = Project(
            project_id="p1", name="Test",
            topology=GraphTopology(asset_types=[]),
            features=[],
            events=[],
        )
        report = build_compliance_report(project)
        sensory_checks = [c for c in report if "sensory" in c["check"].lower()]
        assert len(sensory_checks) == 1
        assert sensory_checks[0]["status"] == "warn"

    def test_edge_timestamps_check(self):
        project = Project(
            project_id="p1", name="Test",
            topology=GraphTopology(asset_types=[]),
            features=[
                FeatureVector(
                    feature_id="F-001",
                    trajectory={
                        "design": EdgeTrajectory(
                            started_at=datetime(2026, 2, 20),
                            converged_at=datetime(2026, 2, 21),
                        ),
                    },
                ),
            ],
            events=[],
        )
        report = build_compliance_report(project)
        ts_checks = [c for c in report if "timestamp" in c["check"].lower()]
        assert len(ts_checks) == 1
        assert ts_checks[0]["status"] == "pass"

    def test_no_edge_timestamps_warns(self):
        project = Project(
            project_id="p1", name="Test",
            topology=GraphTopology(asset_types=[]),
            features=[
                FeatureVector(
                    feature_id="F-001",
                    trajectory={"design": EdgeTrajectory()},
                ),
            ],
            events=[],
        )
        report = build_compliance_report(project)
        ts_checks = [c for c in report if "timestamp" in c["check"].lower()]
        assert len(ts_checks) == 1
        assert ts_checks[0]["status"] == "warn"
