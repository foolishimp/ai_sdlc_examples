package com.cdme.compiler.lookup
// Implements: REQ-F-SYN-006

import com.cdme.model._
import com.cdme.compiler.{LookupVersionError, UnversionedLookup}

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
 *
 * Every lookup must declare one of these strategies. The compiler rejects
 * any lookup reference that lacks version semantics, ensuring deterministic
 * reproducibility (REQ-F-TRV-005).
 */
sealed trait VersionSemantics

/**
 * Pin the lookup to a specific, explicitly declared version string.
 *
 * @param version the exact version identifier
 */
case class ExplicitVersion(version: String) extends VersionSemantics

/**
 * Pin the lookup to the version that was current as of a given epoch boundary.
 *
 * @param epoch the epoch whose boundary determines the lookup version
 */
case class AsOfEpoch(epoch: Epoch) extends VersionSemantics

/**
 * Pin the lookup using a deterministic alias that resolves to a specific version
 * within the execution context (e.g., "production-current", "qa-baseline").
 *
 * @param alias the deterministic alias name
 */
case class DeterministicAlias(alias: String) extends VersionSemantics

/**
 * Validates that every lookup reference carries version semantics.
 *
 * This is a compile-time gate: no unversioned lookups are permitted.
 * The runtime lookup resolver trusts that every reference has been validated.
 */
object LookupVersionValidator {

  /**
   * Validate a single lookup reference.
   *
   * The reference is valid if and only if its [[VersionSemantics]] is
   * one of [[ExplicitVersion]], [[AsOfEpoch]], or [[DeterministicAlias]].
   *
   * Note: in practice this check is always true because [[LookupRef]]
   * requires a non-null [[VersionSemantics]]. This validator exists
   * as a compile-time assertion point and for future extensibility
   * (e.g., rejecting deprecated version aliases).
   *
   * @param ref the lookup reference to validate
   * @return Unit on success, or a [[LookupVersionError]] if invalid
   */
  def validate(ref: LookupRef): Either[LookupVersionError, Unit] = {
    ref.versionSemantics match {
      case ExplicitVersion(v) if v.nonEmpty => Right(())
      case ExplicitVersion(_) =>
        Left(UnversionedLookup(ref.lookupId))
      case AsOfEpoch(_) => Right(())
      case DeterministicAlias(a) if a.nonEmpty => Right(())
      case DeterministicAlias(_) =>
        Left(UnversionedLookup(ref.lookupId))
    }
  }

  /**
   * Validate all lookup references in a list, collecting all errors.
   *
   * @param refs the lookup references to validate
   * @return an empty list on success, or all validation errors
   */
  def validateAll(refs: List[LookupRef]): List[LookupVersionError] = {
    refs.flatMap(ref => validate(ref).left.toOption)
  }
}
