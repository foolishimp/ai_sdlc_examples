// Validates: REQ-F-ADJ-001, REQ-F-ADJ-002, REQ-F-ADJ-003, REQ-F-ADJ-004, REQ-F-ADJ-005, REQ-F-ADJ-006, REQ-F-ADJ-007, REQ-F-ADJ-008
// Tests for containment laws, contravariant composition, all 5 adjoint strategies
package cdme.model.adjoint

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import org.scalacheck.Gen
import cdme.model.category.{MorphismId, EntityId, CardinalityType, ContainmentType}
import cdme.model.context.EpochId
import cdme.model.access.AccessRule
import cdme.model.error.{ErrorObject, ConstraintType}

class AdjointSpec extends AnyFunSuite with Matchers with ScalaCheckPropertyChecks {

  // --- Helpers ---

  private def mkErrorObj(msg: String): ErrorObject = ErrorObject(
    constraintType = ConstraintType.TypeConstraint,
    offendingValues = Map("detail" -> msg),
    sourceEntity = EntityId("test"),
    sourceEpoch = EpochId("epoch1"),
    morphismPath = List(MorphismId("test")),
    timestamp = java.time.Instant.now()
  )

  // --- REQ-F-ADJ-001: Adjoint Interface ---

  test("REQ-F-ADJ-001: every adjoint morphism has forward and backward functions") {
    val iso = IsomorphicAdjoint[Int, Int](
      id = MorphismId("id_test"),
      name = "identity",
      domain = EntityId("a"),
      codomain = EntityId("a"),
      forwardFn = x => Right(x),
      backwardFn = x => x
    )
    iso.forward(42) shouldBe Right(42)
    iso.backward(42).records shouldBe Vector(42)
  }

  test("REQ-F-ADJ-001: morphisms declare containment type") {
    val iso = IsomorphicAdjoint[Int, Int](
      id = MorphismId("test"),
      name = "test",
      domain = EntityId("a"),
      codomain = EntityId("b"),
      forwardFn = x => Right(x),
      backwardFn = x => x
    )
    iso.containment shouldBe ContainmentType.Isomorphic
  }

  // --- REQ-F-ADJ-002: Isomorphic Adjoint (1:1) ---

  test("REQ-F-ADJ-002: isomorphic adjoint - backward(forward(x)) = x (exact round-trip)") {
    val iso = IsomorphicAdjoint[String, String](
      id = MorphismId("stringUpper"),
      name = "toUpper",
      domain = EntityId("a"),
      codomain = EntityId("b"),
      forwardFn = s => Right(s.toUpperCase),
      backwardFn = s => s.toLowerCase
    )

    val asciiStr = Gen.listOf(Gen.alphaChar).map(_.mkString)
    forAll(asciiStr) { (input: String) =>
      val fwd = iso.forward(input.toLowerCase)
      fwd.isRight shouldBe true
      val bwd = iso.backward(fwd.toOption.get)
      bwd.records should have size 1
      bwd.records.head shouldBe input.toLowerCase
    }
  }

  test("REQ-F-ADJ-002: isomorphic adjoint has OneToOne cardinality") {
    val iso = IsomorphicAdjoint[Int, Int](
      id = MorphismId("test"),
      name = "test",
      domain = EntityId("a"),
      codomain = EntityId("b"),
      forwardFn = x => Right(x * 2),
      backwardFn = x => x / 2
    )
    iso.cardinality shouldBe CardinalityType.OneToOne
    iso.containment shouldBe ContainmentType.Isomorphic
  }

  // --- REQ-F-ADJ-003: Preimage Adjoint (N:1) ---

