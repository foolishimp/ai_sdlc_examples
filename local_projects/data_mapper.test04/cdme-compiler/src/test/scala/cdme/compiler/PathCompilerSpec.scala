// Validates: REQ-F-LDM-003, REQ-F-TRV-001, REQ-F-TRV-002, REQ-F-TRV-003, REQ-F-TRV-004, REQ-F-TRV-007, REQ-F-ACC-005
// Tests for path validation, type checking, grain checking, context lifting
package cdme.compiler

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import cdme.model.category.{Category, Entity, EntityId, AttributeId, MorphismId, CardinalityType}
import cdme.model.adjoint.{IsomorphicAdjoint, PreimageAdjoint, KleisliAdjoint}
import cdme.model.grain.{Grain, GrainOrder}
import cdme.model.types.{PrimitiveType, SubtypeRegistry}
import cdme.model.access.{Principal, AccessRule}
import cdme.model.error.ValidationError

class PathCompilerSpec extends AnyFunSuite with Matchers {

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

  private def makeCategory(): Category = {
    val trade = makeEntity("Trade", Grain.Atomic)
    val cp = makeEntity("Counterparty", Grain.Atomic)
    val region = makeEntity("Region", Grain.Atomic)
    val dailySummary = makeEntity("DailySummary", Grain.Daily)

    val tradeToCP = IsomorphicAdjoint[String, String](
      id = MorphismId("tradeToCP"),
      name = "tradeToCP",
      domain = EntityId("trade"),
      codomain = EntityId("counterparty"),
      forwardFn = s => Right(s),
      backwardFn = s => s
    )

    val cpToRegion = IsomorphicAdjoint[String, String](
      id = MorphismId("cpToRegion"),
      name = "cpToRegion",
      domain = EntityId("counterparty"),
      codomain = EntityId("region"),
      forwardFn = s => Right(s),
      backwardFn = s => s
    )

    val tradeToDaily = PreimageAdjoint[String, String](
      id = MorphismId("tradeToDaily"),
      name = "tradeToDaily",
      domain = EntityId("trade"),
      codomain = EntityId("dailysummary"),
      forwardFn = s => Right(s),
      preimageFn = s => Vector(s)
    )

    val result = for {
      c1 <- Category.empty("test").addEntity(trade)
      c2 <- c1.addEntity(cp)
      c3 <- c2.addEntity(region)
      c4 <- c3.addEntity(dailySummary)
      c5 <- c4.addMorphism(tradeToCP)
      c6 <- c5.addMorphism(cpToRegion)
      c7 <- c6.addMorphism(tradeToDaily)
    } yield c7

    result.toOption.get
  }

  private def defaultContext(category: Category): CompilationContext =
    CompilationContext(
      category = category,
      grainOrder = GrainOrder.standardTemporal,
      subtypeRegistry = SubtypeRegistry.empty,
      principal = Principal.universal
    )

  // --- REQ-F-LDM-003: Path Validation ---

  test("REQ-F-LDM-003: valid single-step path compiles successfully") {
    val cat = makeCategory()
    val path = PathExpression(List(PathStep("Trade", "tradeToCP")))
    val result = PathCompiler.compile(path, defaultContext(cat))

    result.isRight shouldBe true
    val compiled = result.toOption.get
    compiled.steps should have size 1
  }

  test("REQ-F-LDM-003: valid multi-step path compiles successfully") {
    val cat = makeCategory()
    val path = PathExpression(List(
      PathStep("Trade", "tradeToCP"),
      PathStep("Counterparty", "cpToRegion")
    ))
    val result = PathCompiler.compile(path, defaultContext(cat))

    result.isRight shouldBe true
    result.toOption.get.steps should have size 2
  }

  test("REQ-F-LDM-003: missing morphism is rejected with MorphismNotFound") {
    val cat = makeCategory()
    val path = PathExpression(List(PathStep("Trade", "nonExistentMorphism")))
    val result = PathCompiler.compile(path, defaultContext(cat))

    result.isLeft shouldBe true
    result.left.toOption.get.head shouldBe a[ValidationError.MorphismNotFound]
  }

  test("REQ-F-LDM-003: missing source entity is rejected") {
    val cat = makeCategory()
    val path = PathExpression(List(PathStep("NonExistent", "tradeToCP")))
    val result = PathCompiler.compile(path, defaultContext(cat))

    result.isLeft shouldBe true
    result.left.toOption.get.head.message should include("not found")
  }

  test("REQ-F-LDM-003: empty path is rejected") {
    val cat = makeCategory()
    val path = PathExpression(Nil)
    val result = PathCompiler.compile(path, defaultContext(cat))

    result.isLeft shouldBe true
    result.left.toOption.get.head.message should include("Empty path")
  }

  // --- REQ-F-TRV-001: Kleisli Context Lifting ---

