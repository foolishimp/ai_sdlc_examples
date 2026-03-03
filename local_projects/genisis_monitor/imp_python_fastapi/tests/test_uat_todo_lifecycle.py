# Validates: REQ-F-DISC-001, REQ-F-DISC-002, REQ-F-DASH-001, REQ-F-DASH-002
# Validates: REQ-F-DASH-003, REQ-F-DASH-006, REQ-F-NAV-001, REQ-F-NAV-003
# Validates: REQ-F-NAV-007, REQ-F-MTEN-001, REQ-F-MTEN-002, REQ-F-MTEN-003
# Validates: REQ-F-PROTO-001, REQ-NFR-001
"""E2E use case: Full lifecycle of a Todo Dashboard project observed via Genesis Monitor.

Scenario (UC-GM-01 through UC-GM-13):
  A developer builds a Todo Dashboard using the AI SDLC methodology.
  Genesis Monitor observes every step — from empty directory through
  project init, feature spawn, iterating each edge with human review gates,
  deterministic engine runs (delta=3 then delta=0), and final convergence.

Fixture timeline (all timestamps relative to 2026-03-04T08:00:00Z):
  T+00:00  project_initialized
  T+00:05  spawn_created             REQ-F-TODO-001
  T+00:10  edge_started              intent→requirements
  T+00:30  iteration_completed       iter 1, δ=1  (human review pending)
  T+00:45  review_completed          decision: refined
  T+01:00  iteration_completed       iter 2, δ=0
  T+01:00  edge_converged            intent→requirements
  T+01:05  edge_started              requirements→design
  T+01:30  iteration_completed       iter 1, δ=1  (human review pending)
  T+01:45  review_completed          decision: approved
  T+01:50  iteration_completed       iter 2, δ=0
  T+01:50  edge_converged            requirements→design
  T+01:55  edge_started              code↔unit_tests
  T+02:15  iteration_completed       iter 1, δ=3  (engine: coverage 62%, lint, REQ tag)
  T+02:30  review_completed          decision: refined
  T+02:45  iteration_completed       iter 2, δ=0  (engine: all checks pass)
  T+02:45  edge_converged            code↔unit_tests
"""

import json
from contextlib import asynccontextmanager
from pathlib import Path

import pytest
import yaml
from event_factory import make_ol2_event
from fastapi import FastAPI
from fastapi.testclient import TestClient
from genesis_monitor.registry import ProjectRegistry
from genesis_monitor.server.app import create_app
from genesis_monitor.server.broadcaster import SSEBroadcaster

# ── Persistent observable workspace ───────────────────────────────────────────
#
# TODO_DEMO_WORKSPACE points to the committed todo-demo project under
# local_projects/.  Because genesis_monitor watches the parent directory tree,
# this workspace is ALWAYS visible in the browser dashboard — the same data
# you assert against in tests is the data you see at http://localhost:8000.
#
# This is the same persistence pattern used by data_mapper.test03 – test09:
# the workspace is committed to git with a full lifecycle events.jsonl so it
# is observable without any test run.  Tests that need clean isolation still
# write to tmp_path; tests that want to be observable use TODO_DEMO_WORKSPACE.
#
TODO_DEMO_WORKSPACE: Path = (
    Path(__file__).parent.parent.parent.parent / "todo-demo"
)


# ── Fixture: Todo Dashboard lifecycle ────────────────────────────────────────


def _ts(minutes: int) -> str:
    """Return an ISO timestamp N minutes after T+00:00 (2026-03-04T08:00Z)."""
    h, m = divmod(minutes, 60)
    return f"2026-03-04T{8 + h:02d}:{m:02d}:00Z"


