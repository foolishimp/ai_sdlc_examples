package com.cdme.model.graph

// Implements: REQ-F-LDM-001, REQ-F-LDM-003

import com.cdme.model.entity.{Entity, EntityId}
import com.cdme.model.morphism.{Morphism, MorphismId}
import com.cdme.model.version.ArtifactVersion

import scala.collection.mutable

/**
 * A directed multigraph representing the Logical Data Model (LDM).
 *
 * Nodes are [[Entity]] instances and edges are [[Morphism]] instances.
 * The graph is the core structure that the topological compiler validates
 * and the runtime traverses.
 *
 * Provides query operations for entity/morphism lookup, adjacency
 * traversal (outgoing/incoming edges), and pathfinding between entities.
 *
 * @param nodes   map from entity ID to entity
 * @param edges   map from morphism ID to morphism
 * @param version artifact version metadata for the graph definition
 */
case class LdmGraph(
  nodes: Map[EntityId, Entity],
  edges: Map[MorphismId, Morphism],
  version: ArtifactVersion
) {

  /**
   * All entities in the graph.
   *
   * @return an iterable of all entity values
   */
  def entities: Iterable[Entity] = nodes.values

  /**
   * All morphisms in the graph.
   *
   * @return an iterable of all morphism values
   */
  def morphisms: Iterable[Morphism] = edges.values

  /**
   * All morphisms whose domain is the given entity.
   *
   * @param entityId the source entity
   * @return list of outgoing morphisms
   */
  def outgoing(entityId: EntityId): List[Morphism] =
    edges.values.filter(_.domain == entityId).toList

  /**
   * All morphisms whose codomain is the given entity.
   *
   * @param entityId the target entity
   * @return list of incoming morphisms
   */
  def incoming(entityId: EntityId): List[Morphism] =
    edges.values.filter(_.codomain == entityId).toList

  /**
   * Find a path of morphisms from one entity to another using BFS.
   *
   * Returns the first (shortest) path found, or None if no path exists.
   * The path is represented as the ordered list of morphisms to traverse.
   *
   * @param from the starting entity
   * @param to   the target entity
   * @return an optional list of morphisms forming a path
   */
  def findPath(from: EntityId, to: EntityId): Option[List[Morphism]] = {
    if (from == to) {
      Some(Nil)
    } else {
      val visited = mutable.Set[EntityId](from)
      val queue   = mutable.Queue[(EntityId, List[Morphism])]((from, Nil))
      var result: Option[List[Morphism]] = None

      while (queue.nonEmpty && result.isEmpty) {
        val (current, path) = queue.dequeue()
        for (morphism <- outgoing(current) if result.isEmpty) {
          val next = morphism.codomain
          if (next == to) {
            result = Some(path :+ morphism)
          } else if (!visited.contains(next)) {
            visited += next
            queue.enqueue((next, path :+ morphism))
          }
        }
      }

      result
    }
  }
}
