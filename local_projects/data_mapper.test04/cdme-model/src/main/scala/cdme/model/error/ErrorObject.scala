// Implements: REQ-F-ERR-001, REQ-F-ERR-002, REQ-F-ERR-003, REQ-BR-ERR-001
// ADR-004: Either Monad for Error Handling
package cdme.model.error

import java.time.Instant
import cdme.model.category.{EntityId, MorphismId}
import cdme.model.context.EpochId

sealed trait ConstraintType
object ConstraintType {
  case object TypeConstraint extends ConstraintType
  case object GrainConstraint extends ConstraintType
  case object RefinementPredicate extends ConstraintType
  case object AccessControl extends ConstraintType
  case object ContextBoundary extends ConstraintType
  case object AccountingInvariant extends ConstraintType
  case object ExternalCalculator extends ConstraintType
  case class Custom(name: String) extends ConstraintType
}

final case class ErrorObject(
    constraintType: ConstraintType,
    offendingValues: Map[String, String],
    sourceEntity: EntityId,
    sourceEpoch: EpochId,
    morphismPath: List[MorphismId],
    timestamp: Instant,
    details: Option[String] = None
)
