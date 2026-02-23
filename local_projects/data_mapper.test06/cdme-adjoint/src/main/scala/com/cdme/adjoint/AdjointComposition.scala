package com.cdme.adjoint

// Implements: REQ-ADJ-07 (Adjoint Composition Validation)
object AdjointComposition {
  /** Compose two adjoints: (g ∘ f)⁻ = f⁻ ∘ g⁻ (contravariant) */
  def compose[A, B, C](f: Adjoint[A, B], g: Adjoint[B, C]): Adjoint[A, C] =
    new Adjoint[A, C] {
      def forward(a: A): C = g.forward(f.forward(a))
      def backward(c: C): A = f.backward(g.backward(c))
      def classification: AdjointClassification =
        AdjointClassification.compose(f.classification, g.classification)
    }

  /** Compose a chain of adjoints */
  def composeChain[T](adjoints: List[Adjoint[T, T]]): Adjoint[T, T] =
    adjoints match {
      case Nil => Adjoint.identity[T]
      case head :: Nil => head
      case head :: tail => compose(head, composeChain(tail))
    }
}
