// Validates: REQ-F-ERR-001, REQ-F-ERR-002, REQ-F-ERR-003, REQ-BR-ERR-001
// Tests for error object structure, validation error types
package cdme.model.error

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import java.time.Instant

class ErrorSpec extends AnyFunSuite with Matchers:

  // --- REQ-F-ERR-002: Error Object Structure ---

  test("REQ-F-ERR-002: ErrorObject contains all required metadata fields") {
    val err = ErrorObject(
      constraintType = ConstraintType.TypeConstraint,
      offendingValues = Map("field" -> "amount", "value" -> "abc"),
      sourceEntity = EntityId("trade"),
      sourceEpoch = EpochId("2026-02-20"),
      morphismPath = List(MorphismId("tradeToCP"), MorphismId("cpToRegion")),
      timestamp = Instant.parse("2026-02-20T12:00:00Z"),
      details = Some("Cannot convert 'abc' to Decimal")
    )

    err.constraintType shouldBe ConstraintType.TypeConstraint
    err.offendingValues should contain("field" -> "amount")
    err.offendingValues should contain("value" -> "abc")
    err.sourceEntity.value shouldBe "trade"
    err.sourceEpoch.value shouldBe "2026-02-20"
    err.morphismPath should have size 2
    err.morphismPath.head.value shouldBe "tradeToCP"
    err.timestamp shouldBe Instant.parse("2026-02-20T12:00:00Z")
    err.details shouldBe Some("Cannot convert 'abc' to Decimal")
  }

  test("REQ-F-ERR-002: ErrorObject supports all constraint types") {
    ConstraintType.TypeConstraint shouldBe a[ConstraintType]
    ConstraintType.GrainConstraint shouldBe a[ConstraintType]
    ConstraintType.RefinementPredicate shouldBe a[ConstraintType]
    ConstraintType.AccessControl shouldBe a[ConstraintType]
    ConstraintType.ContextBoundary shouldBe a[ConstraintType]
    ConstraintType.AccountingInvariant shouldBe a[ConstraintType]
    ConstraintType.ExternalCalculator shouldBe a[ConstraintType]
    ConstraintType.Custom("test") shouldBe a[ConstraintType]
  }

  test("REQ-F-ERR-002: ErrorObject details field is optional") {
    val err = ErrorObject(
      constraintType = ConstraintType.GrainConstraint,
      offendingValues = Map.empty,
      sourceEntity = EntityId("test"),
      sourceEpoch = EpochId("epoch1"),
      morphismPath = Nil,
      timestamp = Instant.now()
    )
    err.details shouldBe None
  }

  // --- REQ-F-ERR-003: Idempotent Error Handling ---

  test("REQ-F-ERR-003: identical inputs produce identical error objects") {
    val timestamp = Instant.parse("2026-02-20T12:00:00Z")

    val err1 = ErrorObject(
      constraintType = ConstraintType.TypeConstraint,
      offendingValues = Map("key" -> "val"),
      sourceEntity = EntityId("test"),
      sourceEpoch = EpochId("epoch1"),
      morphismPath = List(MorphismId("m1")),
      timestamp = timestamp
    )
    val err2 = ErrorObject(
      constraintType = ConstraintType.TypeConstraint,
      offendingValues = Map("key" -> "val"),
      sourceEntity = EntityId("test"),
      sourceEpoch = EpochId("epoch1"),
      morphismPath = List(MorphismId("m1")),
      timestamp = timestamp
    )

    err1 shouldBe err2
  }

  // --- ValidationError Types ---

  test("ValidationError: MorphismNotFound has name and message") {
    val err = ValidationError.MorphismNotFound("myMorphism", "Not found in category")
    err.morphismName shouldBe "myMorphism"
    err.message shouldBe "Not found in category"
  }

  test("ValidationError: MorphismNotFound default message uses morphism name") {
    val err = ValidationError.MorphismNotFound("myMorphism")
    err.toString should include("myMorphism")
  }

  test("ValidationError: TypeMismatch carries message") {
    val err = ValidationError.TypeMismatch("Int vs String")
    err.message shouldBe "Int vs String"
  }

  test("ValidationError: GrainViolation carries message") {
    val err = ValidationError.GrainViolation("Atomic vs Monthly without aggregation")
    err.message should include("Atomic")
  }

  test("ValidationError: AccessDenied carries principal and morphism info") {
    val err = ValidationError.AccessDenied("secretMorphism", "user123")
    err.morphismName shouldBe "secretMorphism"
    err.principalId shouldBe "user123"
    err.toString should include("user123")
    err.toString should include("secretMorphism")
  }

  test("ValidationError: ContextViolation carries message") {
    val err = ValidationError.ContextViolation("Epoch mismatch")
    err.message shouldBe "Epoch mismatch"
  }

  test("ValidationError: BoundaryViolation carries message") {
    val err = ValidationError.BoundaryViolation("Epoch boundary crossing")
    err.message should include("boundary")
  }

  test("ValidationError: BudgetExceeded carries estimate and budget") {
    val err = ValidationError.BudgetExceeded(1000000L, 100000L)
    err.estimatedCardinality shouldBe 1000000L
    err.budget shouldBe 100000L
    err.toString should include("1000000")
    err.toString should include("100000")
  }

  test("ValidationError: General carries arbitrary message") {
    val err = ValidationError.General("Something went wrong")
    err.message shouldBe "Something went wrong"
  }

  test("ValidationError: all subtypes are sealed") {
    // This is a compile-time check -- exhaustive match is enforced by Scala 3
    val err: ValidationError = ValidationError.General("test")
    val _ = err match
      case _: ValidationError.MorphismNotFound => "found"
      case _: ValidationError.TypeMismatch     => "type"
      case _: ValidationError.GrainViolation   => "grain"
      case _: ValidationError.AccessDenied     => "access"
      case _: ValidationError.ContextViolation => "context"
      case _: ValidationError.BoundaryViolation => "boundary"
      case _: ValidationError.BudgetExceeded   => "budget"
      case _: ValidationError.General          => "general"
    // If this compiles, the sealed hierarchy is complete
  }

  // --- Opaque type accessors ---

  test("EntityId opaque type preserves value") {
    val id = EntityId("trade")
    id.value shouldBe "trade"
  }

  test("EpochId opaque type preserves value") {
    val id = EpochId("2026-02-20")
    id.value shouldBe "2026-02-20"
  }

  test("MorphismId opaque type preserves value") {
    val id = MorphismId("tradeToCP")
    id.value shouldBe "tradeToCP"
  }
