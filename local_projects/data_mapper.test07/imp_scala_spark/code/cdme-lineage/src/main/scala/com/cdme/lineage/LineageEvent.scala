// Implements: REQ-INT-03, REQ-TRV-04, REQ-AI-02
// OpenLineage event types with CDME-specific facets.
package com.cdme.lineage

import com.cdme.model._
import com.cdme.runtime.{AccountingLedger, RunManifest}
import com.cdme.compiler.CostEstimate

/**
 * CDME lineage event: OpenLineage-compliant with custom facets.
 * Every execution emits START, COMPLETE, or FAIL events.
 *
 * Implements: REQ-INT-03, REQ-AI-02
 * See ADR-009: OpenLineage Integration
 */
final case class CdmeLineageEvent(
    eventType: LineageEventType,
    runId: RunId,
    jobName: String,
    timestamp: java.time.Instant,
    inputs: List[InputDataset],
    outputs: List[OutputDataset],
    facets: CdmeFacets
)

/** Event type enum. */
sealed trait LineageEventType
object LineageEventType {
  case object Start    extends LineageEventType
  case object Complete extends LineageEventType
  case object Fail     extends LineageEventType
}

/** Input dataset reference. */
final case class InputDataset(
    namespace: String,
    name: String,
    facets: Map[String, String]
)

/** Output dataset reference. */
final case class OutputDataset(
    namespace: String,
    name: String,
    facets: Map[String, String]
)

/**
 * CDME-specific facets extending OpenLineage.
 * Implements: REQ-INT-03, REQ-AI-02
 */
final case class CdmeFacets(
    typeUnificationReport: Option[TypeUnificationReport],
    grainSafetyReport: Option[GrainSafetyReport],
    morphismPathTrace: Option[MorphismPathTrace],
    costEstimate: Option[CostEstimate],
    accountingLedger: Option[AccountingLedger],
    lookupVersionsUsed: Map[LookupId, LookupVersion],
    adjointMetadataLocations: Map[MorphismId, String],
    runManifest: RunManifest
)

/** Type unification report for lineage. */
final case class TypeUnificationReport(
    morphismId: MorphismId,
    codomainType: String,
    domainType: String,
    result: String
)

/** Grain safety report for lineage. */
final case class GrainSafetyReport(
    violations: List[String],
    passed: Boolean
)

/** Morphism path trace for lineage. */
final case class MorphismPathTrace(
    targetAttribute: String,
    morphismPath: List[MorphismId],
    sourceEntities: List[EntityId]
)

/** Quality statistics for telemetry. */
final case class QualityStats(
    nullCount: Long,
    distinctCount: Long,
    minValue: Option[String],
    maxValue: Option[String]
)

/** Key envelope for residue collection. */
final case class KeyEnvelope(
    keyField: String,
    keyValues: Set[String],
    classification: String
)
