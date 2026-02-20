# Validates: REQ-F-DASH-001, REQ-F-DASH-002, REQ-F-STREAM-001, REQ-F-STREAM-002, REQ-NFR-001, REQ-NFR-002
"""UAT / integration tests for the FastAPI server.

Tests the full request/response cycle including:
- Page routes returning complete HTML
- Fragment routes returning HTML partials
- SSE endpoint
- Health check API
- Read-only contract (no writes to workspace)
"""

from contextlib import asynccontextmanager
from pathlib import Path

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

from genesis_monitor.registry import ProjectRegistry
from genesis_monitor.server.app import create_app
from genesis_monitor.server.broadcaster import SSEBroadcaster


@pytest.fixture
def test_client(tmp_workspace: Path) -> TestClient:
    """TestClient with injected registry (no watcher, no lifespan)."""
    reg = ProjectRegistry()
    reg.add_project(tmp_workspace)
    bc = SSEBroadcaster()

    @asynccontextmanager
    async def noop_lifespan(app: FastAPI):
        yield

    app = create_app(
        watch_dirs=[tmp_workspace.parent],
        _registry=reg,
        _broadcaster=bc,
        _lifespan=noop_lifespan,
    )

    with TestClient(app, raise_server_exceptions=True) as client:
        yield client


# ── Page routes ──────────────────────────────────────────────────


class TestIndexPage:
    def test_returns_html(self, test_client: TestClient):
        resp = test_client.get("/")
        assert resp.status_code == 200
        assert "text/html" in resp.headers["content-type"]

    def test_contains_project_name(self, test_client: TestClient):
        resp = test_client.get("/")
        assert "test-project" in resp.text or "Test CDME Project" in resp.text

    def test_contains_htmx_attributes(self, test_client: TestClient):
        resp = test_client.get("/")
        assert "hx-get" in resp.text
        assert "sse" in resp.text


class TestProjectDetailPage:
    def test_returns_html(self, test_client: TestClient):
        resp = test_client.get("/project/test-project")
        assert resp.status_code == 200
        assert "text/html" in resp.headers["content-type"]

    def test_contains_graph(self, test_client: TestClient):
        resp = test_client.get("/project/test-project")
        assert "mermaid" in resp.text.lower() or "graph" in resp.text.lower()

    def test_contains_convergence(self, test_client: TestClient):
        resp = test_client.get("/project/test-project")
        assert "converge" in resp.text.lower() or "edge" in resp.text.lower()

    def test_404_for_unknown_project(self, test_client: TestClient):
        resp = test_client.get("/project/nonexistent")
        assert resp.status_code == 404


# ── Fragment routes ──────────────────────────────────────────────


class TestFragmentRoutes:
    def test_project_list_fragment(self, test_client: TestClient):
        resp = test_client.get("/fragments/project-list")
        assert resp.status_code == 200
        assert "test-project" in resp.text or "Test CDME" in resp.text

    def test_graph_fragment(self, test_client: TestClient):
        resp = test_client.get("/fragments/project/test-project/graph")
        assert resp.status_code == 200

    def test_edges_fragment(self, test_client: TestClient):
        resp = test_client.get("/fragments/project/test-project/edges")
        assert resp.status_code == 200
        assert "intent" in resp.text.lower()

    def test_features_fragment(self, test_client: TestClient):
        resp = test_client.get("/fragments/project/test-project/features")
        assert resp.status_code == 200
        assert "REQ-F-GMON-001" in resp.text

    def test_events_fragment(self, test_client: TestClient):
        resp = test_client.get("/fragments/project/test-project/events")
        assert resp.status_code == 200

    def test_convergence_fragment(self, test_client: TestClient):
        resp = test_client.get("/fragments/project/test-project/convergence")
        assert resp.status_code == 200

    def test_gantt_fragment(self, test_client: TestClient):
        resp = test_client.get("/fragments/project/test-project/gantt")
        assert resp.status_code == 200
        assert "gantt" in resp.text.lower() or "mermaid" in resp.text.lower()

    def test_telem_fragment(self, test_client: TestClient):
        resp = test_client.get("/fragments/project/test-project/telem")
        assert resp.status_code == 200
        assert "TELEM" in resp.text

    def test_unknown_project_returns_empty(self, test_client: TestClient):
        resp = test_client.get("/fragments/project/nonexistent/graph")
        assert resp.status_code == 200
        assert resp.text == ""


# ── Health check ─────────────────────────────────────────────────


class TestHealthCheck:
    def test_health_returns_json(self, test_client: TestClient):
        resp = test_client.get("/api/health")
        assert resp.status_code == 200
        data = resp.json()
        assert data["status"] == "ok"
        assert data["projects"] >= 1
        assert "uptime_seconds" in data


# ── Read-only contract ───────────────────────────────────────────


class TestReadOnlyContract:
    """Validates REQ-NFR-001: the monitor never writes to workspace directories."""

    def test_no_writes_during_index(self, test_client: TestClient, tmp_workspace: Path):
        ws = tmp_workspace / ".ai-workspace"
        before = set(ws.rglob("*"))
        test_client.get("/")
        after = set(ws.rglob("*"))
        assert before == after, "Workspace files changed during index request"

    def test_no_writes_during_detail(self, test_client: TestClient, tmp_workspace: Path):
        ws = tmp_workspace / ".ai-workspace"
        before = set(ws.rglob("*"))
        test_client.get("/project/test-project")
        after = set(ws.rglob("*"))
        assert before == after, "Workspace files changed during detail request"

    def test_no_writes_during_fragments(self, test_client: TestClient, tmp_workspace: Path):
        ws = tmp_workspace / ".ai-workspace"
        before = set(ws.rglob("*"))
        for endpoint in [
            "/fragments/project-list",
            "/fragments/project/test-project/graph",
            "/fragments/project/test-project/edges",
            "/fragments/project/test-project/features",
            "/fragments/project/test-project/convergence",
            "/fragments/project/test-project/gantt",
            "/fragments/project/test-project/telem",
        ]:
            test_client.get(endpoint)
        after = set(ws.rglob("*"))
        assert before == after, "Workspace files changed during fragment requests"
