# Implements: REQ-F-DASH-002
"""Generate Mermaid graph diagrams from topology and status data."""

import re

from genesis_monitor.models import GraphTopology, StatusReport


def _sanitize_node_id(name: str) -> str:
    return re.sub(r"[^a-zA-Z0-9_]", "_", name.replace(" ", "_")).strip("_")


def _extract_target_node(edge_name: str) -> str:
    parts = re.split(r"[→↔]", edge_name)
    return _sanitize_node_id(parts[-1].strip())


def build_graph_mermaid(topology: GraphTopology | None, status: StatusReport | None) -> str:
    if not topology or not topology.asset_types: return _default_graph(status)

    lines = ["graph LR"]
    valid_nodes = set()

    # Define nodes with special styling for Tournament Pattern
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

    # Apply styles
    converged_nodes = set()
    progress_nodes = set()
    tournament_nodes = {"parallel_spawn", "tournament_arbitration", "tournament_merge"}

    if status:
        for entry in status.phase_summary:
            node_name = _extract_target_node(entry.edge)
            if entry.status == "converged": converged_nodes.add(node_name)
            elif entry.status == "in_progress": progress_nodes.add(node_name)

    converged_nodes &= valid_nodes
    progress_nodes &= valid_nodes

    if converged_nodes:
        lines.append("    classDef done fill:#4caf50,stroke:#388e3c,color:#fff")
        for n in sorted(converged_nodes): lines.append(f"    class {n} done")
    if progress_nodes:
        lines.append("    classDef active fill:#ff9800,stroke:#f57c00,color:#fff")
        for n in sorted(progress_nodes): lines.append(f"    class {n} active")

    # Specialized styling for tournament nodes (Finding #Codex-Tournament)
    lines.append("    classDef tournament fill:#e1f5fe,stroke:#01579b,stroke-dasharray: 5 5")
    for n in tournament_nodes:
        if n in valid_nodes: lines.append(f"    class {n} tournament")

    return "\n".join(lines)


def _default_graph(status: StatusReport | None) -> str:
    nodes = ["intent", "requirements", "design", "code", "unit_tests", "uat_tests"]
    labels = ["Intent", "Requirements", "Design", "Code", "Unit Tests", "UAT Tests"]
    valid_nodes = set(nodes)
    lines = ["graph LR"]
    for nid, label in zip(nodes, labels): lines.append(f"    {nid}[{label}]")
    for i in range(len(nodes) - 1): lines.append(f"    {nodes[i]} --> {nodes[i + 1]}")
    if status:
        converged = set(); active = set()
        for entry in status.phase_summary:
            target = _extract_target_node(entry.edge)
            if entry.status == "converged": converged.add(target)
            elif entry.status == "in_progress": active.add(target)
        converged &= valid_nodes; active &= valid_nodes
        if converged:
            lines.append("    classDef done fill:#4caf50,stroke:#388e3c,color:#fff")
            for n in sorted(converged): lines.append(f"    class {n} done")
        if active:
            lines.append("    classDef active fill:#ff9800,stroke:#f57c00,color:#fff")
            for n in sorted(active): lines.append(f"    class {n} active")
    return "\n".join(lines)
