# Implements: REQ-F-PARSE-002, REQ-F-VREL-001, REQ-F-TBOX-001, REQ-F-PROF-002
# Implements: REQ-F-FUNC-001, REQ-F-ETIM-001, REQ-F-ETIM-003
"""Parse .ai-workspace/features/active/*.yml into FeatureVector models."""

from datetime import datetime
from pathlib import Path

import yaml
import re

from genesis_monitor.models.core import EdgeTrajectory, FeatureVector
from genesis_monitor.models.features import TimeBox


def parse_feature_vectors(workspace: Path, project_path: Path = None) -> list[FeatureVector]:
    """Parse all active feature vector YAML files.

    Returns an empty list if the directory doesn't exist or contains no valid files.
    After parsing, populates children lists from parent_id cross-references.
    """
    features_dir = workspace / "features" / "active"
    if not features_dir.is_dir():
        return []

    vectors_dict: dict[str, FeatureVector] = {}
    
    # 1. Load Singleton Definitions from Spec (The "WHAT")
    if project_path:
        spec_file = project_path / "specification" / "features" / "FEATURE_VECTORS.md"
        if spec_file.exists():
            spec_content = spec_file.read_text(encoding="utf-8")
            # Parse ### REQ-F-ID: Title
            feat_matches = re.finditer(r"### (REQ-F-[A-Z0-9-]+): (.*)", spec_content)
            for m in feat_matches:
                fid, title = m.group(1), m.group(2)
                vectors_dict[fid] = FeatureVector(
                    feature_id=fid,
                    title=title.strip(),
                    status="pending",
                    vector_type="feature"
                )

    # 2. Overlay Trajectories from Workspace (The "HOW")
    for yml_path in sorted(features_dir.glob("*.yml")):
        workspace_vec = _parse_one(yml_path)
        if workspace_vec:
            fid = workspace_vec.feature_id
            if fid in vectors_dict:
                # Merge: title and requirements from spec, trajectory and status from workspace
                spec_vec = vectors_dict[fid]
                spec_vec.status = workspace_vec.status
                spec_vec.trajectory = workspace_vec.trajectory
                spec_vec.encoding = workspace_vec.encoding
                # Use titles from spec as source of truth
            else:
                vectors_dict[fid] = workspace_vec

    vectors = list(vectors_dict.values())

    # Cross-reference pass: populate children from parent_id
    id_to_vec = {v.feature_id: v for v in vectors}
    for vec in vectors:
        if vec.parent_id and vec.parent_id in id_to_vec:
            parent = id_to_vec[vec.parent_id]
            if vec.feature_id not in parent.children:
                parent.children.append(vec.feature_id)

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
                    # v2.8 fields
                    started_at=_parse_optional_timestamp(edge_data.get("started_at")),
                    converged_at=_parse_optional_timestamp(edge_data.get("converged_at")),
                    convergence_type=str(edge_data.get("convergence_type", "")),
                    escalations=list(edge_data.get("escalations", [])),
                )

    # v2.5: parse time_box
    time_box = None
    raw_tb = data.get("time_box")
    if isinstance(raw_tb, dict):
        time_box = TimeBox(
            duration=str(raw_tb.get("duration", "")),
            check_in=raw_tb.get("check_in"),
            on_expiry=str(raw_tb.get("on_expiry", "fold_back")),
            partial_results=bool(raw_tb.get("partial_results", True)),
        )

    # v2.8: parse encoding block
    encoding = None
    raw_enc = data.get("encoding")
    if isinstance(raw_enc, dict):
        encoding = raw_enc

    # Parse requirements list
    raw_reqs = data.get("requirements", [])
    requirements = [str(r) for r in raw_reqs] if isinstance(raw_reqs, list) else []

    return FeatureVector(
        feature_id=str(data.get("feature", data.get("feature_id", path.stem))),
        title=str(data.get("title", "")),
        status=str(data.get("status", "pending")),
        vector_type=str(data.get("vector_type", "feature")),
        trajectory=trajectory,
        # v2.5 fields
        profile=data.get("profile"),
        parent_id=data.get("parent_id"),
        spawned_by=data.get("spawned_by"),
        fold_back_status=data.get("fold_back_status"),
        time_box=time_box,
        # v2.8 fields
        encoding=encoding,
        requirements=requirements,
    )


def _parse_optional_timestamp(value: str | None) -> datetime | None:
    """Parse an ISO timestamp string, returning None if absent or invalid."""
    if not value:
        return None
    try:
        return datetime.fromisoformat(str(value))
    except (ValueError, TypeError):
        return None
