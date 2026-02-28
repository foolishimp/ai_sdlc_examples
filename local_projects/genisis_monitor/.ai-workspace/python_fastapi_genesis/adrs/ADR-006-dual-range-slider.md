# ADR-006: Dual-Handle Range Slider Implementation

**Status**: Accepted
**Date**: 2026-02-27

## Context
We need a dual-handle range slider to support "Time Window" zooming (REQ-F-NAV-006). The standard HTML `<input type="range">` only supports a single handle.

## Decision
We will implement a custom range slider by layering two standard `<input type="range">` elements on top of each other using CSS Absolute positioning. We will use a small amount of JavaScript to ensure the handles do not cross and to update the visual "track" between them.

## Consequences
- **Positive**: Zero external dependencies. Maintains REQ-NFR-004 (Zero build step).
- **Negative**: Slightly more complex CSS/JS than a standard input, but highly portable.
