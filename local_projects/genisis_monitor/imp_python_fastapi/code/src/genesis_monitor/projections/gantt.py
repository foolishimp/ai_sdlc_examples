# Implements: REQ-F-DASH-004
"""Generate feature × edge matrix tables from feature vector trajectories.

Replaces the Mermaid Gantt chart (which broke at >8 features) with a plain HTML
matrix table.  Each row is a feature vector; each column is a graph edge; each
cell shows the convergence status and iteration count for that feature on that
edge.  Parent/child hierarchy is preserved via indentation.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime

from genesis_monitor.models.core import FeatureVector, StatusReport

# Canonical edge ordering that matches the bootstrap graph topology
_EDGE_ORDER = [
    "intent→requirements",
    "requirements→feature_decomposition",
    "feature_decomposition→design",
    "design→code",
    "code↔unit_tests",
    "unit_tests→uat_tests",
    "uat_tests→cicd",
    "cicd→telemetry",
    "telemetry→intent",
]


@dataclass
class MatrixCell:
    """One cell in the feature matrix: edge × feature intersection."""

    status: str = "not_started"
    iteration: int = 0
    started_at: datetime | None = None
    converged_at: datetime | None = None


@dataclass
class MatrixRow:
    """One row in the feature matrix: a single feature vector."""

    feature_id: str = ""
    title: str = ""
    status: str = "pending"
    indent: int = 0  # 0 = root, 1 = child
    cells: dict[str, MatrixCell] = field(default_factory=dict)


@dataclass
class FeatureMatrix:
    """Structured data for the feature × edge matrix table."""

    edges: list[str] = field(default_factory=list)
    rows: list[MatrixRow] = field(default_factory=list)


def build_feature_matrix(features: list[FeatureVector] | None) -> FeatureMatrix | None:
    """Build a structured feature × edge matrix from feature vectors.

    Collects all edges that appear in any feature trajectory, orders them
    by the canonical bootstrap-graph topology order, and arranges feature
    rows with parent-before-child ordering (indented hierarchy).
    """
    if not features:
        return None

    features_with_traj = [f for f in features if f.trajectory]
    if not features_with_traj:
        return None

    # Collect edges that appear, ordered canonically
    seen_edges: set[str] = set()
    for f in features_with_traj:
        seen_edges.update(f.trajectory.keys())

    ordered = [e for e in _EDGE_ORDER if e in seen_edges]
    remainder = sorted(seen_edges - set(ordered))
    edges = ordered + remainder

    # Build hierarchy-ordered feature list
    feat_map = {f.feature_id: f for f in features}
    root_ids = [f.feature_id for f in features if not f.parent_id]
    child_map: dict[str, list[str]] = {}
    for f in features:
        if f.parent_id:
            child_map.setdefault(f.parent_id, []).append(f.feature_id)

    ordered_features: list[tuple[FeatureVector, int]] = []

    def _add(fid: str, indent: int) -> None:
        if fid not in feat_map:
            return
        ordered_features.append((feat_map[fid], indent))
        for child_id in child_map.get(fid, []):
            _add(child_id, indent + 1)

    for rid in root_ids:
        _add(rid, 0)

    # Catch any orphan features (parent_id set but parent not in list)
    seen_ids = {f.feature_id for f, _ in ordered_features}
    for f in features:
        if f.feature_id not in seen_ids:
            ordered_features.append((f, 1))

    # Build matrix rows
    rows: list[MatrixRow] = []
    for feat, indent in ordered_features:
        cells: dict[str, MatrixCell] = {}
        for edge in edges:
            traj = feat.trajectory.get(edge)
            if traj:
                cells[edge] = MatrixCell(
                    status=traj.status,
                    iteration=traj.iteration,
                    started_at=traj.started_at,
                    converged_at=traj.converged_at,
                )
        rows.append(MatrixRow(
            feature_id=feat.feature_id,
            title=feat.title or "",
            status=feat.status,
            indent=indent,
            cells=cells,
        ))

    return FeatureMatrix(edges=edges, rows=rows)


def build_gantt_mermaid(
    status: StatusReport | None,
    features: list[FeatureVector] | None = None,  # noqa: ARG001
) -> str | None:
    """Return embedded Mermaid gantt from STATUS.md if present, else None.

    The feature-trajectory-based Mermaid generation has been replaced by
    build_feature_matrix().  This function is kept for the STATUS.md fallback
    path only (rare legacy projects that write their own Mermaid block).
    """
    if status and status.gantt_mermaid:
        return status.gantt_mermaid
    return None
