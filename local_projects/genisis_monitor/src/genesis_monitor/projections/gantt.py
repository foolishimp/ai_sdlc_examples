# Implements: REQ-F-DASH-004
"""Extract Gantt mermaid from status report."""

from genesis_monitor.models import StatusReport


def build_gantt_mermaid(status: StatusReport | None) -> str | None:
    """Return the Gantt Mermaid block from STATUS.md, or None if not available."""
    if not status:
        return None
    return status.gantt_mermaid
