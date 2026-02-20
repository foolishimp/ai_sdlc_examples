// Validates: REQ-F-TYP-001, REQ-F-TYP-002, REQ-F-TYP-003, REQ-F-TYP-004, REQ-F-TYP-005, REQ-BR-TYP-001
// Tests for type unification, subtype registry, no implicit casting, extended type system
package cdme.model.types

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import cdme.model.error.ValidationError

class TypeSystemSpec extends AnyFunSuite with Matchers with ScalaCheckPropertyChecks {

  // --- REQ-F-TYP-001: Extended Type System ---

  test("REQ-F-TYP-001: all seven primitive types exist") {
    PrimitiveType.StringType.typeName shouldBe "String"
    PrimitiveType.IntType.typeName shouldBe "Int"
    PrimitiveType.LongType.typeName shouldBe "Long"
    PrimitiveType.DecimalType.typeName shouldBe "Decimal"
    PrimitiveType.DateType.typeName shouldBe "Date"
    PrimitiveType.TimestampType.typeName shouldBe "Timestamp"
    PrimitiveType.BooleanType.typeName shouldBe "Boolean"
  }

  test("REQ-F-TYP-001: sum types represent tagged unions") {
    val sumType = SumType("type", List(
      ("success", PrimitiveType.StringType),
      ("error", PrimitiveType.IntType)
    ))
    sumType.typeName shouldBe "success | error"
    sumType.discriminant shouldBe "type"
    sumType.variants.size shouldBe 2
  }

  test("REQ-F-TYP-001: product types represent named records") {
    val product = ProductType(Some("Trade"), Vector(
      ("id", PrimitiveType.IntType),
      ("amount", PrimitiveType.DecimalType)
    ))
    product.typeName shouldBe "Trade"
    product.fieldType("id") shouldBe Some(PrimitiveType.IntType)
    product.fieldType("amount") shouldBe Some(PrimitiveType.DecimalType)
    product.fieldType("nonexistent") shouldBe None
  }

  test("REQ-F-TYP-001: unnamed product types display field structure") {
    val product = ProductType(None, Vector(
      ("a", PrimitiveType.IntType),
      ("b", PrimitiveType.StringType)
    ))
    product.typeName shouldBe "(a: Int, b: String)"
  }

  test("REQ-F-TYP-001: nested type composition") {
    val nested = OptionType(ListType(ProductType(Some("Item"), Vector(
      ("key", PrimitiveType.StringType),
      ("value", PrimitiveType.IntType)
    ))))
    nested.typeName shouldBe "Option[List[Item]]"
  }

  // --- REQ-F-TYP-002: Refinement Types ---

  test("REQ-F-TYP-002: refinement type has base type and predicate description") {
    val positiveDecimal = RefinementType(
      PrimitiveType.DecimalType,
      (x: Any) => x.asInstanceOf[BigDecimal] > BigDecimal(0),
      "x > 0"
    )
    positiveDecimal.typeName shouldBe "Decimal{x > 0}"
    positiveDecimal.base shouldBe PrimitiveType.DecimalType
  }

  test("REQ-F-TYP-002: refinement predicate evaluates correctly at runtime") {
    val positiveDecimal = RefinementType(
      PrimitiveType.DecimalType,
      (x: Any) => x.asInstanceOf[BigDecimal] > BigDecimal(0),
      "x > 0"
    )
    positiveDecimal.predicate(BigDecimal(10)) shouldBe true
    positiveDecimal.predicate(BigDecimal(-5)) shouldBe false
    positiveDecimal.predicate(BigDecimal(0)) shouldBe false
  }

  test("REQ-F-TYP-002: refinement types compose with other type constructors") {
    val refinedList = ListType(RefinementType(
      PrimitiveType.IntType,
      (x: Any) => x.asInstanceOf[Int] >= 0,
      "x >= 0"
    ))
    refinedList.typeName shouldBe "List[Int{x >= 0}]"
  }

  // --- REQ-F-TYP-003 / REQ-BR-TYP-001: No Implicit Casting ---

  test("REQ-F-TYP-003: type unification rejects incompatible types") {
    val registry = SubtypeRegistry.empty
    val result = TypeUnifier.unify(PrimitiveType.IntType, PrimitiveType.StringType, registry)
    result.isLeft shouldBe true
    result.left.toOption.get.message should include("no implicit casting")
  }

  test("REQ-F-TYP-003: type unification rejects Int-to-Long without explicit morphism") {
    val registry = SubtypeRegistry.empty
    val result = TypeUnifier.unify(PrimitiveType.IntType, PrimitiveType.LongType, registry)
    result.isLeft shouldBe true
  }

  test("REQ-BR-TYP-001: type unification error message mentions explicit ConversionMorphism") {
    val registry = SubtypeRegistry.empty
    val result = TypeUnifier.unify(PrimitiveType.DecimalType, PrimitiveType.IntType, registry)
    result.isLeft shouldBe true
    result.left.toOption.get.message should include("ConversionMorphism")
  }

  // --- REQ-F-TYP-004: Type Unification Rules ---

  test("REQ-F-TYP-004: exact type match allows composition") {
    val registry = SubtypeRegistry.empty
    val result = TypeUnifier.unify(PrimitiveType.IntType, PrimitiveType.IntType, registry)
    result shouldBe Right(PrimitiveType.IntType)
  }

  test("REQ-F-TYP-004: subtype relationship allows composition") {
    val registry = SubtypeRegistry.empty
      .declare(PrimitiveType.IntType, PrimitiveType.LongType)
      .toOption.get

    val result = TypeUnifier.unify(PrimitiveType.IntType, PrimitiveType.LongType, registry)
    result shouldBe Right(PrimitiveType.LongType)
  }

