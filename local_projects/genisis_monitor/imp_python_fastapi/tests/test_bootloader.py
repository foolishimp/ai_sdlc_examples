# Validates: REQ-F-DASH-006
"""Tests for Genesis Bootloader detection."""

from pathlib import Path

from genesis_monitor.parsers.bootloader import detect_bootloader


class TestDetectBootloader:
    def test_no_claude_md(self, tmp_path: Path):
        assert detect_bootloader(tmp_path) is False

    def test_claude_md_without_bootloader(self, tmp_path: Path):
        (tmp_path / "CLAUDE.md").write_text("# My Project\n\nSome instructions.\n")
        assert detect_bootloader(tmp_path) is False

    def test_claude_md_with_bootloader(self, tmp_path: Path):
        content = (
            "# My Project\n\n"
            "---\n\n"
            "<!-- GENESIS_BOOTLOADER_START -->\n"
            "# Genesis Bootloader: LLM Constraint Context\n"
            "The formal system reduces to four primitives...\n"
            "<!-- GENESIS_BOOTLOADER_END -->\n"
        )
        (tmp_path / "CLAUDE.md").write_text(content)
        assert detect_bootloader(tmp_path) is True

    def test_only_start_marker_present(self, tmp_path: Path):
        content = "# My Project\n\n<!-- GENESIS_BOOTLOADER_START -->\nContent\n"
        (tmp_path / "CLAUDE.md").write_text(content)
        assert detect_bootloader(tmp_path) is True

    def test_empty_claude_md(self, tmp_path: Path):
        (tmp_path / "CLAUDE.md").write_text("")
        assert detect_bootloader(tmp_path) is False

    def test_nonexistent_directory(self):
        assert detect_bootloader(Path("/nonexistent/path")) is False

    def test_claude_md_is_directory(self, tmp_path: Path):
        (tmp_path / "CLAUDE.md").mkdir()
        assert detect_bootloader(tmp_path) is False
