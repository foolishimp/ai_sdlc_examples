# Implements: REQ-F-CDIM-002, REQ-F-CTOL-001, REQ-F-CTOL-002
"""Build constraint dimension coverage matrix."""

from __future__ import annotations

from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from genesis_monitor.models.core import FeatureVector, GraphTopology, ProjectConstraints


def build_dimension_coverage(
    topology: GraphTopology | None,
    features: list[FeatureVector],
    constraints: ProjectConstraints | None = None,
) -> list[dict]:
    """Build a coverage matrix showing constraint dimension status.

    Returns a list of dicts with:
        dimension, mandatory, resolves_via, resolved, feature_count,
        tolerance, breach_status.
    """
    if not topology or not topology.constraint_dimensions:
        return []

    # Check which dimensions are actually bound in project constraints
    raw = constraints.raw if constraints else {}

    result = []
    for dim in topology.constraint_dimensions:
        # Check if this dimension has a value in project constraints
        resolved = dim.name in raw and raw[dim.name] not in (None, "", {}, [])

        # Count features that have trajectory at the resolves_via edge
        covered_count = 0
        if dim.resolves_via:
            covered_count = sum(
                1 for f in features
                if dim.resolves_via in f.trajectory
            )

        result.append({
            "dimension": dim.name,
            "mandatory": dim.mandatory,
            "resolves_via": dim.resolves_via,
            "resolved": resolved,
            "feature_count": covered_count,
            # v2.8 additions
            "tolerance": dim.tolerance,
            "breach_status": dim.breach_status,
        })

    return result
