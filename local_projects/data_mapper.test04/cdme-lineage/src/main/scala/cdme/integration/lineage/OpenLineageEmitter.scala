// Implements: REQ-NFR-OBS-001
package cdme.integration.lineage

import cdme.runtime.{VerifiedLedger, ExecutionResult}

/**
 * Run context for OpenLineage events.
 *
 * @param runId     unique run identifier
 * @param jobName   the pipeline/job name
 * @param namespace the OpenLineage namespace
 */
final case class RunContext(
    runId: String,
    jobName: String,
    namespace: String
)

/**
 * Failure details for FAIL events.
 *
 * @param message     error message
 * @param errorType   classification of the failure
 * @param stackTrace  optional stack trace
 */
final case class FailureDetails(
    message: String,
    errorType: String,
    stackTrace: Option[String] = None
)

/**
 * OpenLineage-compliant event emitter.
 *
 * Emits START, COMPLETE, and FAIL events with dataset facets and
 * CDME-specific custom facets (grain, type, adjoint, accounting).
 *
 * Events are emitted asynchronously (no pipeline latency impact).
 * Emission failure does not fail the pipeline (fire-and-forget with retry).
 *
 * TODO: Implement with OpenLineage SDK or HTTP client.
 */
trait OpenLineageEmitter {

  /**
   * Emit a START event.
   *
   * @param run the run context
   */
  def emitStart(run: RunContext): Unit

  /**
   * Emit a COMPLETE event.
   *
   * @param run    the run context
   * @param ledger the verified accounting ledger
   */
  def emitComplete(run: RunContext, ledger: VerifiedLedger): Unit

  /**
   * Emit a FAIL event.
   *
   * @param run     the run context
   * @param failure the failure details
   */
  def emitFail(run: RunContext, failure: FailureDetails): Unit
}

/**
 * No-op implementation for testing and environments without OpenLineage.
 */
class NoOpOpenLineageEmitter extends OpenLineageEmitter {
  override def emitStart(run: RunContext): Unit = ()
  override def emitComplete(run: RunContext, ledger: VerifiedLedger): Unit = ()
  override def emitFail(run: RunContext, failure: FailureDetails): Unit = ()
}
