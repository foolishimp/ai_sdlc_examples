// Validates: REQ-AI-01, REQ-AI-03
// Tests for the CdmeEngine compile and dry run.
package com.cdme.api

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import com.cdme.model._
import com.cdme.model.grain.{Grain, GrainHierarchy}
import com.cdme.model.types.CdmeType
import com.cdme.compiler._
import com.cdme.runtime._
import java.time.Instant

class CdmeEngineSpec extends AnyFlatSpec with Matchers {

  val tradeEntity: Entity = Entity("trade", "Trade",
    Map("tradeId" -> CdmeType.StringType, "amount" -> CdmeType.FloatType),
    Grain.Atomic, "id_trade", AccessControl.open)

  val positionEntity: Entity = Entity("position", "Position",
    Map("posId" -> CdmeType.StringType, "total" -> CdmeType.FloatType),
    Grain.Daily, "id_position", AccessControl.open)

  val idTrade: Morphism = Morphism("id_trade", "Id(Trade)", "trade", "trade",
    Cardinality.OneToOne, MorphismKind.Structural, None, AccessControl.open)
  val idPosition: Morphism = Morphism("id_position", "Id(Position)", "position", "position",
    Cardinality.OneToOne, MorphismKind.Structural, None, AccessControl.open)
  val aggMorphism: Morphism = Morphism("agg", "Aggregate", "trade", "position",
    Cardinality.NToOne, MorphismKind.Algebraic, None, AccessControl.open)

  val category: Category = Category(
    "TestLDM",
    Map("trade" -> tradeEntity, "position" -> positionEntity),
    Map("id_trade" -> idTrade, "id_position" -> idPosition, "agg" -> aggMorphism),
    GrainHierarchy.default
  )

  val pdm: PhysicalDataModel = PhysicalDataModel(
    Map("trade" -> PhysicalTarget("parquet", "/data/trades", Map.empty)),
    Map.empty
  )

  val mapping: MappingDefinition = MappingDefinition(
    "TestMapping", "trade", "position",
    List(FieldMapping("total", MappingExpression.SourcePath(DotPath.parse("trade.amount")))),
    List("agg")
  )

  val jobConfig: JobConfiguration = JobConfiguration(
    maxOutputRows = Some(1000000L),
    maxJoinDepth = Some(10),
    maxIntermediateSize = Some(Long.MaxValue),
    failureThresholdPercent = Some(5.0),
    failureThresholdAbsolute = None,
    dryRun = false,
    lineageMode = "full"
  )

  val input: CompilationInput = CompilationInput(category, pdm, mapping, jobConfig, Principal.system)

  "CdmeEngine.compile" should "compile a valid mapping" in {
    val result = CdmeEngine.compile(input)
    result.isRight shouldBe true
    val plan = result.toOption.get
    plan.executionDag.sources should not be empty
    plan.costEstimate.withinBudget shouldBe true
  }

  it should "reject invalid morphism reference" in {
    val badMapping = mapping.copy(morphismPath = List("nonexistent"))
    val badInput = input.copy(mapping = badMapping)
    val result = CdmeEngine.compile(badInput)
    result.isLeft shouldBe true
  }

  "CdmeEngine.dryRun" should "produce a dry run result" in {
    val result = CdmeEngine.dryRun(input)
    result.isRight shouldBe true
    val dryRunResult = result.toOption.get
    dryRunResult.compilationPassed shouldBe true
  }

  it should "fail dry run for invalid input" in {
    val badMapping = mapping.copy(morphismPath = List("nonexistent"))
    val badInput = input.copy(mapping = badMapping)
    val result = CdmeEngine.dryRun(badInput)
    result.isLeft shouldBe true
  }

  "CdmeEngine.execute" should "execute in dry run mode" in {
    val manifest = RunManifest("run-1", "cfg", "code", "design", Instant.now(), Map.empty, Map.empty)
    val epoch = Epoch("ep1", Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-01-02T00:00:00Z"))
    val context = ExecutionContext("run-1", epoch, Map.empty, LineageMode.Full,
      FailureThreshold.NoThreshold, dryRun = true, manifest)

    val plan = CdmeEngine.compile(input).toOption.get
    val result = CdmeEngine.execute(plan, context)
    result.isRight shouldBe true
    result.toOption.get.success shouldBe true
  }
}
