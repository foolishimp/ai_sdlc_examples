# Implements: REQ-F-DASH-001, REQ-F-DASH-002, REQ-F-DASH-003, REQ-F-DASH-004, REQ-F-DASH-005, REQ-F-DASH-006, REQ-F-STREAM-002
# Implements: REQ-F-VREL-003, REQ-F-CDIM-002, REQ-F-REGIME-002, REQ-F-CONSC-003, REQ-F-PROTO-001
"""FastAPI route definitions — page routes, fragment routes, SSE endpoint."""

from __future__ import annotations

import time
from datetime import datetime
from typing import TYPE_CHECKING

from fastapi import APIRouter, Request
from fastapi.responses import HTMLResponse
from sse_starlette.sse import EventSourceResponse

from genesis_monitor.projections import (
    build_compliance_report,
    build_consciousness_timeline,
    build_convergence_table,
    build_dimension_coverage,
    build_gantt_mermaid,
    build_graph_mermaid,
    build_project_tree,
    build_regime_summary,
    build_spawn_tree,
    collect_telem_signals,
)
from genesis_monitor.projections.traceability import build_traceability_view
from genesis_monitor.projections.temporal import (
    reconstruct_features,
    reconstruct_status,
    get_event_density,
)

if TYPE_CHECKING:
    from genesis_monitor.registry import ProjectRegistry
    from genesis_monitor.server.broadcaster import SSEBroadcaster


