// Implements: REQ-F-TRV-003, REQ-F-TRV-004, REQ-F-PDM-002, REQ-BR-LDM-002
package cdme.model.context

import cdme.model.error.ValidationError

final case class ContextFiber(
    epoch: Epoch,
    partitionScope: String,
    temporalSemantic: TemporalSemantic
) {
  def isJoinCompatibleWith(other: ContextFiber): Either[ValidationError, Unit] = {
    val epochCheck =
      if (epoch.id == other.epoch.id) Right(())
      else Left(ValidationError.ContextViolation(
        s"Epoch mismatch: ${epoch.id.show} vs ${other.epoch.id.show}. " +
        "Cross-epoch joins require explicit temporal semantic declaration."
      ))

    val partitionCheck =
      if (partitionScope == other.partitionScope) Right(())
      else if (partitionScope == "*" || other.partitionScope == "*") Right(())
      else Left(ValidationError.ContextViolation(
        s"Partition scope mismatch: '$partitionScope' vs '${other.partitionScope}'"
      ))

    for {
      _ <- epochCheck
      _ <- partitionCheck
    } yield ()
  }

  def detectBoundaryCrossing(other: ContextFiber): Option[ValidationError] =
    if (epoch.id != other.epoch.id)
      Some(ValidationError.BoundaryViolation(
        s"Epoch boundary crossing detected: ${epoch.id.show} -> ${other.epoch.id.show}"
      ))
    else if (partitionScope != other.partitionScope &&
            partitionScope != "*" && other.partitionScope != "*")
      Some(ValidationError.BoundaryViolation(
        s"Partition boundary crossing: '$partitionScope' -> '${other.partitionScope}'"
      ))
    else
      None
}

object ContextFiber {
  val UniversalPartition: String = "*"
}
