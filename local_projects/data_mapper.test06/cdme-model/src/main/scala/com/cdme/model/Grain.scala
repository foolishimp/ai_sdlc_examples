package com.cdme.model

// Implements: REQ-LDM-06 (Grain & Type Metadata)
sealed trait Grain {
  def level: Int
}

object Grain {
  case object Atomic extends Grain { val level = 0 }
  case object Daily extends Grain { val level = 1 }
  case object Monthly extends Grain { val level = 2 }
  case object Yearly extends Grain { val level = 3 }
  case class Custom(name: String, level: Int) extends Grain

  /** True if `from` is finer than or equal to `to` */
  def isFinerOrEqual(from: Grain, to: Grain): Boolean = from.level <= to.level

  /** True if `from` is strictly finer than `to` (aggregation needed) */
  def requiresAggregation(from: Grain, to: Grain): Boolean = from.level < to.level

  /** True if grains are compatible (same level) */
  def isCompatible(a: Grain, b: Grain): Boolean = a.level == b.level

  implicit val ordering: Ordering[Grain] = Ordering.by(_.level)
}
