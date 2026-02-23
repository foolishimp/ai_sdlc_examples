// Implements: REQ-ADJ-01, REQ-ADJ-02
// Validates Galois connection containment laws.
package com.cdme.adjoint

import com.cdme.model.adjoint.AdjointClassification
import com.cdme.model.error.{CdmeError, ValidationError}

/**
 * Validates that adjoint pairs satisfy the Galois connection containment laws:
 *   backward(forward(x)) supseteq {x}   (lower closure)
 *   forward(backward(y)) subseteq {y}    (upper closure)
 *
 * For Isomorphisms, both become equalities.
 *
 * Implements: REQ-ADJ-01, REQ-ADJ-02
 */
object GaloisValidator {

  /**
   * Validate the lower closure law: backward(forward(x)) supseteq {x}.
   */
  def validateLowerClosure[A, B](
      adjoint: Adjoint[A, B],
      samples: Set[A]
  ): Either[CdmeError, GaloisValidationResult] = {
    adjoint.validateContainmentLaw(samples).map { result =>
      GaloisValidationResult(
        lowerClosureHolds = result.isValid,
        sampleSize = result.sampleSize,
        violationCount = result.failedCount,
        classification = adjoint.classification
      )
    }
  }

  /**
   * Validate the upper closure law: forward(backward(y)) subseteq {y}.
   */
  def validateUpperClosure[A, B](
      adjoint: Adjoint[A, B],
      samples: Set[B]
  ): Either[CdmeError, GaloisValidationResult] = {
    var violations = 0
    samples.foreach { y =>
      adjoint.backward(y) match {
        case Right(as) =>
          as.foreach { a =>
            adjoint.forward(a) match {
              case Right(fy) if fy != y => violations += 1
              case Left(_)              => violations += 1
              case _                    => // ok
            }
          }
        case Left(_) => violations += 1
      }
    }

    Right(GaloisValidationResult(
      lowerClosureHolds = true,
      sampleSize = samples.size,
      violationCount = violations,
      classification = adjoint.classification
    ))
  }

  /**
   * For isomorphisms, validate exact round-trip equality.
   */
  def validateIsomorphism[A, B](
      adjoint: Adjoint[A, B],
      samples: Set[A]
  ): Either[CdmeError, Boolean] = {
    if (adjoint.classification != AdjointClassification.Isomorphism) {
      return Left(ValidationError(s"Expected Isomorphism but got ${adjoint.classification}"))
    }

    val allExact = samples.forall { x =>
      (for {
        fx  <- adjoint.forward(x)
        bfx <- adjoint.backward(fx)
      } yield bfx == Set(x)).getOrElse(false)
    }
    Right(allExact)
  }
}

/** Result of a Galois connection validation. */
final case class GaloisValidationResult(
    lowerClosureHolds: Boolean,
    sampleSize: Int,
    violationCount: Int,
    classification: AdjointClassification
)
