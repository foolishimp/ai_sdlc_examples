// Validates: REQ-F-ACC-005
// Tests for RBAC filtering, topology view
package cdme.model.access

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import cdme.model.category.{Category, Entity, EntityId, AttributeId, MorphismId}
import cdme.model.adjoint.IsomorphicAdjoint
import cdme.model.grain.Grain
import cdme.model.types.PrimitiveType

class AccessControlSpec extends AnyFunSuite with Matchers {

  // --- Test Fixtures ---

  private def makeEntity(name: String): Entity =
    Entity(
      id = EntityId(name.toLowerCase),
      name = name,
      grain = Grain.Atomic,
      attributes = Vector((AttributeId("id"), PrimitiveType.IntType))
    )

  private def makeMorphism(
      name: String,
      from: String,
      to: String,
      rules: List[AccessRule] = Nil
  ): IsomorphicAdjoint[String, String] =
    IsomorphicAdjoint[String, String](
      id = MorphismId(name),
      name = name,
      domain = EntityId(from),
      codomain = EntityId(to),
      forwardFn = s => Right(s),
      backwardFn = s => s,
      accessRules = rules
    )

  private def buildCategory(): Category = {
    val trade = makeEntity("Trade")
    val cp = makeEntity("Counterparty")
    val secret = makeEntity("Secret")

    val publicMorphism = makeMorphism("tradeToCP", "trade", "counterparty")
    val restrictedMorphism = makeMorphism("tradeToSecret", "trade", "secret",
      rules = List(AccessRule(MorphismId("tradeToSecret"), Set("admin", "auditor")))
    )

    val result = for {
      c1 <- Category.empty("test").addEntity(trade)
      c2 <- c1.addEntity(cp)
      c3 <- c2.addEntity(secret)
      c4 <- c3.addMorphism(publicMorphism)
      c5 <- c4.addMorphism(restrictedMorphism)
    } yield c5

    result.toOption.get
  }

  // --- AccessRule Tests ---

  test("AccessRule: permits principal with matching role") {
    val rule = AccessRule(MorphismId("m1"), Set("admin", "reader"))
    val admin = Principal("user1", Set("admin"))
    rule.permits(admin) shouldBe true
  }

  test("AccessRule: denies principal without matching role") {
    val rule = AccessRule(MorphismId("m1"), Set("admin"))
    val viewer = Principal("user2", Set("viewer"))
    rule.permits(viewer) shouldBe false
  }

  test("AccessRule: allowAll creates wildcard rule") {
    val rule = AccessRule.allowAll(MorphismId("m1"))
    rule.permittedRoles should contain("*")
  }

  // --- Principal Tests ---

  test("Principal: hasRole checks for specific role") {
    val p = Principal("user1", Set("admin", "reader"))
    p.hasRole("admin") shouldBe true
    p.hasRole("writer") shouldBe false
  }

  test("Principal: isUniversal checks for wildcard role") {
    val universal = Principal.universal
    universal.isUniversal shouldBe true
    universal.roles should contain("*")

    val regular = Principal("user1", Set("reader"))
    regular.isUniversal shouldBe false
  }

  // --- REQ-F-ACC-005: TopologyView RBAC Filtering ---

  test("REQ-F-ACC-005: universal principal sees all morphisms") {
    val cat = buildCategory()
    val view = TopologyView(cat, Principal.universal)

    view.accessibleMorphisms.size shouldBe 2
    view.filteredOutCount shouldBe 0
  }

  test("REQ-F-ACC-005: admin sees restricted morphisms") {
    val cat = buildCategory()
    val admin = Principal("adminUser", Set("admin"))
    val view = TopologyView(cat, admin)

    view.accessibleMorphisms.size shouldBe 2
    view.isAccessible(MorphismId("tradeToSecret")) shouldBe true
  }

  test("REQ-F-ACC-005: regular user cannot see restricted morphisms") {
    val cat = buildCategory()
    val viewer = Principal("viewerUser", Set("viewer"))
    val view = TopologyView(cat, viewer)

    view.accessibleMorphisms.size shouldBe 1
    view.isAccessible(MorphismId("tradeToCP")) shouldBe true
    view.isAccessible(MorphismId("tradeToSecret")) shouldBe false
    view.filteredOutCount shouldBe 1
  }

  test("REQ-F-ACC-005: denied morphisms are invisible in filtered category") {
    val cat = buildCategory()
    val viewer = Principal("viewerUser", Set("viewer"))
    val view = TopologyView(cat, viewer)

    val filtered = view.filteredCategory
    filtered.morphismCount shouldBe 1
    filtered.morphisms.contains(MorphismId("tradeToSecret")) shouldBe false
  }

  test("REQ-F-ACC-005: entities are retained even if all morphisms are filtered") {
    val cat = buildCategory()
    val viewer = Principal("viewerUser", Set("viewer"))
    val view = TopologyView(cat, viewer)

    val filtered = view.filteredCategory
    // All 3 entities should remain even though some morphisms are filtered
    filtered.entityCount shouldBe 3
  }

  test("REQ-F-ACC-005: morphisms without access rules are accessible to all") {
    val cat = buildCategory()
    val nobody = Principal("nobody", Set.empty)
    val view = TopologyView(cat, nobody)

    // publicMorphism has no access rules -> accessible to all
    view.isAccessible(MorphismId("tradeToCP")) shouldBe true
    // restrictedMorphism has access rules and nobody has none of the roles
    view.isAccessible(MorphismId("tradeToSecret")) shouldBe false
  }

  test("TopologyView.unfiltered creates view with universal access") {
    val cat = buildCategory()
    val view = TopologyView.unfiltered(cat)

    view.accessibleMorphisms.size shouldBe cat.morphismCount
    view.filteredOutCount shouldBe 0
  }
}