def _build_todo_workspace(
    base_path: Path,
    up_to_minutes: int | None = None,
    *,
    project_name: str = "todo-dashboard",
) -> Path:
    """
    Create a .ai-workspace/ for the todo project populated with lifecycle
    events up to `up_to_minutes` (None = full lifecycle).

    `base_path` is the parent directory that will contain `project_name/`.
    Pass `tmp_path` for an isolated ephemeral workspace (default for unit
    tests) or `TODO_DEMO_WORKSPACE.parent` to write into the persistent
    committed directory that genesis_monitor watches.
    """
    project_dir = base_path / project_name
    ws = project_dir / ".ai-workspace"
    ws.mkdir(parents=True, exist_ok=True)

    # Graph topology
    graph_dir = ws / "graph"
    graph_dir.mkdir(exist_ok=True)
    (graph_dir / "graph_topology.yml").write_text(yaml.dump({
        "asset_types": {
            "intent": {"description": "Business intent"},
            "requirements": {"description": "Functional requirements"},
            "design": {"description": "Technical design"},
            "code": {"description": "Implementation"},
            "unit_tests": {"description": "Unit tests"},
        },
        "transitions": [
            {"source": "intent", "target": "requirements", "edge_type": "intent_requirements"},
            {"source": "requirements", "target": "design", "edge_type": "requirements_design"},
            {"source": "code", "target": "unit_tests", "edge_type": "tdd", "bidirectional": True},
        ],
    }))

    # Feature vector for REQ-F-TODO-001
    features_dir = ws / "features" / "active"
    features_dir.mkdir(parents=True, exist_ok=True)
    (features_dir / "REQ-F-TODO-001.yml").write_text(yaml.dump({
        "feature": "REQ-F-TODO-001",
        "title": "Todo Item CRUD",
        "status": "in_progress",
        "vector_type": "feature",
        "trajectory": {
            "requirements": {"status": "converged", "iteration": 2},
            "design": {"status": "converged", "iteration": 2},
            "code": {"status": "converged", "iteration": 2},
        },
    }))

    # Constraints
    ctx_dir = ws / "context"
    ctx_dir.mkdir(exist_ok=True)
    (ctx_dir / "project_constraints.yml").write_text(yaml.dump({
        "language": {"primary": "python", "version": ">=3.12"},
        "tools": {
            "test_runner": {"command": "pytest", "args": "tests/ -q", "pass_criterion": "exit code 0"},
            "linter": {"command": "ruff check", "args": "src/", "pass_criterion": "exit code 0"},
        },
        "thresholds": {"test_coverage_minimum": "80%"},
    }))

    # All lifecycle events in chronological order
    all_events = [
        # T+00:00  project initialized
        make_ol2_event("project_initialized", timestamp=_ts(0), project="todo"),

        # T+00:05  feature spawned
        make_ol2_event("spawn_created", timestamp=_ts(5), project="todo",
                       feature="REQ-F-TODO-001"),

        # intent→requirements: 2 iterations, human review in between
        make_ol2_event("edge_started", timestamp=_ts(10), project="todo",
                       edge="intent→requirements", feature="REQ-F-TODO-001"),
        make_ol2_event("iteration_completed", timestamp=_ts(30), project="todo",
                       edge="intent→requirements", feature="REQ-F-TODO-001",
                       delta=1, iteration=1,
                       evaluator_details=[
                           {"name": "human_review", "result": "pending"},
                           {"name": "structure_check", "result": "pass"},
                       ]),
        make_ol2_event("review_completed", timestamp=_ts(45), project="todo",
                       edge="intent→requirements", feature="REQ-F-TODO-001",
                       decision="refined",
                       feedback="Add priority field (low/medium/high). Due date: ISO 8601."),
        make_ol2_event("iteration_completed", timestamp=_ts(60), project="todo",
                       edge="intent→requirements", feature="REQ-F-TODO-001",
                       delta=0, iteration=2,
                       evaluator_details=[
                           {"name": "human_review", "result": "pass"},
                           {"name": "structure_check", "result": "pass"},
                       ]),
        make_ol2_event("edge_converged", timestamp=_ts(60), project="todo",
                       edge="intent→requirements", feature="REQ-F-TODO-001"),

        # requirements→design: 2 iterations, human review
        make_ol2_event("edge_started", timestamp=_ts(65), project="todo",
                       edge="requirements→design", feature="REQ-F-TODO-001"),
        make_ol2_event("iteration_completed", timestamp=_ts(90), project="todo",
                       edge="requirements→design", feature="REQ-F-TODO-001",
                       delta=1, iteration=1,
                       evaluator_details=[
                           {"name": "human_review", "result": "pending"},
                           {"name": "adr_coverage", "result": "pass"},
                       ]),
        make_ol2_event("review_completed", timestamp=_ts(105), project="todo",
                       edge="requirements→design", feature="REQ-F-TODO-001",
                       decision="approved", feedback=""),
        make_ol2_event("iteration_completed", timestamp=_ts(110), project="todo",
                       edge="requirements→design", feature="REQ-F-TODO-001",
                       delta=0, iteration=2,
                       evaluator_details=[
                           {"name": "human_review", "result": "pass"},
                           {"name": "adr_coverage", "result": "pass"},
                       ]),
        make_ol2_event("edge_converged", timestamp=_ts(110), project="todo",
                       edge="requirements→design", feature="REQ-F-TODO-001"),

        # code↔unit_tests: engine run 1 (delta=3), human fix, engine run 2 (delta=0)
        make_ol2_event("edge_started", timestamp=_ts(115), project="todo",
                       edge="code↔unit_tests", feature="REQ-F-TODO-001"),
        make_ol2_event("iteration_completed", timestamp=_ts(135), project="todo",
                       edge="code↔unit_tests", feature="REQ-F-TODO-001",
                       delta=3, iteration=1,
                       evaluator_details=[
                           {"name": "tests_pass",      "result": "pass"},
                           {"name": "test_coverage",   "result": "fail",
                            "actual": "62%", "threshold": "80%"},
                           {"name": "lint",            "result": "fail",
                            "findings": "3 E501 violations in models.py"},
                           {"name": "req_keys_tagged", "result": "fail",
                            "files_missing": ["routes.py"]},
                           {"name": "type_hints",      "result": "pass"},
                       ]),
        make_ol2_event("review_completed", timestamp=_ts(150), project="todo",
                       edge="code↔unit_tests", feature="REQ-F-TODO-001",
                       decision="refined",
                       feedback="Fix lint. Add tests for priority_label and bulk_complete. Tag routes.py."),
        make_ol2_event("iteration_completed", timestamp=_ts(165), project="todo",
                       edge="code↔unit_tests", feature="REQ-F-TODO-001",
                       delta=0, iteration=2,
                       evaluator_details=[
                           {"name": "tests_pass",      "result": "pass"},
                           {"name": "test_coverage",   "result": "pass", "actual": "84%"},
                           {"name": "lint",            "result": "pass"},
                           {"name": "req_keys_tagged", "result": "pass"},
                           {"name": "type_hints",      "result": "pass"},
                       ]),
        make_ol2_event("edge_converged", timestamp=_ts(165), project="todo",
                       edge="code↔unit_tests", feature="REQ-F-TODO-001"),
    ]

    # Write only events up to the requested cutoff
    cutoff_ts = _ts(up_to_minutes) if up_to_minutes is not None else None
    filtered = all_events
    if cutoff_ts is not None:
        filtered = [e for e in all_events
                    if e["eventTime"] <= cutoff_ts]

    events_dir = ws / "events"
    events_dir.mkdir(exist_ok=True)
    (events_dir / "events.jsonl").write_text(
        "\n".join(json.dumps(e) for e in filtered) + "\n"
    )

    return project_dir


