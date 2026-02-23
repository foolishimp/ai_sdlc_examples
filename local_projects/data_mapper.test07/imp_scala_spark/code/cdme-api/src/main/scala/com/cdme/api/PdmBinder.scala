// Implements: REQ-PDM-01, REQ-PDM-04
// Binds LDM to physical targets.
package com.cdme.api

import com.cdme.model._
import com.cdme.model.error.{CdmeError, ConfigError}
import com.cdme.compiler.{PhysicalDataModel, PhysicalTarget, LookupBinding}

/**
 * Builder for physical data model bindings.
 * Maps logical entities to physical storage targets.
 *
 * Implements: REQ-PDM-01, REQ-PDM-04
 * See ADR-012: Public API Design
 */
final case class PdmBinder(
    bindings: Map[EntityId, PhysicalTarget],
    lookups: Map[LookupId, LookupBinding]
) {

  /** Bind a logical entity to a physical target. */
  def bindEntity(
      entityId: EntityId,
      format: String,
      path: String,
      options: Map[String, String] = Map.empty
  ): PdmBinder = {
    val target = PhysicalTarget(format, path, options)
    copy(bindings = bindings + (entityId -> target))
  }

  /** Add a lookup binding. */
  def withLookup(
      lookupId: LookupId,
      format: String,
      path: String,
      version: LookupVersion
  ): PdmBinder = {
    val target = PhysicalTarget(format, path, Map.empty)
    val binding = LookupBinding(lookupId, target, version)
    copy(lookups = lookups + (lookupId -> binding))
  }

  /**
   * Build the physical data model.
   * Validates that all bindings have valid formats.
   */
  def build(): Either[CdmeError, PhysicalDataModel] = {
    val validFormats = Set("parquet", "csv", "json", "delta", "iceberg", "jdbc", "avro")
    val invalidBindings = bindings.filter { case (_, target) =>
      !validFormats.contains(target.format.toLowerCase)
    }
    if (invalidBindings.nonEmpty) {
      Left(ConfigError(
        s"Invalid physical format(s): ${invalidBindings.map(_._2.format).mkString(", ")}"
      ))
    } else {
      Right(PhysicalDataModel(bindings, lookups))
    }
  }
}

object PdmBinder {
  def apply(): PdmBinder = PdmBinder(Map.empty, Map.empty)
}
