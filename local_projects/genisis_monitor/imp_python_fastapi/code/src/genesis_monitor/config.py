# Implements: REQ-F-DISC-003
"""Configuration loading from YAML file and CLI arguments."""

from pathlib import Path

import yaml

from genesis_monitor.models import AppConfig


def load_config(
    config_path: Path | None = None,
    cli_watch_dirs: list[Path] | None = None,
) -> AppConfig:
    """Load config from YAML, override with CLI args.

    CLI watch_dirs override config file watch_dirs entirely.
    """
    config_data: dict = {}

    if config_path and config_path.exists():
        with open(config_path) as f:
            config_data = yaml.safe_load(f) or {}

    watch_dirs = cli_watch_dirs or [
        Path(p).expanduser() for p in config_data.get("watch_dirs", [])
    ]

    server = config_data.get("server", {})
    watcher = config_data.get("watcher", {})

    return AppConfig(
        watch_dirs=watch_dirs,
        host=server.get("host", "0.0.0.0"),
        port=server.get("port", 8000),
        debounce_ms=watcher.get("debounce_ms", 500),
        exclude_patterns=watcher.get(
            "exclude_patterns",
            [".git", "__pycache__", ".venv", "node_modules"],
        ),
    )