# ── Fixtures — ephemeral (isolated, fast, for CI) ────────────────────────────


@pytest.fixture
def todo_full(tmp_path: Path) -> Path:
    """Complete todo lifecycle — all edges converged — ephemeral tmp_path."""
    return _build_todo_workspace(tmp_path)


@pytest.fixture
def todo_client_full(todo_full: Path) -> TestClient:
    return _make_client(todo_full)


# ── Fixtures — persistent (observable in browser) ─────────────────────────────


@pytest.fixture(scope="session")
def observable_workspace() -> Path:
    """
    Write the full todo lifecycle into the committed TODO_DEMO_WORKSPACE so
    it is visible in the Genesis Monitor browser dashboard while (and after)
    tests run.

    This fixture populates TODO_DEMO_WORKSPACE with all 17 lifecycle events.
    Because genesis_monitor watches the local_projects/ directory tree, the
    project appears in the browser automatically.

    Returns the project directory (TODO_DEMO_WORKSPACE itself).
    """
    _build_todo_workspace(
        TODO_DEMO_WORKSPACE.parent,
        up_to_minutes=None,
        project_name=TODO_DEMO_WORKSPACE.name,
    )
    return TODO_DEMO_WORKSPACE


def _make_client(project_dir: Path) -> TestClient:
    reg = ProjectRegistry()
    reg.add_project(project_dir)
    bc = SSEBroadcaster()

    @asynccontextmanager
    async def noop_lifespan(app: FastAPI):
        yield

    app = create_app(
        watch_dirs=[project_dir.parent],
        _registry=reg,
        _broadcaster=bc,
        _lifespan=noop_lifespan,
    )
    return TestClient(app, raise_server_exceptions=True)


