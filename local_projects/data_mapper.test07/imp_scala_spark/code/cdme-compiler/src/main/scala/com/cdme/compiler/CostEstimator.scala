// Implements: REQ-TRV-06
// Cost estimation functor: estimates cardinality before execution.
package com.cdme.compiler

import com.cdme.model._
import com.cdme.model.error.{BudgetExceededError, CdmeError}

/**
 * Estimates execution cost based on DAG structure and optional statistics.
 * Blocks execution if estimated cost exceeds the budget.
 *
 * Implements: REQ-TRV-06
 */
object CostEstimator {

  /**
   * Estimate the cost of executing a plan.
   */
  def estimateCost(
      dag: ExecutionDag,
      statistics: Option[DataStatistics],
      jobConfig: JobConfiguration
  ): Either[CdmeError, CostEstimate] = {
    val sourceCount = dag.sources.size.toLong
    val joinDepth   = computeJoinDepth(dag)

    // Estimate output rows based on statistics or heuristic
    val estimatedRows = statistics match {
      case Some(stats) =>
        val sourceRows = dag.sources.flatMap(nid => dag.nodes.get(nid))
          .flatMap(n => stats.rowCounts.get(n.entityId))
          .sum
        // Heuristic: each join can multiply rows by average cardinality
        val multiplier = dag.edges.values
          .flatMap(e => stats.cardinalityEstimates.get(e.morphismId))
          .product
        (sourceRows * Math.max(1.0, multiplier)).toLong
      case None =>
        // Default heuristic: 1M rows per source, 2x per join level
        sourceCount * 1000000L * Math.pow(2, joinDepth).toLong
    }

    val intermediateSize = estimatedRows * 256 // rough bytes per row

    val withinBudget = checkBudget(estimatedRows, joinDepth, intermediateSize, jobConfig)

    val estimate = CostEstimate(
      estimatedOutputRows = estimatedRows,
      estimatedJoinDepth = joinDepth,
      estimatedIntermediateSize = intermediateSize,
      withinBudget = withinBudget,
      details = Map(
        "sourceNodes" -> sourceCount,
        "totalNodes" -> dag.nodes.size.toLong,
        "totalEdges" -> dag.edges.size.toLong,
        "joinDepth" -> joinDepth.toLong
      )
    )

    if (withinBudget) Right(estimate)
    else {
      val budgetLimit = jobConfig.maxOutputRows.getOrElse(Long.MaxValue)
      Left(BudgetExceededError(estimatedRows, budgetLimit))
    }
  }

  private def computeJoinDepth(dag: ExecutionDag): Int = {
    if (dag.edges.isEmpty) return 0
    // Simple BFS from sources to sinks to find max depth
    dag.sources.map { sourceId =>
      computeDepth(sourceId, dag, Set.empty)
    }.maxOption.getOrElse(0)
  }

  private def computeDepth(nodeId: NodeId, dag: ExecutionDag, visited: Set[NodeId]): Int = {
    if (visited.contains(nodeId)) return 0
    val children = dag.edges.values.filter(_.source == nodeId).map(_.target).toList
    if (children.isEmpty) 0
    else 1 + children.map(computeDepth(_, dag, visited + nodeId)).max
  }

  private def checkBudget(
      estimatedRows: Long,
      joinDepth: Int,
      intermediateSize: Long,
      config: JobConfiguration
  ): Boolean = {
    val rowsOk   = config.maxOutputRows.forall(_ >= estimatedRows)
    val depthOk  = config.maxJoinDepth.forall(_ >= joinDepth)
    val sizeOk   = config.maxIntermediateSize.forall(_ >= intermediateSize)
    rowsOk && depthOk && sizeOk
  }
}
