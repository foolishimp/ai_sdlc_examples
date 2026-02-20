package com.cdme.compiler
// Implements: REQ-F-LDM-003, REQ-F-TRV-002, REQ-F-TRV-003, REQ-F-TRV-006, REQ-F-TYP-004,
//             REQ-F-CTX-001, REQ-F-SYN-001, REQ-F-SYN-006, REQ-BR-GRN-001

import com.cdme.model._
import com.cdme.model.entity.Role

/**
 * Sealed hierarchy of compile-time errors produced by the topological compiler.
 *
 * All errors are detected before any data is touched. The runtime trusts the
 * compiled plan and never re-validates these invariants.
 */
sealed trait CompilationError {
  /** Human-readable description of the failure. */
  def message: String
}

// ---------------------------------------------------------------------------
// Path errors
// ---------------------------------------------------------------------------

/** Error raised when a dot-path cannot be resolved against the LDM graph. */
sealed trait PathError extends CompilationError

/** A segment in the dot-path does not correspond to any morphism in the graph. */
case class MorphismNotFound(
  segment: String,
  availableMorphisms: List[String]
) extends PathError {
  override def message: String =
    s"Morphism '$segment' not found. Available: ${availableMorphisms.mkString(", ")}"
}

/** The codomain of one morphism does not match the domain of the next in the path. */
case class DomainCodomainMismatch(
  precedingMorphism: String,
  precedingCodomain: EntityId,
  followingMorphism: String,
  followingDomain: EntityId
) extends PathError {
  override def message: String =
    s"Codomain of '$precedingMorphism' ($precedingCodomain) does not match " +
      s"domain of '$followingMorphism' ($followingDomain)"
}

/** The path is empty (no segments provided). */
case object EmptyPath extends PathError {
  override def message: String = "Path must contain at least one segment"
}

// ---------------------------------------------------------------------------
// Grain errors
// ---------------------------------------------------------------------------

/** Error raised when grain safety is violated. */
sealed trait GrainError extends CompilationError

/** Two grains along a path are incompatible without an intervening aggregation. */
case class IncompatibleGrains(
  finerGrain: Grain,
  coarserGrain: Grain,
  morphismId: String
) extends GrainError {
  override def message: String =
    s"Grain '$finerGrain' is incompatible with '$coarserGrain' at morphism '$morphismId' " +
      "without an intervening algebraic (aggregation) morphism"
}

/** A multi-grain expression references grains that cannot be composed. */
case class MultiGrainConflict(
  expressionDesc: String,
  contextGrain: Grain,
  conflictingGrain: Grain
) extends GrainError {
  override def message: String =
    s"Expression '$expressionDesc' mixes grain '$conflictingGrain' into context grain '$contextGrain' " +
      "without proper aggregation"
}

// ---------------------------------------------------------------------------
// Type errors
// ---------------------------------------------------------------------------

/** Error raised when type unification fails at a composition boundary. */
sealed trait TypeError extends CompilationError

/** Two types cannot be unified. */
case class TypeUnificationFailure(
  expected: CdmeType,
  actual: CdmeType,
  context: String
) extends TypeError {
  override def message: String =
    s"Cannot unify type '$actual' with expected '$expected' at $context"
}

// ---------------------------------------------------------------------------
// Access control errors
// ---------------------------------------------------------------------------

/** Error raised when a principal lacks permission for a morphism. */
sealed trait AccessError extends CompilationError

/** The principal does not have any of the required roles. */
case class AccessDeniedError(
  morphismId: String,
  requiredRoles: Set[Role],
  principalRoles: Set[Role]
) extends AccessError {
  override def message: String =
    s"Access denied for morphism '$morphismId'. " +
      s"Required roles: ${requiredRoles.mkString(", ")}. " +
      s"Principal has: ${principalRoles.mkString(", ")}"
}

// ---------------------------------------------------------------------------
// Budget / cost errors
// ---------------------------------------------------------------------------

/** Error raised when estimated cost exceeds the configured budget. */
case class BudgetExceeded(
  dimension: String,
  estimated: Long,
  budget: Long
) extends CompilationError {
  override def message: String =
    s"Budget exceeded on '$dimension': estimated $estimated > budget $budget"
}

// ---------------------------------------------------------------------------
// Fiber errors
// ---------------------------------------------------------------------------

/** Error raised when fiber compatibility (temporal alignment) fails. */
sealed trait FiberError extends CompilationError

/** Two stages reference different epochs with no declared temporal semantics. */
case class EpochMismatch(
  leftEpoch: Epoch,
  rightEpoch: Epoch
) extends FiberError {
  override def message: String =
    s"Epoch mismatch: left='$leftEpoch', right='$rightEpoch'. " +
      "Declare temporal semantics (AsOf, Latest, Exact) for cross-epoch joins."
}

// ---------------------------------------------------------------------------
// Synthesis errors
// ---------------------------------------------------------------------------

/** Error raised when a synthesis expression is invalid. */
sealed trait SynthesisError extends CompilationError

/** An expression references an attribute that is not reachable. */
case class UnreachableAttribute(
  attributeName: String,
  availableAttributes: Set[String]
) extends SynthesisError {
  override def message: String =
    s"Attribute '$attributeName' is not reachable. " +
      s"Available: ${availableAttributes.mkString(", ")}"
}

/** An expression applies a function to incompatible argument types. */
case class SynthesisTypeError(
  functionName: String,
  expectedArgTypes: List[CdmeType],
  actualArgTypes: List[CdmeType]
) extends SynthesisError {
  override def message: String =
    s"Function '$functionName' expects types [${expectedArgTypes.mkString(", ")}] " +
      s"but received [${actualArgTypes.mkString(", ")}]"
}

// ---------------------------------------------------------------------------
// Lookup version errors
// ---------------------------------------------------------------------------

/** Error raised when a lookup reference lacks version semantics. */
sealed trait LookupVersionError extends CompilationError

/** A lookup is referenced without any version pinning strategy. */
case class UnversionedLookup(
  lookupId: String
) extends LookupVersionError {
  override def message: String =
    s"Lookup '$lookupId' has no version semantics. " +
      "Every lookup must specify ExplicitVersion, AsOfEpoch, or DeterministicAlias."
}
