# Implements: REQ-F-DASH-002
"""Generate Mermaid graph diagrams from topology and status data."""

from genesis_monitor.models import GraphTopology, StatusReport


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

    # Define nodes
    for at in topology.asset_types:
        node_id = at.name.replace(" ", "_")
        label = at.name.replace("_", " ").title()
        lines.append(f"    {node_id}[{label}]")

    # Define edges
    for t in topology.transitions:
        src = t.source.replace(" ", "_")
        tgt = t.target.replace(" ", "_")
        lines.append(f"    {src} --> {tgt}")

    # Apply styles based on status
    converged_nodes: set[str] = set()
    progress_nodes: set[str] = set()

    if status:
        for entry in status.phase_summary:
            # The edge name might reference the target node
            node_name = entry.edge.split("→")[-1].strip().replace(" ", "_").lower()
            if entry.status == "converged":
                converged_nodes.add(node_name)
            elif entry.status == "in_progress":
                progress_nodes.add(node_name)

    # Style definitions
    if converged_nodes:
        lines.append(f"    classDef done fill:#4caf50,stroke:#388e3c,color:#fff")
        for n in converged_nodes:
            lines.append(f"    class {n} done")
    if progress_nodes:
        lines.append(f"    classDef active fill:#ff9800,stroke:#f57c00,color:#fff")
        for n in progress_nodes:
            lines.append(f"    class {n} active")

    return "\n".join(lines)


def _default_graph(status: StatusReport | None) -> str:
    """Fallback graph when no topology YAML is available."""
    nodes = ["intent", "requirements", "design", "code", "unit_tests", "uat_tests"]
    labels = ["Intent", "Requirements", "Design", "Code", "Unit Tests", "UAT Tests"]

    lines = ["graph LR"]
    for nid, label in zip(nodes, labels):
        lines.append(f"    {nid}[{label}]")

    for i in range(len(nodes) - 1):
        lines.append(f"    {nodes[i]} --> {nodes[i + 1]}")

    # Colour based on status
    if status:
        converged = set()
        active = set()
        for entry in status.phase_summary:
            target = entry.edge.split("→")[-1].strip().replace(" ", "_").lower()
            if entry.status == "converged":
                converged.add(target)
            elif entry.status == "in_progress":
                active.add(target)

        if converged:
            lines.append("    classDef done fill:#4caf50,stroke:#388e3c,color:#fff")
            for n in converged:
                lines.append(f"    class {n} done")
        if active:
            lines.append("    classDef active fill:#ff9800,stroke:#f57c00,color:#fff")
            for n in active:
                lines.append(f"    class {n} active")

    return "\n".join(lines)
