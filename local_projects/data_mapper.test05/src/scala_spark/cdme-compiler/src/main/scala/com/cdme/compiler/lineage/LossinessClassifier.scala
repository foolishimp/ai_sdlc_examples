package com.cdme.compiler.lineage
// Implements: REQ-DATA-LIN-002

import com.cdme.model._
import com.cdme.model.morphism.{StructuralMorphism, ComputationalMorphism, AlgebraicMorphism, OneToOne}
import com.cdme.model.adjoint.{AdjointPair, SelfAdjoint, LossyContainment, ContainmentType}

/**
 * Classification tag for whether a morphism preserves all information
 * (lossless) or discards some (lossy).
 *
 * This classification drives the checkpointing policy: lossy morphisms
 * require key envelope checkpoints for lineage reconstruction.
 */
sealed trait LossinessTag

/** The morphism preserves all information. `backward(forward(x)) = {x}`. */
case object Lossless extends LossinessTag

/** The morphism discards information. `backward(forward(x)) supseteq {x}`. */
case object Lossy extends LossinessTag

/**
 * Classifies morphisms as lossless or lossy based on their structure.
 *
 * Classification rules:
 * - **StructuralMorphism**: Classified by its adjoint containment type.
 *   [[SelfAdjoint]] = lossless, [[LossyContainment]] = lossy.
 * - **ComputationalMorphism**: Classified by cardinality and expression.
 *   OneToOne with deterministic expression = lossless; otherwise lossy.
 * - **AlgebraicMorphism**: Always lossy (aggregation discards individual values).
 */
object LossinessClassifier {

  /**
   * Classify a morphism as lossless or lossy.
   *
   * @param morphism the morphism to classify
   * @return [[Lossless]] or [[Lossy]]
   */
  def classify(morphism: Morphism): LossinessTag = morphism match {
    case s: StructuralMorphism =>
      classifyByAdjoint(s.adjoint)

    case c: ComputationalMorphism =>
      (c.cardinality, c.deterministic) match {
        case (OneToOne, true) => classifyByAdjoint(c.adjoint)
        case _                => Lossy
      }

    case _: AlgebraicMorphism =>
      // Aggregation always loses individual-level information
      Lossy
  }

  /**
   * Classify based on the adjoint pair's containment type.
   *
   * - [[SelfAdjoint]]: The forward and backward functions are true inverses.
   *   No information is lost. Lossless.
   * - [[LossyContainment]]: The backward function returns a superset.
   *   Information is lost. Lossy.
   */
  private def classifyByAdjoint(adjoint: AdjointPair[_, _]): LossinessTag =
    adjoint.containmentType match {
      case SelfAdjoint       => Lossless
      case LossyContainment  => Lossy
    }
}
