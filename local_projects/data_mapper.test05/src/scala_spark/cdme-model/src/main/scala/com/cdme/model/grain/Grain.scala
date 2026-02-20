package com.cdme.model.grain

// Implements: REQ-F-LDM-007, REQ-BR-GRN-001

/**
 * Grain represents the level of detail (granularity) of an [[com.cdme.model.entity.Entity]].
 *
 * Grain is a topological invariant in CDME: the compiler enforces that data
 * never flows from a coarser grain to a finer grain without an explicit
 * [[com.cdme.model.morphism.AlgebraicMorphism]] declaring a monoid.
 * Grain mixing is prohibited (REQ-BR-GRN-001) — there is no override mechanism.
 *
 * Grains are ordered by `rank`: lower rank = finer grain. The [[isCompatible]]
 * helper checks that a source grain can flow to a target grain (i.e. the
 * target is the same or coarser).
 */
sealed trait Grain {
  /** Numeric rank — lower values represent finer grains. */
  def rank: Int
}

/** Row-level, unaggregated data. The finest possible grain. */
case object Atomic extends Grain {
  val rank: Int = 0
}

/** Data aggregated to the daily level. */
case object DailyAggregate extends Grain {
  val rank: Int = 10
}

/** Data aggregated to the monthly level. */
case object MonthlyAggregate extends Grain {
  val rank: Int = 20
}

/** Data aggregated to the yearly level. */
case object YearlyAggregate extends Grain {
  val rank: Int = 30
}

/**
 * A user-defined grain with an explicit rank.
 *
 * Use this for domain-specific granularities (e.g. "WeeklyAggregate"
 * with rank 15, or "QuarterlyAggregate" with rank 25).
 *
 * @param name human-readable grain name
 * @param rank numeric rank in the grain ordering
 */
case class CustomGrain(name: String, rank: Int) extends Grain

/**
 * Grain compatibility utilities.
 */
object Grain {

  /**
   * Check whether data at `source` grain can flow to `target` grain.
   *
   * A flow is compatible when the source is at least as fine as the target
   * (i.e. source rank <= target rank). Flowing from coarse to fine requires
   * an explicit algebraic morphism and is rejected by the compiler if absent.
   *
   * @param source the grain of the input data
   * @param target the grain of the output entity
   * @return true if the flow is grain-safe
   */
  def isCompatible(source: Grain, target: Grain): Boolean =
    source.rank <= target.rank
}
