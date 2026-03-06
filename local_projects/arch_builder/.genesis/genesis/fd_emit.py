# Implements: REQ-ITER-003 (Functor Encoding Tracking), REQ-SUPV-003 (Failure Observability)
"""F_D emit — append structured events to events.jsonl (category-fixed: always F_D)."""

import fcntl
import json
from datetime import datetime, timezone
from pathlib import Path

from .models import Event


def make_event(event_type: str, project: str, **data) -> Event:
    """Construct an Event with ISO 8601 timestamp."""
    return Event(
        event_type=event_type,
        timestamp=datetime.now(timezone.utc).isoformat(),
        project=project,
        data=data,
    )


def emit_event(events_path: Path, event: Event) -> None:
    """Append a single event as a JSON line to events.jsonl.

    Uses fcntl.flock for advisory file locking (single-machine safety).
    Creates the file and parent directories if they don't exist.
    """
    if not event.event_type:
        raise ValueError("event_type is required")
    if not event.timestamp:
        raise ValueError("timestamp is required")
    if not event.project:
        raise ValueError("project is required")

    events_path.parent.mkdir(parents=True, exist_ok=True)

    record = {
        "event_type": event.event_type,
        "timestamp": event.timestamp,
        "project": event.project,
    }
    record.update(event.data)

    line = json.dumps(record, separators=(",", ":")) + "\n"

    with open(events_path, "a") as f:
        fcntl.flock(f, fcntl.LOCK_EX)
        try:
            f.write(line)
            f.flush()
        finally:
            fcntl.flock(f, fcntl.LOCK_UN)
