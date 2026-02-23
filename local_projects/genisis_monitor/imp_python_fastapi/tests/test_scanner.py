# Validates: REQ-F-DISC-001
"""Tests for workspace scanning."""

from pathlib import Path

from genesis_monitor.scanner import scan_roots


def test_scan_finds_workspace(tmp_workspace: Path):
    """Scanner discovers directories containing .ai-workspace/."""
    results = scan_roots([tmp_workspace.parent])
    assert tmp_workspace in results


def test_scan_empty_directory(tmp_path: Path):
    """Scanner returns empty list for directory with no workspaces."""
    results = scan_roots([tmp_path])
    assert results == []


def test_scan_nonexistent_directory():
    """Scanner handles non-existent root gracefully."""
    results = scan_roots([Path("/nonexistent/path")])
    assert results == []


def test_scan_multiple_projects(tmp_path: Path):
    """Scanner finds multiple workspaces under a root."""
    (tmp_path / "proj_a" / ".ai-workspace").mkdir(parents=True)
    (tmp_path / "proj_b" / ".ai-workspace").mkdir(parents=True)
    (tmp_path / "not_a_project").mkdir()

    results = scan_roots([tmp_path])
    names = [r.name for r in results]
    assert "proj_a" in names
    assert "proj_b" in names
    assert "not_a_project" not in names


def test_scan_prunes_git_directories(tmp_path: Path):
    """Scanner skips .git directories."""
    (tmp_path / ".git" / ".ai-workspace").mkdir(parents=True)
    (tmp_path / "real_project" / ".ai-workspace").mkdir(parents=True)

    results = scan_roots([tmp_path])
    assert len(results) == 1
    assert results[0].name == "real_project"


def test_scan_multiple_roots(tmp_path: Path):
    """Scanner handles multiple root directories."""
    root_a = tmp_path / "area_a"
    root_b = tmp_path / "area_b"
    (root_a / "proj1" / ".ai-workspace").mkdir(parents=True)
    (root_b / "proj2" / ".ai-workspace").mkdir(parents=True)

    results = scan_roots([root_a, root_b])
    names = [r.name for r in results]
    assert "proj1" in names
    assert "proj2" in names
