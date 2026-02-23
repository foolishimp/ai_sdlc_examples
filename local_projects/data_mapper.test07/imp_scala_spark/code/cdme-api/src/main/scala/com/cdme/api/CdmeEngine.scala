// Implements: REQ-AI-03, REQ-AI-01
// CdmeEngine: top-level API for compile, execute, and dry run.
package com.cdme.api

import com.cdme.model.error.CdmeError
import com.cdme.compiler.{CompilationInput, ValidatedPlan, TopologicalValidator}
import com.cdme.runtime.{ExecutionContext, ExecutionEngine}

/**
 * The top-level CDME engine: compile, execute, and dry run.
 * This is the user-facing entry point for the entire system.
 *
 * Implements: REQ-AI-03, REQ-AI-01
 * See ADR-012: Public API Design
 */
object CdmeEngine {

  /** Result of a dry run. */
  final case class DryRunResult(
      plan: ValidatedPlan,
      compilationPassed: Boolean,
      warnings: List[String]
  )

  /**
   * Compile a mapping: validate topology, types, grains, access control, and cost.
   * Returns a ValidatedPlan if all checks pass.
   */
  def compile(input: CompilationInput): Either[List[CdmeError], ValidatedPlan] =
    TopologicalValidator.validate(input)

  /**
   * Execute a validated plan with the given context.
   * Returns the execution result.
   */
  def execute(
      plan: ValidatedPlan,
      context: ExecutionContext
  ): Either[CdmeError, ExecutionEngine.ExecutionResult] =
    ExecutionEngine.execute(plan, context)

  /**
   * Dry run: compile + validate without executing.
   * Returns the plan and compilation results for review.
   *
   * Implements: REQ-AI-03
   */
  def dryRun(input: CompilationInput): Either[List[CdmeError], DryRunResult] = {
    compile(input).map { plan =>
      DryRunResult(
        plan = plan,
        compilationPassed = true,
        warnings = plan.warnings.map(_.message)
      )
    }
  }
}
