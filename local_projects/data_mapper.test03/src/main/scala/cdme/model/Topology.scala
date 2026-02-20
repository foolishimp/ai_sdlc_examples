package cdme.model

// Implements: REQ-F-LDM-001, REQ-F-LDM-003

/** A topology is the directed multigraph of entities and morphisms.
  *
  * This is the LDM made machine-readable. The topology is the single
  * source of truth for all path validation.
  */
case class Topology(
    name: String,
    entities: Map[String, Entity],
    morphisms: List[Morphism]
):
  /** Find all morphisms from a given entity. */
  def outgoing(entity: Entity): List[Morphism] =
    morphisms.filter(_.domain.name == entity.name)

  /** Find all morphisms to a given entity. */
  def incoming(entity: Entity): List[Morphism] =
    morphisms.filter(_.codomain.name == entity.name)

  /** Check if a named morphism exists. */
  def hasMorphism(name: String): Boolean =
    morphisms.exists(_.name == name)

  /** Resolve a dot-path into a sequence of morphisms (REQ-F-LDM-003). */
  def resolvePath(path: List[String]): Either[String, List[Morphism]] =
    path match
      case Nil => Right(Nil)
      case _ :: Nil => Right(Nil)
      case head :: tail =>
        val steps = path.sliding(2).toList
        val resolved = steps.map { case List(from, to) =>
          morphisms.find(m => m.domain.name == from && m.codomain.name == to)
            .toRight(s"No morphism from $from to $to")
        }
        val errors = resolved.collect { case Left(e) => e }
        if errors.nonEmpty then Left(errors.mkString("; "))
        else Right(resolved.collect { case Right(m) => m })
