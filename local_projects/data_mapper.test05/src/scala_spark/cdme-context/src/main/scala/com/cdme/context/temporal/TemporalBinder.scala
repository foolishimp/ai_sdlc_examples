package com.cdme.context.temporal
// Implements: REQ-F-PDM-006

import com.cdme.model._
import com.cdme.model.config.Epoch
import com.cdme.model.entity.{Entity, EntityId}
import com.cdme.model.pdm.{PdmBinding, TemporalBindingDef, StorageLocation}
import com.cdme.model.morphism.MorphismId

/**
 * Resolves logical entity references to physical storage locations
 * as a function of epoch.
 *
 * Temporal binding allows a single logical entity to map to different
 * physical locations depending on the processing epoch. For example,
 * `trades` might resolve to `s3://data/trades/2026-02-21/` for a
 * daily epoch, or `s3://data/trades/2026-02/` for a monthly epoch.
 *
 * The binder reads the temporal binding definition from the PDM binding
 * and invokes the resolver function to obtain the epoch-specific
 * storage location.
 */
object TemporalBinder {

  /**
   * Resolve a logical entity's physical binding for a given epoch.
   *
   * If the PDM binding has a temporal binding definition, this method
   * invokes the resolver function to get the epoch-specific storage
   * location. If no temporal binding is defined, the original binding
   * is returned unchanged.
   *
   * @param entity  the logical entity being resolved
   * @param epoch   the processing epoch
   * @param binding the PDM binding (may contain a temporal binding definition)
   * @return a resolved [[PdmBinding]] with epoch-specific storage location,
   *         or a [[TemporalResolutionError]] if resolution fails
   */
  def resolve(
    entity: Entity,
    epoch: Epoch,
    binding: PdmBinding
  ): Either[TemporalResolutionError, PdmBinding] = {
    binding.temporalBinding match {
      case None =>
        // No temporal binding: return the original binding unchanged
        Right(binding)

      case Some(temporalDef) =>
        resolveTemporalLocation(binding, temporalDef, epoch).map { resolvedLocation =>
          binding.copy(location = resolvedLocation)
        }
    }
  }

  /**
   * Invoke the temporal binding resolver to get the epoch-specific storage location.
   */
  private def resolveTemporalLocation(
    binding: PdmBinding,
    temporalDef: TemporalBindingDef,
    epoch: Epoch
  ): Either[TemporalResolutionError, StorageLocation] = {
    try {
      val resolved = temporalDef.resolver(epoch)
      Right(resolved)
    } catch {
      case e: Exception =>
        Left(TemporalResolutionError(
          s"Temporal binding resolution failed for entity '${binding.logicalEntity.value}': ${e.getMessage}",
          binding.logicalEntity,
          epoch
        ))
    }
  }
}

/**
 * Error raised when temporal binding resolution fails.
 *
 * This is a context-specific error type (does not extend the sealed CdmeError trait).
 * Convert to a model-level error at the boundary if needed.
 */
case class TemporalResolutionError(
  message: String,
  entityId: EntityId,
  epoch: Epoch
)
