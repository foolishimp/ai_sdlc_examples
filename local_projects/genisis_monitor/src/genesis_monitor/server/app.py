# Implements: REQ-F-DASH-001, REQ-NFR-002, REQ-F-WATCH-001
"""FastAPI application with lifespan — startup scans and starts watcher."""

from __future__ import annotations

import asyncio
import logging
import sys
from collections.abc import AsyncGenerator
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI
from fastapi.templating import Jinja2Templates

from genesis_monitor.config import load_config
from genesis_monitor.registry import ProjectRegistry
from genesis_monitor.scanner import scan_roots
from genesis_monitor.server.broadcaster import SSEBroadcaster
from genesis_monitor.server.routes import create_router
from genesis_monitor.watcher import WorkspaceWatcher

logging.basicConfig(level=logging.INFO, format="%(levelname)s: %(message)s")
logger = logging.getLogger(__name__)

# Shared instances
registry = ProjectRegistry()
broadcaster = SSEBroadcaster()
watcher: WorkspaceWatcher | None = None


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncGenerator[None, None]:
    global watcher

    # Load config — check for CLI-passed watch dirs in app state
    cli_dirs = getattr(app.state, "cli_watch_dirs", None)
    config_path = getattr(app.state, "config_path", None)
    config = load_config(config_path=config_path, cli_watch_dirs=cli_dirs)

    if not config.watch_dirs:
        logger.error("No watch directories configured. Use --watch-dir or config.yml.")
        sys.exit(1)

    # Scan for projects
    logger.info("Scanning %d root(s) for .ai-workspace/ projects...", len(config.watch_dirs))
    project_paths = scan_roots(config.watch_dirs)
    logger.info("Found %d project(s)", len(project_paths))

    for path in project_paths:
        project = registry.add_project(path)
        logger.info("  - %s (%s)", project.name, project.path)

    # Start SSE broadcaster
    broadcaster.set_loop(asyncio.get_running_loop())

    # Start filesystem watcher
    watcher = WorkspaceWatcher(registry, broadcaster, debounce_ms=config.debounce_ms)
    watcher.start(config.watch_dirs)

    yield

    # Shutdown
    if watcher:
        watcher.stop()
    logger.info("Genesis Monitor stopped.")


def create_app(
    watch_dirs: list[Path] | None = None,
    config_path: Path | None = None,
    *,
    _registry: ProjectRegistry | None = None,
    _broadcaster: SSEBroadcaster | None = None,
    _lifespan=None,
) -> FastAPI:
    """Create the FastAPI application.

    The _registry, _broadcaster, and _lifespan params are for testing only.
    """
    reg = _registry or registry
    bc = _broadcaster or broadcaster
    lf = _lifespan if _lifespan is not None else lifespan

    app = FastAPI(title="Genesis Monitor", lifespan=lf)

    # Store CLI args for lifespan to pick up
    app.state.cli_watch_dirs = watch_dirs
    app.state.config_path = config_path

    # Templates
    templates_dir = Path(__file__).parent.parent / "templates"
    app.state.templates = Jinja2Templates(directory=str(templates_dir))

    # Routes
    router = create_router(reg, bc)
    app.include_router(router)

    return app


# Default app instance for `uvicorn genesis_monitor.server.app:app`
app = create_app()
