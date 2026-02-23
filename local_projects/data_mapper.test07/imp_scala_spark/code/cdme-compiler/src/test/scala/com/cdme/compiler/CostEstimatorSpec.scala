// Validates: REQ-TRV-06
// Tests for cost estimation and budget checking.
package com.cdme.compiler

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CostEstimatorSpec extends AnyFlatSpec with Matchers {

  val simpleDag: ExecutionDag = ExecutionDag(
    nodes = Map(
      "src" -> DagNode("src", "trade", DagNodeType.Source),
      "t1"  -> DagNode("t1", "position", DagNodeType.Transform),
      "snk" -> DagNode("snk", "output", DagNodeType.Sink)
    ),
    edges = Map(
      "e1" -> DagEdge("e1", "src", "t1", "m1"),
      "e2" -> DagEdge("e2", "t1", "snk", "m2")
    ),
    sources = Set("src"),
    sinks = Set("snk")
  )

  val permissiveConfig: JobConfiguration = JobConfiguration(
    maxOutputRows = Some(Long.MaxValue),
    maxJoinDepth = Some(100),
    maxIntermediateSize = Some(Long.MaxValue),
    failureThresholdPercent = None,
    failureThresholdAbsolute = None,
    dryRun = false,
    lineageMode = "full"
  )

  "CostEstimator" should "produce a cost estimate for a simple DAG" in {
    val result = CostEstimator.estimateCost(simpleDag, None, permissiveConfig)
    result.isRight shouldBe true
    val estimate = result.toOption.get
    estimate.estimatedJoinDepth should be >= 0
    estimate.withinBudget shouldBe true
  }

  it should "detect budget violation for output rows" in {
    val tightConfig = permissiveConfig.copy(maxOutputRows = Some(1L))
    val result = CostEstimator.estimateCost(simpleDag, None, tightConfig)
    result.isLeft shouldBe true
  }

  it should "respect join depth budget" in {
    val tightConfig = permissiveConfig.copy(maxJoinDepth = Some(0))
    val result = CostEstimator.estimateCost(simpleDag, None, tightConfig)
    // A DAG with edges has join depth >= 1, so budget of 0 should fail
    result.isLeft shouldBe true
  }

  it should "handle empty DAG" in {
    val emptyDag = ExecutionDag(Map.empty, Map.empty, Set.empty, Set.empty)
    val result = CostEstimator.estimateCost(emptyDag, None, permissiveConfig)
    result.isRight shouldBe true
    val estimate = result.toOption.get
    estimate.estimatedJoinDepth shouldBe 0
  }

  it should "use statistics when available" in {
    val stats = DataStatistics(
      rowCounts = Map("trade" -> 1000L),
      averageRowSizes = Map("trade" -> 100L),
      cardinalityEstimates = Map("m1" -> 1.5)
    )
    val result = CostEstimator.estimateCost(simpleDag, Some(stats), permissiveConfig)
    result.isRight shouldBe true
    val estimate = result.toOption.get
    estimate.estimatedOutputRows should be > 0L
  }

  it should "include details in the estimate" in {
    val result = CostEstimator.estimateCost(simpleDag, None, permissiveConfig)
    val estimate = result.toOption.get
    estimate.details should contain key "sourceNodes"
    estimate.details should contain key "totalNodes"
    estimate.details should contain key "totalEdges"
  }
}
