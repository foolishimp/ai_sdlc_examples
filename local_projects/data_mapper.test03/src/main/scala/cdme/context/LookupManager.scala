package cdme.context

// Implements: REQ-F-INT-006, REQ-F-PDM-004, REQ-NFR-SEC-001

/** Lookup manager enforcing versioned lookup semantics.
  *
  * All reference data usage must specify version semantics.
  * Unversioned lookups are rejected (REQ-F-INT-006).
  * Lookups are immutable within an execution context (REQ-NFR-SEC-001).
  */
object LookupManager:

  /** Version semantics for a lookup reference. */
  enum VersionSemantics:
    case ExplicitVersion(version: String)
    case TemporalConstraint(asOf: String)  // e.g., "as-of epoch"
    case DeterministicAlias(alias: String)  // e.g., "Production", "Latest"

  /** A lookup reference with required version semantics. */
  case class LookupRef(
      name: String,
      backing: LookupBacking,
      versionSemantics: Option[VersionSemantics]
  )

  enum LookupBacking:
    case DataBacked(source: String) // Physical table/file
    case LogicBacked(function: String) // Computed/static

  sealed trait LookupError
  case class UnversionedLookup(name: String) extends LookupError
  case class LookupNotFound(name: String, version: String) extends LookupError

  /** Validate that lookup has version semantics (REQ-F-INT-006). */
  def validateVersioning(ref: LookupRef): Either[LookupError, LookupRef] =
    ref.versionSemantics match
      case None => Left(UnversionedLookup(ref.name))
      case Some(_) => Right(ref)
