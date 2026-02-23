package com.cdme.adjoint

// Implements: REQ-ADJ-09 (Impact Analysis via Adjoints)
object ImpactAnalyzer {
  /** Given a target subset, compute contributing source records via backward */
  def analyzeImpact[T, U](adjoint: Adjoint[T, U], targetSubset: Set[U]): Set[T] =
    targetSubset.map(adjoint.backward)

  /** Impact analysis using reverse-join table (for aggregations) */
  def analyzeImpactViaReverseJoin(
    reverseJoinTable: ReverseJoinTable,
    targetKeys: Set[String]
  ): Set[String] =
    targetKeys.flatMap(reverseJoinTable.constituentsFor)
}
