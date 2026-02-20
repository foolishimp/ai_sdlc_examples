package com.cdme.compiler.cost
// Implements: REQ-F-TRV-006

import com.cdme.model._
import com.cdme.model.config.CardinalityBudget
import com.cdme.model.morphism.{OneToOne, ManyToOne, OneToMany}
import com.cdme.compiler.BudgetExceeded
import com.cdme.compiler.plan._

/**
 * Estimated computational cost of executing a plan.
 *
 * @param estimatedOutputRows      predicted number of output rows
 * @param estimatedJoinDepth       maximum join chain depth in the plan
 * @param estimatedIntermediateSize predicted peak intermediate row count
 */
case class CostEstimate(
  estimatedOutputRows: Long,
  estimatedJoinDepth: Int,
  estimatedIntermediateSize: Long
)

/**
 * Optional statistics about the source data, used for more accurate cost estimation.
 *
 * @param entityRowCounts     known row counts per entity
 * @param cardinalityEstimates known cardinality ratios per morphism
 */
case class DataStatistics(
  entityRowCounts: Map[EntityId, Long],
  cardinalityEstimates: Map[MorphismId, Double]
)

/**
 * Estimates the computational cost of an execution plan and validates
 * it against a cardinality budget.
 *
 * Cost estimation is conservative: when statistics are unavailable, the
 * estimator uses worst-case multipliers based on cardinality declarations.
 */
object CostEstimator {

  /** Default multiplier for OneToMany joins when no statistics are available. */
  private val DefaultFanOutMultiplier: Long = 100L

  /**
   * Estimate the cost of executing a plan.
   *
   * @param plan  the compiled execution plan
   * @param stats optional data statistics for more accurate estimation
   * @return a [[CostEstimate]]
   */
  def estimate(plan: ExecutionPlan, stats: Option[DataStatistics]): CostEstimate = {
    var maxIntermediateSize: Long = 0L
    var joinDepth: Int = 0
    var currentRowEstimate: Long = 0L

    // Walk the stages and accumulate cost
    val stageEstimates = new Array[Long](plan.stages.length)

    plan.stages.zipWithIndex.foreach { case (stage, idx) =>
      val rowEstimate = stage match {
        case ReadStage(binding, _) =>
          stats.flatMap(_.entityRowCounts.get(binding.logicalEntity))
            .getOrElse(1000000L) // Default 1M if unknown

        case TransformStage(morphism, input, _) =>
          val inputRows = stageEstimates(input.index)
          morphism.cardinality match {
            case OneToOne  => inputRows
            case ManyToOne => inputRows  // Output <= input
            case OneToMany =>
              val multiplier = stats
                .flatMap(_.cardinalityEstimates.get(morphism.morphismId))
                .map(_.toLong)
                .getOrElse(DefaultFanOutMultiplier)
              inputRows * multiplier
          }

        case JoinStage(left, right, _, _) =>
          joinDepth += 1
          val leftRows = stageEstimates(left.index)
          val rightRows = stageEstimates(right.index)
          // Conservative: assume full cross for unknown selectivity
          Math.max(leftRows, rightRows)

        case AggregateStage(_, groupKeys, input) =>
          val inputRows = stageEstimates(input.index)
          // Aggregation reduces rows; estimate as sqrt of input (heuristic)
          if (groupKeys.isEmpty) 1L
          else Math.max(1L, Math.sqrt(inputRows.toDouble).toLong)

        case WriteStage(_, input) =>
          stageEstimates(input.index)

        case CheckpointStage(_, input) =>
          stageEstimates(input.index)
      }

      stageEstimates(idx) = rowEstimate
      if (rowEstimate > maxIntermediateSize) {
        maxIntermediateSize = rowEstimate
      }
      currentRowEstimate = rowEstimate
    }

    CostEstimate(
      estimatedOutputRows = currentRowEstimate,
      estimatedJoinDepth = joinDepth,
      estimatedIntermediateSize = maxIntermediateSize
    )
  }

  /**
   * Validate that a cost estimate fits within the configured budget.
   *
   * @param costEstimate the estimated cost
   * @param budget       the cardinality budget from job config
   * @return the estimate on success, or a [[BudgetExceeded]] error
   */
  def checkBudget(
    costEstimate: CostEstimate,
    budget: CardinalityBudget
  ): Either[BudgetExceeded, CostEstimate] = {
    if (costEstimate.estimatedOutputRows > budget.maxOutputRows) {
      Left(BudgetExceeded(
        dimension = "output_rows",
        estimated = costEstimate.estimatedOutputRows,
        budget = budget.maxOutputRows
      ))
    } else if (costEstimate.estimatedJoinDepth > budget.maxJoinDepth) {
      Left(BudgetExceeded(
        dimension = "join_depth",
        estimated = costEstimate.estimatedJoinDepth.toLong,
        budget = budget.maxJoinDepth.toLong
      ))
    } else if (costEstimate.estimatedIntermediateSize > budget.maxIntermediateSize) {
      Left(BudgetExceeded(
        dimension = "intermediate_size",
        estimated = costEstimate.estimatedIntermediateSize,
        budget = budget.maxIntermediateSize
      ))
    } else {
      Right(costEstimate)
    }
  }
}
