// Implements: REQ-F-PDM-004, REQ-F-PDM-005
package cdme.pdm

/**
 * Lookup binding: how a lookup entity is resolved.
 *
 * Lookup entities can be backed by physical data (tables) or by pure
 * functions (logic). Lookup immutability is enforced within epoch context
 * (REQ-BR-LDM-002).
 */
sealed trait LookupBinding {
  /** Human-readable description for lineage. */
  def description: String
}

/**
 * Data-backed lookup: resolved from a physical data source.
 *
 * @param source the physical source backing this lookup
 */
final case class DataBackedLookup(source: PhysicalSource) extends LookupBinding {
  override def description: String = s"DataBacked(${source.id.show})"
}

/**
 * Logic-backed lookup: resolved by a pure function.
 *
 * The function must be deterministic and side-effect-free.
 *
 * @param functionName  name of the pure function for lineage
 * @param functionImpl  the lookup function (input key -> output value)
 */
final case class LogicBackedLookup(
    functionName: String,
    functionImpl: String => Option[Any]
) extends LookupBinding {
  override def description: String = s"LogicBacked($functionName)"
}

/**
 * Lookup version semantics.
 *
 * Controls how lookup versions are resolved during execution.
 */
sealed trait LookupVersion {
  def description: String
}

object LookupVersion {
  /** Explicit version pinning. */
  final case class Explicit(version: String) extends LookupVersion {
    override def description: String = s"Explicit($version)"
  }

  /** Temporal version (as-of a specific epoch). */
  final case class Temporal(
      semantic: cdme.model.context.TemporalSemantic,
      epochRef: String
  ) extends LookupVersion {
    override def description: String = s"Temporal($semantic, $epochRef)"
  }

  /** Alias version (e.g., "latest-prod"). */
  final case class Alias(name: String) extends LookupVersion {
    override def description: String = s"Alias($name)"
  }
}
