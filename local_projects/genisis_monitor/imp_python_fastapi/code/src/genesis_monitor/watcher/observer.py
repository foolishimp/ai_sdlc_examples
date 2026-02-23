# Implements: REQ-F-WATCH-001, REQ-F-WATCH-002
"""Watchdog-based filesystem observer with debouncing and periodic discovery.

Watches only .ai-workspace/ directories (not entire root trees) to avoid
processing thousands of irrelevant filesystem events. A periodic rescan
discovers new projects that appear after startup.
"""

from __future__ import annotations

import logging
import threading
from pathlib import Path
from typing import TYPE_CHECKING

from watchdog.events import FileSystemEvent, FileSystemEventHandler
from watchdog.observers import Observer

from genesis_monitor.scanner import scan_roots

if TYPE_CHECKING:
    from genesis_monitor.registry import ProjectRegistry
    from genesis_monitor.server.broadcaster import SSEBroadcaster

logger = logging.getLogger(__name__)

RESCAN_INTERVAL_S = 30


class _WorkspaceHandler(FileSystemEventHandler):
    """Handles events from a specific .ai-workspace/ directory."""

    def __init__(
        self,
        project_id: str,
        registry: ProjectRegistry,
        broadcaster: SSEBroadcaster,
        debounce_ms: int,
    ) -> None:
        self._project_id = project_id
        self._registry = registry
        self._broadcaster = broadcaster
        self._debounce_s = debounce_ms / 1000.0
        self._timer: threading.Timer | None = None
        self._timer_lock = threading.Lock()

    def on_any_event(self, event: FileSystemEvent) -> None:
        # Debounce: reset timer for this project
        with self._timer_lock:
            if self._timer:
                self._timer.cancel()

            self._timer = threading.Timer(
                self._debounce_s,
                self._fire_refresh,
            )
            self._timer.start()

    def _fire_refresh(self) -> None:
        """Called after debounce window — refresh project and notify clients."""
        logger.info("Refreshing project: %s", self._project_id)
        self._registry.refresh_project(self._project_id)
        self._broadcaster.send("project_updated", {"project_id": self._project_id})

        with self._timer_lock:
            self._timer = None


class WorkspaceWatcher:
    """Watches .ai-workspace/ directories for changes.

    Two mechanisms:
    1. Watchdog observers on each known .ai-workspace/ dir (instant updates).
    2. Periodic rescan of roots to discover new projects (every 30s).
    """

    def __init__(
        self,
        registry: ProjectRegistry,
        broadcaster: SSEBroadcaster,
        debounce_ms: int = 500,
    ) -> None:
        self._registry = registry
        self._broadcaster = broadcaster
        self._debounce_ms = debounce_ms
        self._observer = Observer()
        self._roots: list[Path] = []
        self._known_paths: set[Path] = set()
        self._rescan_timer: threading.Timer | None = None
        self._stopped = False

    def start(self, roots: list[Path]) -> None:
        """Start watching .ai-workspace/ dirs and periodic rescan."""
        self._roots = [r.expanduser().resolve() for r in roots]

        # Watch existing projects
        for project in self._registry.list_projects():
            self._watch_project(project.project_id, project.path)

        count = len(self._known_paths)
        logger.info("Watching %d .ai-workspace/ directories", count)

        self._observer.start()
        self._schedule_rescan()

    def stop(self) -> None:
        """Stop the watcher and rescan timer."""
        self._stopped = True
        if self._rescan_timer:
            self._rescan_timer.cancel()
        self._observer.stop()
        self._observer.join(timeout=5)

    def _watch_project(self, project_id: str, project_path: Path) -> None:
        """Add a watchdog schedule for a project's .ai-workspace/."""
        ws_dir = project_path / ".ai-workspace"
        if ws_dir.is_dir() and project_path not in self._known_paths:
            handler = _WorkspaceHandler(
                project_id,
                self._registry,
                self._broadcaster,
                self._debounce_ms,
            )
            self._observer.schedule(handler, str(ws_dir), recursive=True)
            self._known_paths.add(project_path)

    def _schedule_rescan(self) -> None:
        """Schedule the next periodic rescan."""
        if self._stopped:
            return
        self._rescan_timer = threading.Timer(RESCAN_INTERVAL_S, self._rescan)
        self._rescan_timer.daemon = True
        self._rescan_timer.start()

    def _rescan(self) -> None:
        """Rescan roots for new projects and start watching them."""
        if self._stopped:
            return

        try:
            all_paths = scan_roots(self._roots)
            new_paths = [p for p in all_paths if p not in self._known_paths]

            for path in new_paths:
                project = self._registry.add_project(path)
                self._watch_project(project.project_id, path)
                self._broadcaster.send("project_added", {
                    "project_id": project.project_id,
                })
                logger.info("Discovered new project: %s (%s)", project.name, path)
        except Exception:
            logger.exception("Error during periodic rescan")

        self._schedule_rescan()
