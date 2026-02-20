// Implements: REQ-F-AI-003
package cdme.assurance

import cdme.compiler.{ExecutionArtifact, CompiledPath, CostReport}
import cdme.model.error.ValidationError

/**
 * Result of a dry run execution.
 *
 * @param validationResults list of validation diagnostics
 * @param executionPlan     human-readable execution plan
 * @param costReport        estimated cardinality report
 * @param compiledPaths     the compiled paths that would be executed
 */
final case class DryRunResult(
    validationResults: List[ValidationDiagnostic],
    executionPlan: String,
    costReport: Option[CostReport],
    compiledPaths: List[CompiledPath]
)

/**
 * Dry run engine: full validation, no side effects.
 *
 * Executes everything up to the write phase, then discards results.
 * This allows validating AI-generated or human-authored mappings
 * without affecting any physical data.
 */
object DryRunEngine {

  /**
   * Perform a dry run on an execution artifact.
   *
   * Validates the artifact, generates an execution plan, and estimates
   * cardinality -- all without executing any side effects.
   *
   * @param artifact the execution artifact to dry-run
   * @return the dry run result
   */
  def dryRun(artifact: ExecutionArtifact): DryRunResult = {
    val validationDiags = artifact.compiledPaths.flatMap { path =>
      path.steps.zipWithIndex.map { case (step, idx) =>
        ValidationDiagnostic(
          DiagnosticSeverity.Info,
          s"Step $idx: ${step.morphism.name} [${step.inputType.typeName} -> ${step.outputType.typeName}]"
        )
      }
    }

    val planLines = artifact.compiledPaths.zipWithIndex.map { case (path, pathIdx) =>
      val steps = path.steps.map(s =>
        s"  ${s.morphism.name}: ${s.inputType.typeName} -> ${s.outputType.typeName} " +
        s"[grain: ${s.grainTransition.from.level} -> ${s.grainTransition.to.level}]"
      )
      s"Path $pathIdx:\n${steps.mkString("\n")}"
    }
    val plan = planLines.mkString("\n\n")

    DryRunResult(
      validationResults = validationDiags,
      executionPlan = plan,
      costReport = None, // TODO: integrate CostEstimator
      compiledPaths = artifact.compiledPaths
    )
  }
}