  test("REQ-F-ADJ-003: preimage adjoint returns preimage set") {
    // Forward: extract first char. Backward: return all strings starting with that char.
    val preimage = PreimageAdjoint[String, String](
      id = MorphismId("firstChar"),
      name = "firstChar",
      domain = EntityId("words"),
      codomain = EntityId("chars"),
      forwardFn = s => Right(s.take(1)),
      preimageFn = c => Vector("apple", "avocado", "apricot").filter(_.startsWith(c))
    )

    preimage.cardinality shouldBe CardinalityType.ManyToOne
    preimage.containment shouldBe ContainmentType.Preimage

    val fwd = preimage.forward("apple")
    fwd shouldBe Right("a")

    val bwd = preimage.backward("a")
    bwd.records should contain("apple")
    bwd.records should contain("avocado")
    bwd.metadata should contain("adjointType" -> "preimage")
  }

  test("REQ-F-ADJ-003: preimage containment - backward(forward(x)) >= {x}") {
    val lookup = Map(1 -> "a", 2 -> "a", 3 -> "b")
    val reverseLookup = lookup.groupMap(_._2)(_._1).view.mapValues(_.toVector).toMap

    val preimage = PreimageAdjoint[Int, String](
      id = MorphismId("lookup"),
      name = "lookup",
      domain = EntityId("nums"),
      codomain = EntityId("letters"),
      forwardFn = n => lookup.get(n).toRight(mkErrorObj(s"key $n not found")),
      preimageFn = s => reverseLookup.getOrElse(s, Vector.empty)
    )

    val fwd = preimage.forward(1)
    fwd shouldBe Right("a")
    val bwd = preimage.backward("a")
    bwd.records should contain(1) // containment: x is in backward(forward(x))
    bwd.records should contain(2)
  }

  // --- REQ-F-ADJ-004: Kleisli Adjoint (1:N) ---

  test("REQ-F-ADJ-004: kleisli adjoint expands one-to-many") {
    val kleisli = KleisliAdjoint[String, String](
      id = MorphismId("split"),
      name = "splitWords",
      domain = EntityId("sentences"),
      codomain = EntityId("words"),
      forwardFn = s => Right(s.split(" ").toVector),
      parentFn = word => "parent_sentence"
    )

    kleisli.cardinality shouldBe CardinalityType.OneToMany
    kleisli.containment shouldBe ContainmentType.Expansion

    val expanded = kleisli.forwardExpand("hello world")
    expanded shouldBe Right(Vector("hello", "world"))
  }

  test("REQ-F-ADJ-004: kleisli backward collects parent record") {
    val kleisli = KleisliAdjoint[Int, Int](
      id = MorphismId("expand"),
      name = "expand",
      domain = EntityId("parents"),
      codomain = EntityId("children"),
      forwardFn = n => Right(Vector(n * 10, n * 10 + 1, n * 10 + 2)),
      parentFn = child => child / 10
    )

    val bwd = kleisli.backward(52)
    bwd.records should have size 1
    bwd.records.head shouldBe 5
    bwd.metadata should contain("adjointType" -> "kleisli")
  }

  // --- REQ-F-ADJ-005: Aggregation Adjoint ---

  test("REQ-F-ADJ-005: aggregation adjoint stores reverse-join metadata") {
    val agg = AggregationAdjoint[Int, Int](
      id = MorphismId("sum"),
      name = "sumByGroup",
      domain = EntityId("records"),
      codomain = EntityId("aggregated"),
      forwardFn = n => Right(n),
      groupKeyFn = n => s"group_${n % 3}",
      monoidName = "SumMonoid"
    )

    agg.cardinality shouldBe CardinalityType.ManyToOne
    agg.containment shouldBe ContainmentType.Aggregation

    // Backward at model level returns empty + metadata
    val bwd = agg.backward(100)
    bwd.metadata should contain("adjointType" -> "aggregation")
    bwd.metadata should contain("reverseJoinRequired" -> "true")
    bwd.metadata should contain("monoid" -> "SumMonoid")
  }

