package com.cdme.model.pdm

// Implements: REQ-F-PDM-001, REQ-F-PDM-002, REQ-F-PDM-003, REQ-F-PDM-004, REQ-F-PDM-005, REQ-F-PDM-006

import com.cdme.model.config.Epoch
import com.cdme.model.entity.EntityId
import com.cdme.model.morphism.MorphismId
import com.cdme.model.version.ArtifactVersion

// ---------------------------------------------------------------------------
// Generation grain — how data is physically produced
// ---------------------------------------------------------------------------

/**
 * Declares how data is generated for a physical binding.
 *
 * Generation grain affects how the runtime reads data within an epoch:
 *  - [[EventGrain]]: each record is an individual event (append-only)
 *  - [[SnapshotGrain]]: each partition is a point-in-time snapshot (overwrite)
 */
sealed trait GenerationGrain

/** Data is generated as individual events (append-only). */
case object EventGrain extends GenerationGrain

/** Data is generated as point-in-time snapshots (full replacement). */
case object SnapshotGrain extends GenerationGrain

// ---------------------------------------------------------------------------
// Storage locations — the physical "where"
// ---------------------------------------------------------------------------

/**
 * Physical storage location for an entity's data.
 *
 * The sealed hierarchy covers the supported storage backends.
 * Additional backends can be added by extending this trait.
 */
sealed trait StorageLocation

/**
 * Apache Parquet files at a filesystem or object-store path.
 *
 * @param path the directory path (local, HDFS, S3, GCS, etc.)
 */
case class ParquetLocation(path: String) extends StorageLocation

/**
 * Delta Lake table at a filesystem or object-store path.
 *
 * @param path the directory path for the Delta table
 */
case class DeltaLocation(path: String) extends StorageLocation

/**
 * A table accessible via JDBC.
 *
 * @param url   JDBC connection URL
 * @param table fully-qualified table name
 */
case class JdbcLocation(url: String, table: String) extends StorageLocation

/**
 * CSV files at a filesystem path.
 *
 * @param path      the directory or file path
 * @param delimiter the field delimiter (default: comma)
 */
case class CsvLocation(path: String, delimiter: String = ",") extends StorageLocation

// ---------------------------------------------------------------------------
// Boundary definition — epoch scoping
// ---------------------------------------------------------------------------

/**
 * Defines how processing boundaries (epochs) are determined.
 *
 * @param boundaryType the boundary type (e.g. "daily", "monthly", "custom")
 * @param parameters   type-specific parameters (e.g. date format, timezone)
 */
case class BoundaryDef(
  boundaryType: String,
  parameters: Map[String, String]
)

// ---------------------------------------------------------------------------
// Temporal binding — location as a function of epoch
// ---------------------------------------------------------------------------

/**
 * A temporal binding resolves a logical entity to a physical location
 * that varies with the processing epoch.
 *
 * For example, a daily partition layout where the path includes the date:
 * `s3://bucket/trades/date=2024-01-15/`.
 *
 * @param resolver a function from epoch to storage location
 */
case class TemporalBindingDef(resolver: Epoch => StorageLocation)

// ---------------------------------------------------------------------------
// Lookup bindings — reference data resolution
// ---------------------------------------------------------------------------

/**
 * Defines how lookup (reference data) is resolved.
 *
 * Lookups are versioned within a single execution context to ensure
 * consistency: the same key + same lookup = same value throughout a run.
 */
sealed trait LookupBinding

/**
 * A lookup backed by persisted data at a storage location.
 *
 * @param location the physical location of the lookup data
 * @param keyField the field name used as the lookup key
 */
case class DataBackedLookup(
  location: StorageLocation,
  keyField: String
) extends LookupBinding

/**
 * A lookup backed by a morphism computation.
 *
 * The lookup value is computed by evaluating the referenced morphism
 * rather than reading from storage.
 *
 * @param morphismId the morphism that computes the lookup value
 */
case class LogicBackedLookup(morphismId: MorphismId) extends LookupBinding

// ---------------------------------------------------------------------------
// PDM Binding — the full physical binding
// ---------------------------------------------------------------------------

/**
 * A Physical Data Model binding maps a logical entity to its physical
 * storage, including generation semantics, epoch boundary, and optional
 * temporal binding.
 *
 * @param logicalEntity   the logical entity this binding refers to
 * @param location        the default physical storage location
 * @param generationGrain whether data is event-based or snapshot-based
 * @param boundary        how processing epochs are defined
 * @param temporalBinding optional epoch-dependent location resolver
 * @param version         artifact version metadata
 */
case class PdmBinding(
  logicalEntity: EntityId,
  location: StorageLocation,
  generationGrain: GenerationGrain,
  boundary: BoundaryDef,
  temporalBinding: Option[TemporalBindingDef] = None,
  version: ArtifactVersion
)
