# Validates: REQ-F-VREL-002, REQ-F-VREL-003, REQ-F-CDIM-002, REQ-F-REGIME-001,
# REQ-F-REGIME-002, REQ-F-CONSC-001, REQ-F-CONSC-002, REQ-F-CONSC-003,
# REQ-F-TBOX-002, REQ-F-PROTO-001
"""Tests for v2.5 projection functions."""

from datetime import datetime

from genesis_monitor.models.core import (
    EdgeTrajectory,
    FeatureVector,
    GraphTopology,
    Project,
    StatusReport,
)
from genesis_monitor.models.events import (
    EdgeConvergedEvent,
    EvaluatorRanEvent,
    Event,
    FeatureSpawnedEvent,
    FindingRaisedEvent,
    IntentRaisedEvent,
    IterationCompletedEvent,
    SpecModifiedEvent,
    TelemetrySignalEmittedEvent,
)
from genesis_monitor.models.features import (
    ConstraintDimension,
    ProjectionProfile,
    TimeBox,
)
from genesis_monitor.projections.spawn_tree import build_spawn_tree
from genesis_monitor.projections.dimensions import build_dimension_coverage
from genesis_monitor.projections.regimes import build_regime_summary
from genesis_monitor.projections.consciousness import build_consciousness_timeline
from genesis_monitor.projections.compliance import build_compliance_report


# ── Spawn Tree Tests ──────────────────────────────────────────


class TestBuildSpawnTree:
    def test_flat_features_no_parents(self):
        features = [
            FeatureVector(feature_id="F-001", title="Feature 1", status="in_progress"),
            FeatureVector(feature_id="F-002", title="Feature 2", status="pending"),
        ]
        roots = build_spawn_tree(features)
        assert len(roots) == 2
        assert roots[0]["feature_id"] == "F-001"
        assert roots[1]["feature_id"] == "F-002"
        assert roots[0]["children"] == []

    def test_parent_child_nesting(self):
        features = [
            FeatureVector(feature_id="F-001", title="Parent", status="in_progress",
                          children=["F-002", "F-003"]),
            FeatureVector(feature_id="F-002", title="Child 1", status="pending",
                          parent_id="F-001"),
            FeatureVector(feature_id="F-003", title="Child 2", status="pending",
                          parent_id="F-001"),
        ]
        roots = build_spawn_tree(features)
        assert len(roots) == 1  # Only parent at root level
        assert roots[0]["feature_id"] == "F-001"
        assert len(roots[0]["children"]) == 2
        assert roots[0]["children"][0]["feature_id"] == "F-002"

    def test_includes_vector_type_and_fold_back(self):
        features = [
            FeatureVector(
                feature_id="F-001", title="Spike",
                status="folded_back", vector_type="spike",
                fold_back_status="partial_results",
                spawned_by="F-000",
                parent_id="F-000",
            ),
            FeatureVector(feature_id="F-000", title="Parent", status="in_progress",
                          children=["F-001"]),
        ]
        roots = build_spawn_tree(features)
        assert len(roots) == 1
        child = roots[0]["children"][0]
        assert child["vector_type"] == "spike"
        assert child["fold_back_status"] == "partial_results"
        assert child["spawned_by"] == "F-000"

    def test_empty_features(self):
        roots = build_spawn_tree([])
        assert roots == []

    def test_includes_profile_and_time_box(self):
        features = [
            FeatureVector(
                feature_id="F-001", title="PoC",
                status="in_progress", vector_type="poc",
                profile="poc",
                time_box=TimeBox(duration="2h", on_expiry="fold_back"),
            ),
        ]
        roots = build_spawn_tree(features)
        assert roots[0]["profile"] == "poc"
        assert roots[0]["time_box"] is not None
        assert roots[0]["time_box"]["duration"] == "2h"


# ── Dimension Coverage Tests ──────────────────────────────────


