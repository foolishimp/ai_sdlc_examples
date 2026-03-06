# Implements: REQ-ITER-003 (Functor Encoding Tracking)
"""F_D route — deterministic routing: profile lookup, next edge selection, encoding lookup."""

from pathlib import Path

from .models import Category, RouteResult


def lookup_encoding(profile: dict, unit: str) -> Category:
    """Read encoding.functional_units from a profile and return the Category for a unit."""
    encoding = profile.get("encoding", {})
    units = encoding.get("functional_units", {})
    cat_str = units.get(unit)
    if cat_str is None:
        raise KeyError(f"Functional unit '{unit}' not in profile encoding")
    return Category(cat_str)


def select_profile(feature_type: str, profiles_dir: Path) -> str:
    """Map a vector type to a profile name.

    Convention: feature→standard, spike→spike, hotfix→hotfix, poc→poc,
    discovery→poc. Falls back to 'standard'.
    """
    type_to_profile = {
        "feature": "standard",
        "discovery": "poc",
        "spike": "spike",
        "poc": "poc",
        "hotfix": "hotfix",
    }
    profile_name = type_to_profile.get(feature_type.lower(), "standard")

    # Verify profile file exists, fall back to standard
    profile_path = profiles_dir / f"{profile_name}.yml"
    if not profile_path.exists():
        profile_name = "standard"

    return profile_name


def select_next_edge(
    feature_trajectory: dict,
    graph_topology: dict,
    profile: dict,
) -> RouteResult:
    """Given current feature state, return the next edge to traverse.

    Walks the profile's graph.include list and returns the first edge
    whose status is not 'converged'.
    """
    profile_name = profile.get("profile", "unknown")
    graph = profile.get("graph", {})
    include_edges = graph.get("include", [])
    optional_edges = graph.get("optional", [])

    # Handle include: "all" — expand to all transitions from graph_topology
    if include_edges == "all":
        transitions = graph_topology.get("transitions", [])
        include_edges = []
        for t in transitions:
            source = t.get("source", "")
            target = t.get("target", "")
            if source and target:
                sep = "↔" if t.get("edge_type") == "co_evolution" else "→"
                include_edges.append(f"{source}{sep}{target}")
            elif t.get("edge"):
                include_edges.append(t["edge"])
    if not isinstance(include_edges, list):
        include_edges = []
    if not isinstance(optional_edges, list):
        optional_edges = []

    all_edges = include_edges + optional_edges

    trajectory = feature_trajectory.get("trajectory", {})

    for edge in include_edges:
        edge_status = _edge_status(edge, trajectory)
        if edge_status != "converged":
            return RouteResult(
                selected_edge=edge,
                reason=f"First unconverged required edge (status: {edge_status})",
                profile=profile_name,
                candidates=all_edges,
            )

    # All required edges converged — check optional
    for edge in optional_edges:
        edge_status = _edge_status(edge, trajectory)
        if edge_status == "iterating":
            return RouteResult(
                selected_edge=edge,
                reason=f"In-progress optional edge (status: {edge_status})",
                profile=profile_name,
                candidates=all_edges,
            )

    # All converged
    return RouteResult(
        selected_edge="",
        reason="All edges converged",
        profile=profile_name,
        candidates=all_edges,
    )


def _edge_status(edge: str, trajectory: dict) -> str:
    """Look up edge status from feature trajectory.

    Trajectory keys use underscored names (e.g., "code_unit_tests" for "code↔unit_tests").
    """
    # Normalize edge name to trajectory key
    key = edge.replace("→", "_").replace("↔", "_").replace(" ", "")
    entry = trajectory.get(key, {})
    if isinstance(entry, dict):
        return entry.get("status", "pending")
    return "pending"
