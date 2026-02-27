# Implements: REQ-F-NAV-003
"""Tests for the temporal state reconstruction engine."""

from datetime import datetime, timezone
from genesis_monitor.models.events import (
    FeatureSpawnedEvent, EdgeStartedEvent, 
    IterationCompletedEvent, EdgeConvergedEvent
)
from genesis_monitor.projections.temporal import reconstruct_features

def test_reconstruct_features_basic():
    # Setup mock events
    t1 = datetime(2026, 1, 1, 12, 0, tzinfo=timezone.utc)
    t2 = datetime(2026, 1, 1, 12, 5, tzinfo=timezone.utc)
    t3 = datetime(2026, 1, 1, 12, 10, tzinfo=timezone.utc)
    t4 = datetime(2026, 1, 1, 12, 15, tzinfo=timezone.utc)
    
    events = [
        FeatureSpawnedEvent(timestamp=t1, event_type="feature_spawned", project="test", child_vector="REQ-001", data={"feature": "REQ-001", "vector_type": "feature"}),
        EdgeStartedEvent(timestamp=t2, event_type="edge_started", project="test", feature="REQ-001", edge="intent→requirements"),
        IterationCompletedEvent(timestamp=t3, event_type="iteration_completed", project="test", feature="REQ-001", edge="intent→requirements", delta=0),
        EdgeConvergedEvent(timestamp=t4, event_type="edge_converged", project="test", feature="REQ-001", edge="intent→requirements")
    ]
    
    # 1. State at t1: Feature exists, pending
    f_t1 = reconstruct_features(events, t1)
    assert len(f_t1) == 1
    assert f_t1[0].feature_id == "REQ-001"
    assert f_t1[0].status == "iterating" or f_t1[0].status == "pending"
    
    # 2. State at t2: Edge iterating
    f_t2 = reconstruct_features(events, t2)
    assert f_t2[0].trajectory["requirements"].status == "iterating"
    
    # 3. State at t4: Edge converged
    f_t4 = reconstruct_features(events, t4)
    assert f_t4[0].trajectory["requirements"].status == "converged"
    assert f_t4[0].status == "converged"
