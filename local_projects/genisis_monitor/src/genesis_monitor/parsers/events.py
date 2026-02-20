# Implements: REQ-F-PARSE-004
"""Parse .ai-workspace/events/events.jsonl into Event models."""

import json
from datetime import datetime
from pathlib import Path

from genesis_monitor.models import Event


def parse_events(workspace: Path, max_events: int = 200) -> list[Event]:
    """Parse the append-only event log.

    Returns an empty list if the file doesn't exist.
    Reads at most max_events from the end of the file.
    """
    events_path = workspace / "events" / "events.jsonl"
    if not events_path.exists():
        return []

    events: list[Event] = []
    try:
        lines = events_path.read_text(encoding="utf-8").strip().splitlines()
        # Take last N lines for recency
        for line in lines[-max_events:]:
            line = line.strip()
            if not line:
                continue
            try:
                data = json.loads(line)
                ts = data.get("timestamp", "")
                try:
                    timestamp = datetime.fromisoformat(ts) if ts else datetime.now()
                except (ValueError, TypeError):
                    timestamp = datetime.now()

                events.append(Event(
                    timestamp=timestamp,
                    event_type=str(data.get("event_type", data.get("type", "unknown"))),
                    project=str(data.get("project", "")),
                    data=data,
                ))
            except json.JSONDecodeError:
                continue
    except OSError:
        return []

    return events


