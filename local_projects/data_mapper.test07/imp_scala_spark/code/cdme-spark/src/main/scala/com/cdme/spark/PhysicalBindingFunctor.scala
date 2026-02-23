// Implements: REQ-PDM-01, REQ-PDM-02, REQ-PDM-03
// The LDM-to-PDM functor: structure-preserving map from logical to physical.
package com.cdme.spark

import com.cdme.model._
import com.cdme.model.error.{CdmeError, ConfigError}
import com.cdme.compiler.PhysicalTarget

/**
 * The Physical Binding Functor: maps logical entities to physical data sources.
 * This preserves:
 * - Objects: Entity -> data source with matching schema
 * - Morphisms: Logical transforms -> physical operations
 * - Identity: Id_E maps to identity transformation
 * - Composition: g . f maps to operation composition
 *
 * Implements: REQ-PDM-01
 * See ADR-006: Spark Integration Strategy
 */
object PhysicalBindingFunctor {

  /**
   * Bind a logical entity to a physical target.
   * Returns the binding descriptor that can be used to load data.
   */
  def bind(
      entity: Entity,
      target: PhysicalTarget,
      epoch: String
  ): Either[CdmeError, PhysicalBinding] = {
    // Validate the entity schema can be mapped to Spark types
    SparkTypeMapper.mapEntitySchema(entity.attributes).map { sparkSchema =>
      PhysicalBinding(
        entityId = entity.id,
        entityName = entity.name,
        physicalPath = target.path,
        format = target.format,
        sparkSchema = sparkSchema,
        epochFilter = epoch,
        options = target.options
      )
    }
  }

  /**
   * Validate that a physical target has the correct format.
   */
  def validateTarget(target: PhysicalTarget): Either[CdmeError, Unit] = {
    val validFormats = Set("parquet", "csv", "json", "delta", "iceberg", "jdbc", "avro")
    if (validFormats.contains(target.format.toLowerCase)) Right(())
    else Left(ConfigError(s"Unsupported physical format: '${target.format}'. Valid: ${validFormats.mkString(", ")}"))
  }
}

/** A resolved binding from logical entity to physical data source. */
final case class PhysicalBinding(
    entityId: EntityId,
    entityName: String,
    physicalPath: String,
    format: String,
    sparkSchema: Map[String, SparkTypeMapper.SparkType],
    epochFilter: String,
    options: Map[String, String]
)

/**
 * Generation grain: the "physics" of the source system's data generation.
 * Implements: REQ-PDM-02
 */
sealed trait GenerationGrain
object GenerationGrain {
  case object Event    extends GenerationGrain
  case object Snapshot extends GenerationGrain
}

/**
 * Boundary definition: how continuous data flow is sliced into epochs.
 * Implements: REQ-PDM-03
 */
sealed trait BoundaryDefinition
object BoundaryDefinition {
  final case class TemporalWindow(start: java.time.Instant, end: java.time.Instant) extends BoundaryDefinition
  final case class VersionBased(version: String)  extends BoundaryDefinition
  final case class BatchId(batchId: String)        extends BoundaryDefinition
}

/**
 * Temporal binding: single logical entity maps to different physical tables by epoch.
 * Implements: REQ-PDM-05
 */
final case class TemporalBinding(
    logicalEntityId: EntityId,
    bindings: Map[String, PhysicalTarget]
)

/**
 * Late arrival strategy.
 * Implements: REQ-PDM-06
 */
sealed trait LateArrivalStrategy
object LateArrivalStrategy {
  case object Reject     extends LateArrivalStrategy
  case object Reprocess  extends LateArrivalStrategy
  case object Accumulate extends LateArrivalStrategy
  case object Backfill   extends LateArrivalStrategy
}
