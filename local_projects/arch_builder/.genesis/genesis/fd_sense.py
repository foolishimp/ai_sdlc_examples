# Implements: REQ-ITER-003 (Functor Encoding Tracking), REQ-SENSE-001 (Interoceptive Monitors)
"""F_D sense — deterministic sensing: file scanning, staleness, integrity checks."""

import json
import re
import subprocess
from datetime import datetime, timezone
from pathlib import Path

from .models import SenseResult

_REQ_TAG_PATTERN = re.compile(
    r"(?:Implements|Validates):\s*(REQ-[A-Z]+(?:-[A-Z]+)*-\d+)"
)


def sense_event_freshness(
    events_path: Path, threshold_minutes: int = 60
) -> SenseResult:
    """INTRO-001: Check time since last event in events.jsonl."""
    if not events_path.exists():
        return SenseResult(
            monitor_name="event_freshness",
            value=None,
            threshold=threshold_minutes,
            breached=True,
            detail="events.jsonl does not exist",
        )

    last_line = _last_line(events_path)
    if not last_line:
        return SenseResult(
            monitor_name="event_freshness",
            value=None,
            threshold=threshold_minutes,
            breached=True,
            detail="events.jsonl is empty",
        )

    try:
        event = json.loads(last_line)
        ts = datetime.fromisoformat(event["timestamp"])
        age_minutes = (datetime.now(timezone.utc) - ts).total_seconds() / 60.0
        return SenseResult(
            monitor_name="event_freshness",
            value=round(age_minutes, 1),
            threshold=threshold_minutes,
            breached=age_minutes > threshold_minutes,
            detail=f"Last event {age_minutes:.1f} min ago",
        )
    except (json.JSONDecodeError, KeyError, ValueError) as e:
        return SenseResult(
            monitor_name="event_freshness",
            value=None,
            threshold=threshold_minutes,
            breached=True,
            detail=f"Failed to parse last event: {e}",
        )


def sense_feature_stall(
    events_path: Path, feature_id: str, threshold_iterations: int = 3
) -> SenseResult:
    """INTRO-002: Check if a feature's delta has been unchanged for N iterations."""
    if not events_path.exists():
        return SenseResult(
            monitor_name="feature_stall",
            value=None,
            threshold=threshold_iterations,
            breached=False,
            detail="events.jsonl does not exist",
        )

    deltas = []
    with open(events_path) as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                event = json.loads(line)
                if (
                    event.get("event_type") == "iteration_completed"
                    and event.get("feature") == feature_id
                ):
                    deltas.append(event.get("delta", -1))
            except json.JSONDecodeError:
                continue

    if len(deltas) < threshold_iterations:
        return SenseResult(
            monitor_name="feature_stall",
            value=len(deltas),
            threshold=threshold_iterations,
            breached=False,
            detail=f"Only {len(deltas)} iterations recorded (need {threshold_iterations} to detect stall)",
        )

    recent = deltas[-threshold_iterations:]
    stalled = len(set(recent)) == 1 and recent[0] > 0
    return SenseResult(
        monitor_name="feature_stall",
        value=recent[-1],
        threshold=threshold_iterations,
        breached=stalled,
        detail=f"Last {threshold_iterations} deltas: {recent}"
        + (" — STALLED" if stalled else ""),
    )


def sense_test_health(cwd: Path, test_command: str, timeout: int = 60) -> SenseResult:
    """INTRO-003: Run test command and report pass/fail status."""
    try:
        result = subprocess.run(
            test_command,
            shell=True,
            cwd=str(cwd),
            capture_output=True,
            text=True,
            timeout=timeout,
        )
        passed = result.returncode == 0
        return SenseResult(
            monitor_name="test_health",
            value=result.returncode,
            threshold=0,
            breached=not passed,
            detail=f"exit code {result.returncode}"
            + ("" if passed else f": {result.stderr[:200]}"),
        )
    except subprocess.TimeoutExpired:
        return SenseResult(
            monitor_name="test_health",
            value=None,
            threshold=0,
            breached=True,
            detail=f"Test command timed out after {timeout}s",
        )
    except OSError as e:
        return SenseResult(
            monitor_name="test_health",
            value=None,
            threshold=0,
            breached=True,
            detail=str(e),
        )


def sense_req_tag_coverage(source_dir: Path, req_keys: set[str]) -> SenseResult:
    """Scan source files for REQ tag coverage."""
    if not source_dir.exists():
        return SenseResult(
            monitor_name="req_tag_coverage",
            value=0.0,
            threshold=1.0,
            breached=True,
            detail=f"Source directory does not exist: {source_dir}",
        )

    found_keys: set[str] = set()
    for path in source_dir.rglob("*.py"):
        try:
            content = path.read_text(errors="replace")
            found_keys.update(_REQ_TAG_PATTERN.findall(content))
        except OSError:
            continue

    if not req_keys:
        return SenseResult(
            monitor_name="req_tag_coverage",
            value=1.0,
            threshold=1.0,
            breached=False,
            detail="No REQ keys to check against",
        )

    covered = req_keys & found_keys
    coverage = len(covered) / len(req_keys)
    missing = req_keys - found_keys
    return SenseResult(
        monitor_name="req_tag_coverage",
        value=round(coverage, 3),
        threshold=1.0,
        breached=coverage < 1.0,
        detail=f"{len(covered)}/{len(req_keys)} keys covered"
        + (f", missing: {sorted(missing)}" if missing else ""),
    )


def sense_event_log_integrity(events_path: Path) -> SenseResult:
    """INTRO-007: Validate events.jsonl — all lines valid JSON with required fields."""
    if not events_path.exists():
        return SenseResult(
            monitor_name="event_log_integrity",
            value=None,
            breached=True,
            detail="events.jsonl does not exist",
        )

    total = 0
    errors = []
    required_fields = {"event_type", "timestamp", "project"}

    with open(events_path) as f:
        for i, line in enumerate(f, 1):
            line = line.strip()
            if not line:
                continue
            total += 1
            try:
                event = json.loads(line)
                missing = required_fields - set(event.keys())
                if missing:
                    errors.append(f"Line {i}: missing fields {missing}")
            except json.JSONDecodeError as e:
                errors.append(f"Line {i}: invalid JSON: {e}")

    if errors:
        return SenseResult(
            monitor_name="event_log_integrity",
            value=total,
            breached=True,
            detail=f"{len(errors)} errors in {total} events: {errors[:3]}",
        )

    return SenseResult(
        monitor_name="event_log_integrity",
        value=total,
        breached=False,
        detail=f"{total} events, all valid",
    )


def _last_line(path: Path) -> str:
    """Read the last non-empty line of a file efficiently."""
    with open(path, "rb") as f:
        f.seek(0, 2)
        size = f.tell()
        if size == 0:
            return ""
        # Read last 4KB — enough for any single event line
        read_size = min(4096, size)
        f.seek(-read_size, 2)
        chunk = f.read().decode("utf-8", errors="replace")
    lines = [line for line in chunk.splitlines() if line.strip()]
    return lines[-1] if lines else ""
