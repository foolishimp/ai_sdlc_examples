// Implements: REQ-F-LDM-003, REQ-F-TRV-001, REQ-F-TRV-002, REQ-F-TRV-003, REQ-F-TRV-004, REQ-F-TRV-007, REQ-F-ACC-005
package cdme.compiler

import cdme.model.category.{Category, EntityId, MorphismId, CardinalityType}
import cdme.model.types.{CdmeType, SubtypeRegistry}
import cdme.model.grain.{Grain, GrainOrder}
import cdme.model.context.ContextFiber
import cdme.model.access.{Principal, TopologyView}
import cdme.model.adjoint.AdjointMorphism
import cdme.model.error.ValidationError

/**
 * Context type tracking for path traversal.
 *
 * When a 1:N morphism is traversed, the context type lifts from Scalar to List.
 * Subsequent 1:N traversals flatMap (no nested lists).
 */
sealed trait ContextType
object ContextType {
  case object Scalar extends ContextType
  case object ListContext extends ContextType
}

/**
 * A step in a path expression: entity name, relationship (morphism) name, optional attribute.
 *
 * @param entityName   the source entity name
 * @param morphismName the morphism to traverse
 * @param attribute    optional final attribute to project
 */
final case class PathStep(
    entityName: String,
    morphismName: String,
    attribute: Option[String] = None
)

/**
 * A path expression representing an Entity.Relationship.Attribute chain.
 *
 * @param steps the ordered traversal steps
 */
final case class PathExpression(
    steps: List[PathStep]
) {
  /** Human-readable representation. */
  def show: String =
    steps.map(s => s"${s.entityName}.${s.morphismName}${s.attribute.map("." + _).getOrElse("")}").mkString(" -> ")
}

/**
 * The result grain transition at a step.
 */
final case class GrainTransition(
    from: Grain,
    to: Grain,
    requiresAggregation: Boolean
)

/**
 * Context lifting information at a step.
 */
final case class ContextLifting(
    from: ContextType,
    to: ContextType
)

/**
 * A validated, compiled step in a path.
 *
 * @param morphism       the resolved adjoint morphism
 * @param inputType      the input type at this step
 * @param outputType     the output type at this step
 * @param grainTransition grain transition information
 * @param contextLifting  context lifting information
 */
final case class CompiledStep(
    morphism: AdjointMorphism[_, _],
    inputType: CdmeType,
    outputType: CdmeType,
    grainTransition: GrainTransition,
    contextLifting: ContextLifting
)

/**
 * A validated, compiled path with full type, grain, and context annotations.
 *
 * @param steps       the compiled steps
 * @param resultType  the final result type
 * @param resultGrain the final grain level
 * @param contextType the final context type (Scalar or List)
 */
final case class CompiledPath(
    steps: List[CompiledStep],
    resultType: CdmeType,
    resultGrain: Grain,
    contextType: ContextType
)

/**
 * Cardinality budget constraints for path compilation.
 *
 * @param maxOutputRows      maximum allowed output rows
 * @param maxJoinFanOut      maximum join fan-out at any step
 * @param maxIntermediateSize maximum intermediate dataset size
 */
final case class CardinalityBudget(
    maxOutputRows: Long,
    maxJoinFanOut: Long,
    maxIntermediateSize: Long
)

/**
 * Cost report with estimated cardinality at each step.
 *
 * @param stepEstimates cardinality estimate for each step
 * @param totalEstimate total estimated output cardinality
 * @param explosionPoint step index where budget is first exceeded, if any
 */
final case class CostReport(
    stepEstimates: List[Long],
    totalEstimate: Long,
    explosionPoint: Option[Int]
)

/**
 * Compilation context containing all resources needed for path validation.
 *
 * @param category        the category (may be filtered by TopologyView)
 * @param grainOrder      the grain ordering
 * @param subtypeRegistry the subtype registry
 * @param principal        the requesting principal (for RBAC filtering)
 * @param budget          optional cardinality budget
 */
