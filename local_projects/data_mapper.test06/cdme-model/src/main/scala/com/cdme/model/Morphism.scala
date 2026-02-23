package com.cdme.model

// Implements: REQ-LDM-02 (Cardinality Types)
sealed trait Cardinality
object Cardinality {
  case object OneToOne extends Cardinality
  case object NToOne extends Cardinality
  case object OneToN extends Cardinality
}

// Implements: REQ-LDM-01, REQ-LDM-02
sealed trait MorphismType
object MorphismType {
  case object Structural extends MorphismType
  case object Computational extends MorphismType
  case object Algebraic extends MorphismType
}

// Implements: REQ-LDM-01 (Strict Graph Structure)
case class Entity(
  name: String,
  grain: Grain,
  attributes: Map[String, SemanticType]
)

case class MorphismMetadata(
  description: String = "",
  rbacRoles: Set[String] = Set.empty,
  tags: Map[String, String] = Map.empty
)

// Implements: REQ-LDM-01, REQ-LDM-02, REQ-LDM-05
case class Morphism(
  name: String,
  source: String,
  target: String,
  cardinality: Cardinality,
  morphismType: MorphismType,
  domainType: SemanticType,
  codomainType: SemanticType,
  metadata: MorphismMetadata = MorphismMetadata()
)
