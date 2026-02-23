// Implements: REQ-ADJ-04, REQ-ACC-03
// Captures reverse-join metadata during aggregation.
package com.cdme.adjoint

import com.cdme.model.error.{CdmeError, ValidationError}

/**
 * Captures reverse-join metadata during forward aggregation execution.
 * For each group key, records the set of contributing input keys.
 *
 * Implements: REQ-ADJ-04, REQ-ACC-03
 */
final case class ReverseJoinCapture(
    groupToContributors: Map[String, Set[String]]
) {

  /**
   * Record a contribution: input key contributed to group key.
   */
  def capture(groupKey: String, inputKey: String): ReverseJoinCapture =
    ReverseJoinCapture(
      groupToContributors.updated(
        groupKey,
        groupToContributors.getOrElse(groupKey, Set.empty) + inputKey
      )
    )

  /**
   * Batch capture: record multiple contributions for a group key.
   */
  def captureBatch(groupKey: String, inputKeys: Set[String]): ReverseJoinCapture =
    ReverseJoinCapture(
      groupToContributors.updated(
        groupKey,
        groupToContributors.getOrElse(groupKey, Set.empty) ++ inputKeys
      )
    )

  /**
   * Look up the contributing input keys for a group key.
   */
  def lookup(groupKey: String): Either[CdmeError, Set[String]] =
    groupToContributors.get(groupKey) match {
      case Some(keys) => Right(keys)
      case None       => Left(ValidationError(s"No reverse-join metadata for group key '$groupKey'"))
    }

  /** Total number of group keys captured. */
  def groupCount: Int = groupToContributors.size

  /** Total number of input keys across all groups. */
  def totalInputKeys: Int = groupToContributors.values.map(_.size).sum
}

object ReverseJoinCapture {
  val empty: ReverseJoinCapture = ReverseJoinCapture(Map.empty)
}

/**
 * Captures filtered keys during filter operations.
 * Implements: REQ-ADJ-05
 */
final case class FilteredKeysCapture(
    passedKeys: Set[String],
    filteredKeys: Set[String]
) {
  /** Total keys = passed + filtered. */
  def totalKeys: Int = passedKeys.size + filteredKeys.size

  /** Capture a filtering decision. */
  def record(key: String, passed: Boolean): FilteredKeysCapture =
    if (passed) copy(passedKeys = passedKeys + key)
    else copy(filteredKeys = filteredKeys + key)
}

object FilteredKeysCapture {
  val empty: FilteredKeysCapture = FilteredKeysCapture(Set.empty, Set.empty)
}
