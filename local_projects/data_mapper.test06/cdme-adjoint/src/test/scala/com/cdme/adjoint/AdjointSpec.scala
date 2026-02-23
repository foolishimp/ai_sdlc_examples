package com.cdme.adjoint

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

// Validates: REQ-ADJ-01, REQ-ADJ-02, REQ-ADJ-03, REQ-ADJ-07
class AdjointSpec extends AnyFlatSpec with Matchers {

  // ── REQ-ADJ-03: Isomorphisms (self-adjoint) ──

  "Adjoint.isomorphism" should "have exact round-trip: backward(forward(x)) = x" in {
    val doubleIt = Adjoint.isomorphism[Int, Int](_ * 2, _ / 2)
    doubleIt.forward(5) shouldBe 10
    doubleIt.backward(10) shouldBe 5
    doubleIt.backward(doubleIt.forward(5)) shouldBe 5
  }

  it should "be classified as Isomorphism" in {
    val id = Adjoint.identity[Int]
    id.classification shouldBe AdjointClassification.Isomorphism
  }

  // ── REQ-ADJ-01: Identity adjoint ──

  "Adjoint.identity" should "be self-adjoint: id⁻ = id" in {
    val id = Adjoint.identity[String]
    id.forward("hello") shouldBe "hello"
    id.backward("hello") shouldBe "hello"
  }

  // ── REQ-ADJ-07: Composition ──

  "AdjointComposition" should "compose forward correctly: (g ∘ f)(x) = g(f(x))" in {
    val f = Adjoint.isomorphism[Int, Int](_ + 1, _ - 1)
    val g = Adjoint.isomorphism[Int, Int](_ * 3, _ / 3)
    val composed = AdjointComposition.compose(f, g)

    composed.forward(5) shouldBe 18  // (5+1)*3 = 18
  }

  it should "compose backward contravariantly: (g ∘ f)⁻ = f⁻ ∘ g⁻" in {
    val f = Adjoint.isomorphism[Int, Int](_ + 1, _ - 1)
    val g = Adjoint.isomorphism[Int, Int](_ * 3, _ / 3)
    val composed = AdjointComposition.compose(f, g)

    composed.backward(18) shouldBe 5  // (18/3)-1 = 5
  }

  it should "compose classifications correctly" in {
    val iso = Adjoint.isomorphism[Int, Int](_ + 1, _ - 1)
    val proj = Adjoint.projection[Int, Int](_ + 1, _ - 1)
    val composed = AdjointComposition.compose(iso, proj)
    composed.classification shouldBe AdjointClassification.Projection

    val lossy = Adjoint.lossy[Int, Int](_ + 1, _ - 1)
    val composed2 = AdjointComposition.compose(iso, lossy)
    composed2.classification shouldBe AdjointClassification.Lossy
  }

  // ── REQ-ADJ-02: Classification ──

  "AdjointClassification.compose" should "preserve Isomorphism with Isomorphism" in {
    AdjointClassification.compose(AdjointClassification.Isomorphism, AdjointClassification.Isomorphism) shouldBe AdjointClassification.Isomorphism
  }

  it should "compose Embedding with Embedding → Embedding" in {
    AdjointClassification.compose(AdjointClassification.Embedding, AdjointClassification.Embedding) shouldBe AdjointClassification.Embedding
  }

  it should "compose anything with Lossy → Lossy" in {
    AdjointClassification.compose(AdjointClassification.Embedding, AdjointClassification.Projection) shouldBe AdjointClassification.Lossy
  }
}
