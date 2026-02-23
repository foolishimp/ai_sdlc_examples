# Implements: REQ-F-DISC-003, REQ-NFR-002
"""CLI entry point: genesis-monitor --watch-dir PATH [--port 8000]."""

import argparse
from pathlib import Path

import uvicorn

from genesis_monitor.server.app import create_app


def main() -> None:
    parser = argparse.ArgumentParser(description="Genesis Monitor — AI SDLC Dashboard")
    parser.add_argument(
        "--watch-dir",
        type=Path,
        action="append",
        dest="watch_dirs",
        help="Root directory to scan for .ai-workspace/ projects (repeatable)",
    )
    parser.add_argument(
        "--config",
        type=Path,
        default=None,
        help="Path to config.yml",
    )
    parser.add_argument("--host", default="0.0.0.0", help="Bind host (default: 0.0.0.0)")
    parser.add_argument("--port", type=int, default=8000, help="Bind port (default: 8000)")
    parser.add_argument("--reload", action="store_true", help="Enable auto-reload for development")

    args = parser.parse_args()

    app = create_app(watch_dirs=args.watch_dirs, config_path=args.config)

    uvicorn.run(app, host=args.host, port=args.port, reload=args.reload)


if __name__ == "__main__":
    main()
