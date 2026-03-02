# Validates: REQ-F-PARSE-004, REQ-F-EVSCHEMA-001
"""Shared helper for creating OpenLineage v2 events in tests.

All tests that write events to disk should use make_ol2_event() instead of
hand-crafting v1-style dicts.  The parser only processes events that have an
'eventType' key (OL v2 format per ADR-S-011).
"""

import uuid

# OL eventType mapping for methodology event types
_OL2_ET: dict[str, str] = {
    "edge_started": "START",
    "edge_converged": "COMPLETE",
    "iteration_abandoned": "ABORT",
    "command_error": "FAIL",
}


def make_ol2_event(
    event_type: str,
    timestamp: str = "2026-02-23T08:00:00Z",
    project: str = "test",
    edge: str | None = None,
    feature: str | None = None,
    delta: int | None = None,
    **fields,
) -> dict:
    """Create an OL v2 event dict from methodology event fields.

    Parameters
    ----------
    event_type:
        Methodology event type string (e.g. "edge_started", "iteration_completed").
    timestamp:
        ISO 8601 timestamp string.  A trailing "Z" is added automatically if absent.
    project:
        Project name — written to _metadata.project and job.namespace.
    edge:
        Edge identifier (e.g. "design→code").  Goes into sdlc:req_keys.edge and
        original_data.edge.
    feature:
        Feature vector ID (e.g. "REQ-F-001").  Goes into sdlc:req_keys.feature_id
        and original_data.feature.
    delta:
        Numeric convergence delta.  Goes into sdlc:delta.delta and original_data.delta.
    **fields:
        Any additional event-type-specific fields — placed in original_data.
    """
    ol_type = _OL2_ET.get(event_type, "OTHER")

    facets: dict = {
        "sdlc:event_type": {
            "_producer": "test",
            "_schemaURL": "test",
            "type": event_type,
        }
    }

    if edge or feature:
        rk: dict = {"_producer": "test", "_schemaURL": "test"}
        if edge:
            rk["edge"] = edge
        if feature:
            rk["feature_id"] = feature
        facets["sdlc:req_keys"] = rk

    if delta is not None:
        facets["sdlc:delta"] = {
            "_producer": "test",
            "_schemaURL": "test",
            "delta": delta,
        }

    # Build original_data: all the methodology fields go here so the parser
    # can reconstruct typed events via the orig-loop in _parse_one().
    original_data: dict = {"event_type": event_type, "project": project}
    if edge:
        original_data["edge"] = edge
    if feature:
        original_data["feature"] = feature
    if delta is not None:
        original_data["delta"] = delta
    original_data.update(fields)

    ts = timestamp if timestamp.endswith("Z") else timestamp + "Z"

    return {
        "eventType": ol_type,
        "eventTime": ts,
        "run": {
            "runId": str(uuid.uuid4()),
            "facets": facets,
        },
        "job": {
            "namespace": f"aisdlc://{project}",
            "name": edge or "METHODOLOGY",
        },
        "_metadata": {
            "project": project,
            "original_data": original_data,
        },
    }
