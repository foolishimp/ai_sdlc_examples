package com.cdme.api

import java.time.Instant
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import com.cdme.model._
import com.cdme.compiler._
import com.cdme.runtime._

// Validates: API integration
class CdmeApiSpec extends AnyFlatSpec with Matchers {

  val category = Category(
    "TestModel",
    Map(
      "Trade" -> Entity("Trade", Grain.Atomic, Map("id" -> SemanticType.StringType, "amount" -> SemanticType.DoubleType)),
      "Position" -> Entity("Position", Grain.Daily, Map("id" -> SemanticType.StringType))
    ),
    Map(
      "trade_to_position" -> Morphism("trade_to_position", "Trade", "Position",
        Cardinality.NToOne, MorphismType.Structural,
        SemanticType.StringType, SemanticType.StringType)
    )
  )

  val compiler = new TopologicalCompiler()
  val engine = new DefaultExecutionEngine()
  val service = new DefaultCdmeService(Map("TestModel" -> category), compiler, engine)

  "DefaultCdmeService.compile" should "return success for valid mapping" in {
    val req = CompileRequest(
      MappingDefinition("test", "1.0", "TestModel", "Position",
        List(FieldMapping("id", MappingExpression.Path("Trade.trade_to_position"), SemanticType.StringType))),
      "TestModel", "user", Set("admin")
    )
    val resp = service.compile(req)
    resp.success shouldBe true
    resp.plan shouldBe defined
  }

  it should "return errors for unknown category" in {
    val req = CompileRequest(
      MappingDefinition("test", "1.0", "Unknown", "Position", Nil),
      "Unknown", "user", Set("admin")
    )
    val resp = service.compile(req)
    resp.success shouldBe false
    resp.errors should not be empty
  }

  "DefaultCdmeService.execute" should "produce execution result with ledger" in {
    val req = ExecuteRequest(
      MappingDefinition("test", "1.0", "TestModel", "Position",
        List(FieldMapping("id", MappingExpression.Path("Trade.trade_to_position"), SemanticType.StringType))),
      "TestModel", "user", Set("admin"),
      Epoch("ep1", Instant.now(), Instant.now(), GenerationGrain.Event),
      List(Record(Map("id" -> "t1")), Record(Map("id" -> "t2")))
    )
    val resp = service.execute(req)
    resp.success shouldBe true
    resp.outputCount shouldBe 2
    resp.ledger shouldBe defined
    resp.runId shouldBe defined
  }
}
