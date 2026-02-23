# Implements: REQ-F-PROTO-001, REQ-F-CTOL-001, REQ-F-CTOL-002, REQ-F-ETIM-001, REQ-F-SENSE-005
"""Build protocol compliance report for a project."""

from __future__ import annotations

from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from genesis_monitor.models.core import Project


def build_compliance_report(project: Project) -> list[dict]:
    """Check project compliance with v2.5 protocol requirements.

    Returns a list of dicts with:
        check (str), status ('pass'|'warn'|'fail'), detail (str).
    """
    checks: list[dict] = []

    # 1. Graph topology present
    if project.topology and project.topology.asset_types:
        checks.append({
            "check": "Graph topology defined",
            "status": "pass",
            "detail": f"{len(project.topology.asset_types)} asset types, "
                      f"{len(project.topology.transitions)} transitions",
        })
    else:
        checks.append({
            "check": "Graph topology defined",
            "status": "fail",
            "detail": "No topology or asset types found",
        })

    # 2. Constraint dimensions
    has_dims = (
        project.topology is not None
        and len(project.topology.constraint_dimensions) > 0
    )
    if has_dims:
        mandatory = sum(1 for d in project.topology.constraint_dimensions if d.mandatory)
        checks.append({
            "check": "Constraint dimensions defined",
            "status": "pass",
            "detail": f"{len(project.topology.constraint_dimensions)} dimensions "
                      f"({mandatory} mandatory)",
        })
    else:
        checks.append({
            "check": "Constraint dimensions defined",
            "status": "warn",
            "detail": "No constraint dimensions — v2.5 recommends at least one",
        })

    # 3. Projection profiles
    has_profiles = (
        project.topology is not None
        and len(project.topology.profiles) > 0
    )
    if has_profiles:
        checks.append({
            "check": "Projection profiles defined",
            "status": "pass",
            "detail": f"{len(project.topology.profiles)} profiles: "
                      + ", ".join(p.name for p in project.topology.profiles),
        })
    else:
        checks.append({
            "check": "Projection profiles defined",
            "status": "warn",
            "detail": "No projection profiles — v2.5 recommends named profiles",
        })

    # 4. Feature vectors present
    if project.features:
        checks.append({
            "check": "Feature vectors present",
            "status": "pass",
            "detail": f"{len(project.features)} feature vectors",
        })
    else:
        checks.append({
            "check": "Feature vectors present",
            "status": "warn",
            "detail": "No feature vectors found",
        })

    # 5. Features with profiles assigned
    if project.features:
        with_profile = sum(1 for f in project.features if f.profile)
        without_profile = len(project.features) - with_profile
        if without_profile == 0:
            checks.append({
                "check": "Feature profile assignment",
                "status": "pass",
                "detail": f"All {with_profile} features have profiles",
            })
        else:
            checks.append({
                "check": "Feature profile assignment",
                "status": "warn",
                "detail": f"{without_profile}/{len(project.features)} features lack profile",
            })

    # 6. Event log present
    if project.events:
        checks.append({
            "check": "Event log present",
            "status": "pass",
            "detail": f"{len(project.events)} events recorded",
        })
    else:
        checks.append({
            "check": "Event log present",
            "status": "warn",
            "detail": "No events — v2.5 requires append-only event sourcing",
        })

    # 7. Status report present
    if project.status:
        checks.append({
            "check": "Status report present",
            "status": "pass",
            "detail": f"Project: {project.status.project_name}",
        })
    else:
        checks.append({
            "check": "Status report present",
            "status": "warn",
            "detail": "No STATUS.md found",
        })

    # 8. v2.8: Constraint tolerances defined
    if has_dims:
        with_tolerance = sum(
            1 for d in project.topology.constraint_dimensions if d.tolerance
        )
        if with_tolerance == len(project.topology.constraint_dimensions):
            checks.append({
                "check": "Constraint tolerances defined",
                "status": "pass",
                "detail": f"All {with_tolerance} dimensions have tolerances",
            })
        elif with_tolerance > 0:
            checks.append({
                "check": "Constraint tolerances defined",
                "status": "warn",
                "detail": f"{with_tolerance}/{len(project.topology.constraint_dimensions)} "
                          "dimensions have tolerances",
            })
        else:
            checks.append({
                "check": "Constraint tolerances defined",
                "status": "warn",
                "detail": "No tolerances defined — v2.8 recommends measurable thresholds",
            })

    # 9. v2.8: Sensory events present
    sensory_types = {"interoceptive_signal", "exteroceptive_signal", "affect_triage"}
    sensory_count = sum(1 for e in project.events if e.event_type in sensory_types)
    if sensory_count > 0:
        checks.append({
            "check": "Sensory events present",
            "status": "pass",
            "detail": f"{sensory_count} sensory events recorded",
        })
    else:
        checks.append({
            "check": "Sensory events present",
            "status": "warn",
            "detail": "No sensory events — v2.8 defines interoceptive/exteroceptive signals",
        })

    # 10. v2.8: Edge timestamps present
    has_timestamps = False
    for f in project.features:
        for traj in f.trajectory.values():
            if traj.started_at is not None:
                has_timestamps = True
                break
        if has_timestamps:
            break
    if has_timestamps:
        checks.append({
            "check": "Edge timestamps present",
            "status": "pass",
            "detail": "Feature vectors include started_at/converged_at timestamps",
        })
    elif project.features:
        checks.append({
            "check": "Edge timestamps present",
            "status": "warn",
            "detail": "No edge timestamps — v2.8 recommends started_at/converged_at",
        })

    return checks
