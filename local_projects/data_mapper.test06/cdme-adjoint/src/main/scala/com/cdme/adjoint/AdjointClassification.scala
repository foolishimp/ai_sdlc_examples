package com.cdme.adjoint

// Implements: REQ-ADJ-02 (Adjoint Classification)
sealed trait AdjointClassification {
  def description: String
}

object AdjointClassification {
  /** Exact inverse: f⁻(f(x)) = x AND f(f⁻(y)) = y */
  case object Isomorphism extends AdjointClassification {
    val description = "Exact inverse (bijection)"
  }
  /** Injective: f⁻(f(x)) = x BUT f(f⁻(y)) ⊆ y */
  case object Embedding extends AdjointClassification {
    val description = "Injective (exact backward, partial forward recovery)"
  }
  /** Surjective: f⁻(f(x)) ⊇ x BUT f(f⁻(y)) = y */
  case object Projection extends AdjointClassification {
    val description = "Surjective (expanded backward, exact forward recovery)"
  }
  /** Neither: f⁻(f(x)) ⊇ x AND f(f⁻(y)) ⊆ y */
  case object Lossy extends AdjointClassification {
    val description = "Lossy (neither exact)"
  }

  // Implements: REQ-ADJ-07 (composition of classifications)
  def compose(a: AdjointClassification, b: AdjointClassification): AdjointClassification =
    (a, b) match {
      case (Isomorphism, x) => x
      case (x, Isomorphism) => x
      case (Embedding, Embedding) => Embedding
      case (Projection, Projection) => Projection
      case _ => Lossy
    }
}
