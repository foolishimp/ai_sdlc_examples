// Implements: REQ-F-ADJ-001, REQ-F-ADJ-002, REQ-F-ADJ-007, REQ-BR-ADJ-001
// ADR-005: Adjoint-as-Trait for Morphism Design
package cdme.model.adjoint

import cdme.model.category.{MorphismId, MorphismBase, EntityId, CardinalityType, ContainmentType}
import cdme.model.access.AccessRule

/**
 * Result of a backward (adjoint) computation.
 *
 * Contains the recovered records plus metadata about the backward traversal.
 *
 * @tparam A the type of recovered records
 * @param records   the recovered records
 * @param metadata  key-value metadata (e.g., filter reasons, aggregation keys)
 */
final case class BackwardResult[A](
    records: Vector[A],
    metadata: Map[String, String] = Map.empty
)

/**
 * Reverse-join table mapping output group keys to contributing input record keys.
 *
 * Used by aggregation adjoints to enable backward traversal from aggregated
 * output back to the individual contributing input records (ADR-011).
 *
 * @param entries mapping from output group key to the set of input record keys
 */
final case class ReverseJoinTable(
    entries: Map[String, Set[String]]
):
  /** Look up the contributing input keys for a given output group key. */
  def contributingKeys(outputKey: String): Set[String] =
    entries.getOrElse(outputKey, Set.empty)

  /** Total number of group entries. */
  def size: Int = entries.size

/**
 * Store of record keys that were filtered out, with filter predicate metadata.
 *
 * @param filteredKeys set of record keys that were excluded
 * @param filterPredicate human-readable description of the filter
 */
final case class FilteredKeyStore(
    filteredKeys: Set[String],
    filterPredicate: String
):
  /** Number of filtered records. */
  def count: Int = filteredKeys.size

/**
 * Core adjoint morphism trait.
 *
 * Every morphism in CDME must implement this trait (ADR-005). A morphism
 * that cannot provide a backward function is rejected at definition time.
 *
 * The adjoint contract:
 *   - forward: A => B  (standard transformation)
 *   - backward: B => BackwardResult[A]  (reverse traversal)
 *   - containment: ContainmentType  (classification of the backward relationship)
 *
 * External calculator morphisms may declare backward as "opaque" with manually
 * declared containment bounds.
 *
 * @tparam A the domain (input) type
 * @tparam B the codomain (output) type
 */
trait AdjointMorphism[A, B] extends MorphismBase:
  /**
   * Forward transformation function.
   *
   * @param input the domain value
   * @return Right(output) or Left(error) for record-level failures
   */
  def forward(input: A): Either[cdme.model.error.ErrorObject, B]

  /**
   * Backward (adjoint) transformation function.
   *
   * Returns the pre-image or related records from the codomain back to the
   * domain, along with metadata for traceability.
   *
   * @param output the codomain value
   * @return the backward result containing recovered domain records
   */
  def backward(output: B): BackwardResult[A]
