# Validates: REQ-F-PARSE-001, REQ-F-PARSE-002, REQ-F-PARSE-003, REQ-F-PARSE-004, REQ-F-PARSE-005, REQ-F-PARSE-006
"""Tests for all parsers."""

from pathlib import Path

from genesis_monitor.parsers import (
    parse_constraints,
    parse_events,
    parse_feature_vectors,
    parse_graph_topology,
    parse_status,
    parse_tasks,
)


# ── STATUS.md parser ─────────────────────────────────────────────


class TestParseStatus:
    def test_parse_valid_status(self, workspace_path: Path):
        result = parse_status(workspace_path)
        assert result is not None
        assert result.project_name == "Test CDME Project"

    def test_parse_phase_summary(self, workspace_path: Path):
        result = parse_status(workspace_path)
        assert len(result.phase_summary) == 3
        assert result.phase_summary[0].edge == "intent→requirements"
        assert result.phase_summary[0].status == "converged"
        assert result.phase_summary[0].iterations == 1
        assert result.phase_summary[2].status == "in_progress"

    def test_parse_telem_signals(self, workspace_path: Path):
        result = parse_status(workspace_path)
        assert len(result.telem_signals) == 2
        assert result.telem_signals[0].signal_id == "TELEM-001"
        assert "test signal" in result.telem_signals[0].description.lower()

    def test_parse_gantt_mermaid(self, workspace_path: Path):
        result = parse_status(workspace_path)
        assert result.gantt_mermaid is not None
        assert "gantt" in result.gantt_mermaid
        assert "intent→requirements" in result.gantt_mermaid

    def test_parse_metrics(self, workspace_path: Path):
        result = parse_status(workspace_path)
        assert "Total REQ keys" in result.metrics
        assert result.metrics["Total REQ keys"] == "26"

    def test_parse_missing_file(self, tmp_path: Path):
        result = parse_status(tmp_path)
        assert result is None

    def test_parse_empty_file(self, tmp_path: Path):
        (tmp_path / "STATUS.md").write_text("")
        result = parse_status(tmp_path)
        assert result is not None
        assert result.phase_summary == []


# ── Feature vector parser ────────────────────────────────────────


class TestParseFeatureVectors:
    def test_parse_valid_vectors(self, workspace_path: Path):
        result = parse_feature_vectors(workspace_path)
        assert len(result) == 1
        assert result[0].feature_id == "REQ-F-GMON-001"
        assert result[0].title == "Genesis Monitor Dashboard"
        assert result[0].status == "in_progress"

    def test_parse_trajectory(self, workspace_path: Path):
        result = parse_feature_vectors(workspace_path)
        vec = result[0]
        assert "requirements" in vec.trajectory
        assert vec.trajectory["requirements"].status == "converged"
        assert vec.trajectory["requirements"].iteration == 1
        assert vec.trajectory["code"].status == "in_progress"

    def test_parse_missing_directory(self, tmp_path: Path):
        result = parse_feature_vectors(tmp_path)
        assert result == []

    def test_parse_empty_directory(self, tmp_path: Path):
        (tmp_path / "features" / "active").mkdir(parents=True)
        result = parse_feature_vectors(tmp_path)
        assert result == []


# ── Graph topology parser ────────────────────────────────────────


class TestParseGraphTopology:
    def test_parse_valid_topology(self, workspace_path: Path):
        result = parse_graph_topology(workspace_path)
        assert result is not None
        assert len(result.asset_types) == 4
        assert len(result.transitions) == 3

    def test_asset_type_names(self, workspace_path: Path):
        result = parse_graph_topology(workspace_path)
        names = [at.name for at in result.asset_types]
        assert "intent" in names
        assert "code" in names

    def test_transition_edges(self, workspace_path: Path):
        result = parse_graph_topology(workspace_path)
        sources = [t.source for t in result.transitions]
        assert "intent" in sources
        assert "design" in sources

    def test_parse_missing_file(self, tmp_path: Path):
        result = parse_graph_topology(tmp_path)
        assert result is None


# ── Event log parser ─────────────────────────────────────────────


class TestParseEvents:
    def test_parse_valid_events(self, workspace_path: Path):
        result = parse_events(workspace_path)
        assert len(result) == 2
        assert result[0].event_type == "edge_started"
        assert result[1].event_type == "edge_converged"

    def test_parse_timestamps(self, workspace_path: Path):
        result = parse_events(workspace_path)
        assert result[0].timestamp.year == 2026

    def test_parse_missing_file(self, tmp_path: Path):
        result = parse_events(tmp_path)
        assert result == []

    def test_parse_empty_file(self, tmp_path: Path):
        events_dir = tmp_path / "events"
        events_dir.mkdir()
        (events_dir / "events.jsonl").write_text("")
        result = parse_events(tmp_path)
        assert result == []

    def test_parse_corrupt_line(self, tmp_path: Path):
        events_dir = tmp_path / "events"
        events_dir.mkdir()
        (events_dir / "events.jsonl").write_text('{"event_type": "ok"}\nnot json\n{"event_type": "also_ok"}\n')
        result = parse_events(tmp_path)
        assert len(result) == 2


# ── Tasks parser ─────────────────────────────────────────────────


class TestParseTasks:
    def test_parse_valid_tasks(self, workspace_path: Path):
        result = parse_tasks(workspace_path)
        assert len(result) == 3
        assert result[0].task_id == "1"
        assert result[0].title == "Write requirements"
        assert result[0].status == "completed"

    def test_parse_task_statuses(self, workspace_path: Path):
        result = parse_tasks(workspace_path)
        statuses = [t.status for t in result]
        assert "completed" in statuses
        assert "in_progress" in statuses
        assert "pending" in statuses

    def test_parse_missing_file(self, tmp_path: Path):
        result = parse_tasks(tmp_path)
        assert result == []

    def test_parse_bullet_format(self, tmp_path: Path):
        tasks_dir = tmp_path / "tasks" / "active"
        tasks_dir.mkdir(parents=True)
        (tasks_dir / "ACTIVE_TASKS.md").write_text("""\
# Tasks

- [x] #1 - Done task
- [ ] #2 - Pending task
""")
        result = parse_tasks(tmp_path)
        assert len(result) == 2
        assert result[0].status == "completed"
        assert result[1].status == "pending"


# ── Constraints parser ───────────────────────────────────────────


class TestParseConstraints:
    def test_parse_valid_constraints(self, workspace_path: Path):
        result = parse_constraints(workspace_path)
        assert result is not None
        assert result.language == "python"

    def test_parse_tools(self, workspace_path: Path):
        result = parse_constraints(workspace_path)
        assert "test_runner" in result.tools
        assert result.tools["test_runner"]["command"] == "pytest"

    def test_parse_thresholds(self, workspace_path: Path):
        result = parse_constraints(workspace_path)
        assert result.thresholds["test_coverage_minimum"] == "80%"

    def test_raw_preserved(self, workspace_path: Path):
        result = parse_constraints(workspace_path)
        assert "language" in result.raw

    def test_parse_missing_file(self, tmp_path: Path):
        result = parse_constraints(tmp_path)
        assert result is None
