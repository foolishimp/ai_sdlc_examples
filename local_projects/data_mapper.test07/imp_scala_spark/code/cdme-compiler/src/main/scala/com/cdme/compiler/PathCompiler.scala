// Implements: REQ-LDM-03, REQ-LDM-05
// Path verification: validates dot-notation paths against the LDM topology.
package com.cdme.compiler

import com.cdme.model._
import com.cdme.model.error._

/**
 * Validates dot-notation paths against the LDM topology.
 * Each step in the path must be an existing morphism with valid composition.
 *
 * Implements: REQ-LDM-03, REQ-LDM-05
 */
object PathCompiler {

  /**
   * Verify that a dot-path is valid in the given category.
   * Checks: morphism existence, domain/codomain alignment, access control.
   */
  def verifyPath(
      path: DotPath,
      category: Category,
      principal: Principal
  ): Either[CdmeError, VerifiedPath] = {
    if (path.segments.isEmpty) {
      return Left(PathNotFoundError(path.asString, "Empty path"))
    }

    val firstSegment = path.segments.head
    // Find the source entity or morphism for the first segment
    val firstMorphism = category.morphisms.values.find(_.name == firstSegment)
      .orElse(category.morphism(firstSegment))

    firstMorphism match {
      case None =>
        Left(PathNotFoundError(path.asString, s"Morphism '$firstSegment' not found in category"))
      case Some(m) =>
        verifyPathSegments(path.segments, category, principal, m.domain, List.empty)
    }
  }

  private def verifyPathSegments(
      segments: List[String],
      category: Category,
      principal: Principal,
      currentEntity: EntityId,
      accumulated: List[MorphismId]
  ): Either[CdmeError, VerifiedPath] = segments match {
    case Nil =>
      // Determine the final target entity from the last morphism
      accumulated.lastOption.flatMap(category.morphism) match {
        case Some(lastMorphism) =>
          Right(VerifiedPath(accumulated, accumulated.headOption.flatMap(category.morphism).map(_.domain).getOrElse(""), lastMorphism.codomain))
        case None if accumulated.isEmpty =>
          Left(PathNotFoundError("", "No morphisms in path"))
        case None =>
          Left(PathNotFoundError("", "Last morphism not found"))
      }

    case segment :: rest =>
      // Find morphism by name or ID originating from currentEntity
      val morphismOpt = category.morphisms.values
        .find(m => (m.name == segment || m.id == segment) && m.domain == currentEntity)

      morphismOpt match {
        case None =>
          Left(PathNotFoundError(
            segments.mkString("."),
            s"No morphism '$segment' from entity '$currentEntity'"
          ))
        case Some(morphism) =>
          // Check access control
          if (!morphism.accessControl.isAccessible(principal.roles)) {
            Left(AccessDeniedError(morphism.id, principal.name, accumulated))
          } else {
            verifyPathSegments(rest, category, principal, morphism.codomain, accumulated :+ morphism.id)
          }
      }
  }

  /**
   * Verify a morphism chain: check that composition is valid (codomain matches domain).
   */
  def verifyMorphismChain(
      morphismIds: List[MorphismId],
      category: Category
  ): Either[CdmeError, List[Morphism]] = {
    val morphisms = morphismIds.map { id =>
      category.morphism(id) match {
        case Some(m) => Right(m)
        case None    => Left(PathNotFoundError(id, s"Morphism '$id' not found"))
      }
    }

    // Collect all morphisms, short-circuiting on first error
    val collected = morphisms.foldLeft[Either[CdmeError, List[Morphism]]](Right(Nil)) {
      case (Left(err), _)       => Left(err)
      case (_, Left(err))       => Left(err)
      case (Right(acc), Right(m)) => Right(acc :+ m)
    }

    collected.flatMap { ms =>
      // Verify adjacency: codomain(f) == domain(g) for each consecutive pair
      ms.sliding(2).foreach {
        case List(f, g) if f.codomain != g.domain =>
          return Left(CompositionError(
            f.id, g.id,
            s"Codomain '${f.codomain}' does not match domain '${g.domain}'"
          ))
        case _ => // ok
      }
      Right(ms)
    }
  }
}
