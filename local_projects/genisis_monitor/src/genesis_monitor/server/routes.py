# Implements: REQ-F-DASH-001, REQ-F-DASH-002, REQ-F-DASH-003, REQ-F-DASH-004, REQ-F-DASH-005, REQ-F-STREAM-002
"""FastAPI route definitions — page routes, fragment routes, SSE endpoint."""

from __future__ import annotations

import time
from typing import TYPE_CHECKING

from fastapi import APIRouter, Request
from fastapi.responses import HTMLResponse
from sse_starlette.sse import EventSourceResponse

from genesis_monitor.projections import (
    build_convergence_table,
    build_gantt_mermaid,
    build_graph_mermaid,
    collect_telem_signals,
)

if TYPE_CHECKING:
    from genesis_monitor.registry import ProjectRegistry
    from genesis_monitor.server.broadcaster import SSEBroadcaster


def create_router(registry: ProjectRegistry, broadcaster: SSEBroadcaster) -> APIRouter:
    """Create the FastAPI router with all routes."""
    router = APIRouter()
    _start_time = time.time()

    # ── Page routes ──────────────────────────────────────────────

    @router.get("/", response_class=HTMLResponse)
    async def index(request: Request):
        """Project index page."""
        projects = registry.list_projects()
        return request.app.state.templates.TemplateResponse(
            "index.html",
            {"request": request, "projects": projects},
        )

    @router.get("/project/{project_id}", response_class=HTMLResponse)
    async def project_detail(request: Request, project_id: str):
        """Project detail dashboard."""
        project = registry.get_project(project_id)
        if not project:
            return HTMLResponse("<h1>Project not found</h1>", status_code=404)

        graph_mermaid = build_graph_mermaid(project.topology, project.status)
        convergence = build_convergence_table(project.status)
        gantt = build_gantt_mermaid(project.status)

        return request.app.state.templates.TemplateResponse(
            "project.html",
            {
                "request": request,
                "project": project,
                "graph_mermaid": graph_mermaid,
                "convergence": convergence,
                "gantt": gantt,
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
    async def fragment_graph(request: Request, project_id: str):
        project = registry.get_project(project_id)
        if not project:
            return HTMLResponse("")
        mermaid = build_graph_mermaid(project.topology, project.status)
        return request.app.state.templates.TemplateResponse(
            "fragments/_graph.html",
            {"request": request, "mermaid_code": mermaid},
        )

    @router.get("/fragments/project/{project_id}/edges", response_class=HTMLResponse)
    async def fragment_edges(request: Request, project_id: str):
        project = registry.get_project(project_id)
        if not project:
            return HTMLResponse("")
        convergence = build_convergence_table(project.status)
        return request.app.state.templates.TemplateResponse(
            "fragments/_edges.html",
            {"request": request, "convergence": convergence},
        )

    @router.get("/fragments/project/{project_id}/features", response_class=HTMLResponse)
    async def fragment_features(request: Request, project_id: str):
        project = registry.get_project(project_id)
        if not project:
            return HTMLResponse("")
        return request.app.state.templates.TemplateResponse(
            "fragments/_features.html",
            {"request": request, "features": project.features},
        )

    @router.get("/fragments/project/{project_id}/events", response_class=HTMLResponse)
    async def fragment_events(request: Request, project_id: str):
        project = registry.get_project(project_id)
        if not project:
            return HTMLResponse("")
        return request.app.state.templates.TemplateResponse(
            "fragments/_events.html",
            {"request": request, "events": project.events[-50:]},
        )

    @router.get("/fragments/project/{project_id}/convergence", response_class=HTMLResponse)
    async def fragment_convergence(request: Request, project_id: str):
        project = registry.get_project(project_id)
        if not project:
            return HTMLResponse("")
        convergence = build_convergence_table(project.status)
        return request.app.state.templates.TemplateResponse(
            "fragments/_convergence.html",
            {"request": request, "convergence": convergence},
        )

    @router.get("/fragments/project/{project_id}/gantt", response_class=HTMLResponse)
    async def fragment_gantt(request: Request, project_id: str):
        project = registry.get_project(project_id)
        if not project:
            return HTMLResponse("")
        gantt = build_gantt_mermaid(project.status)
        return request.app.state.templates.TemplateResponse(
            "fragments/_gantt.html",
            {"request": request, "gantt": gantt},
        )

    @router.get("/fragments/project/{project_id}/telem", response_class=HTMLResponse)
    async def fragment_telem(request: Request, project_id: str):
        project = registry.get_project(project_id)
        if not project:
            return HTMLResponse("")
        signals = project.status.telem_signals if project.status else []
        return request.app.state.templates.TemplateResponse(
            "fragments/_telem.html",
            {"request": request, "signals": signals},
        )

    # ── SSE endpoint ─────────────────────────────────────────────

    @router.get("/events/stream")
    async def sse_stream(request: Request):
        """SSE endpoint for real-time updates."""
        return EventSourceResponse(broadcaster.subscribe())

    # ── Aggregated TELEM page ────────────────────────────────────

    @router.get("/telem", response_class=HTMLResponse)
    async def telem_aggregate(request: Request):
        projects = registry.list_projects()
        signals = collect_telem_signals(projects)
        return request.app.state.templates.TemplateResponse(
            "fragments/_telem.html",
            {"request": request, "signals": signals, "aggregate": True},
        )

    # ── Health check ─────────────────────────────────────────────

    @router.get("/api/health")
    async def health():
        uptime = int(time.time() - _start_time)
        return {
            "status": "ok",
            "projects": len(registry.list_projects()),
            "uptime_seconds": uptime,
        }

    return router