def create_router(registry: ProjectRegistry, broadcaster: SSEBroadcaster) -> APIRouter:
    """Create the FastAPI router with all routes."""
    router = APIRouter()
    _start_time = time.time()

    def _get_historical_state(project, t: str | None):
        if t:
            try:
                limit = datetime.fromisoformat(t.replace("Z", "+00:00"))
                events = [e for e in project.events if e.timestamp <= limit]
                features = reconstruct_features(events, limit)
                status = reconstruct_status(events, limit)
                return events, features, status
            except Exception:
                pass
        return project.events, project.features, project.status

    # ── Page routes ──────────────────────────────────────────────

    @router.get("/", response_class=HTMLResponse)
    async def index(request: Request):
        """Project index page."""
        projects = registry.list_projects()
        tree = build_project_tree(projects)
        return request.app.state.templates.TemplateResponse(
            "index.html",
            {"request": request, "projects": projects, "tree": tree},
        )

    @router.get("/project/{project_id}", response_class=HTMLResponse)
    async def project_detail(request: Request, project_id: str, t: str = None):
        """Project detail dashboard."""
        project = registry.get_project(project_id)
        if not project:
            return HTMLResponse("<h1>Project not found</h1>", status_code=404)

        events, features, status = _get_historical_state(project, t)
        density = get_event_density(project.events) if project.events else []

        graph_mermaid = build_graph_mermaid(project.topology, status)
        convergence = build_convergence_table(status, events)
        gantt = build_gantt_mermaid(status, features)

        signals = status.telem_signals if status else []

        spawn_tree = build_spawn_tree(features)
        dimensions = build_dimension_coverage(project.topology, features, project.constraints)
        regimes = build_regime_summary(events)
        consciousness = build_consciousness_timeline(events)
        compliance = build_compliance_report(project)
        traceability = build_traceability_view(features, project.traceability)

        return request.app.state.templates.TemplateResponse(
            "project.html",
            {
                "request": request,
                "project": project,
                "graph_mermaid": graph_mermaid,
                "convergence": convergence,
                "gantt": gantt,
                "features": features,
                "signals": signals,
                "events": events,
                "spawn_tree": spawn_tree,
                "dimensions": dimensions,
                "regimes": regimes,
                "consciousness": consciousness,
                "compliance": compliance,
                "traceability": traceability,
                "density": density,
                "current_time": datetime.now().strftime("%H:%M:%S"),
            },
        )

    # ── Fragment routes (HTMX partials) ──────────────────────────

    @router.get("/fragments/project-list", response_class=HTMLResponse)
    async def fragment_project_list(request: Request):
        projects = registry.list_projects()
        return request.app.state.templates.TemplateResponse(
            "fragments/_project_list.html",
            {"request": request, "projects": projects},
        )

    @router.get("/fragments/project/{project_id}/graph", response_class=HTMLResponse)
    async def fragment_graph(request: Request, project_id: str, t: str = None):
        project = registry.get_project(project_id)
        if not project:
            return HTMLResponse("")
        _, _, status = _get_historical_state(project, t)
        mermaid = build_graph_mermaid(project.topology, status)
        return request.app.state.templates.TemplateResponse(
            "fragments/_graph.html",
            {
                "request": request,
                "mermaid_code": mermaid,
                "current_time": datetime.now().strftime("%H:%M:%S"),
            },
        )

    @router.get("/fragments/project/{project_id}/convergence", response_class=HTMLResponse)
    async def fragment_convergence(request: Request, project_id: str, t: str = None):
        project = registry.get_project(project_id)
        if not project:
            return HTMLResponse("")
        events, _, status = _get_historical_state(project, t)
        convergence = build_convergence_table(status, events)
        return request.app.state.templates.TemplateResponse(
            "fragments/_convergence.html",
            {
                "request": request,
                "convergence": convergence,
                "current_time": datetime.now().strftime("%H:%M:%S"),
            },
        )

    @router.get("/fragments/project/{project_id}/features", response_class=HTMLResponse)
    async def fragment_features(request: Request, project_id: str, t: str = None):
        project = registry.get_project(project_id)
        if not project:
            return HTMLResponse("")
        _, features, _ = _get_historical_state(project, t)
        return request.app.state.templates.TemplateResponse(
            "fragments/_features.html",
            {
                "request": request,
                "features": features,
                "current_time": datetime.now().strftime("%H:%M:%S"),
            },
        )

    @router.get("/fragments/project/{project_id}/gantt", response_class=HTMLResponse)
    async def fragment_gantt(request: Request, project_id: str, t: str = None):
        project = registry.get_project(project_id)
        if not project:
            return HTMLResponse("")
        _, features, status = _get_historical_state(project, t)
        gantt = build_gantt_mermaid(status, features)
        return request.app.state.templates.TemplateResponse(
            "fragments/_gantt.html",
            {
                "request": request,
                "gantt": gantt,
                "current_time": datetime.now().strftime("%H:%M:%S"),
            },
        )

    @router.get("/fragments/project/{project_id}/events", response_class=HTMLResponse)
    async def fragment_events(request: Request, project_id: str, t: str = None):
        project = registry.get_project(project_id)
        if not project:
            return HTMLResponse("")
        events, _, _ = _get_historical_state(project, t)
        return request.app.state.templates.TemplateResponse(
            "fragments/_events.html",
            {
                "request": request,
                "events": events[-50:],
                "current_time": datetime.now().strftime("%H:%M:%S"),
            },
        )

    @router.get("/fragments/project/{project_id}/telem", response_class=HTMLResponse)
    async def fragment_telem(request: Request, project_id: str, t: str = None):
        project = registry.get_project(project_id)
        if not project:
            return HTMLResponse("")
        _, _, status = _get_historical_state(project, t)
        signals = status.telem_signals if status else []
        return request.app.state.templates.TemplateResponse(
            "fragments/_telem.html",
            {
                "request": request,
                "signals": signals,
                "current_time": datetime.now().strftime("%H:%M:%S"),
            },
        )

    @router.get("/lineage/source/feature/{feature_id}", response_class=HTMLResponse)
    async def get_feature_source(request: Request, feature_id: str):
        projects = registry.list_projects()
        target_proj = None
        for p in projects:
            for f in p.features:
                if f.feature_id == feature_id:
                    target_proj = p
                    break
            if target_proj:
                break
        if not target_proj:
            return HTMLResponse("Feature not found.", status_code=404)
        feat_path = target_proj.path / "features" / "active" / f"{feature_id}.yml"
        if not feat_path.exists():
            feat_path = target_proj.path / "features" / "completed" / f"{feature_id}.yml"
        if not feat_path.exists():
            return HTMLResponse("Source not found.", status_code=404)
        return HTMLResponse(
            f'<div class="raw-data-block"><pre><code>{feat_path.read_text()}</code></pre></div>'
        )

    @router.get("/events/stream")
    async def sse_stream(request: Request):
        return EventSourceResponse(broadcaster.subscribe(), ping=5)

    @router.get("/api/health")
    async def health():
        uptime = int(time.time() - _start_time)
        return {
            "status": "ok",
            "projects": len(registry.list_projects()),
            "uptime_seconds": uptime,
        }

    return router
