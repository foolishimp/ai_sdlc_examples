// Implements: REQ-LDM-06, REQ-INT-02
// GrainHierarchy: defines aggregation paths between grains.
package com.cdme.model.grain

/**
 * Defines the hierarchy of grains and valid aggregation paths between them.
 * Multi-level aggregation is valid only when a path exists in the hierarchy.
 *
 * Implements: REQ-LDM-06, REQ-INT-02
 */
final case class GrainHierarchy(
    grains: List[Grain],
    aggregationPaths: Map[(Grain, Grain), List[Grain]]
) {

  /**
   * Find the aggregation path from source to target grain.
   * Returns None if no valid path exists.
   */
  def findPath(source: Grain, target: Grain): Option[List[Grain]] =
    aggregationPaths.get((source, target))

  /** Check if aggregation from source to target is supported. */
  def canAggregate(source: Grain, target: Grain): Boolean =
    aggregationPaths.contains((source, target))

  /** All grains in the hierarchy, sorted by level. */
  def sortedGrains: List[Grain] = grains.sortBy(_.level)
}

object GrainHierarchy {

  /** Default hierarchy with standard grains and all valid aggregation paths. */
  val default: GrainHierarchy = {
    val grains = Grain.standardGrains
    val paths = Map(
      (Grain.Atomic, Grain.Daily)      -> List(Grain.Atomic, Grain.Daily),
      (Grain.Atomic, Grain.Monthly)    -> List(Grain.Atomic, Grain.Daily, Grain.Monthly),
      (Grain.Atomic, Grain.Quarterly)  -> List(Grain.Atomic, Grain.Daily, Grain.Monthly, Grain.Quarterly),
      (Grain.Atomic, Grain.Yearly)     -> List(Grain.Atomic, Grain.Daily, Grain.Monthly, Grain.Quarterly, Grain.Yearly),
      (Grain.Daily, Grain.Monthly)     -> List(Grain.Daily, Grain.Monthly),
      (Grain.Daily, Grain.Quarterly)   -> List(Grain.Daily, Grain.Monthly, Grain.Quarterly),
      (Grain.Daily, Grain.Yearly)      -> List(Grain.Daily, Grain.Monthly, Grain.Quarterly, Grain.Yearly),
      (Grain.Monthly, Grain.Quarterly) -> List(Grain.Monthly, Grain.Quarterly),
      (Grain.Monthly, Grain.Yearly)    -> List(Grain.Monthly, Grain.Quarterly, Grain.Yearly),
      (Grain.Quarterly, Grain.Yearly)  -> List(Grain.Quarterly, Grain.Yearly)
    )
    GrainHierarchy(grains, paths)
  }
}
