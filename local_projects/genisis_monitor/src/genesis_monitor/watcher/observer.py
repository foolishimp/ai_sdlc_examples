# Implements: REQ-F-WATCH-001, REQ-F-WATCH-002
"""Watchdog-based filesystem observer with debouncing."""

from __future__ import annotations

import logging
import threading
from pathlib import Path
from typing import TYPE_CHECKING

from watchdog.events import FileSystemEvent, FileSystemEventHandler
from watchdog.observers import Observer

if TYPE_CHECKING:
    from genesis_monitor.registry import ProjectRegistry
    from genesis_monitor.server.broadcaster import SSEBroadcaster

logger = logging.getLogger(__name__)


class _WorkspaceHandler(FileSystemEventHandler):
    """Filters events to .ai-workspace/ paths and debounces per-project."""

    def __init__(
        self,
        registry: ProjectRegistry,
        broadcaster: SSEBroadcaster,
        debounce_ms: int,
    ) -> None:
        self._registry = registry
        self._broadcaster = broadcaster
        self._debounce_s = debounce_ms / 1000.0
        self._timers: dict[str, threading.Timer] = {}
        self._timer_lock = threading.Lock()

    def on_any_event(self, event: FileSystemEvent) -> None:
        path_str = str(event.src_path)
        if ".ai-workspace" not in path_str:
            return

        # Find which project this path belongs to
        project_id = self._registry.project_id_for_path(Path(path_str))
        if not project_id:
            # Might be a new project — check if .ai-workspace dir was just created
            p = Path(path_str)
            if p.name == ".ai-workspace" and p.is_dir():
                project = self._registry.add_project(p.parent)
                self._broadcaster.send("project_added", {
                    "project_id": project.project_id,
                })
            return

        # Debounce: reset timer for this project
        with self._timer_lock:
            existing = self._timers.get(project_id)
            if existing:
                existing.cancel()

            timer = threading.Timer(
                self._debounce_s,
                self._fire_refresh,
                args=(project_id,),
            )
            self._timers[project_id] = timer
            timer.start()

    def _fire_refresh(self, project_id: str) -> None:
        """Called after debounce window — refresh project and notify clients."""
        logger.info("Refreshing project: %s", project_id)
        self._registry.refresh_project(project_id)
        self._broadcaster.send("project_updated", {"project_id": project_id})

        with self._timer_lock:
            self._timers.pop(project_id, None)


class WorkspaceWatcher:
    """Watches root directories for .ai-workspace/ changes."""

    def __init__(
        self,
        registry: ProjectRegistry,
        broadcaster: SSEBroadcaster,
        debounce_ms: int = 500,
    ) -> None:
        self._handler = _WorkspaceHandler(registry, broadcaster, debounce_ms)
        self._observer = Observer()

    def start(self, roots: list[Path]) -> None:
        """Start watching the given root directories."""
        for root in roots:
            root = root.expanduser().resolve()
            if root.is_dir():
                self._observer.schedule(self._handler, str(root), recursive=True)
                logger.info("Watching: %s", root)

        self._observer.start()

    def stop(self) -> None:
        """Stop the watcher."""
        self._observer.stop()
        self._observer.join(timeout=5)
