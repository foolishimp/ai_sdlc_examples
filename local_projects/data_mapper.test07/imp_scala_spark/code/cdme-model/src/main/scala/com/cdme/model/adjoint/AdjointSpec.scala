// Implements: REQ-ADJ-01, REQ-ADJ-03, REQ-ADJ-04, REQ-ADJ-05, REQ-ADJ-06
// Adjoint specification types for morphisms.
package com.cdme.model.adjoint

import com.cdme.model.MorphismId

/**
 * Specification for the adjoint (backward morphism) of a forward morphism.
 * Implements: REQ-ADJ-01
 */
sealed trait AdjointSpec {
  def classification: AdjointClassification
}

/**
 * Self-adjoint: the morphism has an exact inverse.
 * Implements: REQ-ADJ-03
 */
final case class SelfAdjoint(
    inverseMorphismId: MorphismId
) extends AdjointSpec {
  val classification: AdjointClassification = AdjointClassification.Isomorphism
}

/**
 * Aggregation adjoint: backward via reverse-join metadata.
 * Implements: REQ-ADJ-04
 */
final case class AggregationAdjoint(
    reverseJoinStrategy: ReverseJoinStrategy
) extends AdjointSpec {
  val classification: AdjointClassification = AdjointClassification.Projection
}

/**
 * Filter adjoint: backward via filtered key capture.
 * Implements: REQ-ADJ-05
 */
final case class FilterAdjoint(
    captureFilteredKeys: Boolean
) extends AdjointSpec {
  val classification: AdjointClassification =
    if (captureFilteredKeys) AdjointClassification.Embedding
    else AdjointClassification.Lossy
}

/**
 * Kleisli adjoint: backward via parent-child collapse.
 * Implements: REQ-ADJ-06
 */
final case class KleisliAdjoint(
    parentChildCapture: Boolean
) extends AdjointSpec {
  val classification: AdjointClassification = AdjointClassification.Projection
}

/**
 * Storage strategies for reverse-join metadata.
 * Implements: REQ-ADJ-11
 */
sealed trait ReverseJoinStrategy
object ReverseJoinStrategy {
  case object Inline        extends ReverseJoinStrategy
  case object SeparateTable extends ReverseJoinStrategy
  case object Compressed    extends ReverseJoinStrategy
}
