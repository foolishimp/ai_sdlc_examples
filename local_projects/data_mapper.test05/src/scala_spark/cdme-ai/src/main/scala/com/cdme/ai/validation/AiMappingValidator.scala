package com.cdme.ai.validation

// Implements: REQ-F-AIA-001, REQ-BR-REG-004

import com.cdme.compiler.{CompilationError, CompilerConstraints, TopologicalCompiler}
import com.cdme.compiler.plan.MappingDef
import com.cdme.model.entity.EntityId
import com.cdme.model.graph.LdmGraph
import com.cdme.model.pdm.PdmBinding

/**
 * A single validation check result, with a human-readable description
 * and the requirement key it validates.
 *
 * @param name    short name of the check (e.g. "path_validity", "grain_safety")
 * @param reqKey  the requirement key this check validates (e.g. "REQ-F-LDM-003")
 * @param passed  whether the check passed
 * @param details human-readable explanation (especially for failures)
 */
case class ValidationCheck(
  name: String,
  reqKey: String,
  passed: Boolean,
  details: String
)

/**
 * The output of AI mapping validation, suitable for human review and
 * EU AI Act Article 14 compliance (REQ-BR-REG-004).
 *
 * @param mappingId the identifier of the mapping that was validated
 * @param passed    the list of checks that passed
 * @param failed    the list of checks that failed
 * @param summary   a human-readable summary of the validation outcome
 */
case class ValidationReport(
  mappingId: String,
  passed: List[ValidationCheck],
  failed: List[ValidationCheck],
  summary: String
) {

  /** True if all checks passed. */
  def isValid: Boolean = failed.isEmpty
}

/**
 * Validates AI-generated mapping definitions through the same topological
 * compiler as human-authored mappings.
 *
 * There is NO bypass: AI-generated mappings are compiled by
 * [[TopologicalCompiler.compile]] and subject to every path, type, grain,
 * access, fiber, and cost check. Hallucinated relationships (references to
 * non-existent morphisms) are caught by path validation and reported as
 * named errors in the [[ValidationReport]].
 *
 * The validation report is structured for human review and regulatory
 * compliance (EU AI Act Article 14).
 */
object AiMappingValidator {

  /**
   * Validate an AI-generated mapping against the LDM graph.
   *
   * Delegates to [[TopologicalCompiler.compile]] -- the same validation
   * path used for human-authored mappings. Compilation errors are wrapped
   * into a human-readable [[ValidationReport]] with per-check REQ key
   * traceability.
   *
   * @param mapping     the AI-generated mapping definition to validate
   * @param graph       the logical data model graph
   * @param pdmBindings the physical storage bindings per entity
   * @param constraints compiler constraints (budget, principal, type hierarchy, grain hierarchy)
   * @return a [[ValidationReport]] summarizing all checks
   */
  def validate(
    mapping: MappingDef,
    graph: LdmGraph,
    pdmBindings: Map[EntityId, PdmBinding],
    constraints: CompilerConstraints
  ): ValidationReport = {

    val compilationResult = TopologicalCompiler.compile(
      graph = graph,
      mappings = List(mapping),
      pdmBindings = pdmBindings,
      constraints = constraints
    )

    compilationResult match {
      case Right(_) =>
        // All checks passed
        val passedChecks = List(
          ValidationCheck("path_validity", "REQ-F-LDM-003", passed = true,
            "All paths resolve to valid morphisms in the LDM graph"),
          ValidationCheck("grain_safety", "REQ-BR-GRN-001", passed = true,
            "All grain transitions are valid (no fine-from-coarse without aggregation)"),
          ValidationCheck("type_unification", "REQ-F-TYP-004", passed = true,
            "All composition boundaries have compatible types"),
          ValidationCheck("access_control", "REQ-F-LDM-006", passed = true,
            "Principal has access to all morphisms in the mapping"),
          ValidationCheck("cost_budget", "REQ-F-TRV-006", passed = true,
            "Estimated cost is within the configured budget")
        )

        ValidationReport(
          mappingId = mapping.id,
          passed = passedChecks,
          failed = Nil,
          summary = s"Mapping '${mapping.id}' passed all ${passedChecks.size} validation checks."
        )

      case Left(errors) =>
        // Convert compilation errors to validation checks
        val failedChecks = errors.map(errorToValidationCheck)
        val passedChecks = inferPassedChecks(errors)

        val failSummary = failedChecks.map(c => s"  - ${c.name}: ${c.details}").mkString("\n")
        ValidationReport(
          mappingId = mapping.id,
          passed = passedChecks,
          failed = failedChecks,
          summary = s"Mapping '${mapping.id}' failed ${failedChecks.size} validation check(s):\n$failSummary"
        )
    }
  }

  /**
   * Convert a compilation error into a [[ValidationCheck]].
   */
  private def errorToValidationCheck(error: CompilationError): ValidationCheck = {
    import com.cdme.compiler._

    error match {
      case e: PathError =>
        ValidationCheck("path_validity", "REQ-F-LDM-003", passed = false, e.message)
      case e: GrainError =>
        ValidationCheck("grain_safety", "REQ-BR-GRN-001", passed = false, e.message)
      case e: TypeError =>
        ValidationCheck("type_unification", "REQ-F-TYP-004", passed = false, e.message)
      case e: AccessError =>
        ValidationCheck("access_control", "REQ-F-LDM-006", passed = false, e.message)
      case e: BudgetExceeded =>
        ValidationCheck("cost_budget", "REQ-F-TRV-006", passed = false, e.message)
      case e: FiberError =>
        ValidationCheck("fiber_compatibility", "REQ-F-CTX-001", passed = false, e.message)
      case e: SynthesisError =>
        ValidationCheck("synthesis_validity", "REQ-F-SYN-001", passed = false, e.message)
      case e: LookupVersionError =>
        ValidationCheck("lookup_version", "REQ-F-SYN-006", passed = false, e.message)
      case e =>
        ValidationCheck("unknown", "UNKNOWN", passed = false, e.message)
    }
  }

  /**
   * Infer which check categories passed by looking at which error categories
   * are absent from the error list.
   */
  private def inferPassedChecks(errors: List[CompilationError]): List[ValidationCheck] = {
    import com.cdme.compiler._

    val hasPathError = errors.exists(_.isInstanceOf[PathError])
    val hasGrainError = errors.exists(_.isInstanceOf[GrainError])
    val hasTypeError = errors.exists(_.isInstanceOf[TypeError])
    val hasAccessError = errors.exists(_.isInstanceOf[AccessError])
    val hasBudgetError = errors.exists(_.isInstanceOf[BudgetExceeded])

    val checks = List.newBuilder[ValidationCheck]

    if (!hasPathError) checks += ValidationCheck(
      "path_validity", "REQ-F-LDM-003", passed = true,
      "All paths resolve to valid morphisms")
    if (!hasGrainError) checks += ValidationCheck(
      "grain_safety", "REQ-BR-GRN-001", passed = true,
      "All grain transitions are valid")
    if (!hasTypeError) checks += ValidationCheck(
      "type_unification", "REQ-F-TYP-004", passed = true,
      "All composition boundaries have compatible types")
    if (!hasAccessError) checks += ValidationCheck(
      "access_control", "REQ-F-LDM-006", passed = true,
      "Principal has access to all morphisms")
    if (!hasBudgetError) checks += ValidationCheck(
      "cost_budget", "REQ-F-TRV-006", passed = true,
      "Estimated cost is within budget")

    checks.result()
  }
}
