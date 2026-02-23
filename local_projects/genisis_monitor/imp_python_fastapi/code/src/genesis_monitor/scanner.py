# Implements: REQ-F-DISC-001, REQ-F-DISC-003
"""Workspace discovery — scans root directories for .ai-workspace/ projects."""

import os
from pathlib import Path

PRUNE_DIRS = {".git", "node_modules", "__pycache__", ".venv", ".tox", ".mypy_cache"}


def scan_roots(roots: list[Path]) -> list[Path]:
    """Find all directories containing a .ai-workspace/ subdirectory.

    Returns the parent directory (project root) for each match.
    Prunes common non-project directories for speed.
    """
    projects: list[Path] = []

    for root in roots:
        root = root.expanduser().resolve()
        if not root.is_dir():
            continue

        for dirpath, dirnames, _filenames in os.walk(root):
            # Prune directories we never want to descend into
            dirnames[:] = [d for d in dirnames if d not in PRUNE_DIRS]

            if ".ai-workspace" in dirnames:
                projects.append(Path(dirpath))
                # Don't descend into .ai-workspace itself
                dirnames.remove(".ai-workspace")

    return sorted(projects)
