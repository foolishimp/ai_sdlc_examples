package com.cdme.adjoint

// Implements: REQ-ADJ-01 (Adjoint Interface Structure)
trait Adjoint[T, U] {
  /** Forward transformation: T → U */
  def forward(input: T): U

  /** Backward transformation: U → T (Galois connection) */
  def backward(output: U): T

  /** Classification of this adjoint pair */
  def classification: AdjointClassification
}

object Adjoint {
  // Implements: REQ-ADJ-03 (Self-Adjoint / Isomorphisms)
  def isomorphism[T, U](fwd: T => U, bwd: U => T): Adjoint[T, U] =
    new Adjoint[T, U] {
      def forward(input: T): U = fwd(input)
      def backward(output: U): T = bwd(output)
      def classification: AdjointClassification = AdjointClassification.Isomorphism
    }

  def embedding[T, U](fwd: T => U, bwd: U => T): Adjoint[T, U] =
    new Adjoint[T, U] {
      def forward(input: T): U = fwd(input)
      def backward(output: U): T = bwd(output)
      def classification: AdjointClassification = AdjointClassification.Embedding
    }

  def projection[T, U](fwd: T => U, bwd: U => T): Adjoint[T, U] =
    new Adjoint[T, U] {
      def forward(input: T): U = fwd(input)
      def backward(output: U): T = bwd(output)
      def classification: AdjointClassification = AdjointClassification.Projection
    }

  def lossy[T, U](fwd: T => U, bwd: U => T): Adjoint[T, U] =
    new Adjoint[T, U] {
      def forward(input: T): U = fwd(input)
      def backward(output: U): T = bwd(output)
      def classification: AdjointClassification = AdjointClassification.Lossy
    }

  /** Identity adjoint: id⁻ = id (REQ-ADJ-01) */
  def identity[T]: Adjoint[T, T] = isomorphism[T, T](x => x, x => x)
}
