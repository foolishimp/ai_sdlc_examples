// Implements: REQ-TRV-02, REQ-INT-05
// Grain safety checker: enforces 3 grain safety rules at compile time.
package com.cdme.compiler

import com.cdme.model._
import com.cdme.model.error.{CdmeError, GrainViolationError}
import com.cdme.model.grain.{Grain, GrainHierarchy}

/**
 * Enforces three grain safety rules:
 * 1. No cross-grain projection without aggregation
 * 2. No cross-grain expressions
 * 3. No cross-grain joins without explicit aggregation morphism
 *
 * Implements: REQ-TRV-02, REQ-INT-05
 * See ADR-005: Grain Ordering System
 */
object GrainChecker {

  /**
   * Check grain safety for a morphism path in the given category.
   * Returns all grain violations found.
   */
  def checkGrainSafety(
      morphismIds: List[MorphismId],
      category: Category
  ): Either[List[GrainViolationError], Unit] = {
    val violations = morphismIds.flatMap { mid =>
      category.morphism(mid).flatMap { morphism =>
        checkMorphismGrainSafety(morphism, category)
      }
    }

    if (violations.isEmpty) Right(())
    else Left(violations)
  }

  /**
   * Check grain safety for a single morphism.
   * A morphism from grain A to grain B requires:
   * - Same grain: always ok
   * - Coarser grain (A < B): requires algebraic morphism kind
   * - Finer grain (A > B): always forbidden
   */
  private def checkMorphismGrainSafety(
      morphism: Morphism,
      category: Category
  ): Option[GrainViolationError] = {
    for {
      sourceEntity <- category.entity(morphism.domain)
      targetEntity <- category.entity(morphism.codomain)
      violation <- checkGrainCompatibility(
        sourceEntity.grain,
        targetEntity.grain,
        morphism
      )
    } yield violation
  }

  private def checkGrainCompatibility(
      sourceGrain: Grain,
      targetGrain: Grain,
      morphism: Morphism
  ): Option[GrainViolationError] = {
    val cmp = sourceGrain.compare(targetGrain)
    if (cmp == 0) {
      // Same grain: always valid
      None
    } else if (cmp < 0) {
      // Source is finer than target (coarsening): requires algebraic morphism
      if (morphism.morphismKind == MorphismKind.Algebraic) None
      else Some(GrainViolationError(
        sourceGrain, targetGrain,
        List(morphism.id),
        sourceEntity = Some(morphism.domain)
      ))
    } else {
      // Source is coarser than target (refinement): forbidden
      Some(GrainViolationError(
        sourceGrain, targetGrain,
        List(morphism.id),
        sourceEntity = Some(morphism.domain)
      ))
    }
  }

  /**
   * Validate that a multi-level aggregation path is supported by the grain hierarchy.
   * Implements: REQ-INT-02
   */
  def validateAggregationPath(
      sourceGrain: Grain,
      targetGrain: Grain,
      hierarchy: GrainHierarchy
  ): Either[CdmeError, List[Grain]] = {
    hierarchy.findPath(sourceGrain, targetGrain) match {
      case Some(path) => Right(path)
      case None =>
        Left(GrainViolationError(
          sourceGrain, targetGrain, Nil,
          sourceEntity = None,
          sourceEpoch = None
        ))
    }
  }
}
