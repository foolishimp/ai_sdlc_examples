# Implements: REQ-F-DASH-002
"""Generate Mermaid graph diagrams from topology and status data."""

import re

from genesis_monitor.models import GraphTopology, StatusReport


def _sanitize_node_id(name: str) -> str:
    """Convert a name to a valid Mermaid node ID (alphanumeric + underscore only)."""
    return re.sub(r"[^a-zA-Z0-9_]", "_", name.replace(" ", "_")).strip("_")


def _extract_target_node(edge_name: str) -> str:
    """Extract the target node name from an edge like 'intent→requirements' or 'code↔unit_tests'."""
    # Split on → or ↔ and take the last part
    parts = re.split(r"[→↔]", edge_name)
    return _sanitize_node_id(parts[-1].strip())


def build_graph_mermaid(
    topology: GraphTopology | None,
    status: StatusReport | None,
) -> str:
    """Build a Mermaid graph LR diagram with coloured nodes based on edge status.

    Green = converged, yellow = in_progress, grey = not_started.
    """
    if not topology or not topology.asset_types:
        return _default_graph(status)

    # Build status lookup from phase summary
    edge_status: dict[str, str] = {}
    if status:
        for entry in status.phase_summary:
            edge_status[entry.edge.lower()] = entry.status

    lines = ["graph LR"]

    # Collect valid node IDs for filtering
    valid_nodes: set[str] = set()

    # Define nodes
    for at in topology.asset_types:
        node_id = _sanitize_node_id(at.name)
        label = at.name.replace("_", " ").title()
        lines.append(f"    {node_id}[{label}]")
        valid_nodes.add(node_id)

    # Define edges
    for t in topology.transitions:
        src = _sanitize_node_id(t.source)
        tgt = _sanitize_node_id(t.target)
        lines.append(f"    {src} --> {tgt}")

    # Apply styles based on status
    converged_nodes: set[str] = set()
    progress_nodes: set[str] = set()

    if status:
        for entry in status.phase_summary:
            node_name = _extract_target_node(entry.edge)
            if entry.status == "converged":
                converged_nodes.add(node_name)
            elif entry.status == "in_progress":
                progress_nodes.add(node_name)

    # Only apply styles to nodes that exist in the graph
    converged_nodes &= valid_nodes
    progress_nodes &= valid_nodes

    if converged_nodes:
        lines.append("    classDef done fill:#4caf50,stroke:#388e3c,color:#fff")
        for n in sorted(converged_nodes):
            lines.append(f"    class {n} done")
    if progress_nodes:
        lines.append("    classDef active fill:#ff9800,stroke:#f57c00,color:#fff")
        for n in sorted(progress_nodes):
            lines.append(f"    class {n} active")

    return "\n".join(lines)


def _default_graph(status: StatusReport | None) -> str:
    """Fallback graph when no topology YAML is available."""
    nodes = ["intent", "requirements", "design", "code", "unit_tests", "uat_tests"]
    labels = ["Intent", "Requirements", "Design", "Code", "Unit Tests", "UAT Tests"]
    valid_nodes = set(nodes)

    lines = ["graph LR"]
    for nid, label in zip(nodes, labels):
        lines.append(f"    {nid}[{label}]")

    for i in range(len(nodes) - 1):
        lines.append(f"    {nodes[i]} --> {nodes[i + 1]}")

    if status:
        converged: set[str] = set()
        active: set[str] = set()
        for entry in status.phase_summary:
            target = _extract_target_node(entry.edge)
            if entry.status == "converged":
                converged.add(target)
            elif entry.status == "in_progress":
                active.add(target)

        converged &= valid_nodes
        active &= valid_nodes

        if converged:
            lines.append("    classDef done fill:#4caf50,stroke:#388e3c,color:#fff")
            for n in sorted(converged):
                lines.append(f"    class {n} done")
        if active:
            lines.append("    classDef active fill:#ff9800,stroke:#f57c00,color:#fff")
            for n in sorted(active):
                lines.append(f"    class {n} active")

    return "\n".join(lines)
