// Implements: REQ-F-LDM-004, REQ-F-LDM-006, REQ-BR-LDM-001
// ADR-001: Scala 3 Opaque Types for Domain Modelling -> Scala 2.13 case class
package cdme.model.grain

/**
 * A grain level representing the granularity of an entity.
 *
 * Grain levels form a partially ordered set (poset). For example, in a temporal
 * domain: {Atomic, Daily, Monthly, Quarterly, Yearly} where Atomic is the finest
 * and Yearly is the coarsest.
 *
 * Grain levels are configurable per LDM (not hardcoded). The ordering is defined
 * by a GrainOrder instance.
 *
 * @param value the grain level name (e.g., "Atomic", "Daily", "Monthly")
 */
case class Grain(value: String) {
  /** The grain level name. */
  def level: String = value

  /** Human-readable representation. */
  def show: String = s"Grain($value)"
}

object Grain {
  def apply(level: String): Grain = new Grain(level)

  /** Common built-in grain levels. */
  val Atomic: Grain    = Grain("Atomic")
  val Daily: Grain     = Grain("Daily")
  val Monthly: Grain   = Grain("Monthly")
  val Quarterly: Grain = Grain("Quarterly")
  val Yearly: Grain    = Grain("Yearly")
}
