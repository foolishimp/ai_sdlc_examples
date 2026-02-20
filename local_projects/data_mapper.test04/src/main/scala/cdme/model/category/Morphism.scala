// Implements: REQ-F-LDM-001, REQ-F-LDM-002, REQ-F-ADJ-001
package cdme.model.category

import cdme.model.access.AccessRule

/**
 * Opaque type for morphism identifiers.
 */
opaque type MorphismId = String

object MorphismId:
  def apply(value: String): MorphismId = value
  extension (id: MorphismId)
    def value: String = id
    def show: String = s"MorphismId($id)"

/**
 * Cardinality type for morphisms.
 *
 * Defines the cardinality relationship between domain and codomain entities.
 * This affects how adjoints are constructed and how the context type evolves
 * during path traversal.
 */
enum CardinalityType:
  /** One-to-one: exact correspondence between domain and codomain records. */
  case OneToOne
  /** Many-to-one: multiple domain records map to a single codomain record. */
  case ManyToOne
  /** One-to-many: a single domain record maps to multiple codomain records. */
  case OneToMany
  /** Many-to-many: multiple domain records map to multiple codomain records. */
  case ManyToMany

/**
 * Containment type for adjoint morphisms.
 *
 * Classifies how the backward function relates to the forward function
 * in terms of information preservation.
 */
enum ContainmentType:
  /** Forward and backward are exact inverses: backward(forward(x)) = x. */
  case Isomorphic
  /** Forward is injective; backward returns the preimage set. */
  case Preimage
  /** Forward expands; backward collects parent records. */
  case Expansion
  /** Forward aggregates; backward uses reverse-join table. */
  case Aggregation
  /** Forward filters; backward returns passed + filtered with tags. */
  case Filter
  /** External calculator with manually declared containment bounds. */
  case Opaque

/**
 * Base trait for all morphisms in the CDME category.
 *
 * A morphism is a directed, typed mapping between two entities (domain -> codomain)
 * with a cardinality classification and access control rules.
 *
 * Every morphism in CDME is required to provide forward and backward functions
 * (REQ-F-ADJ-001). The specific adjoint implementations are in the adjoint package.
 */
trait MorphismBase:
  /** Unique morphism identifier. */
  def id: MorphismId

  /** Human-readable name. */
  def name: String

  /** The source entity. */
  def domain: EntityId

  /** The target entity. */
  def codomain: EntityId

  /** Cardinality classification. */
  def cardinality: CardinalityType

  /** Containment classification for the adjoint. */
  def containment: ContainmentType

  /** Access control rules for this morphism. */
  def accessRules: List[AccessRule]
