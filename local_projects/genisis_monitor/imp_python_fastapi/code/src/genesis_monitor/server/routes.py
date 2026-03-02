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
    build_feature_matrix,
    build_graph_mermaid,
    build_project_tree,
    build_regime_summary,
    build_spawn_tree,
    collect_telem_signals,
)
from genesis_monitor.projections.feature_module_map import build_feature_module_map
from genesis_monitor.projections.traceability import build_traceability_view
from genesis_monitor.projections.temporal import reconstruct_features, reconstruct_status, get_event_density

if TYPE_CHECKING:
    from genesis_monitor.registry import ProjectRegistry
    from genesis_monitor.server.broadcaster import SSEBroadcaster


def create_router(registry: ProjectRegistry, broadcaster: SSEBroadcaster) -> APIRouter:
    """Create the FastAPI router with all routes."""
    router = APIRouter()
    _start_time = time.time()

    def _get_historical_state(project, t: str | None, design: str | None = None):
        events = project.events
        if design:
            events = [e for e in events if e.project == design]
        if t:
            try:
                limit = datetime.fromisoformat(t.replace('Z', '+00:00'))
                events = [e for e in events if e.timestamp <= limit]
                features = reconstruct_features(events, limit)
                status = reconstruct_status(events, limit)
                return events, features, status
            except Exception:
                pass
        if design:
            features = reconstruct_features(events)
            status = reconstruct_status(events)
            return events, features, status
        return events, project.features, project.status

    @router.get("/", response_class=HTMLResponse)
    async def index(request: Request):
        projects = registry.list_projects()
        tree = build_project_tree(projects)
        return request.app.state.templates.TemplateResponse(
            request,
            "index.html",
            {"projects": projects, "tree": tree},
        )

    @router.get("/project/{project_id}", response_class=HTMLResponse)
    async def project_detail(request: Request, project_id: str, t: str = None, design: str = None):
        project = registry.get_project(project_id)
        if not project:
            return HTMLResponse("<h1>Project not found</h1>", status_code=404)
        events, features, status = _get_historical_state(project, t, design)
        available_designs = sorted(list(set(e.project for e in project.events if e.project)))
        graph_mermaid = build_graph_mermaid(project.topology, status)
        convergence = build_convergence_table(status, events)
        matrix = build_feature_matrix(features)
        feature_module_map = build_feature_module_map(features, project.traceability)
        signals = status.telem_signals if status else []
        spawn_tree = build_spawn_tree(features)
        dimensions = build_dimension_coverage(project.topology, features, project.constraints)
        regimes = build_regime_summary(events)
        consciousness = build_consciousness_timeline(events)
        compliance = build_compliance_report(project)
        traceability = build_traceability_view(features, project.traceability)
        return request.app.state.templates.TemplateResponse(
            request,
            "project.html",
            {
                "project": project,
                "design": design,
                "available_designs": available_designs,
                "graph_mermaid": graph_mermaid,
                "convergence": convergence,
                "matrix": matrix,
                "feature_module_map": feature_module_map,
                "features": features,
                "signals": signals,
                "events": events,
                "all_events": events,
                "recent_events": events[-50:],
                "density": get_event_density(events),
                "spawn_tree": spawn_tree,
                "dimensions": dimensions,
                "regimes": regimes,
                "consciousness": consciousness,
                "compliance": compliance,
                "traceability": traceability,
                "current_time": datetime.now().strftime("%H:%M:%S"),
            },
        )

    @router.get("/fragments/project/{project_id}/graph", response_class=HTMLResponse)
    async def fragment_graph(request: Request, project_id: str, t: str = None, design: str = None):
        project = registry.get_project(project_id)
        if not project: return HTMLResponse("")
        _, _, status = _get_historical_state(project, t, design)
        mermaid = build_graph_mermaid(project.topology, status)
        return request.app.state.templates.TemplateResponse(
            request,
            "fragments/_graph.html",
            {"mermaid_code": mermaid, "current_time": datetime.now().strftime("%H:%M:%S")},
        )

    @router.get("/fragments/project/{project_id}/convergence", response_class=HTMLResponse)
    async def fragment_convergence(request: Request, project_id: str, t: str = None, design: str = None):
        project = registry.get_project(project_id)
        if not project: return HTMLResponse("")
        events, _, status = _get_historical_state(project, t, design)
        convergence = build_convergence_table(status, events)
        return request.app.state.templates.TemplateResponse(
            request,
            "fragments/_convergence.html",
            {"convergence": convergence, "current_time": datetime.now().strftime("%H:%M:%S")},
        )

    @router.get("/fragments/project/{project_id}/features", response_class=HTMLResponse)
    async def fragment_features(request: Request, project_id: str, t: str = None, design: str = None):
        project = registry.get_project(project_id)
        if not project: return HTMLResponse("")
        _, features, _ = _get_historical_state(project, t, design)
        return request.app.state.templates.TemplateResponse(
            request,
            "fragments/_features.html",
            {"features": features, "current_time": datetime.now().strftime("%H:%M:%S")},
        )

    @router.get("/fragments/project/{project_id}/gantt", response_class=HTMLResponse)
    async def fragment_gantt(request: Request, project_id: str, t: str = None, design: str = None):
        project = registry.get_project(project_id)
        if not project: return HTMLResponse("")
        _, features, _ = _get_historical_state(project, t, design)
        matrix = build_feature_matrix(features)
        return request.app.state.templates.TemplateResponse(
            request,
            "fragments/_gantt.html",
            {"matrix": matrix, "current_time": datetime.now().strftime("%H:%M:%S")},
        )

    @router.get("/fragments/project/{project_id}/events", response_class=HTMLResponse)
    async def fragment_events(request: Request, project_id: str, t: str = None, design: str = None):
        project = registry.get_project(project_id)
        if not project: return HTMLResponse("")
        events, _, _ = _get_historical_state(project, t, design)
        return request.app.state.templates.TemplateResponse(
            request,
            "fragments/_events.html",
            {"events": events[-50:], "current_time": datetime.now().strftime("%H:%M:%S")},
        )

    @router.get("/fragments/project/{project_id}/telem", response_class=HTMLResponse)
    async def fragment_telem(request: Request, project_id: str, t: str = None, design: str = None):
        project = registry.get_project(project_id)
        if not project: return HTMLResponse("")
        _, _, status = _get_historical_state(project, t, design)
        signals = status.telem_signals if status else []
        return request.app.state.templates.TemplateResponse(
            request,
            "fragments/_telem.html",
            {"signals": signals, "current_time": datetime.now().strftime("%H:%M:%S")},
        )

    @router.get("/fragments/project-list", response_class=HTMLResponse)
    async def fragment_project_list(request: Request):
        projects = registry.list_projects()
        return request.app.state.templates.TemplateResponse(
            request,
            "fragments/_project_list.html",
            {"projects": projects},
        )

    @router.get("/fragments/project/{project_id}/edges", response_class=HTMLResponse)
    async def fragment_edges(request: Request, project_id: str, t: str = None, design: str = None):
        project = registry.get_project(project_id)
        if not project: return HTMLResponse("")
        events, _, status = _get_historical_state(project, t, design)
        convergence = build_convergence_table(status, events)
        return request.app.state.templates.TemplateResponse(
            request,
            "fragments/_edges.html",
            {"convergence": convergence, "current_time": datetime.now().strftime("%H:%M:%S")},
        )

    @router.get("/fragments/project/{project_id}/spawn-tree", response_class=HTMLResponse)
    async def fragment_spawn_tree(request: Request, project_id: str, t: str = None, design: str = None):
        project = registry.get_project(project_id)
        if not project: return HTMLResponse("")
        _, features, _ = _get_historical_state(project, t, design)
        spawn_tree = build_spawn_tree(features)
        return request.app.state.templates.TemplateResponse(
            request,
            "fragments/_spawn_tree.html",
            {"spawn_tree": spawn_tree, "current_time": datetime.now().strftime("%H:%M:%S")},
        )

    @router.get("/fragments/project/{project_id}/dimensions", response_class=HTMLResponse)
    async def fragment_dimensions(request: Request, project_id: str, t: str = None, design: str = None):
        project = registry.get_project(project_id)
        if not project: return HTMLResponse("")
        _, features, _ = _get_historical_state(project, t, design)
        dimensions = build_dimension_coverage(project.topology, features, project.constraints)
        return request.app.state.templates.TemplateResponse(
            request,
            "fragments/_dimensions.html",
            {"dimensions": dimensions, "current_time": datetime.now().strftime("%H:%M:%S")},
        )

    @router.get("/fragments/project/{project_id}/regimes", response_class=HTMLResponse)
    async def fragment_regimes(request: Request, project_id: str, t: str = None, design: str = None):
        project = registry.get_project(project_id)
        if not project: return HTMLResponse("")
        events, _, _ = _get_historical_state(project, t, design)
        regimes = build_regime_summary(events)
        return request.app.state.templates.TemplateResponse(
            request,
            "fragments/_regimes.html",
            {"regimes": regimes, "current_time": datetime.now().strftime("%H:%M:%S")},
        )

    @router.get("/fragments/project/{project_id}/consciousness", response_class=HTMLResponse)
    async def fragment_consciousness(request: Request, project_id: str, t: str = None, design: str = None):
        project = registry.get_project(project_id)
        if not project: return HTMLResponse("")
        events, _, _ = _get_historical_state(project, t, design)
        consciousness = build_consciousness_timeline(events)
        return request.app.state.templates.TemplateResponse(
            request,
            "fragments/_consciousness.html",
            {"consciousness": consciousness, "current_time": datetime.now().strftime("%H:%M:%S")},
        )

    @router.get("/fragments/project/{project_id}/compliance", response_class=HTMLResponse)
    async def fragment_compliance(request: Request, project_id: str, t: str = None, design: str = None):
        project = registry.get_project(project_id)
        if not project: return HTMLResponse("")
        compliance = build_compliance_report(project)
        return request.app.state.templates.TemplateResponse(
            request,
            "fragments/_compliance.html",
            {"compliance": compliance, "current_time": datetime.now().strftime("%H:%M:%S")},
        )

    @router.get("/fragments/project/{project_id}/traceability", response_class=HTMLResponse)
    async def fragment_traceability(request: Request, project_id: str, t: str = None, design: str = None):
        project = registry.get_project(project_id)
        if not project: return HTMLResponse("")
        _, features, _ = _get_historical_state(project, t, design)
        traceability = build_traceability_view(features, project.traceability)
        return request.app.state.templates.TemplateResponse(
            request,
            "fragments/_traceability.html",
            {"traceability": traceability, "current_time": datetime.now().strftime("%H:%M:%S")},
        )

    @router.get("/fragments/project/{project_id}/feature-module-map", response_class=HTMLResponse)
    async def fragment_feature_module_map(request: Request, project_id: str, t: str = None, design: str = None):
        project = registry.get_project(project_id)
        if not project: return HTMLResponse("")
        _, features, _ = _get_historical_state(project, t, design)
        feature_module_map = build_feature_module_map(features, project.traceability)
        return request.app.state.templates.TemplateResponse(
            request,
            "fragments/_feature_module_map.html",
            {"feature_module_map": feature_module_map, "current_time": datetime.now().strftime("%H:%M:%S")},
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
            if target_proj: break
        if not target_proj: return HTMLResponse("Feature not found.", status_code=404)
        feat_path = target_proj.path / "features" / "active" / f"{feature_id}.yml"
        if not feat_path.exists():
            feat_path = target_proj.path / "features" / "completed" / f"{feature_id}.yml"
        if not feat_path.exists(): return HTMLResponse("Source not found.", status_code=404)
        return HTMLResponse(f"<div class='raw-data-block'><pre><code>{feat_path.read_text()}</code></pre></div>")

    @router.get("/events/stream")
    async def sse_stream(request: Request):
        return EventSourceResponse(broadcaster.subscribe(), ping=5)

    @router.get("/api/health")
    async def health():
        uptime = int(time.time() - _start_time)
        return {"status": "ok", "projects": len(registry.list_projects()), "uptime_seconds": uptime}

    return router
