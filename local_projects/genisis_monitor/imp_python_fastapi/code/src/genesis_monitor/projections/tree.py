# Implements: REQ-F-DASH-006
"""Build a filesystem-hierarchy tree from flat project list."""

from __future__ import annotations

from pathlib import Path

from genesis_monitor.models.core import Project


def build_project_tree(projects: list[Project]) -> dict:
    """Build a hierarchical tree from flat project list using filesystem paths.

    Returns a nested dict:
        {
            "name": "root_dir",
            "path": "/abs/path",
            "is_project": False,
            "project": None,
            "children": [ ... ]
        }

    Project nodes have is_project=True and carry the Project reference.
    Non-project ancestor directories are folder nodes (is_project=False).

    The tree root is the longest common ancestor of all project paths,
    so shallow common prefixes like /Users/jim/src are pruned.
    """
    if not projects:
        return {
            "name": "",
            "path": "",
            "is_project": False,
            "project": None,
            "children": [],
        }

    # Build a path → project lookup
    path_map: dict[Path, Project] = {p.path: p for p in projects}
    all_paths = list(path_map.keys())

    # Find longest common ancestor
    if len(all_paths) == 1:
        common = all_paths[0].parent
    else:
        common = _common_ancestor(all_paths)

    # Build trie
    root: dict = _make_node(common.name or str(common), str(common))

    for project in projects:
        try:
            rel = project.path.relative_to(common)
        except ValueError:
            # Should not happen, but handle gracefully
            rel = Path(project.path.name)

        parts = list(rel.parts)
        _insert(root, parts, project, common)

    # Prune single-child non-project chains
    _prune(root)

    return root


def _common_ancestor(paths: list[Path]) -> Path:
    """Find the longest common ancestor directory of all paths."""
    parts_list = [p.parts for p in paths]
    common_parts: list[str] = []
    for segments in zip(*parts_list):
        if len(set(segments)) == 1:
            common_parts.append(segments[0])
        else:
            break
    if not common_parts:
        return Path("/")
    return Path(*common_parts)


def _make_node(name: str, path: str, project: Project | None = None) -> dict:
    return {
        "name": name,
        "path": path,
        "is_project": project is not None,
        "project": project,
        "children": [],
    }


def _insert(node: dict, parts: list[str], project: Project, base: Path) -> None:
    """Insert a project into the trie at the path given by parts."""
    if not parts:
        # This node IS the project
        node["is_project"] = True
        node["project"] = project
        return

    segment = parts[0]
    remaining = parts[1:]

    # Find or create child
    for child in node["children"]:
        if child["name"] == segment:
            _insert(child, remaining, project, base / segment)
            return

    # Create new intermediate node
    child_path = str(base / segment)
    child = _make_node(segment, child_path)
    node["children"].append(child)
    _insert(child, remaining, project, base / segment)


def _prune(node: dict) -> None:
    """Collapse single-child non-project directory chains.

    E.g. a/b/c where a and b are non-project folders with one child each
    becomes a/b/c as a single node named "a/b/c".
    """
    # Recurse first (bottom-up)
    for child in node["children"]:
        _prune(child)

    # Collapse: if this node is a non-project folder with exactly one child
    # that is also a non-project folder, merge them.
    while (
        not node["is_project"]
        and len(node["children"]) == 1
        and not node["children"][0]["is_project"]
        and node["children"][0]["children"]  # child has grandchildren
    ):
        child = node["children"][0]
        node["name"] = node["name"] + "/" + child["name"]
        node["path"] = child["path"]
        node["children"] = child["children"]
