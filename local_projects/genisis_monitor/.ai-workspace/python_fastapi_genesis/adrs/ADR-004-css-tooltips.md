# ADR-004: CSS-Only Tooltips

**Status**: Accepted
**Date**: 2026-02-27

## Context
We need to add explanatory hovers to the dashboard without introducing heavy JavaScript dependencies, maintaining our zero-build-step requirement (REQ-NFR-004).

## Decision
Implement tooltips using pure CSS with the `:hover` pseudo-class and `data-*` attributes or visually hidden `<span>` elements.

## Consequences
- Maintains the lightweight footprint of the application.
- Limits complex interactive tooltip behaviors (like click-to-pin), but satisfies the immediate requirement for explanatory text.
