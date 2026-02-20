# Implements: REQ-F-PARSE-005
"""Parse .ai-workspace/tasks/active/ACTIVE_TASKS.md into Task models."""

import re
from pathlib import Path

from genesis_monitor.models import Task


def parse_tasks(workspace: Path) -> list[Task]:
    """Parse ACTIVE_TASKS.md for task entries.

    Returns an empty list if the file doesn't exist or can't be parsed.
    Handles both table format and bullet-list format.
    """
    tasks_path = workspace / "tasks" / "active" / "ACTIVE_TASKS.md"
    if not tasks_path.exists():
        return []

    try:
        text = tasks_path.read_text(encoding="utf-8")
    except OSError:
        return []

    tasks = _parse_table_format(text)
    if not tasks:
        tasks = _parse_bullet_format(text)

    return tasks


def _parse_table_format(text: str) -> list[Task]:
    """Parse tasks from markdown table rows."""
    tasks: list[Task] = []

    # Match table rows: | id | title | status | ... |
    row_pattern = re.compile(
        r"^\|\s*#?(\d+)\s*\|\s*(.+?)\s*\|\s*(\w[\w\s]*?)\s*\|",
        re.MULTILINE,
    )

    for m in row_pattern.finditer(text):
        task_id = m.group(1).strip()
        title = m.group(2).strip()
        status = m.group(3).strip().lower()

        # Skip separator rows
        if set(title) <= {"-", " ", "|"}:
            continue

        tasks.append(Task(
            task_id=task_id,
            title=title,
            status=status,
        ))

    return tasks


def _parse_bullet_format(text: str) -> list[Task]:
    """Parse tasks from bullet-list format."""
    tasks: list[Task] = []

    bullet_pattern = re.compile(
        r"^[-*]\s+(?:\[([xX ])\]\s+)?(?:#(\d+)\s*[-:])?\s*(.+)",
        re.MULTILINE,
    )

    for i, m in enumerate(bullet_pattern.finditer(text)):
        checkbox = m.group(1)
        task_id = m.group(2) or str(i + 1)
        title = m.group(3).strip()

        status = "completed" if checkbox and checkbox.lower() == "x" else "pending"

        tasks.append(Task(task_id=task_id, title=title, status=status))

    return tasks