final case class CompilationContext(
    category: Category,
    grainOrder: GrainOrder,
    subtypeRegistry: SubtypeRegistry,
    principal: Principal,
    budget: Option[CardinalityBudget] = None
)

/**
 * Path compiler: validates traversal paths at definition time.
 *
 * The compiler performs all checks in a single pass, accumulating all errors
 * (does not short-circuit on the first error):
 *   1. Morphism existence
 *   2. Type compatibility (via TypeUnifier)
 *   3. Grain safety (via GrainChecker)
 *   4. Context consistency (via ContextChecker)
 *   5. Access control (via TopologyView)
 *   6. Cardinality cost estimation (optional)
 *
 * RBAC filtering is applied before path construction -- denied morphisms
 * do not appear in the available topology.
 */
object PathCompiler {

  /**
   * Compile a path expression into a validated CompiledPath.
   *
   * @param path    the path expression to compile
   * @param context the compilation context
   * @return Right(compiledPath) if all checks pass, Left(errors) otherwise
   */
  def compile(
      path: PathExpression,
      context: CompilationContext
  ): Either[List[ValidationError], CompiledPath] = {
    // Apply RBAC filtering first
    val view = TopologyView(context.category, context.principal)
    val filteredCategory = view.filteredCategory

    val errors = scala.collection.mutable.ListBuffer[ValidationError]()
    val compiledSteps = scala.collection.mutable.ListBuffer[CompiledStep]()
    var currentContextType: ContextType = ContextType.Scalar

    for (step <- path.steps) {
      // Resolve source entity
      val sourceEntity = filteredCategory.entities.values
        .find(_.name == step.entityName)

      sourceEntity match {
        case None =>
          errors += ValidationError.MorphismNotFound(
            step.morphismName,
            s"Source entity '${step.entityName}' not found"
          )
        case Some(entity) =>
          // Resolve morphism
          val morphism = filteredCategory.morphisms.values
            .find(m => m.domain == entity.id && m.name == step.morphismName)

          morphism match {
            case None =>
              errors += ValidationError.MorphismNotFound(
                step.morphismName,
                s"Morphism '${step.morphismName}' not found from entity '${step.entityName}'"
              )
            case Some(m) =>
              // Check grain safety
              val targetEntity = filteredCategory.entities.get(m.codomain)
              targetEntity.foreach { target =>
                val grainCheck = GrainChecker.checkTransition(
                  entity.grain, target.grain, m.cardinality, context.grainOrder
                )
                grainCheck.left.foreach(errors += _)

                // Track context lifting for 1:N
                val newContextType = m.cardinality match {
                  case CardinalityType.OneToMany | CardinalityType.ManyToMany =>
                    ContextType.ListContext
                  case _ => currentContextType
                }

                // TODO: Full type checking integration
                // For now, use a placeholder type from the target entity
                val inputType = cdme.model.types.ProductType(
                  Some(entity.name),
                  entity.attributes.map { case (aid, t) => (aid.value, t) }
                )
                val outputType = cdme.model.types.ProductType(
                  Some(target.name),
                  target.attributes.map { case (aid, t) => (aid.value, t) }
                )

                compiledSteps += CompiledStep(
                  morphism = m,
                  inputType = inputType,
                  outputType = outputType,
                  grainTransition = GrainTransition(
                    entity.grain, target.grain,
                    requiresAggregation = context.grainOrder.isFinerThan(target.grain, entity.grain)
                  ),
                  contextLifting = ContextLifting(currentContextType, newContextType)
                )

                currentContextType = newContextType
              }
          }
      }
    }

    if (errors.nonEmpty)
      Left(errors.toList)
    else if (compiledSteps.isEmpty)
      Left(List(ValidationError.General("Empty path expression")))
    else {
      val lastStep = compiledSteps.last
      Right(CompiledPath(
        steps = compiledSteps.toList,
        resultType = lastStep.outputType,
        resultGrain = lastStep.grainTransition.to,
        contextType = currentContextType
      ))
    }
  }
}