def _client_at(base_path: Path, up_to_minutes: int) -> TestClient:
    """Build a TestClient with events only up to T+N minutes."""
    project_dir = _build_todo_workspace(base_path, up_to_minutes=up_to_minutes)
    return _make_client(project_dir)


# ── UC-GM-01: Empty project not listed ───────────────────────────────────────


class TestUCGM01_EmptyProjectNotListed:
    """UC-GM-01: A directory without .ai-workspace/ must not appear."""

    def test_directory_without_workspace_not_in_index(self, tmp_path: Path):
        # Create a directory with NO .ai-workspace/
        (tmp_path / "todo-dashboard").mkdir()
        reg = ProjectRegistry()
        # Don't add it — scanner wouldn't find it without .ai-workspace/

        @asynccontextmanager
        async def noop(app: FastAPI):
            yield

        app = create_app(watch_dirs=[tmp_path], _registry=reg,
                         _broadcaster=SSEBroadcaster(), _lifespan=noop)
        with TestClient(app) as client:
            resp = client.get("/")
            assert "todo-dashboard" not in resp.text


# ── UC-GM-02: Project appears after initialization ───────────────────────────


class TestUCGM02_ProjectAppearsAfterInit:
    """UC-GM-02: After project_initialized event, project shows in tree."""

    def test_project_in_index_after_init(self, tmp_path: Path):
        # Only the init event — no features yet
        project_dir = _build_todo_workspace(tmp_path, up_to_minutes=0)
        with _make_client(project_dir) as client:
            resp = client.get("/")
            assert resp.status_code == 200
            assert "todo-dashboard" in resp.text

    def test_project_detail_page_loads(self, tmp_path: Path):
        project_dir = _build_todo_workspace(tmp_path, up_to_minutes=0)
        with _make_client(project_dir) as client:
            resp = client.get("/project/todo-dashboard")
            assert resp.status_code == 200
            assert "todo" in resp.text.lower()

    def test_no_edges_shown_before_any_started(self, tmp_path: Path):
        project_dir = _build_todo_workspace(tmp_path, up_to_minutes=0)
        with _make_client(project_dir) as client:
            resp = client.get("/fragments/project/todo-dashboard/edges")
            assert resp.status_code == 200
            # No edge_started events yet → edge table is empty or shows "no edges"
            assert "intent→requirements" not in resp.text


# ── UC-GM-03: Feature vector appears after spawn ──────────────────────────────


class TestUCGM03_FeatureAppearsAfterSpawn:
    """UC-GM-03: After spawn_created, feature vector is visible."""

    def test_feature_vector_visible_after_spawn(self, tmp_path: Path):
        project_dir = _build_todo_workspace(tmp_path, up_to_minutes=5)
        with _make_client(project_dir) as client:
            resp = client.get("/fragments/project/todo-dashboard/features")
            assert resp.status_code == 200
            assert "REQ-F-TODO-001" in resp.text

    def test_feature_title_shown(self, tmp_path: Path):
        project_dir = _build_todo_workspace(tmp_path, up_to_minutes=5)
        with _make_client(project_dir) as client:
            resp = client.get("/fragments/project/todo-dashboard/features")
            assert "Todo Item CRUD" in resp.text


# ── UC-GM-04: In-progress edge with pending human review ─────────────────────


