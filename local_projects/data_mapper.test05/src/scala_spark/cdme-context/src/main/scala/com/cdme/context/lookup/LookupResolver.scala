package com.cdme.context.lookup
// Implements: REQ-F-SYN-006

import com.cdme.model._
import com.cdme.model.config.Epoch
import com.cdme.model.entity.EntityId
import com.cdme.model.morphism.MorphismId

// ---------------------------------------------------------------------------
// Domain types for lookup version semantics (duplicated from compiler module
// to avoid a cross-module dependency; both compiler and context need these).
// ---------------------------------------------------------------------------

/**
 * A reference to a versioned lookup table used in a synthesis expression.
 *
 * @param lookupId         unique identifier for the lookup table
 * @param versionSemantics how the lookup version is pinned for this execution
 */
case class LookupRef(
  lookupId: String,
  versionSemantics: VersionSemantics
)

/**
 * Version pinning strategy for lookup references.
 */
sealed trait VersionSemantics

/** Pin the lookup to a specific, explicitly declared version string. */
case class ExplicitVersion(version: String) extends VersionSemantics

/** Pin the lookup to the version that was current as of a given epoch boundary. */
case class AsOfEpoch(epoch: Epoch) extends VersionSemantics

/** Pin the lookup using a deterministic alias that resolves within the execution context. */
case class DeterministicAlias(alias: String) extends VersionSemantics

// ---------------------------------------------------------------------------
// Pinned lookup
// ---------------------------------------------------------------------------

/**
 * A lookup that has been pinned to a specific version within an execution context.
 *
 * Once pinned, the same key always returns the same value for the duration
 * of the execution run, guaranteeing within-execution consistency.
 *
 * @param ref             the original lookup reference
 * @param resolvedVersion the concrete version string that was pinned
 * @param cache           memoized lookup results for deterministic replay
 */
case class PinnedLookup(
  ref: LookupRef,
  resolvedVersion: String,
  cache: Map[Any, Any]
)

/**
 * Resolves versioned lookup references to concrete values.
 *
 * The resolver has two phases:
 * 1. **Pin**: Resolve the version semantics to a concrete version string
 *    and create a [[PinnedLookup]]. This happens once per lookup per execution.
 * 2. **Resolve**: Look up a key in a pinned lookup. This happens per record.
 *
 * Implementations must guarantee:
 * - Same key + same pinned lookup = same value (within-execution consistency)
 * - Pinning is deterministic given the same epoch and version semantics
 */
trait LookupResolver {

  /**
   * Resolve a lookup key to a value using a pinned lookup.
   *
   * @param ref   the lookup reference (must have been pinned first)
   * @param key   the lookup key
   * @param epoch the processing epoch
   * @return the resolved value on success, or a [[LookupError]] on failure
   */
  def resolve(ref: LookupRef, key: Any, epoch: Epoch): Either[LookupError, Any]

  /**
   * Pin a lookup reference to a concrete version for the current execution.
   *
   * This resolves the [[VersionSemantics]] to a concrete version string and
   * optionally pre-loads the lookup data into an in-memory cache.
   *
   * @param ref   the lookup reference to pin
   * @param epoch the processing epoch (used for [[AsOfEpoch]] semantics)
   * @return a [[PinnedLookup]] on success, or a [[LookupError]] on failure
   */
  def pin(ref: LookupRef, epoch: Epoch): Either[LookupError, PinnedLookup]
}

/**
 * In-memory implementation of [[LookupResolver]] backed by pre-loaded data.
 *
 * Suitable for testing and for lookups small enough to fit in memory.
 * Production implementations would back this with a versioned data store.
 *
 * @param lookupStore maps (lookupId, version) to the lookup data (key -> value)
 * @param versionIndex maps (lookupId, alias/epoch) to concrete version strings
 */
class InMemoryLookupResolver(
  lookupStore: Map[(String, String), Map[Any, Any]],
  versionIndex: Map[String, Map[String, String]]
) extends LookupResolver {

  private var pinnedCache: Map[String, PinnedLookup] = Map.empty

  override def resolve(
    ref: LookupRef,
    key: Any,
    epoch: Epoch
  ): Either[LookupError, Any] = {
    pinnedCache.get(ref.lookupId) match {
      case Some(pinned) =>
        pinned.cache.get(key).toRight(
          LookupKeyNotFound(ref.lookupId, key, epoch)
        )
      case None =>
        // Auto-pin if not already pinned
        pin(ref, epoch).flatMap { pinned =>
          pinned.cache.get(key).toRight(
            LookupKeyNotFound(ref.lookupId, key, epoch)
          )
        }
    }
  }

  override def pin(
    ref: LookupRef,
    epoch: Epoch
  ): Either[LookupError, PinnedLookup] = {
    resolveVersion(ref, epoch).flatMap { version =>
      lookupStore.get((ref.lookupId, version)) match {
        case Some(data) =>
          val pinned = PinnedLookup(ref, version, data)
          pinnedCache = pinnedCache + (ref.lookupId -> pinned)
          Right(pinned)
        case None =>
          Left(LookupDataNotFound(ref.lookupId, version, epoch))
      }
    }
  }

  /**
   * Resolve version semantics to a concrete version string.
   */
  private def resolveVersion(ref: LookupRef, fallbackEpoch: Epoch): Either[LookupError, String] = {
    ref.versionSemantics match {
      case ExplicitVersion(version) =>
        Right(version)

      case AsOfEpoch(epoch) =>
        val epochKey = epoch.parameters.values.mkString("_")
        versionIndex.get(ref.lookupId).flatMap(_.get(epochKey))
          .toRight(LookupVersionResolutionError(
            ref.lookupId,
            s"as_of_epoch($epochKey)",
            epoch
          ))

      case DeterministicAlias(alias) =>
        versionIndex.get(ref.lookupId).flatMap(_.get(alias))
          .toRight(LookupVersionResolutionError(
            ref.lookupId,
            s"alias($alias)",
            fallbackEpoch
          ))
    }
  }
}

// ---------------------------------------------------------------------------
// Lookup-specific error types (context-local, do not extend sealed CdmeError)
// ---------------------------------------------------------------------------

/** Base trait for lookup-specific errors in the context module. */
sealed trait LookupError {
  def message: String
}

/** A lookup key was not found in the pinned lookup data. */
case class LookupKeyNotFound(
  lookupId: String,
  key: Any,
  epoch: Epoch
) extends LookupError {
  val message: String = s"Key '$key' not found in lookup '$lookupId' for epoch '${epoch.id}'"
}

/** The lookup data for the resolved version does not exist. */
case class LookupDataNotFound(
  lookupId: String,
  version: String,
  epoch: Epoch
) extends LookupError {
  val message: String = s"No data found for lookup '$lookupId' version '$version'"
}

/** Version resolution failed (alias or epoch could not be mapped to a version). */
case class LookupVersionResolutionError(
  lookupId: String,
  semanticsDesc: String,
  epoch: Epoch
) extends LookupError {
  val message: String = s"Failed to resolve version for lookup '$lookupId' with semantics '$semanticsDesc'"
}
