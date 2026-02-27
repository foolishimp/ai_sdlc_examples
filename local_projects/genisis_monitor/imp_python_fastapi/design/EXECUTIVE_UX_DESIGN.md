# Design: Executive UX Polish & Drill-Down

**Version**: 1.0.0
**Date**: 2026-02-27
**Implements**: REQ-F-UX-006, REQ-F-DASH-007, REQ-F-DASH-008, REQ-F-UX-007

---

## Architecture Overview
The executive UX enhancements build upon the existing FastAPI + HTMX architecture. We will introduce HTMX-driven modal/drawer interactions for drill-downs, CSS-only tooltip structures for explanatory hovers, and timestamp injection at the Jinja2 template level.

## Component Design

### Component: Tooltip System (Frontend)
**Implements**: REQ-F-DASH-007
**Responsibilities**: Provide zero-JS, CSS-driven tooltips across the dashboard.
**Design**: Add a `.help-hover` CSS class that reveals a hidden `span` on `:hover`. Inject these next to complex metrics like Convergence Velocity or Phase Completion.

### Component: Template Timestamp Injector
**Implements**: REQ-F-DASH-008
**Responsibilities**: Append the current server time to all HTMX partial responses.
**Design**: Update the Jinja2 base fragment to include a `<div class="timestamp">Last updated: {{ current_time }}</div>` element.

### Component: Interactive Gantt (Frontend + Backend)
**Implements**: REQ-F-UX-006
**Responsibilities**: Allow clicking on Mermaid Gantt bars to fetch edge details.
**Design**:
- Configure Mermaid.js to emit click events.
- Add a JS listener that intercepts the click, extracts the edge ID, and triggers an `htmx.ajax('GET', '/edge-details/' + edgeId)` call.
- Backend provides the `/edge-details/{edge_id}` endpoint returning the iteration history fragment.

### Component: Data Lineage Viewer (Backend)
**Implements**: REQ-F-UX-007
**Responsibilities**: Expose the raw YAML/JSONL data backing a specific view.
**Design**:
- Add a `/lineage/source/{artifact_type}/{id}` endpoint.
- Render a styled `<pre><code>` block containing the raw file contents.
- Bind this to a "Show Source" button on the relevant UI cards.

## Traceability Matrix
| REQ Key | Component |
|---------|----------|
| REQ-F-DASH-007 | Tooltip System |
| REQ-F-DASH-008 | Template Timestamp Injector |
| REQ-F-UX-006 | Interactive Gantt |
| REQ-F-UX-007 | Data Lineage Viewer |

## ADR Index
- [ADR-004: CSS-Only Tooltips](adrs/ADR-004-css-tooltips.md)
