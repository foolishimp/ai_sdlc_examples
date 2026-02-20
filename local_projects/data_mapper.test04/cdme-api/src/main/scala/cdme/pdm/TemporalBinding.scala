// Implements: REQ-F-PDM-003, REQ-F-PDM-005
package cdme.pdm

import cdme.model.category.EntityId
import cdme.model.context.Epoch
import cdme.model.error.ValidationError

/**
 * A temporal range mapping an entity to a physical source for a time period.
 *
 * @param from   the start epoch (inclusive)
 * @param to     the end epoch (inclusive)
 * @param source the physical source for this range
 */
final case class TemporalRange(
    from: Epoch,
    to: Epoch,
    source: PhysicalSource
) {
  /**
   * Check whether this range overlaps with another.
   *
   * @param other the other range
   * @return true if the ranges overlap
   */
  def overlaps(other: TemporalRange): Boolean =
    !from.endTime.isAfter(other.to.endTime) &&
    !to.startTime.isBefore(other.from.startTime)
}

/**
 * Temporal binding: maps (entity, epoch) to physical source with
 * non-overlapping temporal ranges.
 *
 * Temporal bindings with overlapping ranges for the same entity are rejected
 * at definition time.
 *
 * @param entityId the entity this binding applies to
 * @param ranges   ordered list of non-overlapping temporal ranges
 */
final case class TemporalBinding(
    entityId: EntityId,
    ranges: List[TemporalRange]
) {
  /**
   * Validate that no ranges overlap.
   *
   * @return list of validation errors (empty if no overlaps)
   */
  def validate(): List[ValidationError] = {
    val sortedRanges = ranges.sortBy(_.from.startTime)
    sortedRanges.sliding(2).collect {
      case List(a, b) if a.overlaps(b) =>
        ValidationError.General(
          s"Overlapping temporal ranges for entity ${entityId.show}: " +
          s"${a.from.id.show}-${a.to.id.show} overlaps ${b.from.id.show}-${b.to.id.show}"
        )
    }.toList
  }

  /**
   * Resolve the physical source for a given epoch.
   *
   * @param epoch the epoch to resolve
   * @return the physical source, if a matching range exists
   */
  def resolve(epoch: Epoch): Option[PhysicalSource] =
    ranges.find { range =>
      !epoch.startTime.isBefore(range.from.startTime) &&
      !epoch.endTime.isAfter(range.to.endTime)
    }.map(_.source)
}
