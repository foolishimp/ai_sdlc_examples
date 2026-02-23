package com.cdme.model

// Implements: REQ-LDM-01 (Strict Graph Structure), REQ-LDM-03 (Composition Validity)
case class Category(
  name: String,
  objects: Map[String, Entity],
  morphisms: Map[String, Morphism]
) {
  /** Find all morphisms from source entity */
  def morphismsFrom(source: String): Set[Morphism] =
    morphisms.values.filter(_.source == source).toSet

  /** Find all morphisms to target entity */
  def morphismsTo(target: String): Set[Morphism] =
    morphisms.values.filter(_.target == target).toSet

  /** Find morphisms between two entities */
  def morphismsBetween(source: String, target: String): Set[Morphism] =
    morphisms.values.filter(m => m.source == source && m.target == target).toSet

  /** Check if an entity exists */
  def hasEntity(name: String): Boolean = objects.contains(name)

  /** Check if a morphism exists */
  def hasMorphism(name: String): Boolean = morphisms.contains(name)
}

// Implements: REQ-LDM-03 (Composition result)
case class ComposedMorphism(
  steps: List[Morphism],
  resultType: SemanticType,
  resultGrain: Grain,
  resultCardinality: Cardinality
)