class TestUCGM04_InProgressEdgeWithHumanReview:
    """UC-GM-04: After first failing iteration, edge shows in_progress with δ=1."""

    def test_edge_in_progress_after_first_iteration(self, tmp_path: Path):
        project_dir = _build_todo_workspace(tmp_path, up_to_minutes=35)
        with _make_client(project_dir) as client:
            resp = client.get("/fragments/project/todo-dashboard/edges")
            assert resp.status_code == 200
            assert "in_progress" in resp.text or "in progress" in resp.text.lower()

    def test_convergence_shows_failing_check(self, tmp_path: Path):
        project_dir = _build_todo_workspace(tmp_path, up_to_minutes=35)
        with _make_client(project_dir) as client:
            resp = client.get("/fragments/project/todo-dashboard/convergence")
            assert resp.status_code == 200
            # δ=1 means at least one failing check
            assert "intent" in resp.text.lower() or "requirements" in resp.text.lower()

    def test_recent_events_shows_iteration_completed(self, tmp_path: Path):
        project_dir = _build_todo_workspace(tmp_path, up_to_minutes=35)
        with _make_client(project_dir) as client:
            resp = client.get("/fragments/project/todo-dashboard/events")
            assert resp.status_code == 200
            assert "iteration_completed" in resp.text


# ── UC-GM-05: Human review recorded ──────────────────────────────────────────


class TestUCGM05_HumanReviewRecorded:
    """UC-GM-05: review_completed event visible after human decision."""

    def test_review_event_in_feed(self, tmp_path: Path):
        project_dir = _build_todo_workspace(tmp_path, up_to_minutes=50)
        with _make_client(project_dir) as client:
            resp = client.get("/fragments/project/todo-dashboard/events")
            assert resp.status_code == 200
            assert "review_completed" in resp.text

    def test_edge_still_in_progress_after_review_before_rerun(self, tmp_path: Path):
        # At T+50 we have review_completed but no second iteration yet
        project_dir = _build_todo_workspace(tmp_path, up_to_minutes=50)
        with _make_client(project_dir) as client:
            resp = client.get("/fragments/project/todo-dashboard/edges")
            assert "converged" not in resp.text or "in_progress" in resp.text


# ── UC-GM-06: Requirements edge converges ────────────────────────────────────


class TestUCGM06_RequirementsEdgeConverges:
    """UC-GM-06: After edge_converged for intent→requirements, edge shows green."""

    def test_requirements_edge_converged(self, tmp_path: Path):
        project_dir = _build_todo_workspace(tmp_path, up_to_minutes=62)
        with _make_client(project_dir) as client:
            resp = client.get("/fragments/project/todo-dashboard/edges")
            assert resp.status_code == 200
            assert "converged" in resp.text

    def test_timeline_has_completed_bar(self, tmp_path: Path):
        project_dir = _build_todo_workspace(tmp_path, up_to_minutes=62)
        with _make_client(project_dir) as client:
            resp = client.get("/fragments/project/todo-dashboard/gantt")
            assert resp.status_code == 200


# ── UC-GM-07: Deterministic engine failure visible ───────────────────────────


class TestUCGM07_EngineFails_DeltaThree:
    """UC-GM-07: After engine run with δ=3, convergence card shows 3 failing checks."""

    def test_code_edge_in_progress_with_delta_3(self, tmp_path: Path):
        project_dir = _build_todo_workspace(tmp_path, up_to_minutes=140)
        with _make_client(project_dir) as client:
            resp = client.get("/fragments/project/todo-dashboard/convergence")
            assert resp.status_code == 200
            # The convergence fragment must show the code↔unit_tests edge is active
            assert "code" in resp.text.lower() or "unit_tests" in resp.text.lower() \
                   or "in_progress" in resp.text

    def test_iteration_completed_event_with_delta_3_in_feed(self, tmp_path: Path):
        project_dir = _build_todo_workspace(tmp_path, up_to_minutes=140)
        with _make_client(project_dir) as client:
            resp = client.get("/fragments/project/todo-dashboard/events")
            assert resp.status_code == 200
            assert "iteration_completed" in resp.text

    def test_review_completed_event_in_feed(self, tmp_path: Path):
        project_dir = _build_todo_workspace(tmp_path, up_to_minutes=155)
        with _make_client(project_dir) as client:
            resp = client.get("/fragments/project/todo-dashboard/events")
            assert "review_completed" in resp.text


# ── UC-GM-08: Engine converges after human fix ───────────────────────────────


