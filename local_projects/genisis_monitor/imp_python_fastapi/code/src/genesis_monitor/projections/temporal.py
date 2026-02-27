# Implements: REQ-F-NAV-001, REQ-F-NAV-003
"""Temporal projection engine. Reconstructs state from the event log."""

from datetime import datetime
from genesis_monitor.models.core import FeatureVector, EdgeTrajectory, StatusReport
from genesis_monitor.models.events import Event

def reconstruct_features(events: list[Event], timestamp_limit: datetime) -> list[FeatureVector]:
    """Reconstruct feature vectors as they existed at timestamp_limit."""
    features_dict = {}
    
    # Filter events
    past_events = [e for e in events if e.timestamp <= timestamp_limit]
    
    for ev in past_events:
        fid = getattr(ev, "feature", None)
        if not fid and ev.event_type == "feature_spawned":
            fid = getattr(ev, "data", {}).get("feature")
            
        if not fid:
            # Maybe nested in data
            if isinstance(getattr(ev, "data", None), dict):
                fid = ev.data.get("feature")
                
        if not fid:
            continue
            
        if fid not in features_dict:
            features_dict[fid] = FeatureVector(feature_id=fid, title=fid)
            
        feat = features_dict[fid]
        
        if ev.event_type == "feature_spawned":
            feat.vector_type = getattr(ev, "data", {}).get("vector_type", "feature")
            feat.parent_id = getattr(ev, "data", {}).get("parent")
            
        edge = getattr(ev, "edge", None)
        if edge:
            # normalize edge
            edge = edge.replace("->", "→")
            target_node = edge.split("→")[-1].strip()
            
            if target_node not in feat.trajectory:
                feat.trajectory[target_node] = EdgeTrajectory()
            
            traj = feat.trajectory[target_node]
            
            if ev.event_type == "edge_started":
                traj.status = "iterating"
                if not traj.started_at:
                    traj.started_at = ev.timestamp
            elif ev.event_type == "iteration_completed":
                traj.iteration += 1
                if getattr(ev, "delta", None) == 0:
                    traj.status = "converged"
                else:
                    traj.status = "iterating"
            elif ev.event_type == "edge_converged":
                traj.status = "converged"
                traj.converged_at = ev.timestamp
                
        # Overall status derivation
        if feat.trajectory:
            if all(t.status == "converged" for t in feat.trajectory.values()):
                feat.status = "converged"
            else:
                feat.status = "iterating"
                
    return list(features_dict.values())
