package com.cdme.lineage.impact

// Implements: REQ-F-ADJ-007

import com.cdme.lineage.backward.{BackwardTraverser, SourceRecord}
import com.cdme.model.morphism.MorphismId
import com.cdme.runtime.adjoint._

/**
 * Report describing the impact of a target subset — which source records
 * contributed to the target, and through which morphism paths.
 *
 * Impact analysis answers the question: "If this subset of output changed,
 * which source records and transformations are responsible?"
 *
 * @param targetSubset             the output keys being analyzed
 * @param contributingSourceRecords the set of source records that contributed
 * @param morphismPaths            the morphism execution paths connecting source to target
 */
case class ImpactReport(
  targetSubset: Set[Any],
  contributingSourceRecords: Set[SourceRecord],
  morphismPaths: List[List[MorphismId]]
)

/**
 * Analyzes which source records and morphism paths contributed to a subset
 * of target output keys.
 *
 * Impact analysis is built on top of backward traversal: for each key in the
 * target subset, it traces backward through the adjoint metadata chain to
 * find all contributing source records, then extracts the morphism paths
 * from the metadata.
 */
object ImpactAnalyzer {

  /**
   * Analyze the impact of a target subset.
   *
   * For each key in `targetSubset`, performs backward traversal to find
   * contributing source records, then extracts the morphism execution paths
   * from the adjoint metadata.
   *
   * @param targetSubset the output keys to analyze
   * @param adjointMeta  the adjoint metadata captured during pipeline execution
   * @return an [[ImpactReport]] describing the contributing sources and paths
   */
  def analyze(
    targetSubset: Set[Any],
    adjointMeta: List[AdjointMetadata]
  ): ImpactReport = {
    // Trace backward from each target key to find contributing source records
    val allSourceRecords: Set[SourceRecord] = targetSubset.flatMap { targetKey =>
      BackwardTraverser.traceBack(targetKey, adjointMeta)
    }

    // Extract morphism paths from the adjoint metadata.
    // Each adjoint metadata entry is associated with a morphism execution;
    // the paths represent the sequence of morphisms from source to target.
    val morphismPaths: List[List[MorphismId]] = extractMorphismPaths(adjointMeta)

    ImpactReport(
      targetSubset = targetSubset,
      contributingSourceRecords = allSourceRecords,
      morphismPaths = morphismPaths
    )
  }

  /**
   * Extract morphism execution paths from adjoint metadata.
   *
   * In a full implementation, this would reconstruct the DAG of morphism
   * executions and enumerate all paths from source to target.
   */
  private def extractMorphismPaths(
    adjointMeta: List[AdjointMetadata]
  ): List[List[MorphismId]] = {
    // TODO: Reconstruct morphism DAG from metadata and enumerate paths.
    // For now, return an empty list — paths will be populated when the
    // adjoint metadata includes morphism ID annotations.
    val _ = adjointMeta // suppress unused warning
    Nil
  }
}
