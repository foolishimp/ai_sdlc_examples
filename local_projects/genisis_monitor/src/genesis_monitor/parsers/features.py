# Implements: REQ-F-PARSE-002
"""Parse .ai-workspace/features/active/*.yml into FeatureVector models."""

from pathlib import Path

import yaml

from genesis_monitor.models import EdgeTrajectory, FeatureVector


def parse_feature_vectors(workspace: Path) -> list[FeatureVector]:
    """Parse all active feature vector YAML files.

    Returns an empty list if the directory doesn't exist or contains no valid files.
    """
    features_dir = workspace / "features" / "active"
    if not features_dir.is_dir():
        return []

    vectors: list[FeatureVector] = []
    for yml_path in sorted(features_dir.glob("*.yml")):
        vec = _parse_one(yml_path)
        if vec:
            vectors.append(vec)

    return vectors


def _parse_one(path: Path) -> FeatureVector | None:
    """Parse a single feature vector YAML file."""
    try:
        with open(path) as f:
            data = yaml.safe_load(f)
    except (OSError, yaml.YAMLError):
        return None

    if not isinstance(data, dict):
        return None

    trajectory: dict[str, EdgeTrajectory] = {}
    raw_traj = data.get("trajectory", {})
    if isinstance(raw_traj, dict):
        for edge_name, edge_data in raw_traj.items():
            if isinstance(edge_data, dict):
                trajectory[edge_name] = EdgeTrajectory(
                    status=str(edge_data.get("status", "not_started")),
                    iteration=int(edge_data.get("iteration", 0)),
                    evaluator_results={
                        str(k): str(v)
                        for k, v in (edge_data.get("evaluator_results", {}) or {}).items()
                    },
                )

    return FeatureVector(
        feature_id=str(data.get("feature", data.get("feature_id", path.stem))),
        title=str(data.get("title", "")),
        status=str(data.get("status", "pending")),
        vector_type=str(data.get("vector_type", "feature")),
        trajectory=trajectory,
    )
