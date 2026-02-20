// Implements: REQ-F-TRV-003, REQ-F-TRV-004, REQ-F-PDM-003
package cdme.model.context

/**
 * Temporal semantics for data access within a context.
 *
 * Defines how time-varying data is resolved when accessed within an epoch:
 *
 *   - AsOf:   Use the value that was current as of a reference timestamp
 *   - Latest: Use the most recent value available at execution time
 *   - Exact:  Use only the value with an exact timestamp match
 *
 * Temporal semantics affect join compatibility: joins between entities with
 * different temporal semantics require explicit handling.
 */
enum TemporalSemantic:
  /** Value current as of a reference timestamp. Provides point-in-time consistency. */
  case AsOf

  /** Most recent value at execution time. May introduce non-determinism across runs. */
  case Latest

  /** Exact timestamp match. Strictest temporal semantics. */
  case Exact
