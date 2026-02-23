package com.cdme.adjoint

// Implements: REQ-ADJ-04, REQ-ADJ-05, REQ-ADJ-06, REQ-ADJ-11

/** Reverse-join table for aggregation backward (REQ-ADJ-04) */
case class ReverseJoinTable(entries: Map[String, Set[String]]) {
  /** Look up constituent keys for a group key */
  def constituentsFor(groupKey: String): Set[String] =
    entries.getOrElse(groupKey, Set.empty)

  /** Total number of constituent records */
  def totalConstituents: Int = entries.values.map(_.size).sum
}

object ReverseJoinTable {
  val empty: ReverseJoinTable = ReverseJoinTable(Map.empty)

  def fromPairs(pairs: Iterable[(String, String)]): ReverseJoinTable = {
    val grouped = pairs.groupBy(_._1).map { case (k, vs) => k -> vs.map(_._2).toSet }
    ReverseJoinTable(grouped)
  }
}

/** Filtered-key capture for filter backward (REQ-ADJ-05) */
case class FilteredKeys(passedKeys: Set[String], filteredOutKeys: Set[String]) {
  /** Full input set = passed ∪ filtered */
  def fullInputKeys: Set[String] = passedKeys ++ filteredOutKeys

  def isComplete: Boolean = passedKeys.nonEmpty || filteredOutKeys.nonEmpty
}

object FilteredKeys {
  val empty: FilteredKeys = FilteredKeys(Set.empty, Set.empty)
}

/** Parent-child map for Kleisli backward (REQ-ADJ-06) */
case class KleisliParentMap(entries: Map[String, String]) {
  /** Look up parent for a child key */
  def parentFor(childKey: String): Option[String] = entries.get(childKey)

  /** All children for a parent */
  def childrenFor(parentKey: String): Set[String] =
    entries.filter(_._2 == parentKey).keySet
}

object KleisliParentMap {
  val empty: KleisliParentMap = KleisliParentMap(Map.empty)
}

/** Storage strategy for adjoint metadata (REQ-ADJ-11) */
sealed trait AdjointStorageStrategy
object AdjointStorageStrategy {
  case object Inline extends AdjointStorageStrategy
  case object SeparateTable extends AdjointStorageStrategy
  case object Compressed extends AdjointStorageStrategy
}

/** Captured adjoint metadata during forward execution */
case class AdjointCaptured(
  morphismName: String,
  reverseJoinTable: Option[ReverseJoinTable] = None,
  filteredKeys: Option[FilteredKeys] = None,
  kleisliParentMap: Option[KleisliParentMap] = None,
  storageStrategy: AdjointStorageStrategy = AdjointStorageStrategy.Inline
)