  test("REQ-F-ADJ-005: ReverseJoinTable tracks contributing keys") {
    val rjt = ReverseJoinTable(Map(
      "group_A" -> Set("rec_1", "rec_2", "rec_3"),
      "group_B" -> Set("rec_4", "rec_5")
    ))

    rjt.contributingKeys("group_A") should have size 3
    rjt.contributingKeys("group_A") should contain("rec_1")
    rjt.contributingKeys("group_B") should have size 2
    rjt.contributingKeys("nonexistent") shouldBe empty
    rjt.size shouldBe 2
  }

  // --- REQ-F-ADJ-006: Filter Adjoint ---

  test("REQ-F-ADJ-006: filter adjoint passes records meeting predicate") {
    val filter = FilterAdjoint[Int](
      id = MorphismId("positiveFilter"),
      name = "positiveOnly",
      domain = EntityId("records"),
      codomain = EntityId("records"),
      predicateFn = _ > 0,
      predicateDescription = "x > 0"
    )

    filter.containment shouldBe ContainmentType.Filter

    filter.forward(5).isRight shouldBe true
    filter.forward(-3).isLeft shouldBe true
  }

  test("REQ-F-ADJ-006: filter backward includes filter metadata") {
    val filter = FilterAdjoint[String](
      id = MorphismId("lenFilter"),
      name = "shortStrings",
      domain = EntityId("strings"),
      codomain = EntityId("strings"),
      predicateFn = _.length < 5,
      predicateDescription = "length < 5"
    )

    val bwd = filter.backward("abc")
    bwd.records should contain("abc")
    bwd.metadata should contain("adjointType" -> "filter")
    bwd.metadata should contain("filterPredicate" -> "length < 5")
  }

  test("REQ-F-ADJ-006: FilteredKeyStore tracks filtered-out keys") {
    val store = FilteredKeyStore(
      filteredKeys = Set("rec_1", "rec_3", "rec_7"),
      filterPredicate = "amount > 1000"
    )
    store.count shouldBe 3
    store.filteredKeys should contain("rec_1")
    store.filterPredicate shouldBe "amount > 1000"
  }

  // --- REQ-F-ADJ-007: Contravariant Adjoint Composition ---

  test("REQ-F-ADJ-007: composition of adjoints - forward is covariant g(f(x))") {
    val f = IsomorphicAdjoint[Int, Int](
      id = MorphismId("f"),
      name = "addOne",
      domain = EntityId("a"),
      codomain = EntityId("b"),
      forwardFn = x => Right(x + 1),
      backwardFn = x => x - 1
    )
    val g = IsomorphicAdjoint[Int, Int](
      id = MorphismId("g"),
      name = "double",
      domain = EntityId("b"),
      codomain = EntityId("c"),
      forwardFn = x => Right(x * 2),
      backwardFn = x => x / 2
    )

    val composed = AdjointComposer.compose(f, g)
    composed.isRight shouldBe true

    val gf = composed.toOption.get
    gf.forward(5) shouldBe Right(12) // (5+1)*2 = 12
  }

  test("REQ-F-ADJ-007: composition of adjoints - backward is contravariant f-(g-(z))") {
    val f = IsomorphicAdjoint[Int, Int](
      id = MorphismId("f"),
      name = "addOne",
      domain = EntityId("a"),
      codomain = EntityId("b"),
      forwardFn = x => Right(x + 1),
      backwardFn = x => x - 1
    )
    val g = IsomorphicAdjoint[Int, Int](
      id = MorphismId("g"),
      name = "double",
      domain = EntityId("b"),
      codomain = EntityId("c"),
      forwardFn = x => Right(x * 2),
      backwardFn = x => x / 2
    )

    val composed = AdjointComposer.compose(f, g).toOption.get
    val bwd = composed.backward(12)
    bwd.records should have size 1
    bwd.records.head shouldBe 5 // g-(12)=6, f-(6)=5
  }

  test("REQ-F-ADJ-007: identity adjoint composition - id- = id") {
    val identity = IsomorphicAdjoint[Int, Int](
      id = MorphismId("id"),
      name = "identity",
      domain = EntityId("a"),
      codomain = EntityId("a"),
      forwardFn = x => Right(x),
      backwardFn = x => x
    )

    forAll { (x: Int) =>
      identity.forward(x) shouldBe Right(x)
      identity.backward(x).records.head shouldBe x
    }
  }

