package cdme.model

// Implements: REQ-F-LDM-001, REQ-F-LDM-006

/** An entity is a node in the LDM topology.
  *
  * Every entity has a grain tag, typed attributes, and an identity morphism.
  */
case class Entity(
    name: String,
    grain: Grain,
    attributes: List[Attribute],
    primaryKey: List[String]
):
  /** Identity morphism: every entity has id: E -> E (category theory requirement). */
  def identityMorphism: Morphism =
    Morphism(
      name = s"id_$name",
      domain = this,
      codomain = this,
      cardinality = Cardinality.OneToOne,
      morphismType = MorphismType.Structural,
      accessControl = AccessControl.open
    )

/** An attribute of an entity with extended type. */
case class Attribute(
    name: String,
    attributeType: ExtendedType,
    isPrimaryKey: Boolean = false,
    semanticType: Option[String] = None // REQ-F-TYP-007
)
