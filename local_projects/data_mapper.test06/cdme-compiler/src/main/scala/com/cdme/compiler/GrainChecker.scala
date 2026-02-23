package com.cdme.compiler

import com.cdme.model.Grain

// Implements: REQ-TRV-02 (Grain Safety)
object GrainChecker {
  /**
   * Check grain compatibility for a morphism composition.
   * Combining attributes from incompatible grains without explicit aggregation is blocked.
   */
  def check(source: Grain, target: Grain, hasAggregation: Boolean): Either[CompilationError, Unit] = {
    if (Grain.isCompatible(source, target)) {
      Right(())
    } else if (Grain.requiresAggregation(source, target) && !hasAggregation) {
      Left(CompilationError.GrainViolation(source, target,
        s"Cannot combine grain ${source} with ${target} without aggregation morphism"))
    } else if (hasAggregation && Grain.requiresAggregation(source, target)) {
      Right(())  // explicit aggregation provided
    } else {
      Left(CompilationError.GrainViolation(source, target,
        s"Cannot disaggregate from ${source} to finer grain ${target}"))
    }
  }

  /**
   * Check that all grains in a set of morphisms are compatible.
   * REQ-TRV-02: projecting attributes from incompatible grains is blocked.
   */
  def checkMultiple(grains: List[(String, Grain)]): Either[CompilationError, Grain] = {
    grains match {
      case Nil => Right(Grain.Atomic)
      case (_, g) :: Nil => Right(g)
      case (name1, g1) :: rest =>
        rest.find { case (_, g) => !Grain.isCompatible(g1, g) } match {
          case Some((name2, g2)) =>
            Left(CompilationError.GrainViolation(g1, g2,
              s"Incompatible grains in projection: '$name1' at $g1 vs '$name2' at $g2"))
          case None => Right(g1)
        }
    }
  }
}
