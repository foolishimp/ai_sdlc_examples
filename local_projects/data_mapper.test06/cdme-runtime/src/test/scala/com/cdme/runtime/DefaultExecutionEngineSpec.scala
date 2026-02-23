package com.cdme.runtime

import java.time.Instant
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import com.cdme.compiler._
import com.cdme.model._

// Validates: REQ-TRV-01, REQ-TRV-04, REQ-ACC-01
class DefaultExecutionEngineSpec extends AnyFlatSpec with Matchers {

  val engine = new DefaultExecutionEngine()

  def makeContext(steps: List[ExecutionStep]): ExecutionContext = {
    val manifest = RunManifest(
      "test-run", "cfg", "code", "design", "1.0",
      Map.empty, Map.empty, "0.1.0", Instant.now()
    )
    val epoch = Epoch("epoch-1", Instant.now(), Instant.now(), GenerationGrain.Event)
    val plan = CompiledPlan(steps, CostEstimate(100, 1, 200), Map.empty, Map.empty, Map.empty)
    ExecutionContext(manifest, epoch, plan)
  }

  "DefaultExecutionEngine" should "produce balanced accounting ledger (AC-CDME-003)" in {
    val ctx = makeContext(Nil)
    val data = List(Record(Map("a" -> 1)), Record(Map("a" -> 2)))
    val result = engine.execute(ctx, data)
    result.ledger.isBalanced shouldBe true
  }

  it should "capture telemetry for each step (REQ-TRV-04)" in {
    val morphism = Morphism("test_morph", "A", "B", Cardinality.OneToOne,
      MorphismType.Computational, SemanticType.IntType, SemanticType.IntType)
    val ctx = makeContext(List(TraverseStep(morphism)))
    val data = List(Record(Map("x" -> 1)), Record(Map("x" -> 2)))
    val result = engine.execute(ctx, data)
    result.telemetry.entries should have size 1
    result.telemetry.entries.head.morphismName shouldBe "test_morph"
  }

  it should "produce deterministic results (AC-CDME-006)" in {
    val ctx = makeContext(Nil)
    val data = List(Record(Map("a" -> 1)), Record(Map("a" -> 2)))
    val result1 = engine.execute(ctx, data)
    val result2 = engine.execute(ctx, data)
    result1.output shouldBe result2.output
    result1.ledger.inputCount shouldBe result2.ledger.inputCount
    result1.ledger.processedCount shouldBe result2.ledger.processedCount
  }

  it should "preserve all records through pass-through" in {
    val ctx = makeContext(Nil)
    val data = (1 to 100).map(i => Record(Map("id" -> i))).toList
    val result = engine.execute(ctx, data)
    result.output should have size 100
    result.ledger.inputCount shouldBe 100
    result.ledger.processedCount shouldBe 100
  }
}
