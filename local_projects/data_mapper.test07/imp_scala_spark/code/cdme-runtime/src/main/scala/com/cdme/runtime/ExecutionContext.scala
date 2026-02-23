// Implements: REQ-TRV-05, REQ-TRV-05-A, REQ-SHF-01
// Execution context: the sheaf-like scoping for a processing run.
package com.cdme.runtime

import com.cdme.model._

/**
 * Execution context: immutable snapshot of everything needed for a run.
 * Implements: REQ-TRV-05, REQ-SHF-01
 */
final case class ExecutionContext(
    runId: RunId,
    epoch: Epoch,
    lookupVersions: Map[LookupId, LookupVersion],
    lineageMode: LineageMode,
    failureThreshold: FailureThreshold,
    dryRun: Boolean,
    runManifest: RunManifest
)

/** Epoch: the temporal window for a processing run. */
final case class Epoch(
    id: EpochId,
    start: java.time.Instant,
    end: java.time.Instant
)

/**
 * Immutable run manifest: binds a run to its exact artifacts.
 * Implements: REQ-TRV-05-A
 */
final case class RunManifest(
    runId: RunId,
    configHash: String,
    codeHash: String,
    designHash: String,
    timestamp: java.time.Instant,
    inputChecksums: Map[String, String],
    outputChecksums: Map[String, String]
)

/**
 * Lineage mode: controls lineage capture granularity.
 * Implements: RIC-LIN-01
 */
sealed trait LineageMode
object LineageMode {
  case object Full        extends LineageMode
  case object KeyDerivable extends LineageMode
  case object Sampled     extends LineageMode
}

/**
 * Failure threshold: when to halt execution.
 * Implements: REQ-TYP-03-A
 */
sealed trait FailureThreshold
object FailureThreshold {
  final case class AbsoluteCount(maxErrors: Long) extends FailureThreshold
  final case class Percentage(maxPercent: Double)  extends FailureThreshold
  case object NoThreshold                          extends FailureThreshold
}
