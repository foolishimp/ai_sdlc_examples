# Implements: REQ-F-PARSE-003, REQ-F-CDIM-001, REQ-F-PROF-001
"""Parse .ai-workspace/graph/graph_topology.yml into a GraphTopology model."""

from pathlib import Path

import yaml

from genesis_monitor.models.core import AssetType, GraphTopology, Transition
from genesis_monitor.models.features import ConstraintDimension, ProjectionProfile


def parse_graph_topology(workspace: Path) -> GraphTopology | None:
    """Parse graph topology YAML.

    Returns None if the file doesn't exist or can't be parsed.
    """
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
    for name, info in (data.get("asset_types", {}) or {}).items():
        desc = info.get("description", "") if isinstance(info, dict) else str(info)
        asset_types.append(AssetType(name=str(name), description=str(desc)))

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
