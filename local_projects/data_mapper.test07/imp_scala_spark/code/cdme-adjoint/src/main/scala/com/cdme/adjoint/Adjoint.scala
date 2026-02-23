// Implements: REQ-ADJ-01
// Core adjoint interface: forward/backward with containment laws.
package com.cdme.adjoint

import com.cdme.model.adjoint.AdjointClassification
import com.cdme.model.error.CdmeError

/**
 * The core adjoint interface: every morphism f: A -> B has a corresponding
 * backward morphism f^-: B -> Set[A] forming a Galois connection.
 *
 * Containment laws:
 *   backward(forward(x)) supseteq x   (lower closure)
 *   forward(backward(y)) subseteq y   (upper closure)
 *
 * Implements: REQ-ADJ-01
 * See ADR-004: Adjoint Morphism Design
 */
trait Adjoint[A, B] {
  /** Forward morphism: A -> B */
  def forward(a: A): Either[CdmeError, B]

  /** Backward morphism: B -> Set[A] */
  def backward(b: B): Either[CdmeError, Set[A]]

  /** Classification of this adjoint pair. */
  def classification: AdjointClassification

  /**
   * Validate the containment law: backward(forward(x)) supseteq {x}.
   * Returns the containment check result.
   */
  def validateContainmentLaw(
      sample: Set[A]
  ): Either[CdmeError, ContainmentResult[A]] = {
    val results = sample.toList.map { x =>
      for {
        fx    <- forward(x)
        bfx   <- backward(fx)
      } yield (x, bfx)
    }

    val errors = results.collect { case Left(e) => e }
    if (errors.nonEmpty) return Left(errors.head)

    val pairs = results.collect { case Right(pair) => pair }
    val violations = pairs.filter { case (x, bfx) => !bfx.contains(x) }

    Right(ContainmentResult(
      sampleSize = sample.size,
      passedCount = pairs.size - violations.size,
      failedCount = violations.size,
      violations = violations.map { case (x, bfx) => ContainmentViolation(x, bfx) },
      isValid = violations.isEmpty
    ))
  }
}

/** Result of a containment law validation. */
final case class ContainmentResult[A](
    sampleSize: Int,
    passedCount: Int,
    failedCount: Int,
    violations: List[ContainmentViolation[A]],
    isValid: Boolean
)

/** A single containment law violation. */
final case class ContainmentViolation[A](
    originalInput: A,
    roundTripResult: Set[A]
)
