# Validates: REQ-F-DISC-002
"""Tests for the project registry."""

from pathlib import Path

from genesis_monitor.registry import ProjectRegistry


class TestProjectRegistry:
    def test_add_project(self, tmp_workspace: Path):
        reg = ProjectRegistry()
        project = reg.add_project(tmp_workspace)
        assert project.project_id == "test-project"
        assert project.path == tmp_workspace

    def test_list_projects(self, tmp_workspace: Path):
        reg = ProjectRegistry()
        reg.add_project(tmp_workspace)
        projects = reg.list_projects()
        assert len(projects) == 1

    def test_get_project(self, tmp_workspace: Path):
        reg = ProjectRegistry()
        reg.add_project(tmp_workspace)
        project = reg.get_project("test-project")
        assert project is not None
        assert project.name == "test_project"

    def test_get_nonexistent_project(self):
        reg = ProjectRegistry()
        assert reg.get_project("nonexistent") is None

    def test_remove_project(self, tmp_workspace: Path):
        reg = ProjectRegistry()
        reg.add_project(tmp_workspace)
        reg.remove_project(tmp_workspace)
        assert reg.get_project("test-project") is None

    def test_refresh_project(self, tmp_workspace: Path):
        reg = ProjectRegistry()
        reg.add_project(tmp_workspace)
        refreshed = reg.refresh_project("test-project")
        assert refreshed is not None
        assert refreshed.project_id == "test-project"

    def test_refresh_nonexistent(self):
        reg = ProjectRegistry()
        assert reg.refresh_project("nonexistent") is None

    def test_project_id_for_path(self, tmp_workspace: Path):
        reg = ProjectRegistry()
        reg.add_project(tmp_workspace)
        ws_file = tmp_workspace / ".ai-workspace" / "STATUS.md"
        pid = reg.project_id_for_path(ws_file)
        assert pid == "test-project"

    def test_project_id_for_unknown_path(self, tmp_workspace: Path):
        reg = ProjectRegistry()
        reg.add_project(tmp_workspace)
        pid = reg.project_id_for_path(Path("/some/other/path"))
        assert pid is None

    def test_parsed_data_populated(self, tmp_workspace: Path):
        reg = ProjectRegistry()
        project = reg.add_project(tmp_workspace)
        assert project.status is not None
        assert len(project.features) == 1
        assert project.topology is not None
        assert len(project.events) == 2
        assert len(project.tasks) == 3
        assert project.constraints is not None
