# Validates: REQ-F-PARSE-001, REQ-F-PARSE-002
"""Tests for data models."""

from pathlib import Path

from genesis_monitor.models import (
    AppConfig,
    EdgeTrajectory,
    FeatureVector,
    PhaseEntry,
    Project,
    StatusReport,
)


class TestModels:
    def test_project_defaults(self):
        p = Project()
        assert p.project_id == ""
        assert p.features == []
        assert p.status is None

    def test_status_report_defaults(self):
        s = StatusReport()
        assert s.phase_summary == []
        assert s.telem_signals == []
        assert s.gantt_mermaid is None

    def test_phase_entry(self):
        pe = PhaseEntry(edge="a→b", status="converged", iterations=2)
        assert pe.edge == "a→b"
        assert pe.source_findings == 0

    def test_feature_vector_trajectory(self):
        fv = FeatureVector(
            feature_id="REQ-001",
            trajectory={
                "requirements": EdgeTrajectory(status="converged", iteration=1),
            },
        )
        assert fv.trajectory["requirements"].status == "converged"

    def test_app_config_defaults(self):
        c = AppConfig()
        assert c.port == 8000
        assert ".git" in c.exclude_patterns
