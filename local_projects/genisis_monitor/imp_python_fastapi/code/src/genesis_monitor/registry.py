# Implements: REQ-F-DISC-002
"""In-memory project registry — thread-safe store of discovered projects."""

import re
import threading
from datetime import datetime
from pathlib import Path

from genesis_monitor.models import Project
from genesis_monitor.parsers import (
    detect_bootloader,
    parse_constraints,
    parse_events,
    parse_feature_vectors,
    parse_graph_topology,
    parse_status,
    parse_tasks,
)


def _slugify(name: str) -> str:
    """Convert a directory name to a URL-safe slug."""
    slug = name.lower().strip()
    slug = re.sub(r"[^\w\s-]", "", slug)
    slug = re.sub(r"[\s_]+", "-", slug)
    return slug.strip("-")


class ProjectRegistry:
    """Thread-safe registry of discovered AI SDLC projects."""

    def __init__(self) -> None:
        self._projects: dict[str, Project] = {}
        self._lock = threading.Lock()

    def add_project(self, path: Path) -> Project:
        """Add a project by its filesystem path. Parses all workspace data."""
        project_id = _slugify(path.name)
        workspace = path / ".ai-workspace"

        status = parse_status(workspace)
        # Use directory name as display name — it's always unique and descriptive.
        # STATUS.md heading is often generic ("Project Status").
        name = path.name

        project = Project(
            project_id=project_id,
            path=path,
            name=name,
            status=status,
            features=parse_feature_vectors(workspace),
            topology=parse_graph_topology(workspace),
            events=parse_events(workspace),
            tasks=parse_tasks(workspace),
            constraints=parse_constraints(workspace),
            has_bootloader=detect_bootloader(path),
            last_updated=datetime.now(),
        )

        with self._lock:
            self._projects[project_id] = project

        return project

    def remove_project(self, path: Path) -> None:
        """Remove a project by path."""
        project_id = _slugify(path.name)
        with self._lock:
            self._projects.pop(project_id, None)

    def get_project(self, project_id: str) -> Project | None:
        """Get a project by its slug ID."""
        with self._lock:
            return self._projects.get(project_id)

    def list_projects(self) -> list[Project]:
        """List all registered projects, sorted by name."""
        with self._lock:
            return sorted(self._projects.values(), key=lambda p: p.name.lower())

    def refresh_project(self, project_id: str) -> Project | None:
        """Re-parse all data for an existing project."""
        with self._lock:
            existing = self._projects.get(project_id)

        if not existing:
            return None

        return self.add_project(existing.path)

    def project_id_for_path(self, path: Path) -> str | None:
        """Find the project_id that contains the given path."""
        with self._lock:
            for pid, proj in self._projects.items():
                try:
                    path.relative_to(proj.path)
                    return pid
                except ValueError:
                    continue
        return None
