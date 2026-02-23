// Implements: REQ-LDM-06, REQ-TRV-02
// Grain: the aggregation level of an entity, forming a total order.
package com.cdme.model.grain

/**
 * Grain represents the aggregation level of an entity.
 * Grains form a total order: Atomic < Daily < Monthly < Quarterly < Yearly.
 * Custom grains can be inserted at any level.
 *
 * Implements: REQ-LDM-06, REQ-TRV-02
 * See ADR-005: Grain Ordering System
 */
sealed trait Grain extends Ordered[Grain] {
  def level: Int
  override def compare(that: Grain): Int = this.level - that.level
}

object Grain {
  case object Atomic    extends Grain { val level: Int = 0 }
  case object Daily     extends Grain { val level: Int = 1 }
  case object Monthly   extends Grain { val level: Int = 2 }
  case object Quarterly extends Grain { val level: Int = 3 }
  case object Yearly    extends Grain { val level: Int = 4 }

  final case class Custom(name: String, level: Int) extends Grain

  /** All standard grains in order. */
  val standardGrains: List[Grain] = List(Atomic, Daily, Monthly, Quarterly, Yearly)

  /** Check if aggregation from source to target grain is valid (coarsening). */
  def canAggregate(source: Grain, target: Grain): Boolean =
    source.level < target.level
}
