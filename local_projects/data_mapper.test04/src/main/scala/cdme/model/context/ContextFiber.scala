// Implements: REQ-F-TRV-003, REQ-F-TRV-004, REQ-F-PDM-002, REQ-BR-LDM-002
package cdme.model.context

import cdme.model.error.ValidationError

/**
 * A composite context fiber attached to entity instances in the execution graph.
 *
 * The context fiber binds together the epoch, partition scope, and temporal
 * semantic. Every entity instance in a running execution carries a context fiber,
 * which is used by the ContextChecker to validate join compatibility and detect
 * boundary crossings.
 *
 * @param epoch            the processing epoch
 * @param partitionScope   the partition scope identifier (e.g., "region=US")
 * @param temporalSemantic how time-varying data is resolved
 */
final case class ContextFiber(
    epoch: Epoch,
    partitionScope: String,
    temporalSemantic: TemporalSemantic
):
  /**
   * Check whether this context fiber is compatible with another for joining.
   *
   * Two fibers are join-compatible if:
   *   1. They share the same epoch (or temporal semantics are explicitly declared)
   *   2. Partition scopes are compatible (same or one is universal)
   *
   * @param other the other context fiber
   * @return Right(()) if compatible, Left(error) with the incompatibility reason
   */
  def isJoinCompatibleWith(other: ContextFiber): Either[ValidationError, Unit] =
    val epochCheck =
      if epoch.id == other.epoch.id then Right(())
      else Left(ValidationError.ContextViolation(
        s"Epoch mismatch: ${epoch.id.show} vs ${other.epoch.id.show}. " +
        "Cross-epoch joins require explicit temporal semantic declaration."
      ))

    val partitionCheck =
      if partitionScope == other.partitionScope then Right(())
      else if partitionScope == "*" || other.partitionScope == "*" then Right(())
      else Left(ValidationError.ContextViolation(
        s"Partition scope mismatch: '$partitionScope' vs '${other.partitionScope}'"
      ))

    for
      _ <- epochCheck
      _ <- partitionCheck
    yield ()

  /**
   * Detect a boundary crossing with another fiber.
   *
   * @param other the target fiber
   * @return Some(violation) if a boundary is crossed, None otherwise
   */
  def detectBoundaryCrossing(other: ContextFiber): Option[ValidationError] =
    if epoch.id != other.epoch.id then
      Some(ValidationError.BoundaryViolation(
        s"Epoch boundary crossing detected: ${epoch.id.show} -> ${other.epoch.id.show}"
      ))
    else if partitionScope != other.partitionScope &&
            partitionScope != "*" && other.partitionScope != "*" then
      Some(ValidationError.BoundaryViolation(
        s"Partition boundary crossing: '$partitionScope' -> '${other.partitionScope}'"
      ))
    else
      None

object ContextFiber:
  /** Universal partition scope that is compatible with all scopes. */
  val UniversalPartition: String = "*"
