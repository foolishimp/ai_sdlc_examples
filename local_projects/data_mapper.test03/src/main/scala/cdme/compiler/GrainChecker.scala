package cdme.compiler

// Implements: REQ-F-LDM-006, REQ-F-TRV-002, REQ-F-INT-002, REQ-F-INT-005

import cdme.model.{Grain, Morphism, MorphismType}

/** Grain safety checker.
  *
  * Operations combining attributes from incompatible grains without
  * explicit aggregation are rejected (REQ-F-TRV-002).
  */
object GrainChecker:

  sealed trait GrainError
  case class IncompatibleGrains(a: Grain, b: Grain, context: String) extends GrainError
  case class MissingAggregation(fineGrain: Grain, coarseGrain: Grain) extends GrainError

  /** Check grain safety for a morphism composition path. */
  def checkPath(morphisms: List[Morphism]): Either[GrainError, Unit] =
    morphisms.sliding(2).foreach {
      case List(m1, m2) =>
        val sourceGrain = m1.codomain.grain
        val targetGrain = m2.domain.grain
        if !Grain.isCompatible(sourceGrain, targetGrain) then
          // Grains differ — must have an algebraic morphism (aggregation) between them
          if m2.morphismType != MorphismType.Algebraic then
            return Left(IncompatibleGrains(sourceGrain, targetGrain,
              s"${m1.name} -> ${m2.name}: grains differ without aggregation"))
      case _ => ()
    }
    Right(())

  /** Validate multi-grain formulation (REQ-F-INT-005).
    *
    * Finer-grained attributes must be wrapped in explicit aggregation morphisms
    * aligned to the coarser entity's grain.
    */
  def checkMultiGrainFormula(
      sourceGrain: Grain,
      targetGrain: Grain,
      hasAggregation: Boolean
  ): Either[GrainError, Unit] =
    if Grain.canAggregateTo(sourceGrain, targetGrain) && !hasAggregation then
      Left(MissingAggregation(sourceGrain, targetGrain))
    else
      Right(())