  test("REQ-F-TRV-001: OneToOne morphism stays Scalar context") {
    val cat = makeCategory()
    val path = PathExpression(List(PathStep("Trade", "tradeToCP")))
    val result = PathCompiler.compile(path, defaultContext(cat))

    result.isRight shouldBe true
    result.toOption.get.contextType shouldBe ContextType.Scalar
  }

  test("REQ-F-TRV-001: context lifting is tracked through path") {
    val cat = makeCategory()
    val path = PathExpression(List(PathStep("Trade", "tradeToCP")))
    val result = PathCompiler.compile(path, defaultContext(cat))

    result.isRight shouldBe true
    val step = result.toOption.get.steps.head
    step.contextLifting.from shouldBe ContextType.Scalar
  }

  // --- REQ-F-TRV-002: Grain Safety ---

  test("REQ-F-TRV-002: same-grain traversal is allowed") {
    val cat = makeCategory()
    val path = PathExpression(List(PathStep("Trade", "tradeToCP")))
    val result = PathCompiler.compile(path, defaultContext(cat))

    result.isRight shouldBe true
  }

  test("REQ-F-TRV-002: finer-to-coarser with ManyToOne (aggregation) is allowed") {
    // tradeToDaily is ManyToOne: Atomic -> Daily
    val cat = makeCategory()
    val path = PathExpression(List(PathStep("Trade", "tradeToDaily")))
    val result = PathCompiler.compile(path, defaultContext(cat))

    result.isRight shouldBe true
  }

  // --- REQ-F-ACC-005: RBAC applied before path compilation ---

  test("REQ-F-ACC-005: restricted morphism is invisible to unauthorized principal") {
    val trade = makeEntity("Trade")
    val secret = makeEntity("Secret")
    val restrictedMorphism = IsomorphicAdjoint[String, String](
      id = MorphismId("tradeToSecret"),
      name = "tradeToSecret",
      domain = EntityId("trade"),
      codomain = EntityId("secret"),
      forwardFn = s => Right(s),
      backwardFn = s => s,
      accessRules = List(AccessRule(MorphismId("tradeToSecret"), Set("admin")))
    )

    val cat = (for {
      c1 <- Category.empty("test").addEntity(trade)
      c2 <- c1.addEntity(secret)
      c3 <- c2.addMorphism(restrictedMorphism)
    } yield c3).toOption.get

    val ctx = CompilationContext(
      category = cat,
      grainOrder = GrainOrder.standardTemporal,
      subtypeRegistry = SubtypeRegistry.empty,
      principal = Principal("viewer", Set("viewer"))
    )

    val path = PathExpression(List(PathStep("Trade", "tradeToSecret")))
    val result = PathCompiler.compile(path, ctx)

    result.isLeft shouldBe true
    result.left.toOption.get.head shouldBe a[ValidationError.MorphismNotFound]
  }

  test("REQ-F-ACC-005: admin can access restricted morphism") {
    val trade = makeEntity("Trade")
    val secret = makeEntity("Secret")
    val restrictedMorphism = IsomorphicAdjoint[String, String](
      id = MorphismId("tradeToSecret"),
      name = "tradeToSecret",
      domain = EntityId("trade"),
      codomain = EntityId("secret"),
      forwardFn = s => Right(s),
      backwardFn = s => s,
      accessRules = List(AccessRule(MorphismId("tradeToSecret"), Set("admin")))
    )

    val cat = (for {
      c1 <- Category.empty("test").addEntity(trade)
      c2 <- c1.addEntity(secret)
      c3 <- c2.addMorphism(restrictedMorphism)
    } yield c3).toOption.get

    val ctx = CompilationContext(
      category = cat,
      grainOrder = GrainOrder.standardTemporal,
      subtypeRegistry = SubtypeRegistry.empty,
      principal = Principal("admin", Set("admin"))
    )

    val path = PathExpression(List(PathStep("Trade", "tradeToSecret")))
    val result = PathCompiler.compile(path, ctx)

    result.isRight shouldBe true
  }

  // --- Compiled Path properties ---

  test("compiled path carries result type and grain") {
    val cat = makeCategory()
    val path = PathExpression(List(PathStep("Trade", "tradeToCP")))
    val result = PathCompiler.compile(path, defaultContext(cat))

    result.isRight shouldBe true
    val compiled = result.toOption.get
    compiled.resultGrain shouldBe Grain.Atomic
    compiled.resultType should not be null
  }

  // --- PathExpression ---

  test("PathExpression.show produces human-readable representation") {
    val path = PathExpression(List(
      PathStep("Trade", "tradeToCP"),
      PathStep("Counterparty", "cpToRegion")
    ))
    path.show should include("Trade")
    path.show should include("tradeToCP")
  }
}
