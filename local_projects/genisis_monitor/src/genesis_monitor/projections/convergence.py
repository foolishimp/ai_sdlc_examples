# Implements: REQ-F-DASH-003
"""Build convergence table from status report."""

from genesis_monitor.models import EdgeConvergence, StatusReport


def build_convergence_table(status: StatusReport | None) -> list[EdgeConvergence]:
    """Derive convergence view rows from a status report."""
    if not status or not status.phase_summary:
        return []

    rows: list[EdgeConvergence] = []
    for entry in status.phase_summary:
        # If evaluator_results has a "summary" key, use the raw summary string
        if "summary" in entry.evaluator_results:
            summary = entry.evaluator_results["summary"]
        elif entry.evaluator_results:
            total = len(entry.evaluator_results)
            passed = sum(1 for v in entry.evaluator_results.values() if v.lower() == "pass")
            summary = f"{passed}/{total} pass"
        else:
            summary = "no evaluators"

        rows.append(EdgeConvergence(
            edge=entry.edge,
            iterations=entry.iterations,
            evaluator_summary=summary,
            source_findings=entry.source_findings,
            process_gaps=entry.process_gaps,
            status=entry.status,
        ))

    return rows
