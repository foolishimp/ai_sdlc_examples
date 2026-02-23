// Implements: REQ-LDM-02, REQ-ADJ-01
// Morphism: a directed edge in the LDM category with cardinality and adjoint spec.
package com.cdme.model

import com.cdme.model.adjoint.AdjointSpec

/**
 * A Morphism is a directed edge from domain entity to codomain entity.
 * Every morphism declares its cardinality, kind, and optional adjoint specification.
 *
 * Implements: REQ-LDM-02, REQ-ADJ-01
 */
final case class Morphism(
    id: MorphismId,
    name: String,
    domain: EntityId,
    codomain: EntityId,
    cardinality: Cardinality,
    morphismKind: MorphismKind,
    adjoint: Option[AdjointSpec],
    accessControl: AccessControl
)

/**
 * Cardinality types for morphisms.
 * Implements: REQ-LDM-02
 */
sealed trait Cardinality
object Cardinality {
  case object OneToOne extends Cardinality
  case object NToOne  extends Cardinality
  case object OneToN  extends Cardinality
}

/**
 * Morphism kind classification.
 * Implements: REQ-LDM-02, REQ-INT-01, REQ-INT-08
 */
sealed trait MorphismKind
object MorphismKind {
  case object Structural    extends MorphismKind
  case object Computational extends MorphismKind
  case object Algebraic     extends MorphismKind
  case object External      extends MorphismKind
}
