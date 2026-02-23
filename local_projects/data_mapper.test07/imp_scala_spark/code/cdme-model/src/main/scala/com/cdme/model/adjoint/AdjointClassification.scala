// Implements: REQ-ADJ-02
// Adjoint classification: the precision of the round-trip.
package com.cdme.model.adjoint

/**
 * Every morphism is classified by its round-trip precision.
 *
 * - Isomorphism: f^-(f(x)) = x and f(f^-(y)) = y
 * - Embedding:   f^-(f(x)) = x but f(f^-(y)) subset y
 * - Projection:  f^-(f(x)) supset x but f(f^-(y)) = y
 * - Lossy:       f^-(f(x)) supset x and f(f^-(y)) subset y
 *
 * Implements: REQ-ADJ-02
 */
sealed trait AdjointClassification

object AdjointClassification {
  case object Isomorphism extends AdjointClassification
  case object Embedding   extends AdjointClassification
  case object Projection  extends AdjointClassification
  case object Lossy       extends AdjointClassification

  /**
   * Compose two classifications: the result classification of (g . f).
   * Implements: REQ-ADJ-07
   */
  def compose(
      fClass: AdjointClassification,
      gClass: AdjointClassification
  ): AdjointClassification = (fClass, gClass) match {
    case (Isomorphism, other)      => other
    case (other, Isomorphism)      => other
    case (Embedding, Embedding)    => Embedding
    case (Projection, Projection)  => Projection
    case _                         => Lossy
  }
}