class TestBuildDimensionCoverage:
    def test_basic_coverage(self):
        topology = GraphTopology(
            constraint_dimensions=[
                ConstraintDimension(name="security", mandatory=True, resolves_via="design"),
                ConstraintDimension(name="performance", mandatory=False, resolves_via="code"),
            ],
        )
        features = [
            FeatureVector(feature_id="F-001", title="Auth", status="in_progress",
                          trajectory={"design": EdgeTrajectory(status="converged")}),
        ]
        result = build_dimension_coverage(topology, features)
        assert len(result) == 2
        assert result[0]["dimension"] == "security"
        assert result[0]["mandatory"] is True
        assert result[1]["dimension"] == "performance"

    def test_no_topology(self):
        result = build_dimension_coverage(None, [])
        assert result == []

    def test_no_dimensions(self):
        topology = GraphTopology()
        result = build_dimension_coverage(topology, [])
        assert result == []

    def test_coverage_count(self):
        topology = GraphTopology(
            constraint_dimensions=[
                ConstraintDimension(name="security", mandatory=True, resolves_via="design"),
            ],
        )
        features = [
            FeatureVector(feature_id="F-001", title="A", status="converged",
                          trajectory={"design": EdgeTrajectory(status="converged")}),
            FeatureVector(feature_id="F-002", title="B", status="in_progress",
                          trajectory={"design": EdgeTrajectory(status="in_progress")}),
        ]
        result = build_dimension_coverage(topology, features)
        # Both features have trajectories at the resolves_via edge
        assert result[0]["feature_count"] >= 1


# ── Regime Summary Tests ──────────────────────────────────────


class TestBuildRegimeSummary:
    def _ts(self):
        return datetime(2026, 2, 21, 10, 0, 0)

    def test_classifies_conscious_events(self):
        events = [
            IntentRaisedEvent(timestamp=self._ts(), event_type="intent_raised",
                              project="p1", data={}, trigger="gap_found"),
            SpecModifiedEvent(timestamp=self._ts(), event_type="spec_modified",
                              project="p1", data={}, delta="added section"),
            FindingRaisedEvent(timestamp=self._ts(), event_type="finding_raised",
                               project="p1", data={}, finding_type="backward"),
        ]
        result = build_regime_summary(events)
        assert result["conscious_count"] == 3
        assert result["reflex_count"] == 0

    def test_classifies_reflex_events(self):
        events = [
            EvaluatorRanEvent(timestamp=self._ts(), event_type="evaluator_ran",
                              project="p1", data={}, evaluator_type="agent"),
            IterationCompletedEvent(timestamp=self._ts(), event_type="iteration_completed",
                                    project="p1", data={}, edge="design→code"),
            EdgeConvergedEvent(timestamp=self._ts(), event_type="edge_converged",
                               project="p1", data={}, edge="design→code"),
            TelemetrySignalEmittedEvent(timestamp=self._ts(), event_type="telemetry_signal_emitted",
                                        project="p1", data={}, signal_id="TELEM-001"),
        ]
        result = build_regime_summary(events)
        assert result["conscious_count"] == 0
        assert result["reflex_count"] == 4

    def test_mixed_events(self):
        events = [
            IntentRaisedEvent(timestamp=self._ts(), event_type="intent_raised",
                              project="p1", data={}, trigger="gap_found"),
            EvaluatorRanEvent(timestamp=self._ts(), event_type="evaluator_ran",
                              project="p1", data={}, evaluator_type="agent"),
        ]
        result = build_regime_summary(events)
        assert result["conscious_count"] == 1
        assert result["reflex_count"] == 1
        assert result["total"] == 2

    def test_empty_events(self):
        result = build_regime_summary([])
        assert result["conscious_count"] == 0
        assert result["reflex_count"] == 0
        assert result["total"] == 0

    def test_unknown_events_classified(self):
        events = [
            Event(timestamp=self._ts(), event_type="custom_event",
                  project="p1", data={}),
        ]
        result = build_regime_summary(events)
        # Unknown events go to unclassified
        assert result["total"] == 1


# ── Consciousness Timeline Tests ──────────────────────────────


