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
from genesis_monitor.projections.temporal import reconstruct_features

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

        if t:
            try:
                limit = datetime.fromisoformat(t.replace("Z", "+00:00"))
                events = [e for e in project.events if e.timestamp <= limit]
                features = reconstruct_features(events, limit)
                status = project.status  # Fallback for now
            except ValueError:
                events = project.events
                features = project.features
                status = project.status
        else:
            events = project.events
            features = project.features
            status = project.status

        graph_mermaid = build_graph_mermaid(project.topology, status)
        convergence = build_convergence_table(status, events)
        gantt = build_gantt_mermaid(status, features)

        signals = project.status.telem_signals if project.status else []
        events = events[-50:]

        # v2.5 projections
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
                "events": events[-50:],
                "spawn_tree": spawn_tree,
                "dimensions": dimensions,
                "regimes": regimes,
                "consciousness": consciousness,
                "compliance": compliance,
                "traceability": traceability,
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

    @router.get("/fragments/tree", response_class=HTMLResponse)
    async def fragment_tree(request: Request):
        """Tree navigator fragment (REQ-F-DASH-006)."""
        projects = registry.list_projects()
        tree = build_project_tree(projects)
        return request.app.state.templates.TemplateResponse(
            "fragments/_tree.html",
            {"request": request, "tree": tree},
        )

    @router.get("/fragments/project/{project_id}/graph", response_class=HTMLResponse)
    async def fragment_graph(request: Request, project_id: str, t: str = None):
        project = registry.get_project(project_id)
        if not project:
            return HTMLResponse("")
        if t:
            try:
                limit = datetime.fromisoformat(t.replace("Z", "+00:00"))
                events = [e for e in project.events if e.timestamp <= limit]
                features = reconstruct_features(events, limit)
                status = project.status  # Fallback for now
            except ValueError:
                events = project.events
                features = project.features
                status = project.status
        else:
            events = project.events
            features = project.features
            status = project.status
        mermaid = build_graph_mermaid(project.topology, status)
        return request.app.state.templates.TemplateResponse(
            "fragments/_graph.html",
            {"request": request, "mermaid_code": mermaid},
        )

    @router.get("/fragments/project/{project_id}/edges", response_class=HTMLResponse)
    async def fragment_edges(request: Request, project_id: str, t: str = None):
        project = registry.get_project(project_id)
        if not project:
            return HTMLResponse("")
        if t:
            try:
                limit = datetime.fromisoformat(t.replace("Z", "+00:00"))
                events = [e for e in project.events if e.timestamp <= limit]
                features = reconstruct_features(events, limit)
                status = project.status  # Fallback for now
            except ValueError:
                events = project.events
                features = project.features
                status = project.status
        else:
            events = project.events
            features = project.features
            status = project.status
        convergence = build_convergence_table(status, events)
        return request.app.state.templates.TemplateResponse(
            "fragments/_edges.html",
            {"request": request, "convergence": convergence},
        )

    @router.get("/fragments/project/{project_id}/features", response_class=HTMLResponse)
    async def fragment_features(request: Request, project_id: str, t: str = None):
        project = registry.get_project(project_id)
        if not project:
            return HTMLResponse("")
        if t:
            try:
                limit = datetime.fromisoformat(t.replace("Z", "+00:00"))
                events = [e for e in project.events if e.timestamp <= limit]
                features = reconstruct_features(events, limit)
                status = project.status  # Fallback for now
            except ValueError:
                events = project.events
                features = project.features
                status = project.status
        else:
            events = project.events
            features = project.features
            status = project.status
        return request.app.state.templates.TemplateResponse(
            "fragments/_features.html",
            {"request": request, "features": features},
        )

    @router.get("/fragments/project/{project_id}/events", response_class=HTMLResponse)
    async def fragment_events(request: Request, project_id: str, t: str = None):
        project = registry.get_project(project_id)
        if not project:
            return HTMLResponse("")
        if t:
            try:
                limit = datetime.fromisoformat(t.replace("Z", "+00:00"))
                events = [e for e in project.events if e.timestamp <= limit]
                features = reconstruct_features(events, limit)
                status = project.status  # Fallback for now
            except ValueError:
                events = project.events
                features = project.features
                status = project.status
        else:
            events = project.events
            features = project.features
            status = project.status
        return request.app.state.templates.TemplateResponse(
            "fragments/_events.html",
            {"request": request, "events": events[-50:]},
        )

    @router.get("/fragments/project/{project_id}/convergence", response_class=HTMLResponse)
    async def fragment_convergence(request: Request, project_id: str, t: str = None):
        project = registry.get_project(project_id)
        if not project:
            return HTMLResponse("")
        if t:
            try:
                limit = datetime.fromisoformat(t.replace("Z", "+00:00"))
                events = [e for e in project.events if e.timestamp <= limit]
                features = reconstruct_features(events, limit)
                status = project.status  # Fallback for now
            except ValueError:
                events = project.events
                features = project.features
                status = project.status
        else:
            events = project.events
            features = project.features
            status = project.status
        convergence = build_convergence_table(status, events)
        return request.app.state.templates.TemplateResponse(
            "fragments/_convergence.html",
            {"request": request, "convergence": convergence},
        )

    @router.get("/fragments/project/{project_id}/gantt", response_class=HTMLResponse)
    async def fragment_gantt(request: Request, project_id: str, t: str = None):
        project = registry.get_project(project_id)
        if not project:
            return HTMLResponse("")
        if t:
            try:
                limit = datetime.fromisoformat(t.replace("Z", "+00:00"))
                events = [e for e in project.events if e.timestamp <= limit]
                features = reconstruct_features(events, limit)
                status = project.status  # Fallback for now
            except ValueError:
                events = project.events
                features = project.features
                status = project.status
        else:
            events = project.events
            features = project.features
            status = project.status
        gantt = build_gantt_mermaid(status, features)
        return request.app.state.templates.TemplateResponse(
            "fragments/_gantt.html",
            {"request": request, "gantt": gantt},
        )

    @router.get("/fragments/project/{project_id}/telem", response_class=HTMLResponse)
    async def fragment_telem(request: Request, project_id: str, t: str = None):
        project = registry.get_project(project_id)
        if not project:
            return HTMLResponse("")
        if t:
            try:
                limit = datetime.fromisoformat(t.replace("Z", "+00:00"))
                events = [e for e in project.events if e.timestamp <= limit]
                features = reconstruct_features(events, limit)
                status = project.status  # Fallback for now
            except ValueError:
                events = project.events
                features = project.features
                status = project.status
        else:
            events = project.events
            features = project.features
            status = project.status
        signals = project.status.telem_signals if project.status else []
        return request.app.state.templates.TemplateResponse(
            "fragments/_telem.html",
            {"request": request, "signals": signals},
        )

    # ── v2.5 Fragment routes ────────────────────────────────────

    @router.get("/fragments/project/{project_id}/spawn-tree", response_class=HTMLResponse)
    async def fragment_spawn_tree(request: Request, project_id: str, t: str = None):
        project = registry.get_project(project_id)
        if not project:
            return HTMLResponse("")
        if t:
            try:
                limit = datetime.fromisoformat(t.replace("Z", "+00:00"))
                events = [e for e in project.events if e.timestamp <= limit]
                features = reconstruct_features(events, limit)
                status = project.status  # Fallback for now
            except ValueError:
                events = project.events
                features = project.features
                status = project.status
        else:
            events = project.events
            features = project.features
            status = project.status
        spawn_tree = build_spawn_tree(features)
        return request.app.state.templates.TemplateResponse(
            "fragments/_spawn_tree.html",
            {"request": request, "spawn_tree": spawn_tree},
        )

    @router.get("/fragments/project/{project_id}/dimensions", response_class=HTMLResponse)
    async def fragment_dimensions(request: Request, project_id: str, t: str = None):
        project = registry.get_project(project_id)
        if not project:
            return HTMLResponse("")
        if t:
            try:
                limit = datetime.fromisoformat(t.replace("Z", "+00:00"))
                events = [e for e in project.events if e.timestamp <= limit]
                features = reconstruct_features(events, limit)
                status = project.status  # Fallback for now
            except ValueError:
                events = project.events
                features = project.features
                status = project.status
        else:
            events = project.events
            features = project.features
            status = project.status
        dimensions = build_dimension_coverage(project.topology, features, project.constraints)
        return request.app.state.templates.TemplateResponse(
            "fragments/_dimensions.html",
            {"request": request, "dimensions": dimensions},
        )

    @router.get("/fragments/project/{project_id}/regimes", response_class=HTMLResponse)
    async def fragment_regimes(request: Request, project_id: str, t: str = None):
        project = registry.get_project(project_id)
        if not project:
            return HTMLResponse("")
        if t:
            try:
                limit = datetime.fromisoformat(t.replace("Z", "+00:00"))
                events = [e for e in project.events if e.timestamp <= limit]
                features = reconstruct_features(events, limit)
                status = project.status  # Fallback for now
            except ValueError:
                events = project.events
                features = project.features
                status = project.status
        else:
            events = project.events
            features = project.features
            status = project.status
        regimes = build_regime_summary(events)
        return request.app.state.templates.TemplateResponse(
            "fragments/_regimes.html",
            {"request": request, "regimes": regimes},
        )

    @router.get("/fragments/project/{project_id}/consciousness", response_class=HTMLResponse)
    async def fragment_consciousness(request: Request, project_id: str, t: str = None):
        project = registry.get_project(project_id)
        if not project:
            return HTMLResponse("")
        if t:
            try:
                limit = datetime.fromisoformat(t.replace("Z", "+00:00"))
                events = [e for e in project.events if e.timestamp <= limit]
                features = reconstruct_features(events, limit)
                status = project.status  # Fallback for now
            except ValueError:
                events = project.events
                features = project.features
                status = project.status
        else:
            events = project.events
            features = project.features
            status = project.status
        consciousness = build_consciousness_timeline(events)
        return request.app.state.templates.TemplateResponse(
            "fragments/_consciousness.html",
            {"request": request, "consciousness": consciousness},
        )

    @router.get("/fragments/project/{project_id}/compliance", response_class=HTMLResponse)
    async def fragment_compliance(request: Request, project_id: str, t: str = None):
        project = registry.get_project(project_id)
        if not project:
            return HTMLResponse("")
        if t:
            try:
                limit = datetime.fromisoformat(t.replace("Z", "+00:00"))
                events = [e for e in project.events if e.timestamp <= limit]
                features = reconstruct_features(events, limit)
                status = project.status  # Fallback for now
            except ValueError:
                events = project.events
                features = project.features
                status = project.status
        else:
            events = project.events
            features = project.features
            status = project.status
        compliance = build_compliance_report(project)
        return request.app.state.templates.TemplateResponse(
            "fragments/_compliance.html",
            {"request": request, "compliance": compliance},
        )

    @router.get("/fragments/project/{project_id}/traceability", response_class=HTMLResponse)
    async def fragment_traceability(request: Request, project_id: str, t: str = None):
        project = registry.get_project(project_id)
        if not project:
            return HTMLResponse("")
        if t:
            try:
                limit = datetime.fromisoformat(t.replace("Z", "+00:00"))
                events = [e for e in project.events if e.timestamp <= limit]
                features = reconstruct_features(events, limit)
                status = project.status  # Fallback for now
            except ValueError:
                events = project.events
                features = project.features
                status = project.status
        else:
            events = project.events
            features = project.features
            status = project.status
        traceability = build_traceability_view(features, project.traceability)
        return request.app.state.templates.TemplateResponse(
            "fragments/_traceability.html",
            {"request": request, "traceability": traceability},
        )

    @router.get("/lineage/source/feature/{feature_id}", response_class=HTMLResponse)
    async def get_feature_source(request: Request, feature_id: str):
        import yaml

        # Find the project containing this feature
        projects = registry.list_projects()
        target_proj = None
        target_feat = None

        for p in projects:
            for f in p.features:
                if f.feature_id == feature_id:
                    target_proj = p
                    target_feat = f
                    break
            if target_proj:
                break

        if not target_proj:
            return HTMLResponse("Feature not found.", status_code=404)

        feat_path = target_proj.path / "features" / "active" / f"{feature_id}.yml"
        if not feat_path.exists():
            feat_path = target_proj.path / "features" / "completed" / f"{feature_id}.yml"

        if not feat_path.exists():
            return HTMLResponse(f"YAML source not found for {feature_id}", status_code=404)

        raw_content = feat_path.read_text()

        html = f"""
        <div class="raw-data-block">
            <pre><code>{raw_content}</code></pre>
        </div>
        """
        return HTMLResponse(html)

    # ── SSE endpoint ─────────────────────────────────────────────

    @router.get("/events/stream")
    async def sse_stream(request: Request):
        """SSE endpoint for real-time updates."""
        return EventSourceResponse(
            broadcaster.subscribe(),
            ping=5,  # Ping every 5s — detects dead connections fast on navigation
        )

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
