// Implements: REQ-F-ERR-001, REQ-F-LDM-003
package cdme.model.error

/**
 * Definition-time validation errors.
 *
 * These errors are raised during path compilation, type checking, grain checking,
 * and other definition-time validation phases. They are structured data (REQ-F-ERR-001),
 * not exceptions.
 *
 * The error hierarchy corresponds to the validation checks performed by the
 * Path Compiler (Section 2.7 of the design).
 */
sealed trait ValidationError:
  /** Human-readable error message. */
  def message: String

object ValidationError:

  /** A referenced morphism does not exist in the category. */
  final case class MorphismNotFound(
      morphismName: String,
      override val message: String = ""
  ) extends ValidationError:
    override def toString: String =
      if message.nonEmpty then message
      else s"Morphism not found: $morphismName"

  /** Types are incompatible for composition. */
  final case class TypeMismatch(
      override val message: String
  ) extends ValidationError

  /** Grain levels are incompatible without aggregation. */
  final case class GrainViolation(
      override val message: String
  ) extends ValidationError

  /** Principal lacks permission to access a morphism. */
  final case class AccessDenied(
      morphismName: String,
      principalId: String,
      override val message: String = ""
  ) extends ValidationError:
    override def toString: String =
      if message.nonEmpty then message
      else s"Access denied: principal '$principalId' cannot access morphism '$morphismName'"

  /** Context fibers are incompatible (epoch mismatch, temporal semantic conflict). */
  final case class ContextViolation(
      override val message: String
  ) extends ValidationError

  /** A traversal crosses an epoch or partition boundary. */
  final case class BoundaryViolation(
      override val message: String
  ) extends ValidationError

  /** Estimated cardinality exceeds the declared budget. */
  final case class BudgetExceeded(
      estimatedCardinality: Long,
      budget: Long,
      override val message: String = ""
  ) extends ValidationError:
    override def toString: String =
      if message.nonEmpty then message
      else s"Cardinality budget exceeded: estimated $estimatedCardinality > budget $budget"

  /** A generic validation error for cases not covered by specific subtypes. */
  final case class General(
      override val message: String
  ) extends ValidationError
