// Implements: REQ-F-ADJ-001, REQ-F-ADJ-002, REQ-F-ADJ-007, REQ-BR-ADJ-001
// ADR-005: Adjoint-as-Trait for Morphism Design
package cdme.model.adjoint

import cdme.model.category.{MorphismId, MorphismBase, EntityId, CardinalityType, ContainmentType}
import cdme.model.access.AccessRule

final case class BackwardResult[A](
    records: Vector[A],
    metadata: Map[String, String] = Map.empty
)

final case class ReverseJoinTable(
    entries: Map[String, Set[String]]
) {
  def contributingKeys(outputKey: String): Set[String] =
    entries.getOrElse(outputKey, Set.empty)
  def size: Int = entries.size
}

final case class FilteredKeyStore(
    filteredKeys: Set[String],
    filterPredicate: String
) {
  def count: Int = filteredKeys.size
}

trait AdjointMorphism[A, B] extends MorphismBase {
  def forward(input: A): Either[cdme.model.error.ErrorObject, B]
  def backward(output: B): BackwardResult[A]
}