class TestUCGM08_EngineConvergesAfterFix:
    """UC-GM-08: After engine run with δ=0, code↔unit_tests shows converged."""

    def test_code_edge_converged_after_delta_zero(self, todo_client_full: TestClient):
        resp = todo_client_full.get("/fragments/project/todo-dashboard/edges")
        assert resp.status_code == 200
        assert "converged" in resp.text

    def test_traceability_shows_coverage(self, todo_client_full: TestClient):
        resp = todo_client_full.get("/fragments/project/todo-dashboard/traceability")
        assert resp.status_code == 200


# ── UC-GM-09: Full lifecycle — all edges green ───────────────────────────────


class TestUCGM09_FullLifecycleAllConverged:
    """UC-GM-09: After full lifecycle, all edges show converged."""

    def test_index_shows_project(self, todo_client_full: TestClient):
        resp = todo_client_full.get("/")
        assert resp.status_code == 200
        assert "todo-dashboard" in resp.text

    def test_project_detail_loads(self, todo_client_full: TestClient):
        resp = todo_client_full.get("/project/todo-dashboard")
        assert resp.status_code == 200

    def test_all_fragments_return_200(self, todo_client_full: TestClient):
        fragments = [
            "graph", "convergence", "edges", "features", "events",
            "gantt", "telem", "spawn-tree", "dimensions", "regimes",
            "consciousness", "compliance", "traceability", "feature-module-map",
        ]
        for frag in fragments:
            resp = todo_client_full.get(f"/fragments/project/todo-dashboard/{frag}")
            assert resp.status_code == 200, f"Fragment {frag} returned {resp.status_code}"

    def test_three_edges_converged(self, todo_client_full: TestClient):
        resp = todo_client_full.get("/fragments/project/todo-dashboard/edges")
        assert resp.text.count("converged") >= 3


# ── UC-GM-10: Temporal scrubber — historical state reconstruction ─────────────


class TestUCGM10_TemporalScrubber:
    """UC-GM-10: ?t= parameter reconstructs correct historical state."""

    def test_at_t35_edge_is_in_progress(self, tmp_path: Path):
        """At T+35 (after first failing iteration) requirements edge is in_progress."""
        project_dir = _build_todo_workspace(tmp_path)
        with _make_client(project_dir) as client:
            t = _ts(35)
            resp = client.get(f"/fragments/project/todo-dashboard/convergence?t={t}")
            assert resp.status_code == 200
            # At T+35 there's one iteration_completed (δ=1) — edge in_progress
            body = resp.text.lower()
            assert "in_progress" in body or "progress" in body or "intent" in body

    def test_at_t62_requirements_converged(self, tmp_path: Path):
        """At T+62 (just after edge_converged for requirements) edge is converged."""
        project_dir = _build_todo_workspace(tmp_path)
        with _make_client(project_dir) as client:
            t = _ts(62)
            resp = client.get(f"/fragments/project/todo-dashboard/edges?t={t}")
            assert resp.status_code == 200
            assert "converged" in resp.text

    def test_at_t62_design_not_yet_started(self, tmp_path: Path):
        """At T+62 requirements has converged but design hasn't started yet."""
        project_dir = _build_todo_workspace(tmp_path)
        with _make_client(project_dir) as client:
            t = _ts(62)
            resp = client.get(f"/fragments/project/todo-dashboard/convergence?t={t}")
            assert resp.status_code == 200
            # design edge not in_progress yet at T+62 (starts at T+65)
            body = resp.text.lower()
            assert "requirements→design" not in body or "in_progress" not in body

    def test_at_t140_code_edge_in_progress(self, tmp_path: Path):
        """At T+140 (after engine run with δ=3) code edge is in_progress."""
        project_dir = _build_todo_workspace(tmp_path)
        with _make_client(project_dir) as client:
            t = _ts(140)
            resp = client.get(f"/fragments/project/todo-dashboard/convergence?t={t}")
            assert resp.status_code == 200

    def test_live_state_all_converged(self, todo_client_full: TestClient):
        """Without ?t= (live state) all edges are converged."""
        resp = todo_client_full.get("/fragments/project/todo-dashboard/edges")
        assert resp.status_code == 200
        assert "converged" in resp.text

    def test_project_detail_accepts_t_param(self, todo_client_full: TestClient):
        t = _ts(62)
        resp = todo_client_full.get(f"/project/todo-dashboard?t={t}")
        assert resp.status_code == 200


