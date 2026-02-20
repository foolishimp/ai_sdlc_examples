package com.cdme.context.epoch
// Implements: REQ-F-PDM-004

import com.cdme.model._
import com.cdme.model.config.Epoch
import com.cdme.model.pdm.BoundaryDef
import com.cdme.model.entity.EntityId
import com.cdme.model.morphism.MorphismId

/**
 * Manages epoch lifecycle: definition, resolution, and scoping.
 *
 * An epoch defines the temporal boundary of a processing run. The epoch
 * manager resolves a [[BoundaryDef]] (e.g., "daily", "monthly") with
 * runtime parameters (e.g., `date -> "2026-02-21"`) into a concrete
 * [[Epoch]] value.
 *
 * Epochs are immutable once resolved. They scope all reads, writes,
 * and lookup version pins within a single execution.
 */
object EpochManager {

  /**
   * Resolve a boundary definition with runtime parameters into a concrete epoch.
   *
   * @param boundary the boundary definition (e.g., daily, monthly, custom)
   * @param params   runtime parameters that bind template variables in the boundary
   * @return a resolved [[Epoch]] on success, or an [[EpochResolutionError]] on failure
   */
  def resolve(
    boundary: BoundaryDef,
    params: Map[String, String]
  ): Either[EpochResolutionError, Epoch] = {
    // Validate that all required parameters from the boundary definition are present.
    // The boundary's own `parameters` map defines keys that must be supplied at runtime.
    val requiredKeys = boundary.parameters.keys.toSet
    val missingParams = requiredKeys.filterNot(params.contains)
    if (missingParams.nonEmpty) {
      return Left(EpochResolutionError(
        s"Missing required epoch parameters: ${missingParams.mkString(", ")}",
        boundary,
        params
      ))
    }

    // Resolve the boundary into epoch metadata using supplied params
    val resolvedParams = requiredKeys.map { key =>
      key -> params(key)
    }.toMap

    // Generate a deterministic epoch ID from the boundary type and resolved params
    val epochId = s"${boundary.boundaryType}_${resolvedParams.values.mkString("_")}"

    Right(Epoch(
      id = epochId,
      start = java.time.Instant.EPOCH,  // Placeholder; real implementation resolves from boundary type
      end = java.time.Instant.EPOCH,     // Placeholder; real implementation resolves from boundary type
      parameters = resolvedParams
    ))
  }

  /**
   * Check whether two epochs represent the same processing window.
   *
   * @param a first epoch
   * @param b second epoch
   * @return true if the epochs are structurally equal
   */
  def isSameEpoch(a: Epoch, b: Epoch): Boolean = a == b
}

/**
 * Error raised when epoch resolution fails.
 *
 * This is a context-specific error type (does not extend the sealed CdmeError trait).
 * Convert to a model-level error at the boundary if needed.
 *
 * @param message    description of the failure
 * @param boundary   the boundary definition that was being resolved
 * @param params     the parameters that were provided
 */
case class EpochResolutionError(
  message: String,
  boundary: BoundaryDef,
  params: Map[String, String]
)
