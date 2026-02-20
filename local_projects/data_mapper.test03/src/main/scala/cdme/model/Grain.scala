package cdme.model

// Implements: REQ-F-LDM-006, REQ-F-TRV-002

/** Grain represents the aggregation level of an entity.
  *
  * Grain is a formal dimension — mixing grains without explicit aggregation
  * is topologically forbidden (REQ-F-TRV-002).
  */
sealed trait Grain:
  def level: Int

object Grain:
  case object Atomic extends Grain:
    val level = 0

  case class Aggregate(name: String, hierarchyLevel: Int) extends Grain:
    def level: Int = hierarchyLevel

  /** Check if combining two grains is safe without aggregation. */
  def isCompatible(a: Grain, b: Grain): Boolean =
    a.level == b.level

  /** Check if aggregation from finer to coarser grain is valid. */
  def canAggregateTo(from: Grain, to: Grain): Boolean =
    from.level < to.level
