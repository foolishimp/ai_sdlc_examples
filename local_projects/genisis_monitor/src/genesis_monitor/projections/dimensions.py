# Implements: REQ-F-CDIM-002, REQ-F-CTOL-001, REQ-F-CTOL-002
"""Build constraint dimension coverage matrix."""

from __future__ import annotations

from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from genesis_monitor.models.core import FeatureVector, GraphTopology


def build_dimension_coverage(
    topology: GraphTopology | None,
    features: list[FeatureVector],
) -> list[dict]:
    """Build a coverage matrix showing which features address each constraint dimension.

    Returns a list of dicts with:
        dimension, mandatory, resolves_via, feature_count, features (list of feature_ids).
    """
    if not topology or not topology.constraint_dimensions:
        return []

    result = []
    for dim in topology.constraint_dimensions:
        # Count features that have trajectory at the resolves_via edge
        covered_features = []
        for f in features:
            if dim.resolves_via and dim.resolves_via in f.trajectory:
                covered_features.append(f.feature_id)

        result.append({
            "dimension": dim.name,
            "mandatory": dim.mandatory,
            "resolves_via": dim.resolves_via,
            "feature_count": len(covered_features),
            "features": covered_features,
            # v2.8 additions
            "tolerance": dim.tolerance,
            "breach_status": dim.breach_status,
        })

    return result
