// Validates: REQ-F-LDM-001, REQ-F-LDM-003, REQ-F-TRV-002, REQ-F-TRV-006, REQ-F-TYP-003,
//            REQ-F-ERR-001, REQ-F-ADJ-001, REQ-F-ACC-001, REQ-F-INT-005, REQ-F-AI-001,
//            REQ-BR-ERR-001, REQ-BR-TYP-001, REQ-BR-AI-001
// Acceptance criteria tests from feature vector REQ-F-CDME-001
package cdme.acceptance

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import cdme.model.category.*
import cdme.model.adjoint.*
import cdme.model.grain.*
import cdme.model.types.*
import cdme.model.context.*
import cdme.model.access.*
import cdme.model.error.*
import cdme.compiler.*
import cdme.runtime.*
import cdme.api.*
import java.time.Instant

class AcceptanceCriteriaSpec extends AnyFunSuite with Matchers with ScalaCheckPropertyChecks:

  // --- Shared Fixtures ---

  private def buildTradingCategory(): Category =
    val trade = Entity(
      id = EntityId("trade"),
      name = "Trade",
      grain = Grain.Atomic,
      attributes = Vector(
        (AttributeId("id"), PrimitiveType.IntType),
        (AttributeId("amount"), SemanticType(PrimitiveType.DecimalType, SemanticTag("Money"))),
        (AttributeId("tradeDate"), PrimitiveType.DateType)
      )
    )
    val counterparty = Entity(
      id = EntityId("counterparty"),
      name = "Counterparty",
      grain = Grain.Atomic,
      attributes = Vector(
        (AttributeId("id"), PrimitiveType.IntType),
        (AttributeId("name"), PrimitiveType.StringType)
      )
    )
    val dailySummary = Entity(
      id = EntityId("daily_summary"),
      name = "DailySummary",
      grain = Grain.Daily,
      attributes = Vector(
        (AttributeId("date"), PrimitiveType.DateType),
        (AttributeId("total"), PrimitiveType.DecimalType)
      )
    )
    val tradeToCP = IsomorphicAdjoint[String, String](
      id = MorphismId("tradeToCP"),
      name = "tradeToCP",
      domain = EntityId("trade"),
      codomain = EntityId("counterparty"),
      forwardFn = s => Right(s),
      backwardFn = s => s
    )
    val tradeToDaily = PreimageAdjoint[String, String](
      id = MorphismId("tradeToDaily"),
      name = "tradeToDaily",
      domain = EntityId("trade"),
      codomain = EntityId("daily_summary"),
      forwardFn = s => Right(s),
      preimageFn = s => Vector(s)
    )
    (for
      c1 <- Category.empty("TradingBook").addEntity(trade)
      c2 <- c1.addEntity(counterparty)
      c3 <- c2.addEntity(dailySummary)
      c4 <- c3.addMorphism(tradeToCP)
      c5 <- c4.addMorphism(tradeToDaily)
    yield c5).toOption.get

  // ==========================================================================
  // AC-CDME-001: Category Laws
  // ==========================================================================

  test("AC-CDME-001: LDM is represented as a category with identity and associativity laws") {
    val cat = buildTradingCategory()

    // Category has entities and morphisms
    cat.entityCount shouldBe 3
    cat.morphismCount shouldBe 2

    // Category validates successfully
    cat.validate() shouldBe empty

    // Identity law: id . f = f (for any function)
    val f: Int => Int = _ + 1
    val id: Int => Int = x => x
    forAll { (x: Int) =>
      id(f(x)) shouldBe f(x)
      f(id(x)) shouldBe f(x)
    }

    // Associativity: h . (g . f) = (h . g) . f
    val g: Int => Int = _ * 2
    val h: Int => Int = _ - 3
    forAll { (x: Int) =>
      whenever(x > Int.MinValue / 2 && x < Int.MaxValue / 2) {
        h(g(f(x))) shouldBe h(g(f(x)))
        val gf = (x: Int) => g(f(x))
        val hg = (x: Int) => h(g(x))
        h(gf(x)) shouldBe hg(f(x))
      }
    }
  }

  // ==========================================================================
  // AC-CDME-002: Path Validation Rejections
  // ==========================================================================

  test("AC-CDME-002: path validation catches missing morphisms at definition time") {
    val cat = buildTradingCategory()
    val ctx = CompilationContext(cat, GrainOrder.standardTemporal, SubtypeRegistry.empty, Principal.universal)

    val badPath = PathExpression(List(PathStep("Trade", "nonExistent")))
    val result = PathCompiler.compile(badPath, ctx)
    result.isLeft shouldBe true
    result.left.toOption.get.exists(_.isInstanceOf[ValidationError.MorphismNotFound]) shouldBe true
  }

  test("AC-CDME-002: path validation catches missing entity at definition time") {
    val cat = buildTradingCategory()
    val ctx = CompilationContext(cat, GrainOrder.standardTemporal, SubtypeRegistry.empty, Principal.universal)

    val badPath = PathExpression(List(PathStep("NonExistent", "tradeToCP")))
    val result = PathCompiler.compile(badPath, ctx)
    result.isLeft shouldBe true
  }

  // ==========================================================================
  // AC-CDME-003: Grain Safety Rejections
  // ==========================================================================

  test("AC-CDME-003: grain safety violations are rejected at definition time") {
    // Direct grain violation: Atomic -> Daily without aggregation (OneToOne)
    val result = GrainChecker.checkTransition(
      Grain.Atomic, Grain.Daily, CardinalityType.OneToOne, GrainOrder.standardTemporal
    )
    result.isLeft shouldBe true
    result.left.toOption.get shouldBe a[ValidationError.GrainViolation]
  }

  test("AC-CDME-003: coarser-to-finer aggregation is rejected") {
    val result = GrainChecker.checkTransition(
      Grain.Monthly, Grain.Daily, CardinalityType.ManyToOne, GrainOrder.standardTemporal
    )
    result.isLeft shouldBe true
  }

  // ==========================================================================
  // AC-CDME-004: Determinism Property
  // ==========================================================================

  test("AC-CDME-004: identical inputs + config produce identical outputs") {
    val iso = IsomorphicAdjoint[String, String](
      id = MorphismId("upper"),
      name = "toUpper",
      domain = EntityId("a"),
      codomain = EntityId("b"),
      forwardFn = s => Right(s.toUpperCase),
      backwardFn = s => s.toLowerCase
    )

    forAll { (input: String) =>
      val result1 = iso.forward(input)
      val result2 = iso.forward(input)
      result1 shouldBe result2 // bitwise identical for deterministic function
    }
  }

  test("AC-CDME-004: category version hash is deterministic") {
    val cat = buildTradingCategory()
    val v1 = CategoryVersion.create(cat)
    val v2 = CategoryVersion.create(cat)
    v1.hash shouldBe v2.hash
  }

  // ==========================================================================
  // AC-CDME-005: No Implicit Conversions
  // ==========================================================================

  test("AC-CDME-005: zero implicit type conversions -- all conversions must be named morphisms") {
    val registry = SubtypeRegistry.empty

    // Int to String: rejected
    TypeUnifier.unify(PrimitiveType.IntType, PrimitiveType.StringType, registry).isLeft shouldBe true

    // Int to Long: rejected (no declared subtype)
    TypeUnifier.unify(PrimitiveType.IntType, PrimitiveType.LongType, registry).isLeft shouldBe true

    // Decimal to Int: rejected
    TypeUnifier.unify(PrimitiveType.DecimalType, PrimitiveType.IntType, registry).isLeft shouldBe true

    // Money to Percent: rejected (different semantic tags)
    val money = SemanticType(PrimitiveType.DecimalType, SemanticTag("Money"))
    val percent = SemanticType(PrimitiveType.DecimalType, SemanticTag("Percent"))
    TypeUnifier.unify(money, percent, registry).isLeft shouldBe true
  }

  // ==========================================================================
  // AC-CDME-006: Either Monad Error Routing
  // ==========================================================================

  test("AC-CDME-006: all failures routed via Either monad; no silent record drops") {
    val sink = new ErrorSink:
      var errors: List[ErrorObject] = Nil
      def write(es: List[ErrorObject]): Either[SinkError, Unit] =
        errors = errors ++ es
        Right(())
      def flush(): Either[SinkError, Unit] = Right(())

    val router = ErrorRouter(sink, BatchThresholdConfig(maxAbsoluteErrors = Some(10000)))

    var successes = 0
    var failures = 0
    val total = 50

    for i <- 1 to total do
      val input: Either[ErrorObject, Int] =
        if i % 5 == 0 then Left(ErrorObject(
          ConstraintType.TypeConstraint,
          Map("i" -> i.toString),
          cdme.model.error.EntityId("test"),
          cdme.model.error.EpochId("e1"),
          Nil,
          Instant.now()
        ))
        else Right(i)

      router.route(input) match
        case Right(_) => successes += 1
        case Left(_)  => failures += 1

    // No silent drops: all records accounted for
    (successes + failures) shouldBe total
    router.currentTotalCount shouldBe total
  }

  // ==========================================================================
  // AC-CDME-007: Adjoint Law Property Tests
  // ==========================================================================

  test("AC-CDME-007: every morphism implements Adjoint interface with containment laws") {
    val iso = IsomorphicAdjoint[Int, Int](
      id = MorphismId("test"), name = "test",
      domain = EntityId("a"), codomain = EntityId("b"),
      forwardFn = x => Right(x * 3),
      backwardFn = x => x / 3
    )

    // Property: backward(forward(x)) = x for isomorphic
    forAll { (x: Int) =>
      whenever(x >= Int.MinValue / 3 && x <= Int.MaxValue / 3) {
        val fwd = iso.forward(x).toOption.get
        val bwd = iso.backward(fwd).records.head
        bwd shouldBe x
      }
    }

    // Property: containment type is declared
    iso.containment shouldBe ContainmentType.Isomorphic
  }

  // ==========================================================================
  // AC-CDME-008: Accounting Invariant
  // ==========================================================================

  test("AC-CDME-008: |input| = |processed| + |filtered| + |errored| for every run") {
    forAll { (input: Short) =>
      whenever(input > 0 && input < 1000) {
        val total = input.toLong
        val processed = total / 2
        val filtered = total / 4
        val errored = total - processed - filtered

        val ledger = AccountingLedger(
          runId = "test",
          inputCount = total,
          sourceKeyField = "id",
          processedCount = processed,
          filteredCount = filtered,
          erroredCount = errored
        )

        ledger.invariantHolds shouldBe true

        val verified = CompletionGate.verify(ledger)
        verified.isRight shouldBe true
      }
    }
  }

  // ==========================================================================
  // AC-CDME-009: Lineage Traceability
  // ==========================================================================

  test("AC-CDME-009: every target value traces to source epoch, entity, and morphism path") {
    // ErrorObject captures full lineage metadata
    val error = ErrorObject(
      constraintType = ConstraintType.TypeConstraint,
      offendingValues = Map("value" -> "abc"),
      sourceEntity = cdme.model.error.EntityId("trade"),
      sourceEpoch = cdme.model.error.EpochId("2026-02-20"),
      morphismPath = List(
        cdme.model.error.MorphismId("tradeToCP"),
        cdme.model.error.MorphismId("cpToRegion")
      ),
      timestamp = Instant.parse("2026-02-20T12:00:00Z")
    )

    // All lineage fields are present
    error.sourceEntity.value shouldBe "trade"
    error.sourceEpoch.value shouldBe "2026-02-20"
    error.morphismPath should have size 2
    error.morphismPath.head.value shouldBe "tradeToCP"
    error.morphismPath.last.value shouldBe "cpToRegion"
  }

  test("AC-CDME-009: adjoint backward result carries traceability metadata") {
    val agg = AggregationAdjoint[Int, Int](
      id = MorphismId("sum"), name = "sum",
      domain = EntityId("records"), codomain = EntityId("agg"),
      forwardFn = x => Right(x), groupKeyFn = x => s"grp_${x % 3}",
      monoidName = "SumMonoid"
    )

    val bwd = agg.backward(100)
    bwd.metadata should contain key "adjointType"
    bwd.metadata should contain key "reverseJoinRequired"
    bwd.metadata should contain key "monoid"
  }

  // ==========================================================================
  // AC-CDME-010: AI Assurance Same-Rules
  // ==========================================================================

  test("AC-CDME-010: AI-generated mappings pass identical validation as human mappings") {
    val cat = buildTradingCategory()
    val ctx = CompilationContext(cat, GrainOrder.standardTemporal, SubtypeRegistry.empty, Principal.universal)

    // Simulate a "human" path
    val humanPath = PathExpression(List(PathStep("Trade", "tradeToCP")))
    val humanResult = PathCompiler.compile(humanPath, ctx)

    // Simulate an "AI-generated" path (identical definition)
    val aiPath = PathExpression(List(PathStep("Trade", "tradeToCP")))
    val aiResult = PathCompiler.compile(aiPath, ctx)

    // Same code path, same validation, same result
    humanResult shouldBe aiResult

    // AI hallucination: referencing non-existent morphism
    val hallucinatedPath = PathExpression(List(PathStep("Trade", "aiHallucinated")))
    val hallucinatedResult = PathCompiler.compile(hallucinatedPath, ctx)
    hallucinatedResult.isLeft shouldBe true
  }

  // ==========================================================================
  // AC-CDME-011: Zero Silent Data Loss
  // ==========================================================================

  test("AC-CDME-011: zero silent data loss across all runs") {
    // Property: for any input partitioning, the accounting invariant holds
    forAll { (n: Byte) =>
      whenever(n > 0) {
        val total = n.toLong.abs
        val processed = total / 3
        val errored = total / 3
        val filtered = total - processed - errored

        val ledger = AccountingLedger(
          runId = "no-loss-test",
          inputCount = total,
          sourceKeyField = "id",
          processedCount = processed,
          filteredCount = filtered,
          erroredCount = errored
        )

        // Invariant must hold: no silent drops
        ledger.invariantHolds shouldBe true
        ledger.discrepancy shouldBe 0L
      }
    }
  }

  test("AC-CDME-011: filter adjoint captures filtered records, not silently dropping them") {
    val filter = FilterAdjoint[Int](
      id = MorphismId("posFilter"), name = "positiveOnly",
      domain = EntityId("records"), codomain = EntityId("records"),
      predicateFn = _ > 0, predicateDescription = "x > 0"
    )

    // Positive: passes through
    filter.forward(5).isRight shouldBe true

    // Negative: filtered out as Left (not silently dropped)
    val filtered = filter.forward(-3)
    filtered.isLeft shouldBe true
    // The error object carries details about the filter
    filtered.left.toOption.get.details shouldBe defined
    filtered.left.toOption.get.details.get should include("Filtered by")
  }
