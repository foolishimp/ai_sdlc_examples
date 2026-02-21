# Implements: REQ-F-VREL-002, REQ-F-VREL-003
"""Build spawn tree from feature vectors showing parent/child relationships."""

from __future__ import annotations

from genesis_monitor.models.core import FeatureVector


def build_spawn_tree(features: list[FeatureVector]) -> list[dict]:
    """Build a tree of feature vectors based on parent_id relationships.

    Returns a list of root-level nodes. Each node is a dict with:
        feature_id, title, status, vector_type, profile, parent_id,
        spawned_by, fold_back_status, time_box, children (list of nodes).
    """
    if not features:
        return []

    id_to_feature = {f.feature_id: f for f in features}
    child_ids: set[str] = set()

    for f in features:
        if f.parent_id and f.parent_id in id_to_feature:
            child_ids.add(f.feature_id)

    def _to_node(f: FeatureVector) -> dict:
        time_box_dict = None
        if f.time_box is not None:
            time_box_dict = {
                "duration": f.time_box.duration,
                "check_in": f.time_box.check_in,
                "on_expiry": f.time_box.on_expiry,
                "partial_results": f.time_box.partial_results,
            }

        child_nodes = []
        for cid in f.children:
            if cid in id_to_feature:
                child_nodes.append(_to_node(id_to_feature[cid]))

        return {
            "feature_id": f.feature_id,
            "title": f.title,
            "status": f.status,
            "vector_type": f.vector_type,
            "profile": f.profile,
            "parent_id": f.parent_id,
            "spawned_by": f.spawned_by,
            "fold_back_status": f.fold_back_status,
            "time_box": time_box_dict,
            "children": child_nodes,
        }

    roots = []
    for f in features:
        if f.feature_id not in child_ids:
            roots.append(_to_node(f))

    return roots
