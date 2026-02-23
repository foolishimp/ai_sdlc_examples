// Implements: REQ-ADJ-07
// Adjoint composition: (g . f)^- = f^- . g^-
package com.cdme.adjoint

import com.cdme.model.adjoint.AdjointClassification
import com.cdme.model.error.CdmeError

/**
 * Composes adjoint pairs. The backward of a composition follows the
 * contravariant rule: (g . f)^- = f^- . g^-
 *
 * Implements: REQ-ADJ-07
 */
object AdjointComposer {

  /**
   * Compose two adjoint pairs into a single adjoint pair.
   * Forward: g(f(x))
   * Backward: f^-(g^-(y))
   */
  def compose[A, B, C](
      f: Adjoint[A, B],
      g: Adjoint[B, C]
  ): Adjoint[A, C] = new Adjoint[A, C] {

    def forward(a: A): Either[CdmeError, C] =
      f.forward(a).flatMap(g.forward)

    def backward(c: C): Either[CdmeError, Set[A]] =
      g.backward(c).flatMap { bs =>
        val results = bs.toList.map(f.backward)
        val errors = results.collect { case Left(e) => e }
        if (errors.nonEmpty) Left(errors.head)
        else Right(results.collect { case Right(as) => as }.flatten.toSet)
      }

    def classification: AdjointClassification =
      AdjointClassification.compose(f.classification, g.classification)
  }

  /**
   * Classify the composition of two adjoint classifications.
   */
  def classifyComposition(
      fClass: AdjointClassification,
      gClass: AdjointClassification
  ): AdjointClassification =
    AdjointClassification.compose(fClass, gClass)
}
