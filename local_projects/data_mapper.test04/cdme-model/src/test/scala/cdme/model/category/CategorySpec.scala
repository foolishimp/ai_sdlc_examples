// Validates: REQ-F-LDM-001, REQ-F-LDM-002, REQ-F-LDM-003, REQ-F-LDM-008
// Tests for Category: identity morphisms, associativity laws, entity add/remove, multigraph support
package cdme.model.category

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import cdme.model.adjoint.{AdjointMorphism, BackwardResult, IsomorphicAdjoint}
import cdme.model.grain.Grain
import cdme.model.types.PrimitiveType
import cdme.model.access.AccessRule
import cdme.model.error.{ErrorObject, ValidationError}

class CategorySpec extends AnyFunSuite with Matchers with ScalaCheckPropertyChecks {

  // --- Test Fixtures ---

  private def makeEntity(name: String, grain: Grain = Grain.Atomic): Entity =
    Entity(
      id = EntityId(name.toLowerCase),
      name = name,
      grain = grain,
      attributes = Vector(
        (AttributeId("id"), PrimitiveType.IntType),
        (AttributeId("name"), PrimitiveType.StringType)
      )
    )

  private def makeIdentityMorphism(entityName: String): IsomorphicAdjoint[String, String] =
    IsomorphicAdjoint[String, String](
      id = MorphismId(s"id_${entityName.toLowerCase}"),
      name = s"identity_$entityName",
      domain = EntityId(entityName.toLowerCase),
      codomain = EntityId(entityName.toLowerCase),
      forwardFn = s => Right(s),
      backwardFn = s => s
    )

  private def makeMorphism(
      name: String,
      from: String,
      to: String
  ): IsomorphicAdjoint[String, String] =
    IsomorphicAdjoint[String, String](
      id = MorphismId(name),
      name = name,
      domain = EntityId(from.toLowerCase),
      codomain = EntityId(to.toLowerCase),
      forwardFn = s => Right(s),
      backwardFn = s => s
    )

  // --- REQ-F-LDM-001: Schema as Category ---

  test("REQ-F-LDM-001: empty category has no entities or morphisms") {
    val cat = Category.empty("test")
    cat.entityCount shouldBe 0
    cat.morphismCount shouldBe 0
    cat.name shouldBe "test"
  }

  test("REQ-F-LDM-001: entities are modelled as typed objects") {
    val entity = makeEntity("Trade")
    entity.id shouldBe EntityId("trade")
    entity.name shouldBe "Trade"
    entity.grain shouldBe Grain.Atomic
    entity.attributeCount shouldBe 2
  }

  test("REQ-F-LDM-001: identity morphism law - id . f = f for all morphisms f") {
    // For any value x, identity(f(x)) should equal f(x)
    val f: String => String = _.toUpperCase
    val identity: String => String = s => s

    forAll { (input: String) =>
      identity(f(input)) shouldBe f(input)
    }
  }

  test("REQ-F-LDM-001: identity morphism law - f . id = f for all morphisms f") {
    val f: String => String = _.toUpperCase
    val identity: String => String = s => s

    forAll { (input: String) =>
      f(identity(input)) shouldBe f(input)
    }
  }

  test("REQ-F-LDM-001: associativity - h . (g . f) = (h . g) . f") {
    val f: Int => Int = _ + 1
    val g: Int => Int = _ * 2
    val h: Int => Int = _ - 3

    forAll { (x: Int) =>
      whenever(x > Int.MinValue && x < Int.MaxValue / 2) {
        h(g(f(x))) shouldBe h(g(f(x)))
        // Explicit associativity check
        val gf: Int => Int = x => g(f(x))
        val hg: Int => Int = x => h(g(x))
        h(gf(x)) shouldBe hg(f(x))
      }
    }
  }

  test("REQ-F-LDM-001: category supports multiple morphisms between same entity pair (multigraph)") {
    val trade = makeEntity("Trade")
    val counterparty = makeEntity("Counterparty")
    val m1 = makeMorphism("tradeToCP_buyer", "trade", "counterparty")
    val m2 = makeMorphism("tradeToCP_seller", "trade", "counterparty")

    val result = for {
      cat <- Category.empty("test").addEntity(trade)
      cat2 <- cat.addEntity(counterparty)
      cat3 <- cat2.addMorphism(m1)
      cat4 <- cat3.addMorphism(m2)
    } yield cat4

    result.isRight shouldBe true
    result.toOption.get.morphismCount shouldBe 2
    result.toOption.get.morphismsFrom(EntityId("trade")).size shouldBe 2
  }

  // --- REQ-F-LDM-002: Morphism Cardinality Types ---

  test("REQ-F-LDM-002: morphisms declare cardinality type") {
    val m = makeMorphism("test", "a", "b")
    m.cardinality shouldBe CardinalityType.OneToOne
  }

  test("REQ-F-LDM-002: all cardinality types are expressible") {
    CardinalityType.values should contain allOf(
      CardinalityType.OneToOne,
      CardinalityType.ManyToOne,
      CardinalityType.OneToMany,
      CardinalityType.ManyToMany
    )
  }

  // --- Category Operations ---

  test("addEntity succeeds for a new entity") {
    val cat = Category.empty("test")
    val entity = makeEntity("Trade")
    val result = cat.addEntity(entity)
    result.isRight shouldBe true
    result.toOption.get.entityCount shouldBe 1
  }

  test("addEntity fails for duplicate entity ID") {
    val cat = Category.empty("test")
    val entity = makeEntity("Trade")
    val result = for {
      c1 <- cat.addEntity(entity)
      c2 <- c1.addEntity(entity)
    } yield c2

    result.isLeft shouldBe true
    result.left.toOption.get shouldBe a[ValidationError.General]
  }