  test("REQ-F-ADJ-007: composition rejects mismatched domain/codomain") {
    val f = IsomorphicAdjoint[Int, Int](
      id = MorphismId("f"),
      name = "f",
      domain = EntityId("a"),
      codomain = EntityId("b"),
      forwardFn = x => Right(x),
      backwardFn = x => x
    )
    val g = IsomorphicAdjoint[Int, Int](
      id = MorphismId("g"),
      name = "g",
      domain = EntityId("c"), // mismatch: expected "b"
      codomain = EntityId("d"),
      forwardFn = x => Right(x),
      backwardFn = x => x
    )

    val result = AdjointComposer.compose(f, g)
    result.isLeft shouldBe true
  }

  test("REQ-F-ADJ-007: composed morphism has correct domain and codomain") {
    val f = IsomorphicAdjoint[Int, Int](
      id = MorphismId("f"),
      name = "f",
      domain = EntityId("a"),
      codomain = EntityId("b"),
      forwardFn = x => Right(x),
      backwardFn = x => x
    )
    val g = IsomorphicAdjoint[Int, Int](
      id = MorphismId("g"),
      name = "g",
      domain = EntityId("b"),
      codomain = EntityId("c"),
      forwardFn = x => Right(x),
      backwardFn = x => x
    )

    val composed = AdjointComposer.compose(f, g).toOption.get
    composed.domain shouldBe EntityId("a")
    composed.codomain shouldBe EntityId("c")
  }

  // --- REQ-F-ADJ-008: Backward Metadata Capture ---

  test("REQ-F-ADJ-008: composed backward merges metadata") {
    val f = IsomorphicAdjoint[Int, Int](
      id = MorphismId("f"),
      name = "f",
      domain = EntityId("a"),
      codomain = EntityId("b"),
      forwardFn = x => Right(x + 1),
      backwardFn = x => x - 1
    )
    val g = IsomorphicAdjoint[Int, Int](
      id = MorphismId("g"),
      name = "g",
      domain = EntityId("b"),
      codomain = EntityId("c"),
      forwardFn = x => Right(x * 2),
      backwardFn = x => x / 2
    )

    val composed = AdjointComposer.compose(f, g).toOption.get
    val bwd = composed.backward(10)
    bwd.metadata should contain key "composedFrom"
  }

  // --- Property: Round-trip containment ---

  test("REQ-F-ADJ-002: property - isomorphic round-trip is exact for all integers") {
    val double = IsomorphicAdjoint[Int, Int](
      id = MorphismId("double"),
      name = "double",
      domain = EntityId("a"),
      codomain = EntityId("b"),
      forwardFn = x => Right(x * 2),
      backwardFn = x => x / 2
    )

    forAll { (x: Int) =>
      whenever(x >= Int.MinValue / 2 && x <= Int.MaxValue / 2) {
        val fwd = double.forward(x).toOption.get
        val bwd = double.backward(fwd).records.head
        bwd shouldBe x
      }
    }
  }

  // --- Cardinality Composition ---

  test("cardinality composition: OneToOne + X = X") {
    val oto = IsomorphicAdjoint[Int, Int](
      id = MorphismId("f"), name = "f",
      domain = EntityId("a"), codomain = EntityId("b"),
      forwardFn = x => Right(x), backwardFn = x => x
    )
    val mto = PreimageAdjoint[Int, Int](
      id = MorphismId("g"), name = "g",
      domain = EntityId("b"), codomain = EntityId("c"),
      forwardFn = x => Right(x), preimageFn = x => Vector(x)
    )

    val composed = AdjointComposer.compose(oto, mto).toOption.get
    composed.cardinality shouldBe CardinalityType.ManyToOne
  }
}
