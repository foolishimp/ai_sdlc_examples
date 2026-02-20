package com.cdme.model.config

// Implements: REQ-F-ERR-002, REQ-F-TRV-006

import com.cdme.model.pdm.StorageLocation
import com.cdme.model.version.ArtifactVersion

/**
 * Job configuration — the top-level configuration for a CDME pipeline run.
 *
 * JobConfig is frozen at pipeline initialization and never modified during
 * execution. Any configuration change requires a new version and a new run.
 *
 * @param id               unique job identifier (e.g. "daily-risk-aggregation")
 * @param version          artifact version metadata
 * @param lineageMode      the lineage capture mode for this run
 * @param failureThreshold optional batch failure threshold (halt condition)
 * @param cardinalityBudget computational cost governance limits
 * @param artifactRefs     versioned references to all input artifacts (LDM, PDM, mapping, etc.)
 * @param epochConfig      epoch boundary configuration
 * @param sinkConfig       output sink locations (success, error, ledger)
 */
case class JobConfig(
  id: String,
  version: ArtifactVersion,
  lineageMode: LineageMode,
  failureThreshold: Option[FailureThreshold],
  cardinalityBudget: CardinalityBudget,
  artifactRefs: Map[String, ArtifactRef],
  epochConfig: EpochConfig,
  sinkConfig: SinkConfig
)

// ---------------------------------------------------------------------------
// Lineage mode
// ---------------------------------------------------------------------------

/**
 * Controls the level of lineage detail captured during execution.
 *
 * The mode affects checkpoint frequency and storage requirements:
 *  - [[FullLineage]]: capture key envelopes at every morphism
 *  - [[KeyDerivableLineage]]: capture only at graph boundaries and lossy morphisms
 *  - [[SampledLineage]]: capture a probabilistic sample
 */
sealed trait LineageMode

/** Capture complete lineage — key envelopes at every morphism. */
case object FullLineage extends LineageMode

/** Capture lineage only at graph boundaries and after lossy morphisms. */
case object KeyDerivableLineage extends LineageMode

/**
 * Capture a probabilistic sample of lineage data.
 *
 * @param rate sampling rate in [0.0, 1.0] (e.g. 0.01 = 1% of records)
 */
case class SampledLineage(rate: Double) extends LineageMode

// ---------------------------------------------------------------------------
// Failure threshold
// ---------------------------------------------------------------------------

/**
 * Configures when the pipeline should halt due to excessive errors.
 *
 * Only one mode is active per job (not both simultaneously).
 */
sealed trait FailureThreshold

/**
 * Halt when the absolute number of errors exceeds the threshold.
 *
 * @param maxErrors the maximum number of errors before halting
 */
case class AbsoluteThreshold(maxErrors: Long) extends FailureThreshold

/**
 * Halt when the error percentage exceeds the threshold within a sample window.
 *
 * @param maxPercent   the maximum error percentage (e.g. 5.0 = 5%)
 * @param sampleWindow the number of records to sample for percentage calculation
 */
case class PercentageThreshold(maxPercent: Double, sampleWindow: Int) extends FailureThreshold

// ---------------------------------------------------------------------------
// Cardinality budget
// ---------------------------------------------------------------------------

/**
 * Computational cost governance limits.
 *
 * The compiler uses these limits to reject plans that would exceed
 * the budgeted resource consumption.
 *
 * @param maxOutputRows       maximum total output records
 * @param maxJoinDepth        maximum depth of chained joins
 * @param maxIntermediateSize maximum intermediate dataset size (estimated rows)
 */
case class CardinalityBudget(
  maxOutputRows: Long,
  maxJoinDepth: Int,
  maxIntermediateSize: Long
)

// ---------------------------------------------------------------------------
// Artifact references
// ---------------------------------------------------------------------------

/**
 * A versioned reference to an external artifact (LDM, PDM, mapping, etc.).
 *
 * @param path    file path or URI to the artifact
 * @param version version string of the referenced artifact
 */
case class ArtifactRef(path: String, version: String)

// ---------------------------------------------------------------------------
// Epoch configuration
// ---------------------------------------------------------------------------

/**
 * Configuration for epoch boundary resolution.
 *
 * @param boundaryType the boundary type (e.g. "daily", "monthly")
 * @param parameters   boundary-specific parameters (e.g. date, timezone)
 */
case class EpochConfig(
  boundaryType: String,
  parameters: Map[String, String]
)

// ---------------------------------------------------------------------------
// Sink configuration
// ---------------------------------------------------------------------------

/**
 * Output sink locations for pipeline results.
 *
 * Every pipeline run produces three output streams:
 *  - Success: records that passed all validations
 *  - Error: records that failed (routed to Error Domain)
 *  - Ledger: the accounting ledger proving zero-loss invariant
 *
 * @param successSink location for successfully processed records
 * @param errorSink   location for error domain records
 * @param ledgerSink  location for the accounting ledger
 */
case class SinkConfig(
  successSink: StorageLocation,
  errorSink: StorageLocation,
  ledgerSink: StorageLocation
)
