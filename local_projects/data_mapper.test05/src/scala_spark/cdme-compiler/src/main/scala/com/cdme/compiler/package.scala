package com.cdme

import com.cdme.model._
import com.cdme.model.grain.{Grain, Atomic, DailyAggregate, MonthlyAggregate, YearlyAggregate, CustomGrain}

/**
 * Package-level utilities shared across compiler sub-packages.
 */
package object compiler {

  /**
   * Extract the numeric rank from a [[Grain]] instance.
   *
   * Lower rank = finer granularity. Used throughout the compiler for
   * grain comparison and safety enforcement.
   */
  def grainRank(grain: Grain): Int = grain match {
    case Atomic              => 0
    case DailyAggregate      => 10
    case MonthlyAggregate    => 20
    case YearlyAggregate     => 30
    case CustomGrain(_, rank) => rank
  }
}
