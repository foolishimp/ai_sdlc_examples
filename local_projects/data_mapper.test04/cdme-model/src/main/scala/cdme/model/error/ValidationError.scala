// Implements: REQ-F-ERR-001, REQ-F-LDM-003
package cdme.model.error

sealed trait ValidationError {
  def message: String
}

object ValidationError {

  final case class MorphismNotFound(
      morphismName: String,
      override val message: String = ""
  ) extends ValidationError {
    override def toString: String =
      if (message.nonEmpty) message
      else s"Morphism not found: $morphismName"
  }

  final case class TypeMismatch(
      override val message: String
  ) extends ValidationError

  final case class GrainViolation(
      override val message: String
  ) extends ValidationError

  final case class AccessDenied(
      morphismName: String,
      principalId: String,
      override val message: String = ""
  ) extends ValidationError {
    override def toString: String =
      if (message.nonEmpty) message
      else s"Access denied: principal '$principalId' cannot access morphism '$morphismName'"
  }

  final case class ContextViolation(
      override val message: String
  ) extends ValidationError

  final case class BoundaryViolation(
      override val message: String
  ) extends ValidationError

  final case class BudgetExceeded(
      estimatedCardinality: Long,
      budget: Long,
      override val message: String = ""
  ) extends ValidationError {
    override def toString: String =
      if (message.nonEmpty) message
      else s"Cardinality budget exceeded: estimated $estimatedCardinality > budget $budget"
  }

  final case class General(
      override val message: String
  ) extends ValidationError
}