class TestBuildConsciousnessTimeline:
    def _ts(self, minute=0):
        return datetime(2026, 2, 21, 10, minute, 0)

    def test_extracts_intent_events(self):
        events = [
            IntentRaisedEvent(timestamp=self._ts(0), event_type="intent_raised",
                              project="p1", data={}, trigger="gap_found",
                              signal_source="telemetry"),
            IntentRaisedEvent(timestamp=self._ts(5), event_type="intent_raised",
                              project="p1", data={}, trigger="ecosystem_change",
                              signal_source="monitor"),
        ]
        result = build_consciousness_timeline(events)
        assert len(result) >= 2
        assert result[0]["event_type"] == "intent_raised"
        assert result[0]["trigger"] == "gap_found"

    def test_extracts_spec_modifications(self):
        events = [
            SpecModifiedEvent(timestamp=self._ts(0), event_type="spec_modified",
                              project="p1", data={}, delta="added section",
                              trigger_intent="INT-001"),
        ]
        result = build_consciousness_timeline(events)
        assert len(result) == 1
        assert result[0]["delta"] == "added section"

    def test_extracts_findings(self):
        events = [
            FindingRaisedEvent(timestamp=self._ts(0), event_type="finding_raised",
                               project="p1", data={}, finding_type="backward",
                               description="Missing requirement"),
        ]
        result = build_consciousness_timeline(events)
        assert len(result) == 1
        assert result[0]["finding_type"] == "backward"

    def test_filters_out_reflex_events(self):
        events = [
            IntentRaisedEvent(timestamp=self._ts(0), event_type="intent_raised",
                              project="p1", data={}, trigger="gap_found"),
            EvaluatorRanEvent(timestamp=self._ts(1), event_type="evaluator_ran",
                              project="p1", data={}, evaluator_type="agent"),
            IterationCompletedEvent(timestamp=self._ts(2), event_type="iteration_completed",
                                    project="p1", data={}, edge="d→c"),
        ]
        result = build_consciousness_timeline(events)
        assert len(result) == 1  # Only intent_raised

    def test_chronological_order(self):
        events = [
            IntentRaisedEvent(timestamp=self._ts(10), event_type="intent_raised",
                              project="p1", data={}, trigger="gap_found"),
            SpecModifiedEvent(timestamp=self._ts(5), event_type="spec_modified",
                              project="p1", data={}, delta="updated"),
        ]
        result = build_consciousness_timeline(events)
        assert result[0]["timestamp"] <= result[1]["timestamp"]

    def test_empty_events(self):
        result = build_consciousness_timeline([])
        assert result == []

    def test_includes_spawned_events(self):
        events = [
            FeatureSpawnedEvent(timestamp=self._ts(0), event_type="feature_spawned",
                                project="p1", data={}, parent_vector="F-001",
                                child_vector="F-002", reason="gap"),
        ]
        result = build_consciousness_timeline(events)
        assert len(result) == 1
        assert result[0]["parent_vector"] == "F-001"


# ── Compliance Report Tests ───────────────────────────────────


class TestBuildComplianceReport:
    def test_full_compliant_project(self):
        project = Project(
            project_id="p1",
            name="Test",
            status=StatusReport(project_name="Test"),
            features=[FeatureVector(feature_id="F-001", profile="standard")],
            topology=GraphTopology(
                constraint_dimensions=[
                    ConstraintDimension(name="security", mandatory=True, resolves_via="design"),
                ],
                profiles=[
                    ProjectionProfile(name="standard"),
                ],
            ),
            events=[
                Event(timestamp=datetime.now(), event_type="edge_converged",
                      project="p1", data={}),
            ],
        )
        report = build_compliance_report(project)
        assert isinstance(report, list)
        assert len(report) > 0
        # Each entry has check, status, detail
        for entry in report:
            assert "check" in entry
            assert "status" in entry
            assert "detail" in entry

    def test_missing_topology(self):
        project = Project(
            project_id="p1", name="Test",
            topology=None,
        )
        report = build_compliance_report(project)
        topo_checks = [e for e in report if "topology" in e["check"].lower()]
        assert any(e["status"] == "fail" for e in topo_checks)

    def test_missing_events(self):
        project = Project(project_id="p1", name="Test", events=[])
        report = build_compliance_report(project)
        event_checks = [e for e in report if "event" in e["check"].lower()]
        assert any(e["status"] == "warn" for e in event_checks)

    def test_missing_features(self):
        project = Project(project_id="p1", name="Test", features=[])
        report = build_compliance_report(project)
        feat_checks = [e for e in report if "feature" in e["check"].lower()]
        assert any(e["status"] in ("warn", "fail") for e in feat_checks)

    def test_missing_constraint_dimensions(self):
        project = Project(
            project_id="p1", name="Test",
            topology=GraphTopology(constraint_dimensions=[]),
        )
        report = build_compliance_report(project)
        dim_checks = [e for e in report if "dimension" in e["check"].lower()]
        assert any(e["status"] in ("warn", "fail") for e in dim_checks)

    def test_missing_profiles(self):
        project = Project(
            project_id="p1", name="Test",
            topology=GraphTopology(profiles=[]),
        )
        report = build_compliance_report(project)
        prof_checks = [e for e in report if "profile" in e["check"].lower()]
        assert any(e["status"] in ("warn", "fail") for e in prof_checks)

    def test_features_without_profiles(self):
        project = Project(
            project_id="p1", name="Test",
            features=[
                FeatureVector(feature_id="F-001", profile=None),
                FeatureVector(feature_id="F-002", profile="standard"),
            ],
        )
        report = build_compliance_report(project)
        # Should note that some features lack profiles
        prof_checks = [e for e in report if "profile" in e["check"].lower()]
        assert len(prof_checks) > 0
