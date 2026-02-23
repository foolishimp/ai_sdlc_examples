# Validates: REQ-F-DASH-002, REQ-F-DASH-003, REQ-F-DASH-004, REQ-F-DASH-005, REQ-F-TELEM-001
"""Tests for projection functions."""

from pathlib import Path

from genesis_monitor.models import (
    AssetType,
    GraphTopology,
    PhaseEntry,
    Project,
    StatusReport,
    TelemSignal,
    Transition,
)
from genesis_monitor.projections import (
    build_convergence_table,
    build_gantt_mermaid,
    build_graph_mermaid,
    collect_telem_signals,
)


class TestBuildGraphMermaid:
    def test_generates_mermaid(self):
        topology = GraphTopology(
            asset_types=[
                AssetType(name="intent", description=""),
                AssetType(name="code", description=""),
            ],
            transitions=[Transition(source="intent", target="code", edge_type="ic")],
        )
        result = build_graph_mermaid(topology, None)
        assert "graph LR" in result
        assert "intent" in result
        assert "code" in result
        assert "-->" in result

    def test_colours_converged_nodes(self):
        topology = GraphTopology(
            asset_types=[
                AssetType(name="intent", description=""),
                AssetType(name="requirements", description=""),
            ],
            transitions=[Transition(source="intent", target="requirements", edge_type="ir")],
        )
        status = StatusReport(
            phase_summary=[
                PhaseEntry(edge="intent→requirements", status="converged", iterations=1),
            ]
        )
        result = build_graph_mermaid(topology, status)
        assert "done" in result  # classDef done
        assert "#4caf50" in result  # green

    def test_default_graph_without_topology(self):
        result = build_graph_mermaid(None, None)
        assert "graph LR" in result
        assert "intent" in result
        assert "uat_tests" in result

    def test_default_graph_with_status(self):
        status = StatusReport(
            phase_summary=[
                PhaseEntry(edge="intent→requirements", status="converged", iterations=1),
            ]
        )
        result = build_graph_mermaid(None, status)
        assert "done" in result


class TestBuildConvergenceTable:
    def test_builds_table(self):
        status = StatusReport(
            phase_summary=[
                PhaseEntry(
                    edge="intent→requirements",
                    status="converged",
                    iterations=1,
                    evaluator_results={"agent": "pass", "human": "skip"},
                    source_findings=3,
                    process_gaps=0,
                ),
            ]
        )
        result = build_convergence_table(status)
        assert len(result) == 1
        assert result[0].edge == "intent→requirements"
        assert result[0].evaluator_summary == "1/2 pass"
        assert result[0].source_findings == 3

    def test_no_evaluators(self):
        status = StatusReport(
            phase_summary=[PhaseEntry(edge="e", status="not_started")]
        )
        result = build_convergence_table(status)
        assert result[0].evaluator_summary == "no evaluators"

    def test_none_status(self):
        result = build_convergence_table(None)
        assert result == []


class TestBuildGanttMermaid:
    def test_extracts_gantt(self):
        status = StatusReport(gantt_mermaid="gantt\n    title Test\n    section A\n    task :done, 2026-01-01, 1d")
        result = build_gantt_mermaid(status)
        assert "gantt" in result

    def test_none_when_no_gantt(self):
        status = StatusReport()
        assert build_gantt_mermaid(status) is None

    def test_none_when_no_status(self):
        assert build_gantt_mermaid(None) is None


class TestCollectTelemSignals:
    def test_collects_across_projects(self):
        projects = [
            Project(
                project_id="p1",
                name="P1",
                status=StatusReport(
                    telem_signals=[
                        TelemSignal(signal_id="TELEM-001", category="obs", description="sig 1"),
                    ]
                ),
            ),
            Project(
                project_id="p2",
                name="P2",
                status=StatusReport(
                    telem_signals=[
                        TelemSignal(signal_id="TELEM-001", category="obs", description="sig 2"),
                    ]
                ),
            ),
        ]
        result = collect_telem_signals(projects)
        assert len(result) == 2
        assert result[0].project_id == "p1"
        assert result[1].project_id == "p2"

    def test_skips_projects_without_status(self):
        projects = [
            Project(project_id="p1", name="P1", status=None),
        ]
        result = collect_telem_signals(projects)
        assert result == []
