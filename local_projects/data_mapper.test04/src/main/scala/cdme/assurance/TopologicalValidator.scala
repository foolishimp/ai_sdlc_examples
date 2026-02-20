// Implements: REQ-F-AI-001, REQ-F-AI-002, REQ-BR-AI-001
package cdme.assurance

import cdme.model.category.Category
import cdme.model.error.ValidationError
import cdme.compiler.{PathExpression, PathCompiler, CompilationContext}

/**
 * Validation diagnostic for AI or human-authored mappings.
 */
final case class ValidationDiagnostic(
    severity: DiagnosticSeverity,
    message: String,
    location: Option[String] = None
)

/**
 * Diagnostic severity levels.
 */
enum DiagnosticSeverity:
  case Error, Warning, Info

/**
 * Topological validator: validates mapping definitions against the LDM.
 *
 * There is explicitly NO special fast-path for AI-generated definitions;
 * the validation code path is identical for AI and human authors (REQ-BR-AI-001).
 *
 * The TopologicalValidator is a facade over the standard PathCompiler --
 * it does not have separate validation logic.
 */
object TopologicalValidator:

  /**
   * Validate a set of path expressions against the LDM category.
   *
   * @param paths   the path expressions to validate
   * @param context the compilation context
   * @return list of validation diagnostics
   */
  def validate(
      paths: List[PathExpression],
      context: CompilationContext
  ): List[ValidationDiagnostic] =
    paths.flatMap { path =>
      PathCompiler.compile(path, context) match
        case Right(_) =>
          List(ValidationDiagnostic(
            DiagnosticSeverity.Info,
            s"Path '${path.show}' validated successfully"
          ))
        case Left(errors) =>
          errors.map(err => ValidationDiagnostic(
            DiagnosticSeverity.Error,
            err.message,
            Some(path.show)
          ))
    }

  /**
   * Check for hallucinated morphisms (referencing nonexistent entities).
   *
   * @param morphismNames the morphism names to check
   * @param category      the LDM category
   * @return list of diagnostics for hallucinated references
   */
  def checkHallucinations(
      morphismNames: List[String],
      category: Category
  ): List[ValidationDiagnostic] =
    val validNames = category.morphisms.values.map(_.name).toSet
    morphismNames.filterNot(validNames.contains).map { name =>
      ValidationDiagnostic(
        DiagnosticSeverity.Error,
        s"Hallucinated morphism: '$name' does not exist in the LDM",
        None
      )
    }
