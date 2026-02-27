# Design: Interactive Temporal Navigator

**Version**: 1.0.0
**Date**: 2026-02-27
**Implements**: REQ-F-NAV-001, REQ-F-NAV-002, REQ-F-NAV-003, REQ-F-NAV-004

---

## Architecture Overview
The Interactive Temporal Navigator allows users to travel back in time to inspect the state of a methodology project at any given point. Because the methodology is event-sourced (`events.jsonl` is an append-only log), we can reconstruct the exact state (feature trajectories, graph status, completion status) by replaying events up to a specific timestamp `T`.

## Component Design

### Component: Event Log Replay Engine (Backend)
**Implements**: REQ-F-NAV-001, REQ-F-NAV-003
**Responsibilities**: Replay the `events.jsonl` up to a given timestamp to reconstruct feature trajectories and project status.
**Design**: 
- Introduce a `timestamp_limit` parameter to the `load_events` and projection building functions (`build_project_tree`, `get_feature_status`).
- When a `timestamp_limit` is provided, filter out all events occurring after that timestamp before passing the event list to the `Projector`.

### Component: Temporal Scrubber UI (Frontend)
**Implements**: REQ-F-NAV-002
**Responsibilities**: Provide a visual slider to navigate the project timeline.
**Design**:
- An HTML `<input type="range">` element at the top of the project detail dashboard.
- The `min` and `max` values are derived from the first and last event timestamps in the project's history.
- Using HTMX, sliding the scrubber triggers an `hx-get` to the fragment endpoints, appending `?t={selected_timestamp}` to the query string.

### Component: Causal Event Tracing Projection (Backend & Frontend)
**Implements**: REQ-F-NAV-004
**Responsibilities**: Visually link events based on their causal relationships.
**Design**:
- A new endpoint `fragments/project/{project_id}/event-trace/{event_id}`.
- Extracts `prior_intents` and `spawned_by` fields to build a Mermaid graph representing the chain of causality leading to and from the selected event.

## Traceability Matrix
| REQ Key | Component |
|---------|----------|
| REQ-F-NAV-001 | Event Log Replay Engine |
| REQ-F-NAV-002 | Temporal Scrubber UI |
| REQ-F-NAV-003 | Event Log Replay Engine |
| REQ-F-NAV-004 | Causal Event Tracing Projection |

## ADR Index
- [ADR-005: Event-Sourced State Reconstruction](adrs/ADR-005-event-sourced-state-reconstruction.md)
