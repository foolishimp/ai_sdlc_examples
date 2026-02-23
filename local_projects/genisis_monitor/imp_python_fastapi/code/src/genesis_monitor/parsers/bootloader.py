# Implements: REQ-F-DASH-006
"""Detect Genesis Bootloader presence in a project's CLAUDE.md."""

from __future__ import annotations

from pathlib import Path

BOOTLOADER_START_MARKER = "<!-- GENESIS_BOOTLOADER_START -->"


def detect_bootloader(project_path: Path) -> bool:
    """Check if the project has GENESIS_BOOTLOADER installed in CLAUDE.md.

    The installer (gen-setup.py) appends the bootloader content between
    <!-- GENESIS_BOOTLOADER_START --> and <!-- GENESIS_BOOTLOADER_END --> markers.
    We only need the start marker to confirm presence.
    """
    claude_md = project_path / "CLAUDE.md"
    if not claude_md.is_file():
        return False

    try:
        content = claude_md.read_text(errors="replace")
        return BOOTLOADER_START_MARKER in content
    except OSError:
        return False
