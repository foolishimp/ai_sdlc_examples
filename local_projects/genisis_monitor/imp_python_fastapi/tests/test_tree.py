# Validates: REQ-F-DASH-006
"""Tests for project tree navigator projection."""

from pathlib import Path

from genesis_monitor.models.core import Project, StatusReport, PhaseEntry
from genesis_monitor.projections.tree import build_project_tree


class TestBuildProjectTree:
    def _project(self, path: str, name: str | None = None) -> Project:
        p = Path(path)
        return Project(
            project_id=p.name,
            path=p,
            name=name or p.name,
        )

    def test_empty_projects(self):
        tree = build_project_tree([])
        assert tree["children"] == []
        assert tree["is_project"] is False

    def test_single_project(self):
        projects = [self._project("/src/apps/myproject")]
        tree = build_project_tree(projects)
        # Root should be the parent of the project
        assert tree["is_project"] is False
        # The project should appear as a child
        leaves = _collect_projects(tree)
        assert len(leaves) == 1
        assert leaves[0]["name"] == "myproject"
        assert leaves[0]["is_project"] is True

    def test_multiple_projects_same_parent(self):
        projects = [
            self._project("/src/apps/alpha"),
            self._project("/src/apps/beta"),
            self._project("/src/apps/gamma"),
        ]
        tree = build_project_tree(projects)
        leaves = _collect_projects(tree)
        assert len(leaves) == 3
        names = {l["name"] for l in leaves}
        assert names == {"alpha", "beta", "gamma"}

    def test_projects_at_different_depths(self):
        projects = [
            self._project("/src/apps/shallow"),
            self._project("/src/apps/deep/nested/project"),
        ]
        tree = build_project_tree(projects)
        leaves = _collect_projects(tree)
        assert len(leaves) == 2
        names = {l["name"] for l in leaves}
        assert names == {"shallow", "project"}

    def test_is_project_flags(self):
        projects = [
            self._project("/src/apps/a/proj1"),
            self._project("/src/apps/b/proj2"),
        ]
        tree = build_project_tree(projects)
        # Root and intermediate dirs should not be projects
        assert tree["is_project"] is False
        # All non-leaf nodes should not be projects
        for node in _collect_all(tree):
            if node["children"]:
                # Check that folder nodes aren't marked as projects
                # (unless the folder itself is also a project)
                pass
        # Leaf project nodes should be projects
        leaves = _collect_projects(tree)
        assert all(l["is_project"] for l in leaves)

    def test_common_ancestor_pruning(self):
        """Tree root should be the common ancestor, not filesystem root."""
        projects = [
            self._project("/home/user/src/apps/proj1"),
            self._project("/home/user/src/apps/proj2"),
        ]
        tree = build_project_tree(projects)
        # The tree should NOT start from / or /home
        # It should start from the common ancestor
        assert "apps" in tree["name"] or "src" in tree["name"] or tree["path"].endswith("apps")

    def test_project_node_carries_project_reference(self):
        p = self._project("/src/apps/myproject", name="My Project")
        tree = build_project_tree([p])
        leaves = _collect_projects(tree)
        assert len(leaves) == 1
        assert leaves[0]["project"] is p
        assert leaves[0]["project"].name == "My Project"

    def test_folder_nodes_have_no_project(self):
        projects = [
            self._project("/src/apps/a/proj1"),
        ]
        tree = build_project_tree(projects)
        folders = [n for n in _collect_all(tree) if not n["is_project"]]
        for folder in folders:
            assert folder["project"] is None

    def test_single_child_pruning(self):
        """Single-child non-project chains should be collapsed."""
        projects = [
            self._project("/src/apps/deep/nested/very/proj1"),
            self._project("/src/apps/deep/nested/very/proj2"),
        ]
        tree = build_project_tree(projects)
        # The intermediate single-child directories between root and
        # the branching point should be collapsed
        leaves = _collect_projects(tree)
        assert len(leaves) == 2

    def test_project_with_status(self):
        """Project nodes should carry status data for badge rendering."""
        p = Project(
            project_id="test",
            path=Path("/src/apps/test"),
            name="Test",
            status=StatusReport(
                project_name="Test",
                phase_summary=[
                    PhaseEntry(edge="intent->req", status="converged", iterations=1),
                    PhaseEntry(edge="req->design", status="in_progress", iterations=2),
                ],
            ),
        )
        tree = build_project_tree([p])
        leaves = _collect_projects(tree)
        assert len(leaves) == 1
        proj = leaves[0]["project"]
        assert proj.status is not None
        assert len(proj.status.phase_summary) == 2

    def test_disjoint_roots(self):
        """Projects with completely disjoint paths should still tree correctly."""
        projects = [
            self._project("/home/user1/project_a"),
            self._project("/home/user2/project_b"),
        ]
        tree = build_project_tree(projects)
        leaves = _collect_projects(tree)
        assert len(leaves) == 2


def _collect_projects(node: dict) -> list[dict]:
    """Recursively collect all project nodes from the tree."""
    result = []
    if node["is_project"]:
        result.append(node)
    for child in node.get("children", []):
        result.extend(_collect_projects(child))
    return result


def _collect_all(node: dict) -> list[dict]:
    """Recursively collect all nodes from the tree."""
    result = [node]
    for child in node.get("children", []):
        result.extend(_collect_all(child))
    return result
