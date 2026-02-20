// Implements: REQ-F-LDM-004, REQ-F-LDM-006, REQ-BR-LDM-001
// ADR-001: Scala 3 Opaque Types for Domain Modelling
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
 * ADR-001 uses opaque types for zero-cost abstraction at compile time.
 */
opaque type Grain = String

object Grain:
  /**
   * Create a Grain from a string identifier.
   *
   * @param level the grain level name (e.g., "Atomic", "Daily", "Monthly")
   * @return the Grain value
   */
  def apply(level: String): Grain = level

  extension (g: Grain)
    /** The grain level name. */
    def level: String = g

    /** Human-readable representation. */
    def show: String = s"Grain($g)"

  /** Common built-in grain levels. */
  val Atomic: Grain    = Grain("Atomic")
  val Daily: Grain     = Grain("Daily")
  val Monthly: Grain   = Grain("Monthly")
  val Quarterly: Grain = Grain("Quarterly")
  val Yearly: Grain    = Grain("Yearly")
