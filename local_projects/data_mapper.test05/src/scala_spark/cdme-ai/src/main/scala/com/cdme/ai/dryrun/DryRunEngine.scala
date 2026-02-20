package com.cdme.ai.dryrun

// Implements: REQ-F-AIA-003

import com.cdme.ai.validation.{AiMappingValidator, ValidationReport}
import com.cdme.compiler.{CompilerConstraints, TopologicalCompiler}
import com.cdme.compiler.cost.CostEstimate
import com.cdme.compiler.plan.{ExecutionPlan, MappingDef}
import com.cdme.model.config.JobConfig
import com.cdme.model.entity.EntityId
import com.cdme.model.graph.LdmGraph
import com.cdme.model.pdm.PdmBinding

/**
 * Result of a dry run: full validation plus optional cost estimation
 * and compiled plan, but no execution and no sink writes.
 *
 * @param validationReport the validation report for the mapping
 * @param costEstimate     the estimated cost (present only if validation succeeded)
 * @param plan             the compiled execution plan (present only if validation succeeded)
 */
case class DryRunResult(
  validationReport: ValidationReport,
  costEstimate: Option[CostEstimate],
  plan: Option[ExecutionPlan]
)

/**
 * Performs a full dry run of an AI-generated mapping: validation plus
 * cost estimation, without executing the pipeline or writing to sinks.
 *
 * A dry run is the recommended first step for evaluating AI-generated
 * mappings. It catches all compile-time errors and provides a cost
 * estimate, allowing human reviewers to assess feasibility before
 * committing compute resources.
 */
object DryRunEngine {

  /**
   * Execute a dry run for the given mapping.
   *
   * The dry run performs:
   *  1. Full validation via [[AiMappingValidator.validate]]
   *  2. If validation passes, compile to produce an [[ExecutionPlan]]
   *  3. Extract [[CostEstimate]] from the compiled plan
   *  4. Return all results without executing or writing
   *
   * @param mapping     the mapping definition to dry-run
   * @param graph       the logical data model graph
   * @param pdmBindings the physical storage bindings per entity
   * @param constraints compiler constraints (budget, principal, type hierarchy, grain hierarchy)
   * @param jobConfig   the job configuration (for lineage mode, failure thresholds, etc.)
   * @return a [[DryRunResult]] containing validation, cost, and plan
   */
  def dryRun(
    mapping: MappingDef,
    graph: LdmGraph,
    pdmBindings: Map[EntityId, PdmBinding],
    constraints: CompilerConstraints,
    jobConfig: JobConfig
  ): DryRunResult = {

    // Step 1: Validate the mapping
    val validationReport = AiMappingValidator.validate(
      mapping = mapping,
      graph = graph,
      pdmBindings = pdmBindings,
      constraints = constraints
    )

    if (!validationReport.isValid) {
      // Validation failed — no plan or cost estimate possible
      return DryRunResult(
        validationReport = validationReport,
        costEstimate = None,
        plan = None
      )
    }

    // Step 2: Compile to get the execution plan and cost estimate
    val compilationResult = TopologicalCompiler.compile(
      graph = graph,
      mappings = List(mapping),
      pdmBindings = pdmBindings,
      constraints = constraints
    )

    compilationResult match {
      case Right(plan) =>
        DryRunResult(
          validationReport = validationReport,
          costEstimate = Some(plan.costEstimate),
          plan = Some(plan)
        )
      case Left(_) =>
        // Should not happen if validation passed, but handle defensively
        DryRunResult(
          validationReport = validationReport,
          costEstimate = None,
          plan = None
        )
    }
  }
}
