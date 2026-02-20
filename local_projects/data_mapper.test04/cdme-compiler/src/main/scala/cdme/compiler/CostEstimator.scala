// Implements: REQ-F-TRV-007, REQ-F-ACC-005
package cdme.compiler

import cdme.model.category.CardinalityType
import cdme.model.error.ValidationError

/**
 * Cardinality cost estimator for path compilation.
 *
 * Estimates use declared statistics and heuristics at compile time (SA-02).
 * Actual statistics are collected at runtime for future improvement.
 */
object CostEstimator {

  /** Default fan-out heuristics for each cardinality type. */
  private val DefaultFanOut: Map[CardinalityType, Long] = Map(
    CardinalityType.OneToOne  -> 1L,
    CardinalityType.ManyToOne -> 1L,
    CardinalityType.OneToMany -> 10L,
    CardinalityType.ManyToMany -> 100L
  )

  /**
   * Estimate the cardinality at each step of a compiled path.
   *
   * @param inputCardinality the estimated input cardinality
   * @param steps            cardinality types for each step
   * @param declaredStats    optional declared statistics (step index -> fan-out)
   * @return a CostReport with estimates at each step
   */
  def estimate(
      inputCardinality: Long,
      steps: List[CardinalityType],
      declaredStats: Map[Int, Long] = Map.empty
  ): CostReport = {
    var current = inputCardinality
    val estimates = steps.zipWithIndex.map { case (card, idx) =>
      val fanOut = declaredStats.getOrElse(idx, DefaultFanOut(card))
      current = current * fanOut
      current
    }
    CostReport(
      stepEstimates = estimates,
      totalEstimate = current,
      explosionPoint = None
    )
  }

  /**
   * Check whether a cost estimate exceeds the budget.
   *
   * @param report the cost report
   * @param budget the cardinality budget
   * @return Right(report) if within budget, Left(error) if exceeded
   */
  def checkBudget(
      report: CostReport,
      budget: CardinalityBudget
  ): Either[ValidationError, CostReport] = {
    val explosionIdx = report.stepEstimates.zipWithIndex.collectFirst {
      case (est, idx) if est > budget.maxIntermediateSize =>
        idx
    }

    explosionIdx match {
      case Some(idx) =>
        Left(ValidationError.BudgetExceeded(
          estimatedCardinality = report.stepEstimates(idx),
          budget = budget.maxIntermediateSize,
          message = s"Budget exceeded at step $idx: estimated ${report.stepEstimates(idx)} > " +
            s"max intermediate size ${budget.maxIntermediateSize}"
        ))
      case None if report.totalEstimate > budget.maxOutputRows =>
        Left(ValidationError.BudgetExceeded(
          estimatedCardinality = report.totalEstimate,
          budget = budget.maxOutputRows,
          message = s"Total output exceeds budget: ${report.totalEstimate} > ${budget.maxOutputRows}"
        ))
      case None =>
        Right(report.copy(explosionPoint = None))
    }
  }
}
