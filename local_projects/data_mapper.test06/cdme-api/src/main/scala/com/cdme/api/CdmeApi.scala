package com.cdme.api

import com.cdme.model._
import com.cdme.compiler._
import com.cdme.runtime._

// Implements: External interface for CDME

case class CompileRequest(
  mapping: MappingDefinition,
  categoryName: String,
  principalName: String,
  principalRoles: Set[String]
)

case class CompileResponse(
  success: Boolean,
  plan: Option[CompiledPlan] = None,
  errors: List[String] = Nil,
  costEstimate: Option[CostEstimate] = None
)

case class ExecuteRequest(
  mapping: MappingDefinition,
  categoryName: String,
  principalName: String,
  principalRoles: Set[String],
  epoch: Epoch,
  data: List[Record],
  lineageMode: LineageMode = LineageMode.Full
)

case class ExecuteResponse(
  success: Boolean,
  outputCount: Long = 0,
  errorCount: Long = 0,
  ledger: Option[AccountingLedger] = None,
  runId: Option[String] = None,
  errors: List[String] = Nil
)

trait CdmeService {
  def compile(request: CompileRequest): CompileResponse
  def execute(request: ExecuteRequest): ExecuteResponse
  def getRunManifest(runId: String): Option[RunManifest]
  def getLedger(runId: String): Option[AccountingLedger]
}

class DefaultCdmeService(
  categories: Map[String, Category],
  compiler: TopologicalCompiler,
  engine: ExecutionEngine
) extends CdmeService {

  private val manifests = scala.collection.mutable.Map.empty[String, RunManifest]
  private val ledgers = scala.collection.mutable.Map.empty[String, AccountingLedger]

  override def compile(request: CompileRequest): CompileResponse = {
    categories.get(request.categoryName) match {
      case None =>
        CompileResponse(success = false, errors = List(s"Category '${request.categoryName}' not found"))
      case Some(category) =>
        val principal = Principal(request.principalName, request.principalRoles)
        compiler.compile(request.mapping, category, principal) match {
          case Right(plan) =>
            CompileResponse(success = true, plan = Some(plan), costEstimate = Some(plan.estimatedCost))
          case Left(errors) =>
            CompileResponse(success = false, errors = errors.map(_.message))
        }
    }
  }

  override def execute(request: ExecuteRequest): ExecuteResponse = {
    val compileResult = compile(CompileRequest(
      request.mapping, request.categoryName, request.principalName, request.principalRoles
    ))

    compileResult.plan match {
      case None =>
        ExecuteResponse(success = false, errors = compileResult.errors)
      case Some(plan) =>
        val runId = java.util.UUID.randomUUID().toString
        val manifest = RunManifest(
          runId = runId,
          configHash = request.mapping.hashCode.toHexString,
          codeHash = "0.1.0",
          designHash = "1.0.0",
          mappingVersion = request.mapping.version,
          sourceBindings = Map.empty,
          lookupVersions = request.mapping.lookups.map { case (k, v) => k -> v.version },
          cdmeVersion = "0.1.0",
          timestamp = java.time.Instant.now()
        )

        val ctx = ExecutionContext(
          runManifest = manifest,
          epoch = request.epoch,
          compiledPlan = plan,
          lineageMode = request.lineageMode
        )

        val result = engine.execute(ctx, request.data)
        manifests += (runId -> result.manifest)
        ledgers += (runId -> result.ledger)

        ExecuteResponse(
          success = result.errors.isEmpty,
          outputCount = result.output.size.toLong,
          errorCount = result.errors.size.toLong,
          ledger = Some(result.ledger),
          runId = Some(runId)
        )
    }
  }

  override def getRunManifest(runId: String): Option[RunManifest] = manifests.get(runId)
  override def getLedger(runId: String): Option[AccountingLedger] = ledgers.get(runId)
}
