// Implements: REQ-ADJ-08, REQ-ADJ-09
// Reconciliation engine and impact analysis.
package com.cdme.adjoint

import com.cdme.model.MorphismId
import com.cdme.model.error.CdmeError

/**
 * Reconciliation engine: compares original input with backward traversal result
 * to identify matched, missing, and extra records.
 *
 * Implements: REQ-ADJ-08
 */
object ReconciliationEngine {

  /**
   * Reconcile: given an adjoint and original input, compute what was preserved,
   * what was lost, and what appeared extra.
   */
  def reconcile[A, B](
      adjoint: Adjoint[A, B],
      originalInput: Set[A]
  ): Either[CdmeError, ReconciliationResult[A]] = {
    // Forward all inputs
    val forwardResults = originalInput.toList.map { a =>
      adjoint.forward(a).map(b => (a, b))
    }
    val forwardErrors = forwardResults.collect { case Left(e) => e }
    if (forwardErrors.nonEmpty) return Left(forwardErrors.head)

    val forwardPairs = forwardResults.collect { case Right(pair) => pair }

    // Backward all forward results
    val allBackward = forwardPairs.flatMap { case (_, b) =>
      adjoint.backward(b).getOrElse(Set.empty)
    }.toSet

    val matched = originalInput.intersect(allBackward)
    val missing = originalInput.diff(allBackward)
    val extra   = allBackward.diff(originalInput)

    Right(ReconciliationResult(
      matched = matched,
      missing = missing,
      extra = extra,
      isExact = missing.isEmpty && extra.isEmpty
    ))
  }
}

/** Reconciliation result. */
final case class ReconciliationResult[A](
    matched: Set[A],
    missing: Set[A],
    extra: Set[A],
    isExact: Boolean
)

/**
 * Impact analysis: given a target subset, trace backward to find contributing sources.
 * Implements: REQ-ADJ-09
 */
object ImpactAnalyzer {

  /**
   * Analyze which source records contributed to a target subset.
   */
  def analyzeImpact[A, B](
      targetSubset: Set[B],
      backward: B => Either[CdmeError, Set[A]],
      morphismIds: List[MorphismId]
  ): Either[CdmeError, ImpactAnalysisResult[A]] = {
    val results = targetSubset.toList.map(backward)
    val errors = results.collect { case Left(e) => e }
    if (errors.nonEmpty) return Left(errors.head)

    val contributing = results.collect { case Right(as) => as }.flatten.toSet

    Right(ImpactAnalysisResult(
      contributingSources = contributing,
      pathsTraversed = morphismIds,
      estimatedCost = contributing.size.toLong
    ))
  }
}

/** Impact analysis result. */
final case class ImpactAnalysisResult[A](
    contributingSources: Set[A],
    pathsTraversed: List[MorphismId],
    estimatedCost: Long
)
