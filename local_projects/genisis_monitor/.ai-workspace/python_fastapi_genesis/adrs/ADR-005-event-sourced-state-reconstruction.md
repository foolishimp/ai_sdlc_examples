# ADR-005: Event-Sourced State Reconstruction

**Status**: Accepted
**Date**: 2026-02-27

## Context
We need to provide a temporal navigator that allows users to view the methodology state at any past timestamp `T`.

## Decision
Instead of periodically snapshotting state, we will leverage the immutable, append-only nature of the `events.jsonl` log. To view the state at time `T`, we will simply filter the loaded event stream to only include events where `timestamp <= T`, and then feed this filtered stream into our existing projection logic.

## Consequences
- **Positive**: Zero data duplication. Guarantees 100% accurate historical representation.
- **Negative**: Performance may degrade if the event log becomes massively large (10,000+ events) and the user scrubs rapidly. We may need to introduce memoization or state-caching in the future if this occurs.
