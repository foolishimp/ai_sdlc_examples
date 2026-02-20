# Validates: REQ-F-DISC-003
"""Tests for configuration loading."""

from pathlib import Path

import yaml

from genesis_monitor.config import load_config


class TestLoadConfig:
    def test_default_config(self):
        config = load_config()
        assert config.watch_dirs == []
        assert config.port == 8000
        assert config.debounce_ms == 500

    def test_cli_watch_dirs(self):
        dirs = [Path("/tmp/test")]
        config = load_config(cli_watch_dirs=dirs)
        assert config.watch_dirs == dirs

    def test_yaml_config(self, tmp_path: Path):
        config_file = tmp_path / "config.yml"
        config_file.write_text(yaml.dump({
            "watch_dirs": ["/home/user/projects"],
            "server": {"host": "127.0.0.1", "port": 9000},
            "watcher": {"debounce_ms": 1000},
        }))
        config = load_config(config_path=config_file)
        assert len(config.watch_dirs) == 1
        assert config.host == "127.0.0.1"
        assert config.port == 9000
        assert config.debounce_ms == 1000

    def test_cli_overrides_yaml(self, tmp_path: Path):
        config_file = tmp_path / "config.yml"
        config_file.write_text(yaml.dump({
            "watch_dirs": ["/yaml/path"],
        }))
        cli_dirs = [Path("/cli/path")]
        config = load_config(config_path=config_file, cli_watch_dirs=cli_dirs)
        assert config.watch_dirs == cli_dirs

    def test_missing_config_file(self):
        config = load_config(config_path=Path("/nonexistent/config.yml"))
        assert config.watch_dirs == []
