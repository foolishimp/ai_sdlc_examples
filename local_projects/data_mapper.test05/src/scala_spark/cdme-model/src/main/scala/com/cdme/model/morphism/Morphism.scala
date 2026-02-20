package com.cdme.model.morphism

// Implements: REQ-F-LDM-002, REQ-F-ADJ-001

import com.cdme.model.adjoint.AdjointPair
import com.cdme.model.entity.{AccessControlList, EntityId}
import com.cdme.model.grain.Grain
import com.cdme.model.monoid.Monoid
import com.cdme.model.types.CdmeType
import com.cdme.model.version.ArtifactVersion

/**
 * Unique identifier for a [[Morphism]] (edge) within an LDM graph.
 *
 * @param value the string identifier (e.g. "trade_to_counterparty")
 */
case class MorphismId(value: String)

/**
 * Cardinality classification for a morphism.
 *
 * Cardinality determines how the runtime handles record multiplicity:
 *  - [[OneToOne]]: each input produces exactly one output
 *  - [[ManyToOne]]: multiple inputs collapse to one output (requires monoid)
 *  - [[OneToMany]]: each input may produce multiple outputs (Kleisli lifting)
 */
sealed trait Cardinality

/** Exactly one output per input. Isomorphic relationship. */
case object OneToOne extends Cardinality

/** Multiple inputs map to one output. Requires monoidal aggregation. */
case object ManyToOne extends Cardinality

/** One input may produce multiple outputs. Requires Kleisli context lifting. */
case object OneToMany extends Cardinality

/**
 * A Morphism is an edge in the LDM directed multigraph.
 *
 * Morphisms represent typed, directed relationships between entities.
 * Every morphism carries:
 *  - An identity ([[MorphismId]])
 *  - Domain and codomain entities ([[EntityId]])
 *  - Cardinality classification
 *  - An adjoint pair (forward + backward functions)
 *  - Optional RBAC
 *  - Version metadata
 *
 * The sealed hierarchy has three subtypes reflecting the three kinds
 * of data transformation: structural (joins), computational (expressions),
 * and algebraic (aggregations).
 */
sealed trait Morphism {
  /** Unique identifier for this morphism. */
  def id: MorphismId

  /** The source entity (domain of the function). */
  def domain: EntityId

  /** The target entity (codomain of the function). */
  def codomain: EntityId

  /** Cardinality classification (1:1, N:1, 1:N). */
  def cardinality: Cardinality

  /** The adjoint pair providing forward and backward traversal. */
  def adjoint: AdjointPair[Any, Any]

  /** Optional access control restricting who may traverse this morphism. */
  def acl: Option[AccessControlList]

  /** Artifact version metadata. */
  def version: ArtifactVersion
}

/**
 * A structural morphism represents a join between entities.
 *
 * The join condition specifies how records from the domain entity
 * are matched to records in the codomain entity.
 *
 * @param id            morphism identifier
 * @param domain        source entity
 * @param codomain      target entity
 * @param cardinality   cardinality classification
 * @param adjoint       forward/backward function pair
 * @param joinCondition the condition under which domain records match codomain records
 * @param acl           optional access control
 * @param version       artifact version
 */
case class StructuralMorphism(
  id: MorphismId,
  domain: EntityId,
  codomain: EntityId,
  cardinality: Cardinality,
  adjoint: AdjointPair[Any, Any],
  joinCondition: JoinCondition,
  acl: Option[AccessControlList] = None,
  version: ArtifactVersion
) extends Morphism

/**
 * A computational morphism represents an expression-based transformation.
 *
 * The expression defines how domain attributes are combined or transformed
 * to produce codomain attributes. The `deterministic` flag indicates whether
 * the same inputs always produce the same outputs.
 *
 * @param id            morphism identifier
 * @param domain        source entity
 * @param codomain      target entity
 * @param cardinality   cardinality classification
 * @param adjoint       forward/backward function pair
 * @param expression    the transformation expression
 * @param deterministic whether this morphism is deterministic (same input = same output)
 * @param acl           optional access control
 * @param version       artifact version
 */
case class ComputationalMorphism(
  id: MorphismId,
  domain: EntityId,
  codomain: EntityId,
  cardinality: Cardinality,
  adjoint: AdjointPair[Any, Any],
  expression: Expression,
  deterministic: Boolean,
  acl: Option[AccessControlList] = None,
  version: ArtifactVersion
) extends Morphism

/**
 * An algebraic morphism represents a monoidal aggregation.
 *
 * Algebraic morphisms always change the grain of the data from finer
 * to coarser, using a declared monoid to combine values.
 *
 * @param id          morphism identifier
 * @param domain      source entity
 * @param codomain    target entity
 * @param cardinality cardinality classification (typically ManyToOne)
 * @param adjoint     forward/backward function pair
 * @param monoid      the monoid used for aggregation
 * @param targetGrain the grain of the codomain entity
 * @param acl         optional access control
 * @param version     artifact version
 */
case class AlgebraicMorphism(
  id: MorphismId,
  domain: EntityId,
  codomain: EntityId,
  cardinality: Cardinality,
  adjoint: AdjointPair[Any, Any],
  monoid: Monoid[Any],
  targetGrain: Grain,
  acl: Option[AccessControlList] = None,
  version: ArtifactVersion
) extends Morphism

// ---------------------------------------------------------------------------
// Join conditions
// ---------------------------------------------------------------------------

/**
 * Specifies how records from two entities are matched in a structural join.
 */
sealed trait JoinCondition

/**
 * An equi-join on two named columns.
 *
 * @param leftCol  column name in the domain entity
 * @param rightCol column name in the codomain entity
 */
case class ColumnEquality(leftCol: String, rightCol: String) extends JoinCondition

// ---------------------------------------------------------------------------
// Expressions (for computational morphisms)
// ---------------------------------------------------------------------------

/**
 * An expression tree used in computational morphisms to define
 * how domain attributes are transformed into codomain attributes.
 */
sealed trait Expression

/**
 * A reference to a named column in the domain entity.
 *
 * @param name the column name
 */
case class ColumnRef(name: String) extends Expression

/**
 * A literal constant value.
 *
 * @param value   the constant value
 * @param cdmeType the CDME type of this literal
 */
case class Literal(value: Any, cdmeType: CdmeType) extends Expression

/**
 * A named function applied to a list of argument expressions.
 *
 * @param name the function name (e.g. "UPPER", "ABS", "COALESCE")
 * @param args the arguments to the function
 */
case class FunctionCall(name: String, args: List[Expression]) extends Expression

/**
 * A conditional (if-then-else) expression.
 *
 * @param condition the boolean condition expression
 * @param ifTrue    the expression evaluated when condition is true
 * @param ifFalse   the expression evaluated when condition is false
 */
case class Conditional(
  condition: Expression,
  ifTrue: Expression,
  ifFalse: Expression
) extends Expression

/**
 * A priority expression — evaluates options in order, returning the
 * first non-null/non-error result. Similar to SQL COALESCE but
 * generalized to arbitrary expressions.
 *
 * @param options ordered list of expressions to try
 */
case class Priority(options: List[Expression]) extends Expression
