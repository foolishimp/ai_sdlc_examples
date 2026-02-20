// Implements: REQ-F-ACC-004
package cdme.runtime

import cdme.model.adjoint.{ReverseJoinTable, FilteredKeyStore}

/**
 * Backward proof: output-to-source traceability without recomputation.
 *
 * Given an output key, returns the source keys by querying adjoint metadata
 * (reverse-join tables, filter key stores, parent maps) without re-executing
 * the pipeline.
 */
trait BackwardProof:

  /**
   * Trace an output key back to its source keys.
   *
   * @param outputKey the output record key to trace
   * @param morphismPath the ordered list of morphism IDs in the path
   * @return Right(sourceKeys) or Left(error) if tracing fails
   */
  def trace(
      outputKey: String,
      morphismPath: List[String]
  ): Either[String, Set[String]]

/**
 * Implementation of backward proof using in-memory adjoint metadata.
 *
 * @param reverseJoinTables captured reverse-join tables by morphism ID
 * @param filteredKeyStores captured filtered key stores by morphism ID
 * @param parentMaps captured parent maps by morphism ID
 */
final class InMemoryBackwardProof(
    reverseJoinTables: Map[String, ReverseJoinTable],
    filteredKeyStores: Map[String, FilteredKeyStore],
    parentMaps: Map[String, Map[String, String]]
) extends BackwardProof:

  override def trace(
      outputKey: String,
      morphismPath: List[String]
  ): Either[String, Set[String]] =
    // Walk the morphism path in reverse, tracing backward
    morphismPath.reverse.foldLeft(Right(Set(outputKey)): Either[String, Set[String]]) {
      case (Left(err), _) => Left(err)
      case (Right(currentKeys), morphismId) =>
        traceOneStep(currentKeys, morphismId)
    }

  /**
   * Trace one step backward through adjoint metadata.
   */
  private def traceOneStep(
      keys: Set[String],
      morphismId: String
  ): Either[String, Set[String]] =
    // Try reverse-join table first (aggregation)
    reverseJoinTables.get(morphismId) match
      case Some(rjt) =>
        val sourceKeys = keys.flatMap(rjt.contributingKeys)
        Right(sourceKeys)
      case None =>
        // Try parent map (1:N Kleisli)
        parentMaps.get(morphismId) match
          case Some(pm) =>
            val sourceKeys = keys.flatMap(k => pm.get(k))
            Right(sourceKeys)
          case None =>
            // For 1:1 and filter morphisms, keys pass through
            Right(keys)
