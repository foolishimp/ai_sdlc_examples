// Implements: REQ-F-TRV-003, REQ-F-TRV-004, REQ-BR-LDM-002
package cdme.compiler

import cdme.model.context.{ContextFiber, TemporalSemantic}
import cdme.model.error.ValidationError

/**
 * Context consistency checker for path compilation.
 *
 * Validates that joins along a path have compatible context fibers.
 * Detects boundary crossings (epoch or partition) during path compilation
 * and emits specific errors with the crossing point identified.
 */
object ContextChecker:

  /**
   * Validate join compatibility between two context fibers.
   *
   * @param left  the context fiber of the left side of the join
   * @param right the context fiber of the right side of the join
   * @param stepDescription human-readable description of the join step
   * @return Right(()) if compatible, Left(error) otherwise
   */
  def checkJoinCompatibility(
      left: ContextFiber,
      right: ContextFiber,
      stepDescription: String
  ): Either[ValidationError, Unit] =
    left.isJoinCompatibleWith(right) match
      case Left(err) =>
        Left(ValidationError.ContextViolation(
          s"At step '$stepDescription': ${err.message}"
        ))
      case Right(_) =>
        Right(())

  /**
   * Detect boundary crossings in a sequence of context fibers.
   *
   * @param fibers ordered list of (stepDescription, contextFiber) along the path
   * @return list of boundary violations (empty if no crossings)
   */
  def detectBoundaryCrossings(
      fibers: List[(String, ContextFiber)]
  ): List[ValidationError] =
    fibers.sliding(2).collect {
      case List((desc1, fiber1), (desc2, fiber2)) =>
        fiber1.detectBoundaryCrossing(fiber2).map { violation =>
          ValidationError.BoundaryViolation(
            s"Boundary crossing between '$desc1' and '$desc2': ${violation.message}"
          )
        }
    }.flatten.toList

  /**
   * Validate temporal semantic compatibility for a join.
   *
   * Joins between entities with different temporal semantics require
   * explicit handling.
   *
   * @param leftSemantic  temporal semantic of the left entity
   * @param rightSemantic temporal semantic of the right entity
   * @return Right(()) if compatible, Left(warning) if semantics differ
   */
  def checkTemporalSemantics(
      leftSemantic: TemporalSemantic,
      rightSemantic: TemporalSemantic
  ): Either[ValidationError, Unit] =
    if leftSemantic == rightSemantic then
      Right(())
    else
      (leftSemantic, rightSemantic) match
        case (TemporalSemantic.Exact, _) | (_, TemporalSemantic.Exact) =>
          Left(ValidationError.ContextViolation(
            s"Temporal semantic mismatch: $leftSemantic vs $rightSemantic. " +
            "Joins involving Exact temporal semantics require matching semantics on both sides."
          ))
        case _ =>
          // AsOf and Latest can be mixed with a warning (not an error)
          Right(())