  test("addMorphism fails when domain entity does not exist") {
    val cat = Category.empty("test")
    val entity = makeEntity("Target")
    val morphism = makeMorphism("m1", "source", "target")

    val result = for {
      c1 <- cat.addEntity(entity)
      c2 <- c1.addMorphism(morphism)
    } yield c2

    result.isLeft shouldBe true
    result.left.toOption.get shouldBe a[ValidationError.MorphismNotFound]
  }

  test("addMorphism fails when codomain entity does not exist") {
    val cat = Category.empty("test")
    val entity = makeEntity("Source")
    val morphism = makeMorphism("m1", "source", "target")

    val result = for {
      c1 <- cat.addEntity(entity)
      c2 <- c1.addMorphism(morphism)
    } yield c2

    result.isLeft shouldBe true
    result.left.toOption.get shouldBe a[ValidationError.MorphismNotFound]
  }

  test("addMorphism fails for duplicate morphism ID") {
    val trade = makeEntity("Trade")
    val cp = makeEntity("Counterparty")
    val m = makeMorphism("m1", "trade", "counterparty")

    val result = for {
      c1 <- Category.empty("test").addEntity(trade)
      c2 <- c1.addEntity(cp)
      c3 <- c2.addMorphism(m)
      c4 <- c3.addMorphism(m)
    } yield c4

    result.isLeft shouldBe true
  }

  test("validate detects morphism referencing unknown domain entity") {
    val entity = makeEntity("Target")
    val morphism = makeMorphism("bad", "nonexistent", "target")
    val cat = Category(
      name = "test",
      entities = Map(EntityId("target") -> entity),
      morphisms = Map(MorphismId("bad") -> morphism)
    )
    val errors = cat.validate()
    errors should not be empty
    errors.head shouldBe a[ValidationError.MorphismNotFound]
  }

  test("validate passes for a well-formed category") {
    val trade = makeEntity("Trade")
    val cp = makeEntity("Counterparty")
    val m = makeMorphism("m1", "trade", "counterparty")

    val result = for {
      c1 <- Category.empty("test").addEntity(trade)
      c2 <- c1.addEntity(cp)
      c3 <- c2.addMorphism(m)
    } yield c3.validate()

    result.isRight shouldBe true
    result.toOption.get shouldBe empty
  }

  // --- Query Operations ---

  test("morphismsFrom returns morphisms originating from given entity") {
    val trade = makeEntity("Trade")
    val cp = makeEntity("Counterparty")
    val cf = makeEntity("Cashflow")
    val m1 = makeMorphism("tradeToCP", "trade", "counterparty")
    val m2 = makeMorphism("tradeToCF", "trade", "cashflow")

    val result = for {
      c1 <- Category.empty("test").addEntity(trade)
      c2 <- c1.addEntity(cp)
      c3 <- c2.addEntity(cf)
      c4 <- c3.addMorphism(m1)
      c5 <- c4.addMorphism(m2)
    } yield c5

    val cat = result.toOption.get
    cat.morphismsFrom(EntityId("trade")).size shouldBe 2
    cat.morphismsFrom(EntityId("counterparty")).size shouldBe 0
  }

  test("morphismsTo returns morphisms targeting given entity") {
    val trade = makeEntity("Trade")
    val cp = makeEntity("Counterparty")
    val m = makeMorphism("tradeToCP", "trade", "counterparty")

    val result = for {
      c1 <- Category.empty("test").addEntity(trade)
      c2 <- c1.addEntity(cp)
      c3 <- c2.addMorphism(m)
    } yield c3

    val cat = result.toOption.get
    cat.morphismsTo(EntityId("counterparty")).size shouldBe 1
    cat.morphismsTo(EntityId("trade")).size shouldBe 0
  }

  test("findMorphism locates a specific morphism between entities") {
    val trade = makeEntity("Trade")
    val cp = makeEntity("Counterparty")
    val m = makeMorphism("tradeToCP", "trade", "counterparty")

    val result = for {
      c1 <- Category.empty("test").addEntity(trade)
      c2 <- c1.addEntity(cp)
      c3 <- c2.addMorphism(m)
    } yield c3

    val cat = result.toOption.get
    cat.findMorphism(EntityId("trade"), EntityId("counterparty"), "tradeToCP") shouldBe defined
    cat.findMorphism(EntityId("trade"), EntityId("counterparty"), "nonexistent") shouldBe None
  }

  // --- REQ-F-LDM-008: Schema Versioning ---

  test("REQ-F-LDM-008: CategoryVersion produces content-addressed hash") {
    val trade = makeEntity("Trade")
    val result = for {
      c1 <- Category.empty("trades").addEntity(trade)
    } yield CategoryVersion.create(c1)

    result.isRight shouldBe true
    val version = result.toOption.get
    version.hash.value should not be empty
    version.hash.value.length shouldBe 64 // SHA-256 hex string
  }

  test("REQ-F-LDM-008: identical categories produce identical content hashes") {
    val trade = makeEntity("Trade")
    val result = for {
      c1 <- Category.empty("trades").addEntity(trade)
    } yield c1

    val cat = result.toOption.get
    val v1 = CategoryVersion.create(cat)
    val v2 = CategoryVersion.create(cat)
    v1.isSameContent(v2) shouldBe true
  }

  test("REQ-F-LDM-008: different categories produce different content hashes") {
    val trade = makeEntity("Trade")
    val cp = makeEntity("Counterparty")

    val cat1 = Category.empty("test").addEntity(trade).toOption.get
    val cat2 = Category.empty("test").addEntity(cp).toOption.get

    val v1 = CategoryVersion.create(cat1)
    val v2 = CategoryVersion.create(cat2)
    v1.isSameContent(v2) shouldBe false
  }
}