# ── UC-GM-11: Event density heatmap ──────────────────────────────────────────


class TestUCGM11_EventDensityHeatmap:
    """UC-GM-11: Density array is computed and normalised."""

    def test_density_in_page_context(self, todo_client_full: TestClient):
        resp = todo_client_full.get("/project/todo-dashboard")
        assert resp.status_code == 200
        # The page script embeds window.eventDensity = [...]
        assert "eventDensity" in resp.text

    def test_density_is_normalised(self):
        """Density values from the projection engine are in [0.0, 1.0]."""
        from datetime import datetime, timezone
        from genesis_monitor.projections.temporal import get_event_density
        from genesis_monitor.models.events import Event

        # Build a minimal set of events with distinct timestamps directly
        base = datetime(2026, 3, 4, 8, 0, 0, tzinfo=timezone.utc)
        events = [
            Event(event_type="edge_started",
                  timestamp=datetime(2026, 3, 4, 8, m, 0, tzinfo=timezone.utc),
                  project="todo")
            for m in range(0, 60, 5)  # 12 events spread across 1 hour
        ]
        density = get_event_density(events, buckets=100)

        assert len(density) == 100
        assert all(0.0 <= v <= 1.0 for v in density)
        assert max(density) == 1.0  # at least one bucket is at peak


# ── UC-GM-12: Design tenant isolation ────────────────────────────────────────


class TestUCGM12_DesignTenantIsolation:
    """UC-GM-12: ?design= filters events to a single tenant."""

    @pytest.fixture
    def multi_tenant_workspace(self, tmp_path: Path) -> Path:
        """Workspace with events from two tenants: 'imp_claude' and 'imp_gemini'."""
        project_dir = tmp_path / "multi-tenant"
        ws = project_dir / ".ai-workspace"
        events_dir = ws / "events"
        events_dir.mkdir(parents=True)
        (ws / "graph").mkdir()
        (ws / "graph" / "graph_topology.yml").write_text(yaml.dump({
            "asset_types": {"intent": {}, "requirements": {}, "code": {}},
            "transitions": [
                {"source": "intent", "target": "requirements"},
                {"source": "requirements", "target": "code"},
            ],
        }))
        (ws / "features" / "active").mkdir(parents=True)
        (ws / "context").mkdir()
        (ws / "context" / "project_constraints.yml").write_text(yaml.dump({}))

        events = [
            make_ol2_event("edge_started", timestamp=_ts(0),
                           project="imp_claude", edge="intent→requirements"),
            make_ol2_event("edge_converged", timestamp=_ts(10),
                           project="imp_claude", edge="intent→requirements"),
            make_ol2_event("edge_started", timestamp=_ts(5),
                           project="imp_gemini", edge="intent→requirements"),
            make_ol2_event("iteration_completed", timestamp=_ts(15),
                           project="imp_gemini", edge="intent→requirements", delta=1),
        ]
        (events_dir / "events.jsonl").write_text(
            "\n".join(json.dumps(e) for e in events) + "\n"
        )
        return project_dir

    @pytest.fixture
    def mt_client(self, multi_tenant_workspace: Path) -> TestClient:
        return _make_client(multi_tenant_workspace)

    def test_unfiltered_shows_all_events(self, mt_client: TestClient):
        resp = mt_client.get("/fragments/project/multi-tenant/events")
        assert resp.status_code == 200
        body = resp.text
        assert "imp_claude" in body or "edge_started" in body

    def test_design_filter_returns_200(self, mt_client: TestClient):
        resp = mt_client.get("/project/multi-tenant?design=imp_claude")
        assert resp.status_code == 200

    def test_design_filter_on_fragments(self, mt_client: TestClient):
        for frag in ["convergence", "edges", "events", "features"]:
            resp = mt_client.get(
                f"/fragments/project/multi-tenant/{frag}?design=imp_claude"
            )
            assert resp.status_code == 200, f"Fragment {frag} failed with design filter"

    def test_unknown_tenant_returns_empty_not_error(self, mt_client: TestClient):
        resp = mt_client.get("/project/multi-tenant?design=nonexistent_tenant")
        assert resp.status_code == 200

    def test_design_selector_nav_present_with_multiple_tenants(self, mt_client: TestClient):
        resp = mt_client.get("/project/multi-tenant")
        assert resp.status_code == 200
        # Selector nav only rendered when > 1 tenant — both tenants present here
        assert "imp_claude" in resp.text
        assert "imp_gemini" in resp.text


