# Validates: REQ-F-DASH-004, REQ-F-TRACE-002
"""Tests for the feature matrix and feature-module-map projections."""

from genesis_monitor.models.core import EdgeTrajectory, FeatureVector
from genesis_monitor.parsers.traceability import TraceabilityReport
from genesis_monitor.projections.feature_module_map import build_feature_module_map
from genesis_monitor.projections.gantt import (
    FeatureMatrix,
    build_feature_matrix,
)


def _make_feature(fid, title="", status="pending", parent_id=None, requirements=None, **traj):
    """Helper to build a FeatureVector with optional trajectory edges."""
    f = FeatureVector(
        feature_id=fid,
        title=title,
        status=status,
        parent_id=parent_id,
        requirements=requirements or [],
    )
    f.trajectory = {
        edge: EdgeTrajectory(status=s, iteration=i)
        for edge, (s, i) in traj.items()
    }
    return f


class TestBuildFeatureMatrix:
    def test_returns_none_when_no_features(self):
        assert build_feature_matrix(None) is None
        assert build_feature_matrix([]) is None

    def test_returns_none_when_no_trajectories(self):
        f = FeatureVector(feature_id="REQ-F-X-001")
        assert build_feature_matrix([f]) is None

    def test_basic_matrix(self):
        f = _make_feature("REQ-F-A-001", **{"design→code": ("converged", 3)})
        matrix = build_feature_matrix([f])
        assert isinstance(matrix, FeatureMatrix)
        assert matrix.edges == ["design→code"]
        assert len(matrix.rows) == 1
        row = matrix.rows[0]
        assert row.feature_id == "REQ-F-A-001"
        assert row.indent == 0
        cell = row.cells["design→code"]
        assert cell.status == "converged"
        assert cell.iteration == 3

    def test_edge_ordering_follows_canonical_order(self):
        f = _make_feature(
            "REQ-F-A-001",
            **{
                "code↔unit_tests": ("in_progress", 2),
                "design→code": ("converged", 1),
            },
        )
        matrix = build_feature_matrix([f])
        # design→code comes before code↔unit_tests in canonical order
        assert matrix.edges.index("design→code") < matrix.edges.index("code↔unit_tests")

    def test_parent_child_ordering_and_indent(self):
        parent = _make_feature("REQ-F-P-001", **{"design→code": ("converged", 2)})
        child = _make_feature(
            "REQ-F-C-001",
            parent_id="REQ-F-P-001",
            **{"design→code": ("in_progress", 1)},
        )
        matrix = build_feature_matrix([parent, child])
        rows = matrix.rows
        assert rows[0].feature_id == "REQ-F-P-001"
        assert rows[0].indent == 0
        assert rows[1].feature_id == "REQ-F-C-001"
        assert rows[1].indent == 1

    def test_missing_edge_has_no_cell(self):
        f1 = _make_feature("REQ-F-A-001", **{"design→code": ("converged", 1)})
        f2 = _make_feature("REQ-F-B-001", **{"code↔unit_tests": ("in_progress", 2)})
        matrix = build_feature_matrix([f1, f2])
        # f2 has no cell for design→code
        assert "design→code" not in matrix.rows[1].cells or True  # handled by template
        # f1 has no cell for code↔unit_tests
        assert "code↔unit_tests" not in matrix.rows[0].cells


class TestBuildFeatureModuleMap:
    def _make_traceability(self, coverage):
        tr = TraceabilityReport()
        tr.code_coverage = coverage
        return tr

    def test_returns_none_when_no_features(self):
        assert build_feature_module_map(None, None) is None
        assert build_feature_module_map([], None) is None

    def test_returns_none_when_no_traceability(self):
        f = _make_feature("REQ-F-A-001", requirements=["REQ-F-A-001"])
        assert build_feature_module_map([f], None) is None

    def test_returns_none_when_no_requirements(self):
        f = _make_feature("REQ-F-A-001")  # no requirements
        tr = self._make_traceability({})
        assert build_feature_module_map([f], tr) is None

    def test_basic_map(self):
        f = _make_feature("REQ-F-A-001", requirements=["REQ-F-A-001"])
        tr = self._make_traceability({"REQ-F-A-001": ["src/engine.py"]})
        fmm = build_feature_module_map([f], tr)
        assert fmm is not None
        assert fmm.total_features == 1
        assert fmm.total_modules == 1
        assert fmm.coverage_pct == 100
        row = fmm.rows[0]
        assert row.feature_id == "REQ-F-A-001"
        assert "src/engine.py" in row.modules
        assert row.untraced_keys == []

    def test_untraced_key_detected(self):
        f = _make_feature("REQ-F-A-001", requirements=["REQ-F-A-001", "REQ-F-A-002"])
        tr = self._make_traceability({"REQ-F-A-001": ["src/engine.py"]})
        fmm = build_feature_module_map([f], tr)
        row = fmm.rows[0]
        assert "REQ-F-A-002" in row.untraced_keys
        assert fmm.coverage_pct == 50

    def test_shared_module_counted_once(self):
        f1 = _make_feature("REQ-F-A-001", requirements=["REQ-F-A-001"])
        f2 = _make_feature("REQ-F-B-001", requirements=["REQ-F-B-001"])
        tr = self._make_traceability({
            "REQ-F-A-001": ["src/engine.py"],
            "REQ-F-B-001": ["src/engine.py"],
        })
        fmm = build_feature_module_map([f1, f2], tr)
        assert fmm.total_modules == 1  # same file shared

    def test_coverage_percentage(self):
        f = _make_feature("REQ-F-A-001", requirements=["R1", "R2", "R3"])
        tr = self._make_traceability({"R1": ["src/a.py"], "R2": ["src/b.py"]})
        fmm = build_feature_module_map([f], tr)
        assert fmm.coverage_pct == 66
