// Implements: REQ-F-PDM-002
package cdme.pdm

/**
 * Generation grain semantics for physical data sources.
 *
 * Defines how a physical source produces data:
 *   - Event: each record is an immutable event (append-only)
 *   - Snapshot: each record represents state at a point in time (overwritable)
 */
enum GenerationGrain:
  /** Immutable events. Each record is a fact that happened. */
  case Event

  /** Point-in-time state snapshots. Records may change between snapshots. */
  case Snapshot
