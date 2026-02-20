# Implements: REQ-F-PARSE-003
"""Parse .ai-workspace/graph/graph_topology.yml into a GraphTopology model."""

from pathlib import Path

import yaml

from genesis_monitor.models import AssetType, GraphTopology, Transition


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

    return GraphTopology(asset_types=asset_types, transitions=transitions)