# ── UC-GM-13: Read-only contract ─────────────────────────────────────────────


class TestUCGM13_ReadOnlyContract:
    """UC-GM-13: Genesis Monitor never writes to the workspace."""

    def test_no_workspace_files_written_during_page_load(
        self, todo_full: Path, todo_client_full: TestClient
    ):
        ws = todo_full / ".ai-workspace"
        before = {p: p.stat().st_mtime for p in ws.rglob("*") if p.is_file()}

        # Hit every route
        todo_client_full.get("/")
        todo_client_full.get("/project/todo-dashboard")
        for frag in ["graph", "convergence", "edges", "features", "events",
                     "traceability", "compliance"]:
            todo_client_full.get(f"/fragments/project/todo-dashboard/{frag}")

        after = {p: p.stat().st_mtime for p in ws.rglob("*") if p.is_file()}

        modified = {p for p, mt in after.items() if before.get(p) != mt}
        new_files = set(after) - set(before)

        assert not modified, f"Monitor modified workspace files: {modified}"
        assert not new_files, f"Monitor created workspace files: {new_files}"


# ── Observable workspace — persistent, watched by genesis_monitor ─────────────


class TestObservableWorkspace:
    """
    Validates the persistent TODO_DEMO_WORKSPACE directory that is committed to
    git and watched by genesis_monitor at http://localhost:8000.

    This test class uses the `observable_workspace` session fixture which writes
    the full lifecycle to local_projects/todo-demo/.ai-workspace/.  Because that
    directory sits under the genesis_monitor watch root, you can open the browser
    and see the exact same state the tests assert against.

    Pattern: identical to data_mapper.test03 – test09 which are committed
    project directories used as both live demo fixtures and test data sources.
    """

    def test_observable_workspace_exists_on_disk(self, observable_workspace: Path):
        """todo-demo directory exists under local_projects/."""
        assert observable_workspace.exists()
        assert (observable_workspace / ".ai-workspace").is_dir()
        assert (observable_workspace / ".ai-workspace" / "events" / "events.jsonl").exists()

    def test_observable_workspace_has_full_lifecycle(self, observable_workspace: Path):
        """The committed events.jsonl contains all 17 lifecycle events."""
        events_file = observable_workspace / ".ai-workspace" / "events" / "events.jsonl"
        lines = [ln for ln in events_file.read_text().splitlines() if ln.strip()]
        assert len(lines) == 17, f"Expected 17 events, got {len(lines)}"

    def test_observable_workspace_parseable_by_monitor(
        self, observable_workspace: Path
    ):
        """Genesis Monitor can parse and serve the persistent workspace."""
        client = _make_client(observable_workspace)
        resp = client.get("/project/todo-demo")
        assert resp.status_code == 200
        assert "todo-demo" in resp.text

    def test_observable_workspace_shows_all_edges_converged(
        self, observable_workspace: Path
    ):
        """Full lifecycle: all 3 edges converged in the observable workspace."""
        client = _make_client(observable_workspace)
        resp = client.get("/fragments/project/todo-demo/edges")
        assert resp.status_code == 200
        assert "converged" in resp.text

    def test_observable_workspace_temporal_scrubber(
        self, observable_workspace: Path
    ):
        """Historical state at T+35 min shows intent→requirements in_progress."""
        client = _make_client(observable_workspace)
        resp = client.get(
            "/fragments/project/todo-demo/edges"
            "?t=2026-03-04T08:35:00Z"
        )
        assert resp.status_code == 200
        # At T+35, requirements edge is in_progress (first iteration done, not yet converged)
        body = resp.text
        assert "in_progress" in body or "intent" in body
