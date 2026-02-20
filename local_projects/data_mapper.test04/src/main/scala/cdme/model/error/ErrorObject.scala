// Implements: REQ-F-ERR-001, REQ-F-ERR-002, REQ-F-ERR-003, REQ-BR-ERR-001
// ADR-004: Either Monad for Error Handling
package cdme.model.error

import java.time.Instant

/** Opaque type for entity identifiers within error metadata. */
opaque type EntityId = String
object EntityId:
  def apply(value: String): EntityId = value
  extension (id: EntityId) def value: String = id

/** Opaque type for epoch identifiers within error metadata. */
opaque type EpochId = String
object EpochId:
  def apply(value: String): EpochId = value
  extension (id: EpochId) def value: String = id

/** Opaque type for morphism identifiers within error metadata. */
opaque type MorphismId = String
object MorphismId:
  def apply(value: String): MorphismId = value
  extension (id: MorphismId) def value: String = id

/**
 * Classification of constraint types that can generate errors.
 */
enum ConstraintType:
  case TypeConstraint
  case GrainConstraint
  case RefinementPredicate
  case AccessControl
  case ContextBoundary
  case AccountingInvariant
  case ExternalCalculator
  case Custom(name: String)

/**
 * A structured runtime error object.
 *
 * Every morphism failure produces an ErrorObject rather than throwing an exception
 * (ADR-004). Error objects are first-class data that flow through the Error Router
 * to the declared Error Sink.
 *
 * Error handling is deterministic and idempotent: the same input with the same
 * configuration produces bitwise-identical error output (REQ-F-ERR-003).
 *
 * @param constraintType  the kind of constraint that was violated
 * @param offendingValues the specific values that caused the violation
 * @param sourceEntity    the entity where the error originated
 * @param sourceEpoch     the epoch context of the error
 * @param morphismPath    the chain of morphism IDs leading to the error
 * @param timestamp       when the error was detected
 * @param details         optional additional diagnostic information
 */
final case class ErrorObject(
    constraintType: ConstraintType,
    offendingValues: Map[String, String],
    sourceEntity: EntityId,
    sourceEpoch: EpochId,
    morphismPath: List[MorphismId],
    timestamp: Instant,
    details: Option[String] = None
)
