// Implements: REQ-INT-03, REQ-TRV-04
// Emits OpenLineage START/COMPLETE/FAIL events.
package com.cdme.lineage

import com.cdme.model.error.CdmeError
import com.cdme.compiler.ValidatedPlan
import com.cdme.runtime.{ExecutionContext, AccountingLedger, RunManifest}

/**
 * Emits OpenLineage-compliant events for CDME executions.
 * Implements: REQ-INT-03
 * See ADR-009: OpenLineage Integration
 */
object OpenLineageEmitter {

  /**
   * Emit a START event when execution begins.
   */
  def emitStart(
      context: ExecutionContext,
      plan: ValidatedPlan,
      jobName: String,
      inputs: List[InputDataset],
      outputs: List[OutputDataset]
  ): Either[CdmeError, CdmeLineageEvent] = {
    val event = CdmeLineageEvent(
      eventType = LineageEventType.Start,
      runId = context.runId,
      jobName = jobName,
      timestamp = java.time.Instant.now(),
      inputs = inputs,
      outputs = outputs,
      facets = CdmeFacets(
        typeUnificationReport = None,
        grainSafetyReport = None,
        morphismPathTrace = None,
        costEstimate = Some(plan.costEstimate),
        accountingLedger = None,
        lookupVersionsUsed = context.lookupVersions,
        adjointMetadataLocations = Map.empty,
        runManifest = context.runManifest
      )
    )
    Right(event)
  }

  /**
   * Emit a COMPLETE event when execution succeeds.
   */
  def emitComplete(
      context: ExecutionContext,
      ledger: AccountingLedger,
      jobName: String,
      inputs: List[InputDataset],
      outputs: List[OutputDataset],
      morphismTraces: List[MorphismPathTrace]
  ): Either[CdmeError, CdmeLineageEvent] = {
    val event = CdmeLineageEvent(
      eventType = LineageEventType.Complete,
      runId = context.runId,
      jobName = jobName,
      timestamp = java.time.Instant.now(),
      inputs = inputs,
      outputs = outputs,
      facets = CdmeFacets(
        typeUnificationReport = None,
        grainSafetyReport = Some(GrainSafetyReport(Nil, passed = true)),
        morphismPathTrace = morphismTraces.headOption,
        costEstimate = None,
        accountingLedger = Some(ledger),
        lookupVersionsUsed = context.lookupVersions,
        adjointMetadataLocations = Map.empty,
        runManifest = context.runManifest
      )
    )
    Right(event)
  }

  /**
   * Emit a FAIL event when execution fails.
   */
  def emitFail(
      context: ExecutionContext,
      error: CdmeError,
      jobName: String,
      inputs: List[InputDataset]
  ): Either[CdmeError, CdmeLineageEvent] = {
    val event = CdmeLineageEvent(
      eventType = LineageEventType.Fail,
      runId = context.runId,
      jobName = jobName,
      timestamp = java.time.Instant.now(),
      inputs = inputs,
      outputs = Nil,
      facets = CdmeFacets(
        typeUnificationReport = None,
        grainSafetyReport = None,
        morphismPathTrace = None,
        costEstimate = None,
        accountingLedger = None,
        lookupVersionsUsed = context.lookupVersions,
        adjointMetadataLocations = Map.empty,
        runManifest = context.runManifest
      )
    )
    Right(event)
  }
}
