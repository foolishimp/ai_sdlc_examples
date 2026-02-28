# Validates: REQ-F-NAV-007
"""Tests for event density heatmap logic."""

from datetime import datetime, timezone, timedelta
from genesis_monitor.models.events import Event
from genesis_monitor.projections.temporal import get_event_density

def test_get_event_density_clumping():
    # 10 events clumped at the start
    start_t = datetime(2026, 1, 1, 12, 0, tzinfo=timezone.utc)
    events = [Event(timestamp=start_t + timedelta(minutes=i), project="test") for i in range(10)]
    # 1 event at the far end
    end_t = start_t + timedelta(hours=10)
    events.append(Event(timestamp=end_t, project="test"))
    
    density = get_event_density(events, buckets=10)
    
    # First bucket should be high (1.0), middle buckets low (0.0), last bucket low (0.1)
    assert density[0] == 1.0
    assert density[5] == 0.0
    assert 0.0 < density[9] < 0.2
