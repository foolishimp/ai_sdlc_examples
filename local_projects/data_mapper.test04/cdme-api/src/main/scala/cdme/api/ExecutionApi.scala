// Implements: REQ-F-API-001, REQ-F-INT-004
package cdme.api

import cdme.compiler._
import cdme.runtime._
import cdme.model.category.Category
import cdme.model.grain.GrainOrder
import cdme.model.types.SubtypeRegistry
import cdme.model.access.Principal
import cdme.model.error.ValidationError

/**
 * High-level execution API for running and dry-running CDME pipelines.
 *
 * Provides the entry points for:
 *   1. Compiling paths and building execution artifacts
 *   2. Running an artifact against an execution engine
 *   3. Dry-running an artifact (full validation, no side effects)
 */
object ExecutionApi {

  /**
   * Compile a set of path expressions against a category.
   *
   * @param paths     the path expressions to compile
   * @param category  the category
   * @param grainOrder grain ordering
   * @param registry   subtype registry
   * @param principal  requesting principal
   * @param budget    optional cardinality budget
   * @return Right(compiledPaths) or Left(errors)
   */
  def compilePaths(
      paths: List[PathExpression],
      category: Category,
      grainOrder: GrainOrder,
      registry: SubtypeRegistry,
      principal: Principal,
      budget: Option[CardinalityBudget] = None
  ): Either[List[ValidationError], List[CompiledPath]] = {
    val context = CompilationContext(category, grainOrder, registry, principal, budget)
    val results = paths.map(PathCompiler.compile(_, context))
    val errors = results.flatMap(_.left.toOption).flatten
    val compiled = results.flatMap(_.toOption)

    if (errors.nonEmpty) Left(errors)
    else Right(compiled)
  }

  /**
   * Build an execution artifact from compiled paths.
   *
   * @param compiledPaths the compiled paths
   * @param errorSinkConfig error sink configuration
   * @param budget cardinality budget
   * @return Right(artifact) or Left(errors)
   */
  def buildArtifact(
      compiledPaths: List[CompiledPath],
      errorSinkConfig: ErrorSinkConfig,
      budget: CardinalityBudget
  ): Either[List[ValidationError], ExecutionArtifact] =
    ArtifactBuilder.build(
      compiledPaths = compiledPaths,
      errorSink = errorSinkConfig,
      budget = budget
    )

  /**
   * Execute an artifact against an engine.
   *
   * @param artifact the execution artifact
   * @param engine   the execution engine
   * @return Right(result) or Left(error)
   */
  def execute(
      artifact: ExecutionArtifact,
      engine: ExecutionEngine
  ): Either[ExecutionError, ExecutionResult] =
    engine.execute(artifact)
}
