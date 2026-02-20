# Implements: REQ-F-PARSE-001
"""Parse .ai-workspace/STATUS.md into a StatusReport model."""

import re
from pathlib import Path

from genesis_monitor.models import PhaseEntry, StatusReport, TelemSignal


def parse_status(workspace: Path) -> StatusReport | None:
    """Parse STATUS.md from a workspace directory.

    Returns None if the file doesn't exist or can't be parsed.
    """
    status_path = workspace / "STATUS.md"
    if not status_path.exists():
        return None

    try:
        text = status_path.read_text(encoding="utf-8")
    except OSError:
        return None

    report = StatusReport()

    # Extract project name — try **Project**/**Feature** lines, then full first heading
    proj_match = re.search(r"\*\*(?:Project|Feature)\*\*\s*:\s*(.+)", text)
    if proj_match:
        report.project_name = proj_match.group(1).strip()
    else:
        m = re.search(r"^#\s+(.+)$", text, re.MULTILINE)
        if m:
            report.project_name = m.group(1).strip()

    report.phase_summary = _parse_phase_table(text)
    report.telem_signals = _parse_telem_signals(text)
    report.gantt_mermaid = _extract_gantt_mermaid(text)
    report.metrics = _parse_metrics(text)

    return report


def _parse_phase_table(text: str) -> list[PhaseEntry]:
    """Extract the phase completion summary table."""
    entries: list[PhaseEntry] = []

    # Find table rows that look like phase entries
    # Pattern: | edge | status | iterations | ... |
    table_pattern = re.compile(
        r"^\|\s*(.+?)\s*\|\s*(converged|in.progress|not.started|complete|pending|done)\s*\|"
        r"\s*(\d+)\s*\|",
        re.MULTILINE | re.IGNORECASE,
    )

    for m in table_pattern.finditer(text):
        edge = m.group(1).strip()
        # Skip header rows
        if edge.startswith("-") or edge.lower() in ("edge", "phase", "stage"):
            continue

        status_raw = m.group(2).strip().lower()
        status = "converged" if status_raw in ("converged", "complete", "done") else (
            "in_progress" if "progress" in status_raw else "not_started"
        )

        entries.append(PhaseEntry(
            edge=edge,
            status=status,
            iterations=int(m.group(3)),
        ))

    return entries


def _parse_telem_signals(text: str) -> list[TelemSignal]:
    """Extract TELEM signal entries.

    Supports multiple formats:
    - Heading: ### TELEM-001: Title  (followed by **Signal**: ...)
    - Table: | TELEM-001 | category | description |
    - Bullet: - **TELEM-001**: description
    """
    signals: list[TelemSignal] = []

    # Format 1: Heading format (### TELEM-NNN: Title) with **Signal**: body
    heading_pattern = re.compile(
        r"^###\s+(TELEM-\d+)\s*:\s*(.+?)$",
        re.MULTILINE,
    )
    signal_body_pattern = re.compile(
        r"\*\*Signal\*\*\s*:\s*(.+?)(?=\n\*\*|\n###|\n##|\Z)",
        re.DOTALL,
    )

    for m in heading_pattern.finditer(text):
        signal_id = m.group(1)
        title = m.group(2).strip()
        # Look for **Signal**: body after the heading
        after = text[m.end():]
        body_match = signal_body_pattern.search(after)
        description = body_match.group(1).strip() if body_match else title

        signals.append(TelemSignal(
            signal_id=signal_id,
            category="observation",
            description=f"{title}: {description}" if body_match else title,
        ))

    if signals:
        return signals

    # Format 2: Table format (| TELEM-001 | category | description |)
    telem_pattern = re.compile(
        r"(TELEM-\d+)\s*[:\|]\s*(?:\*\*)?([^*\|]+?)(?:\*\*)?\s*[:\|]\s*(.+?)(?:\||$)",
        re.MULTILINE,
    )
    for m in telem_pattern.finditer(text):
        signals.append(TelemSignal(
            signal_id=m.group(1).strip(),
            category=m.group(2).strip(),
            description=m.group(3).strip().rstrip("|"),
        ))

    if signals:
        return signals

    # Format 3: Bullet format (- **TELEM-001**: description)
    bullet_pattern = re.compile(
        r"[-*]\s+\*\*(TELEM-\d+)\*\*\s*[:\-]\s*(.+)",
        re.MULTILINE,
    )
    for m in bullet_pattern.finditer(text):
        signals.append(TelemSignal(
            signal_id=m.group(1),
            category="observation",
            description=m.group(2).strip(),
        ))

    return signals


def _extract_gantt_mermaid(text: str) -> str | None:
    """Extract Mermaid gantt chart block if present."""
    pattern = re.compile(
        r"```mermaid\s*\n(gantt\s*\n.*?)```",
        re.DOTALL,
    )
    m = pattern.search(text)
    return m.group(1).strip() if m else None


def _parse_metrics(text: str) -> dict[str, str]:
    """Extract key-value metrics from an Aggregate Metrics section."""
    metrics: dict[str, str] = {}

    # Find the metrics section
    section_match = re.search(
        r"(?:Aggregate\s+Metrics|Metrics)\s*\n(.*?)(?=\n##|\Z)",
        text,
        re.DOTALL | re.IGNORECASE,
    )
    if not section_match:
        return metrics

    section = section_match.group(1)
    # Match key: value or | key | value | patterns
    kv_pattern = re.compile(r"[-*]\s+\*\*(.+?)\*\*\s*:\s*(.+)")
    for m in kv_pattern.finditer(section):
        metrics[m.group(1).strip()] = m.group(2).strip()

    return metrics
