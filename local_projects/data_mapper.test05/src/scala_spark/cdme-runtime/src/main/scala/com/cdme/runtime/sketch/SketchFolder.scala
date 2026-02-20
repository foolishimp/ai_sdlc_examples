package com.cdme.runtime.sketch

// Implements: REQ-DATA-QAL-002

import com.cdme.model.monoid.Monoid

/**
 * Marker trait for approximate aggregation monoids (sketches).
 *
 * Extends [[Monoid]] to indicate that the aggregation is probabilistic
 * rather than exact.  Implementations include data structures like
 * t-Digest (for quantile estimation), HyperLogLog (for cardinality
 * estimation), and Count-Min Sketch (for frequency estimation).
 *
 * Sketch aggregators satisfy the monoid laws (associativity, identity)
 * which makes them safe for distributed execution — partial sketches
 * from different partitions can be combined without ordering dependencies.
 *
 * This is a placeholder trait; concrete implementations will be provided
 * in a future iteration.
 *
 * @tparam A the sketch type (e.g. TDigest, HyperLogLog)
 */
trait SketchAggregator[A] extends Monoid[A] {

  /**
   * Estimated error bound for this sketch algorithm.
   *
   * @return the relative error bound (e.g. 0.01 for 1% error)
   */
  def errorBound: Double

  /**
   * Human-readable name of the sketch algorithm.
   *
   * @return algorithm name (e.g. "t-Digest", "HyperLogLog")
   */
  def algorithmName: String
}
