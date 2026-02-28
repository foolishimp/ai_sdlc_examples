# Design: Static Global Control Plane & Zoomable Scrubber

**Version**: 1.0.0
**Date**: 2026-02-27
**Implements**: REQ-F-NAV-005, REQ-F-NAV-006, REQ-F-NAV-007

---

## Architecture Overview
The Global Control Plane moves temporal navigation from a per-page component to a persistent framework element. It introduces "Windowed State Reconstruction," allowing users to see not just a snapshot at time T, but the delta of changes that occurred between T-start and T-end.

## Component Design

### Component: Global Control Footer (Frontend)
**Implements**: REQ-F-NAV-005
**Responsibilities**: Provide a persistent UI at the bottom of the screen.
**Design**:
- A `<footer>` element with `position: fixed; bottom: 0; width: 100%;`.
- High-contrast "Executive" theme (dark background, primary color accents).
- Contains: Project Version, Scrubber Range, Total Event Count, and the Scrubber itself.

### Component: Dual-Handle Zoom Scrubber (Frontend)
**Implements**: REQ-F-NAV-006
**Responsibilities**: Allow selecting a start and end event index.
**Design**:
- Implemented using two overlapping `<input type="range">` elements with CSS pointer-event passthrough logic.
- Displays the date/time for both the Start and End handles.
- Triggers HTMX reloads with `?start_t={ts1}&end_t={ts2}`.

### Component: Event Density Heatmap (Backend & Frontend)
**Implements**: REQ-F-NAV-007
**Responsibilities**: Visualize clusters of high methodology activity.
**Design**:
- **Backend**: Computes an "activity score" per percentage-bucket of the project timeline.
- **Frontend**: Renders a `linear-gradient` as the background of the scrubber track, where color intensity matches event density.

## Traceability Matrix
| REQ Key | Component |
|---------|----------|
| REQ-F-NAV-005 | Global Control Footer |
| REQ-F-NAV-006 | Dual-Handle Zoom Scrubber |
| REQ-F-NAV-007 | Event Density Heatmap |

## ADR Index
- [ADR-006: Dual-Handle Range Slider Implementation](adrs/ADR-006-dual-range-slider.md)
