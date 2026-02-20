package com.cdme.runtime.adjoint

// Implements: REQ-F-ACC-003, REQ-F-ADJ-004

import com.cdme.model.error.CdmeError
import com.cdme.model.morphism.{AlgebraicMorphism, ComputationalMorphism, Morphism, StructuralMorphism}

/**
 * Sealed trait representing metadata captured at each morphism execution
 * for adjoint (backward) traversal.
 *
 * Every morphism produces adjoint metadata recording the relationship between
 * input keys and output keys.  This metadata enables backward traversal
 * from aggregate/derived keys back to contributing source keys without
 * recomputation.
 */
sealed trait AdjointMetadata

/**
 * Metadata for reverse-join operations: maps an aggregate output key to the
 * set of source keys that contributed to it.
 *
 * @param aggregateKey the output key produced by the join/aggregation
 * @param sourceKeys   the set of input keys that contributed
 */
case class ReverseJoinMetadata(
  aggregateKey: Any,
  sourceKeys: Set[Any]
) extends AdjointMetadata

/**
 * Metadata for filter operations: records which input keys were excluded
 * and the filter condition that excluded them.
 *
 * @param excludedKeys    the set of input keys that were filtered out
 * @param filterCondition human-readable description of the filter condition
 */
case class FilteredKeysMetadata(
  excludedKeys: Set[Any],
  filterCondition: String
) extends AdjointMetadata

/**
 * Metadata for explode (1:N) operations: maps a parent key to the list of
 * child keys it produced.
 *
 * @param parentKey the input key that was exploded
 * @param childKeys the output keys produced from the parent
 */
case class ExplodeMetadata(
  parentKey: Any,
  childKeys: List[Any]
) extends AdjointMetadata

/**
 * Metadata for error routing: records the source key and the error that
 * caused the record to be routed to the Error Domain.
 *
 * @param sourceKey the input key that produced an error
 * @param error     the CdmeError that was produced
 */
case class ErrorKeyMetadata(
  sourceKey: Any,
  error: CdmeError
) extends AdjointMetadata

/**
 * Captures adjoint metadata at each morphism execution point.
 *
 * The capturer compares input and output key sets to determine the type of
 * metadata to produce.  This is called by the pipeline runner after each
 * morphism completes.
 */
object AdjointKeyCapturer {

  /**
   * Capture adjoint metadata for a morphism execution by comparing input
   * and output key sets.
   *
   * The type of metadata produced depends on the morphism type:
   *  - [[StructuralMorphism]] -> [[ReverseJoinMetadata]] (join key mapping)
   *  - [[ComputationalMorphism]] with filtering -> [[FilteredKeysMetadata]]
   *  - 1:N morphisms -> [[ExplodeMetadata]]
   *  - Morphisms where inputKeys != outputKeys and none of the above apply
   *    -> [[ReverseJoinMetadata]] as a fallback
   *
   * @param morphism   the morphism that was executed
   * @param inputKeys  the set of keys present in the input
   * @param outputKeys the set of keys present in the output (success partition)
   * @return the captured adjoint metadata
   */
  def capture(
    morphism: Morphism,
    inputKeys: Set[Any],
    outputKeys: Set[Any]
  ): AdjointMetadata = {
    morphism match {
      case _: AlgebraicMorphism =>
        // Aggregation: output keys are aggregate group keys; input keys are
        // the individual source records that were folded.
        // Each output key maps back to the input keys that contributed.
        ReverseJoinMetadata(
          aggregateKey = outputKeys,
          sourceKeys = inputKeys
        )

      case _: StructuralMorphism =>
        // Join: output keys map to input keys via the join condition.
        val excluded = inputKeys -- outputKeys
        if (excluded.nonEmpty) {
          // Some input keys were filtered by the join (e.g. inner join excludes non-matching)
          FilteredKeysMetadata(
            excludedKeys = excluded,
            filterCondition = s"Join on morphism ${morphism.id}"
          )
        } else {
          ReverseJoinMetadata(
            aggregateKey = outputKeys,
            sourceKeys = inputKeys
          )
        }

      case _: ComputationalMorphism =>
        val excluded = inputKeys -- outputKeys
        if (excluded.nonEmpty) {
          FilteredKeysMetadata(
            excludedKeys = excluded,
            filterCondition = s"Computational morphism ${morphism.id} filtered records"
          )
        } else if (outputKeys.size > inputKeys.size) {
          // 1:N expansion
          ExplodeMetadata(
            parentKey = inputKeys,
            childKeys = outputKeys.toList
          )
        } else {
          // 1:1 mapping — keys preserved
          ReverseJoinMetadata(
            aggregateKey = outputKeys,
            sourceKeys = inputKeys
          )
        }
    }
  }
}