  test("REQ-F-TYP-004: OptionType unification recurses into inner types") {
    val registry = SubtypeRegistry.empty
    val result = TypeUnifier.unify(
      OptionType(PrimitiveType.IntType),
      OptionType(PrimitiveType.IntType),
      registry
    )
    result shouldBe Right(OptionType(PrimitiveType.IntType))
  }

  test("REQ-F-TYP-004: ListType unification recurses into element types") {
    val registry = SubtypeRegistry.empty
    val result = TypeUnifier.unify(
      ListType(PrimitiveType.StringType),
      ListType(PrimitiveType.StringType),
      registry
    )
    result shouldBe Right(ListType(PrimitiveType.StringType))
  }

  test("REQ-F-TYP-004: ProductType unification checks all fields") {
    val registry = SubtypeRegistry.empty
    val p1 = ProductType(Some("A"), Vector(
      ("x", PrimitiveType.IntType),
      ("y", PrimitiveType.StringType)
    ))
    val p2 = ProductType(Some("B"), Vector(
      ("x", PrimitiveType.IntType),
      ("y", PrimitiveType.StringType)
    ))
    val result = TypeUnifier.unify(p1, p2, registry)
    result.isRight shouldBe true
  }

  test("REQ-F-TYP-004: ProductType unification rejects missing fields") {
    val registry = SubtypeRegistry.empty
    val p1 = ProductType(Some("A"), Vector(("x", PrimitiveType.IntType)))
    val p2 = ProductType(Some("B"), Vector(
      ("x", PrimitiveType.IntType),
      ("y", PrimitiveType.StringType)
    ))
    val result = TypeUnifier.unify(p1, p2, registry)
    result.isLeft shouldBe true
    result.left.toOption.get.message should include("Missing field")
  }

  // --- REQ-F-TYP-005: Semantic Type Distinctions ---

  test("REQ-F-TYP-005: semantic types wrap base types with domain tag") {
    val money = SemanticType(PrimitiveType.DecimalType, SemanticTag("Money"))
    money.typeName shouldBe "Money[Decimal]"
    money.tag.value shouldBe "Money"
  }

  test("REQ-F-TYP-005: same semantic tag unifies successfully") {
    val registry = SubtypeRegistry.empty
    val money1 = SemanticType(PrimitiveType.DecimalType, SemanticTag("Money"))
    val money2 = SemanticType(PrimitiveType.DecimalType, SemanticTag("Money"))
    val result = TypeUnifier.unify(money1, money2, registry)
    result.isRight shouldBe true
  }

  test("REQ-F-TYP-005: different semantic tags reject unification") {
    val registry = SubtypeRegistry.empty
    val money = SemanticType(PrimitiveType.DecimalType, SemanticTag("Money"))
    val percent = SemanticType(PrimitiveType.DecimalType, SemanticTag("Percent"))
    val result = TypeUnifier.unify(money, percent, registry)
    result.isLeft shouldBe true
  }

  // --- SubtypeRegistry ---

  test("SubtypeRegistry: empty registry has no relationships") {
    val reg = SubtypeRegistry.empty
    reg.isSubtypeOf(PrimitiveType.IntType, PrimitiveType.LongType) shouldBe false
  }

  test("SubtypeRegistry: reflexive - every type is a subtype of itself") {
    val reg = SubtypeRegistry.empty
    reg.isSubtypeOf(PrimitiveType.IntType, PrimitiveType.IntType) shouldBe true
  }

  test("SubtypeRegistry: declare establishes subtype relationship") {
    val reg = SubtypeRegistry.empty
      .declare(PrimitiveType.IntType, PrimitiveType.LongType)
      .toOption.get

    reg.isSubtypeOf(PrimitiveType.IntType, PrimitiveType.LongType) shouldBe true
    reg.isSubtypeOf(PrimitiveType.LongType, PrimitiveType.IntType) shouldBe false
  }

  test("SubtypeRegistry: transitive subtype chain") {
    val a = PrimitiveType.IntType
    val b = PrimitiveType.LongType
    val c = PrimitiveType.DecimalType

    val reg = for {
      r1 <- SubtypeRegistry.empty.declare(a, b)
      r2 <- r1.declare(b, c)
    } yield r2

    reg.isRight shouldBe true
    reg.toOption.get.isSubtypeOf(a, c) shouldBe true
  }

  test("SubtypeRegistry: cycle detection rejects circular subtype declarations") {
    val a = PrimitiveType.IntType
    val b = PrimitiveType.LongType

    val result = for {
      r1 <- SubtypeRegistry.empty.declare(a, b)
      r2 <- r1.declare(b, a) // cycle!
    } yield r2

    result.isLeft shouldBe true
  }

  test("SubtypeRegistry: supertypesOf returns all declared supertypes") {
    val reg = for {
      r1 <- SubtypeRegistry.empty.declare(PrimitiveType.IntType, PrimitiveType.LongType)
      r2 <- r1.declare(PrimitiveType.LongType, PrimitiveType.DecimalType)
    } yield r2

    val supers = reg.toOption.get.supertypesOf(PrimitiveType.IntType)
    supers should contain(PrimitiveType.LongType)
    supers should contain(PrimitiveType.DecimalType)
  }

  // --- isCompatible shorthand ---

  test("isCompatible returns true for compatible types") {
    TypeUnifier.isCompatible(
      PrimitiveType.IntType,
      PrimitiveType.IntType,
      SubtypeRegistry.empty
    ) shouldBe true
  }

  test("isCompatible returns false for incompatible types") {
    TypeUnifier.isCompatible(
      PrimitiveType.IntType,
      PrimitiveType.StringType,
      SubtypeRegistry.empty
    ) shouldBe false
  }
}
