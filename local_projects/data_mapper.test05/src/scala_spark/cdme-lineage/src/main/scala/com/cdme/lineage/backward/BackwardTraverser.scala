package com.cdme.lineage.backward

// Implements: REQ-F-ACC-005, REQ-F-ADJ-005

import com.cdme.model.config.Epoch
import com.cdme.model.entity.EntityId
import com.cdme.runtime.adjoint._

/**
 * A source record identified during backward traversal.
 *
 * Represents an original input record that contributed to a target output key.
 *
 * @param entityId the logical entity this record belongs to
 * @param key      the record's key value
 * @param epoch    the epoch in which this record was produced
 */
case class SourceRecord(
  entityId: EntityId,
  key: Any,
  epoch: Epoch
)

/**
 * Traverses the adjoint metadata chain backward from a target output key
 * to the set of contributing source records.
 *
 * Backward traversal uses persisted adjoint metadata exclusively — it never
 * re-executes morphisms or recomputes transformations.  This is a fundamental
 * design principle (ADR-003): the adjoint metadata captured at execution time
 * is sufficient for complete backward traceability.
 *
 * The traversal algorithm:
 *  1. Start from the target key
 *  2. For each adjoint metadata entry (in reverse execution order):
 *     - [[ReverseJoinMetadata]]: map aggregate key to source keys
 *     - [[FilteredKeysMetadata]]: no backward contribution (filtered keys are dead ends)
 *     - [[ExplodeMetadata]]: map child keys back to parent key
 *     - [[ErrorKeyMetadata]]: map error key to source key
 *  3. Collect all terminal source keys
 */
object BackwardTraverser {

  /**
   * Trace a target key backward through the adjoint metadata chain to find
   * all contributing source records.
   *
   * @param targetKey    the output key to trace backward from
   * @param adjointMeta  the list of adjoint metadata entries (in execution order)
   * @return the set of source records that contributed to the target key
   */
  def traceBack(
    targetKey: Any,
    adjointMeta: List[AdjointMetadata]
  ): Set[SourceRecord] = {
    // Walk the adjoint metadata in reverse order, expanding the key set
    // at each step based on the metadata type.
    val sourceKeys = adjointMeta.foldRight(Set[Any](targetKey)) { (meta, currentKeys) =>
      meta match {
        case ReverseJoinMetadata(aggregateKey, sourceKeys) =>
          // If any current key matches the aggregate key, expand to source keys
          if (currentKeys.contains(aggregateKey) || currentKeys.subsetOf(aggregateKey.asInstanceOf[Set[Any]])) {
            currentKeys ++ sourceKeys
          } else {
            currentKeys
          }

        case FilteredKeysMetadata(_, _) =>
          // Filtered keys do not contribute backward — pass through
          currentKeys

        case ExplodeMetadata(parentKey, childKeys) =>
          // If any current key is in the child keys, trace back to parent
          val matchingChildren = currentKeys.intersect(childKeys.toSet)
          if (matchingChildren.nonEmpty) {
            (currentKeys -- matchingChildren) + parentKey
          } else {
            currentKeys
          }

        case ErrorKeyMetadata(sourceKey, _) =>
          // Error keys trace back to their source
          if (currentKeys.contains(sourceKey)) {
            currentKeys + sourceKey
          } else {
            currentKeys
          }
      }
    }

    // Convert raw keys to SourceRecord instances.
    // In a full implementation, the entity ID and epoch would be resolved
    // from the lineage graph metadata.
    val unknownEpoch = Epoch(
      id = "unknown",
      start = java.time.Instant.EPOCH,
      end = java.time.Instant.EPOCH,
      parameters = Map.empty
    )
    sourceKeys.map { key =>
      SourceRecord(
        entityId = EntityId("unknown"), // Resolved from lineage graph in production
        key = key,
        epoch = unknownEpoch // Resolved from lineage graph in production
      )
    }
  }
}
