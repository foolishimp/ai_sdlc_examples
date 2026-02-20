package cdme.context

// Implements: REQ-F-SHF-001, REQ-F-TRV-003, REQ-F-PDM-002, REQ-F-PDM-003

/** Sheaf-like context manager for epoch and partition scope enforcement.
  *
  * Joins must respect contextual fibers: same epoch or explicit temporal semantics,
  * compatible partitioning scopes (REQ-F-SHF-001).
  */
object SheafContextManager:

  /** A contextual fiber: the local section of data. */
  case class Fiber(
      entity: String,
      epoch: String,           // e.g., "2024-01-10"
      partitionScope: String,  // e.g., "global", "region=US"
      generationGrain: GenerationGrain
  )

  /** Generation grain: Event or Snapshot (REQ-F-PDM-002). */
  enum GenerationGrain:
    case Event    // Continuous stream sliced by temporal windows
    case Snapshot // State at a point in time

  /** Temporal semantics for cross-boundary joins (REQ-F-TRV-003). */
  enum TemporalSemantics:
    case AsOf       // State as-of a specific time
    case Latest     // Most recent version
    case Exact      // Exact epoch match required

  sealed trait ContextError
  case class EpochMismatch(left: String, right: String) extends ContextError
  case class PartitionMismatch(left: String, right: String) extends ContextError
  case class MissingTemporalSemantics(left: Fiber, right: Fiber) extends ContextError

  /** Validate join context compatibility (REQ-F-SHF-001). */
  def validateJoinContext(
      left: Fiber,
      right: Fiber,
      declaredSemantics: Option[TemporalSemantics] = None
  ): Either[ContextError, Unit] =
    // Same epoch: always compatible
    if left.epoch == right.epoch then
      // Check partition scope compatibility
      if left.partitionScope == right.partitionScope
        || left.partitionScope == "global"
        || right.partitionScope == "global"
      then Right(())
      else Left(PartitionMismatch(left.partitionScope, right.partitionScope))
    // Different epoch: require declared temporal semantics (REQ-F-TRV-003)
    else
      declaredSemantics match
        case Some(_) => Right(()) // Explicit semantics declared
        case None => Left(MissingTemporalSemantics(left, right))
