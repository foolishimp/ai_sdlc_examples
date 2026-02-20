package cdme.cost

// Implements: REQ-F-TRV-006, REQ-NFR-PERF-002

import cdme.model.{Budget, ExecutionPlan}

/** Cost estimation functor for computational governance.
  *
  * Before executing a plan, estimate cardinality explosion and
  * block execution if budget is exceeded (REQ-F-TRV-006).
  */
object CostEstimator:

  case class CostEstimate(
      estimatedOutputRows: Long,
      maxJoinDepth: Int,
      maxIntermediateSize: Long
  )

  sealed trait CostError
  case class BudgetExceeded(
      estimate: CostEstimate,
      budget: Budget,
      reason: String
  ) extends CostError

  /** Estimate execution cost from plan stages. */
  def estimate(plan: ExecutionPlan): CostEstimate =
    val totalRows = plan.stages.map(_.estimatedCardinality).maxOption.getOrElse(0L)
    CostEstimate(
      estimatedOutputRows = totalRows,
      maxJoinDepth = plan.stages.size,
      maxIntermediateSize = plan.stages.map(_.estimatedCardinality).sum
    )

  /** Check if estimate is within budget (REQ-F-TRV-006). */
  def checkBudget(est: CostEstimate, budget: Budget): Either[CostError, Unit] =
    if est.estimatedOutputRows > budget.maxOutputRows then
      Left(BudgetExceeded(est, budget, s"Output rows ${est.estimatedOutputRows} > budget ${budget.maxOutputRows}"))
    else if est.maxJoinDepth > budget.maxJoinDepth then
      Left(BudgetExceeded(est, budget, s"Join depth ${est.maxJoinDepth} > budget ${budget.maxJoinDepth}"))
    else if est.maxIntermediateSize > budget.maxIntermediateSize then
      Left(BudgetExceeded(est, budget, s"Intermediate size ${est.maxIntermediateSize} > budget ${budget.maxIntermediateSize}"))
    else
      Right(())
