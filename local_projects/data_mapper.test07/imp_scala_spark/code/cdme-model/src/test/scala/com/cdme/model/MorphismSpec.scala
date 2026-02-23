// Validates: REQ-LDM-02, REQ-ADJ-01, REQ-ADJ-02
// Tests for Morphism types, cardinalities, and adjoint specifications.
package com.cdme.model

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import com.cdme.model.adjoint._

class MorphismSpec extends AnyFlatSpec with Matchers {

  "Cardinality" should "have three variants" in {
    val cardinalities: Set[Cardinality] = Set(
      Cardinality.OneToOne,
      Cardinality.NToOne,
      Cardinality.OneToN
    )
    cardinalities should have size 3
  }

  "MorphismKind" should "have four variants" in {
    val kinds: Set[MorphismKind] = Set(
      MorphismKind.Structural,
      MorphismKind.Computational,
      MorphismKind.Algebraic,
      MorphismKind.External
    )
    kinds should have size 4
  }

  "Morphism" should "carry domain and codomain" in {
    val m = Morphism(
      id = "m1",
      name = "TestMorphism",
      domain = "A",
      codomain = "B",
      cardinality = Cardinality.OneToOne,
      morphismKind = MorphismKind.Structural,
      adjoint = None,
      accessControl = AccessControl.open
    )
    m.domain shouldBe "A"
    m.codomain shouldBe "B"
  }

  "SelfAdjoint" should "classify as Isomorphism" in {
    val sa = SelfAdjoint("inverse_m1")
    sa.classification shouldBe AdjointClassification.Isomorphism
    sa.inverseMorphismId shouldBe "inverse_m1"
  }

  "AggregationAdjoint" should "classify as Projection" in {
    val aa = AggregationAdjoint(ReverseJoinStrategy.Inline)
    aa.classification shouldBe AdjointClassification.Projection
    aa.reverseJoinStrategy shouldBe ReverseJoinStrategy.Inline
  }

  "FilterAdjoint with captured keys" should "classify as Embedding" in {
    val fa = FilterAdjoint(captureFilteredKeys = true)
    fa.classification shouldBe AdjointClassification.Embedding
  }

  "FilterAdjoint without captured keys" should "classify as Lossy" in {
    val fa = FilterAdjoint(captureFilteredKeys = false)
    fa.classification shouldBe AdjointClassification.Lossy
  }

  "KleisliAdjoint" should "classify as Projection" in {
    val ka = KleisliAdjoint(parentChildCapture = true)
    ka.classification shouldBe AdjointClassification.Projection
  }

  "AdjointClassification.compose" should "preserve Isomorphism" in {
    AdjointClassification.compose(
      AdjointClassification.Isomorphism,
      AdjointClassification.Isomorphism
    ) shouldBe AdjointClassification.Isomorphism
  }

  it should "degrade to Lossy for mixed types" in {
    AdjointClassification.compose(
      AdjointClassification.Embedding,
      AdjointClassification.Projection
    ) shouldBe AdjointClassification.Lossy
  }

  it should "preserve Embedding when both are Embedding" in {
    AdjointClassification.compose(
      AdjointClassification.Embedding,
      AdjointClassification.Embedding
    ) shouldBe AdjointClassification.Embedding
  }

  it should "pass through classification when composed with Isomorphism" in {
    AdjointClassification.compose(
      AdjointClassification.Isomorphism,
      AdjointClassification.Projection
    ) shouldBe AdjointClassification.Projection

    AdjointClassification.compose(
      AdjointClassification.Lossy,
      AdjointClassification.Isomorphism
    ) shouldBe AdjointClassification.Lossy
  }

  "ReverseJoinStrategy" should "have three variants" in {
    val strategies: Set[ReverseJoinStrategy] = Set(
      ReverseJoinStrategy.Inline,
      ReverseJoinStrategy.SeparateTable,
      ReverseJoinStrategy.Compressed
    )
    strategies should have size 3
  }
}
