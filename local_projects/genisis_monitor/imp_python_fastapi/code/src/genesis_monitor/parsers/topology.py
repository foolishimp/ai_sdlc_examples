# Implements: REQ-F-PARSE-003, REQ-F-CDIM-001, REQ-F-PROF-001, REQ-F-CTOL-001, REQ-F-CTOL-002
"""Parse graph_topology.yml into a GraphTopology model.

Source priority (first found wins):
  1. Plugin config: {project_root}/*/code/**/plugins/*/config/graph_topology.yml
  2. Workspace copy: {workspace}/graph/graph_topology.yml  (may be a symlink)

Rationale: the plugin config is the authoritative source. The workspace copy
exists for compatibility but may lag if not kept in sync. Reading the plugin
config directly avoids that class of staleness.
"""

from pathlib import Path

import yaml

from genesis_monitor.models.core import AssetType, GraphTopology, Transition
from genesis_monitor.models.features import ConstraintDimension, ProjectionProfile


def _find_plugin_config(project_root: Path) -> Path | None:
    """Search for a genesis plugin graph_topology.yml under the project root.

    Matches: {project_root}/*/code/**/plugins/*/config/graph_topology.yml
    Returns the first match (sorted for determinism), or None.
    """
    for candidate in sorted(
        project_root.glob("*/code/**/" + "plugins/*/config/graph_topology.yml")
    ):
        if candidate.is_file():
            return candidate
    return None


def parse_graph_topology(
    workspace: Path,
    project_root: Path | None = None,
) -> GraphTopology | None:
    """Parse graph topology YAML.

    If project_root is provided, searches for the plugin config first.
    Falls back to workspace/graph/graph_topology.yml.
    Returns None if no file exists or parsing fails.
    """
    topo_path: Path | None = None

    if project_root:
        topo_path = _find_plugin_config(project_root)

    if topo_path is None:
        topo_path = workspace / "graph" / "graph_topology.yml"

    if not topo_path.exists():
        return None

    try:
        with open(topo_path) as f:
            data = yaml.safe_load(f)
    except (OSError, yaml.YAMLError):
        return None

    if not isinstance(data, dict):
        return None

    asset_types: list[AssetType] = []
    raw_assets = data.get("asset_types") or {}
    if isinstance(raw_assets, dict):
        for name, info in raw_assets.items():
            desc = info.get("description", "") if isinstance(info, dict) else str(info)
            asset_types.append(AssetType(name=str(name), description=str(desc)))
    elif isinstance(raw_assets, list):
        for item in raw_assets:
            if isinstance(item, dict):
                name = str(item.get("id") or item.get("name", ""))
                desc = str(item.get("description", ""))
                asset_types.append(AssetType(name=name, description=desc))
            elif isinstance(item, str):
                asset_types.append(AssetType(name=item, description=""))

    transitions: list[Transition] = []
    for t in data.get("transitions", []) or []:
        if isinstance(t, dict):
            transitions.append(Transition(
                source=str(t.get("source", "")),
                target=str(t.get("target", "")),
                edge_type=str(t.get("edge_type", f"{t.get('source', '')}_{t.get('target', '')}")),
            ))

    # v2.5: parse constraint_dimensions
    constraint_dimensions: list[ConstraintDimension] = []
    raw_dims = data.get("constraint_dimensions", {})
    if isinstance(raw_dims, dict):
        for name, dim_data in raw_dims.items():
            if isinstance(dim_data, dict):
                constraint_dimensions.append(ConstraintDimension(
                    name=str(name),
                    mandatory=bool(dim_data.get("mandatory", False)),
                    resolves_via=str(dim_data.get("resolves_via", "")),
                    # v2.8 fields
                    tolerance=str(dim_data.get("tolerance", "")),
                    breach_status=str(dim_data.get("breach_status", "")),
                ))

    # v2.5: parse profiles
    profiles: list[ProjectionProfile] = []
    raw_profiles = data.get("profiles", {})
    if isinstance(raw_profiles, dict):
        for prof_name, prof_data in raw_profiles.items():
            if isinstance(prof_data, dict):
                profiles.append(ProjectionProfile(
                    name=str(prof_name),
                    graph_edges=list(prof_data.get("graph_edges", [])),
                    evaluator_types=list(prof_data.get("evaluator_types", [])),
                    convergence=str(prof_data.get("convergence", "")),
                    context_density=str(prof_data.get("context_density", "")),
                    iteration_budget=prof_data.get("iteration_budget"),
                    vector_types=list(prof_data.get("vector_types", [])),
                ))

    return GraphTopology(
        asset_types=asset_types,
        transitions=transitions,
        constraint_dimensions=constraint_dimensions,
        profiles=profiles,
    )
