package cdme.model

// Implements: REQ-F-LDM-002, REQ-F-LDM-003, REQ-F-LDM-005, REQ-F-ACC-003

/** A morphism represents any directed connection in the LDM.
  *
  * Three types: Structural (FK edges), Computational (pure functions),
  * Algebraic (folds/aggregations).
  */
case class Morphism(
    name: String,
    domain: Entity,
    codomain: Entity,
    cardinality: Cardinality,
    morphismType: MorphismType,
    accessControl: AccessControl = AccessControl.open,
    adjointStrategy: Option[AdjointStrategy] = None // REQ-F-ACC-003
)

/** Cardinality types for morphisms (REQ-F-LDM-002). */
enum Cardinality:
  case OneToOne   // Isomorphism/Bijection
  case NToOne     // Standard Function
  case OneToN     // Kleisli Arrow / List Monad

/** Morphism classification (structural, computational, algebraic). */
enum MorphismType:
  case Structural    // Foreign key edges
  case Computational // Pure functions / derivations
  case Algebraic     // Folds / aggregations (must satisfy Monoid laws)

/** Access control on morphisms (REQ-F-LDM-005).
  * If a principal lacks permission, the morphism does not exist in their topology view.
  */
case class AccessControl(roles: Set[String])
object AccessControl:
  val open: AccessControl = AccessControl(Set.empty)

/** Adjoint strategy for reverse transformations (REQ-F-ACC-003). */
enum AdjointStrategy:
  case ExactInverse    // 1:1 isomorphism
  case PreimageLookup  // N:1 pure function
  case ReverseJoin     // Aggregation reverse-join table
  case FilterLog       // Filter exclusion log
