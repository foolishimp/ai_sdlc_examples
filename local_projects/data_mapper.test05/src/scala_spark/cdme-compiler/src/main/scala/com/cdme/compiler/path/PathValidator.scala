package com.cdme.compiler.path
// Implements: REQ-F-LDM-003

import com.cdme.model._
import com.cdme.model.morphism.{StructuralMorphism, ComputationalMorphism, AlgebraicMorphism}
import com.cdme.compiler.{PathError, MorphismNotFound, DomainCodomainMismatch, EmptyPath}
import com.cdme.compiler.access.Principal

/**
 * A symbolic dot-notation path through the LDM graph.
 *
 * Example: `"trade.counterparty.region"` parses to `DotPath(List("trade", "counterparty", "region"))`.
 *
 * @param segments ordered list of morphism names to traverse
 */
case class DotPath(segments: List[String])

object DotPath {
  /** Parse a dot-separated string into a [[DotPath]]. */
  def parse(raw: String): DotPath =
    DotPath(raw.split('.').toList.filter(_.nonEmpty))
}

/**
 * A path that has been validated against the LDM graph.
 *
 * Each segment has been resolved to a real morphism, and domain/codomain
 * continuity has been verified.
 *
 * @param morphisms    ordered list of morphisms along the path
 * @param sourceEntity the domain of the first morphism
 * @param targetEntity the codomain of the last morphism
 */
case class ValidatedPath(
  morphisms: List[Morphism],
  sourceEntity: EntityId,
  targetEntity: EntityId
)

/**
 * Validates dot-notation paths against the LDM graph.
 *
 * Checks:
 * - Every segment references a morphism that exists in the graph.
 * - The codomain of each morphism matches the domain of the next.
 */
object PathValidator {

  /**
   * Validate a dot-path against the LDM graph.
   *
   * @param path      the symbolic dot-path to validate
   * @param graph     the logical data model graph
   * @param principal the security principal (used for scoping; access checks are separate)
   * @return a [[ValidatedPath]] on success, or a [[PathError]] describing the failure
   */
  def validate(
    path: DotPath,
    graph: LdmGraph,
    principal: Principal
  ): Either[PathError, ValidatedPath] = {
    if (path.segments.isEmpty) {
      return Left(EmptyPath)
    }

    // Build an index of morphisms by name for efficient lookup
    val morphismsByName: Map[String, List[Morphism]] =
      graph.edges.values.toList.groupBy(morphismName)

    // Resolve each segment to a morphism
    val resolved = path.segments.foldLeft[Either[PathError, List[Morphism]]](Right(Nil)) {
      case (Left(err), _) => Left(err)
      case (Right(acc), segment) =>
        morphismsByName.get(segment) match {
          case None | Some(Nil) =>
            Left(MorphismNotFound(segment, morphismsByName.keys.toList.sorted))
          case Some(candidates) =>
            acc match {
              case Nil =>
                // First segment: pick any candidate (ambiguity resolved later by context)
                Right(candidates.head :: Nil)
              case _ =>
                val prevCodomain = codomainOf(acc.last)
                candidates.find(m => domainOf(m) == prevCodomain) match {
                  case Some(matched) =>
                    Right(acc :+ matched)
                  case None =>
                    Left(DomainCodomainMismatch(
                      precedingMorphism = morphismName(acc.last),
                      precedingCodomain = prevCodomain,
                      followingMorphism = segment,
                      followingDomain = domainOf(candidates.head)
                    ))
                }
            }
        }
    }

    resolved.map { morphisms =>
      ValidatedPath(
        morphisms = morphisms,
        sourceEntity = domainOf(morphisms.head),
        targetEntity = codomainOf(morphisms.last)
      )
    }
  }

  // --- Helper methods to extract morphism properties ---
  // These use pattern matching since Morphism is a sealed trait with subtypes.

  private def morphismName(m: Morphism): String = m match {
    case s: StructuralMorphism     => s.id.value
    case c: ComputationalMorphism  => c.id.value
    case a: AlgebraicMorphism      => a.id.value
  }

  private def domainOf(m: Morphism): EntityId = m match {
    case s: StructuralMorphism     => s.domain
    case c: ComputationalMorphism  => c.domain
    case a: AlgebraicMorphism      => a.domain
  }

  private def codomainOf(m: Morphism): EntityId = m match {
    case s: StructuralMorphism     => s.codomain
    case c: ComputationalMorphism  => c.codomain
    case a: AlgebraicMorphism      => a.codomain
  }
}
