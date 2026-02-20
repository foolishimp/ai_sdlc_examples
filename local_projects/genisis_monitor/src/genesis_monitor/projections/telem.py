# Implements: REQ-F-DASH-005, REQ-F-TELEM-001
"""Aggregate TELEM signals across projects."""

from genesis_monitor.models import Project, TelemSignal


def collect_telem_signals(projects: list[Project]) -> list[TelemSignal]:
    """Collect all TELEM signals across projects, attributed to their source."""
    signals: list[TelemSignal] = []

    for project in projects:
        if not project.status:
            continue
        for sig in project.status.telem_signals:
            signals.append(TelemSignal(
                signal_id=sig.signal_id,
                category=sig.category,
                description=sig.description,
                project_id=project.project_id,
            ))

    return signals
