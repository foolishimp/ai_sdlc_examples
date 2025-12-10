package cdme.compiler

import cats.implicits._
import cdme.core._
import cdme.config._

/**
 * Compiler for CDME mappings.
 * Implements: REQ-AI-01 (Topological Validation), REQ-TRV-02 (Grain Safety)
 */
class Compiler(registry: SchemaRegistry) {

  /**
   * Compile mapping to execution plan.
   * Implements: Compile-time validation (path, type, grain)
   */
  def compile(
    mapping: MappingConfig,
    ctx: ExecutionContext
  ): Either[CdmeError, ExecutionPlan] = {
    for {
      // Validate source entity exists
      sourceEntity <- registry.getEntity(mapping.source.entity)

      // Validate target entity exists
      _ <- registry.getEntity(mapping.target.entity)

      // Validate all projection paths
      _ <- validateAllProjections(mapping.projections)

      // Generate execution plan
      plan <- generatePlan(mapping, sourceEntity)
    } yield plan
  }

  /**
   * Validate all projection paths.
   */
  private def validateAllProjections(projections: List[ProjectionConfig]): Either[CdmeError, Unit] = {
    projections.foldLeft(Right(()): Either[CdmeError, Unit]) { (acc, proj) =>
      acc.flatMap(_ => validateProjectionPath(proj.source).map(_ => ()))
    }
  }

  /**
   * Validate projection path.
   */
  private def validateProjectionPath(path: String): Either[CdmeError, PathValidationResult] = {
    if (path.contains(".")) {
      registry.validatePath(path)
    } else {
      // Simple attribute reference
      Right(PathValidationResult(path, List.empty, "String", true))
    }
  }

  /**
   * Generate execution plan.
   */
  private def generatePlan(
    mapping: MappingConfig,
    sourceEntity: Entity
  ): Either[CdmeError, ExecutionPlan] = {

    val morphismOps = mapping.morphisms.getOrElse(List.empty).map { m =>
      MorphismOp(
        name = m.name,
        morphismType = m.`type`,
        predicate = m.predicate,
        path = m.path,
        cardinality = m.cardinality.flatMap(Cardinality.fromString),
        groupBy = m.groupBy,
        orderBy = m.orderBy
      )
    }

    val projections = mapping.projections.map { p =>
      ProjectionOp(
        name = p.name,
        source = p.source,
        aggregation = p.aggregation
      )
    }

    val validationOps = mapping.validations.map(_.map { v =>
      ValidationOp(
        field = v.field,
        validationType = v.validationType,
        expression = v.expression,
        errorMessage = v.errorMessage
      )
    })

    Right(ExecutionPlan(
      mappingName = mapping.name,
      sourceEntity = mapping.source.entity,
      targetEntity = mapping.target.entity,
      sourceGrain = sourceEntity.grain,
      targetGrain = Grain(
        level = GrainLevel.fromString(mapping.target.grain.level).getOrElse(GrainLevel.Atomic),
        key = mapping.target.grain.key
      ),
      morphisms = morphismOps,
      projections = projections,
      validations = validationOps
    ))
  }
}

/**
 * Execution plan.
 */
case class ExecutionPlan(
  mappingName: String,
  sourceEntity: String,
  targetEntity: String,
  sourceGrain: Grain,
  targetGrain: Grain,
  morphisms: List[MorphismOp],
  projections: List[ProjectionOp],
  validations: Option[List[ValidationOp]] = None
)

/**
 * Morphism operation.
 */
case class MorphismOp(
  name: String,
  morphismType: String,
  predicate: Option[String] = None,
  path: Option[String] = None,
  cardinality: Option[Cardinality] = None,
  groupBy: Option[List[String]] = None,
  orderBy: Option[List[String]] = None
)

/**
 * Projection operation.
 */
case class ProjectionOp(
  name: String,
  source: String,
  aggregation: Option[String] = None
) {
  // Alias for cleaner UAT test access
  def targetField: String = name
  def sourcePath: String = source
}

/**
 * Validation operation.
 */
case class ValidationOp(
  field: String,
  validationType: String,
  expression: Option[String] = None,
  errorMessage: String
)

/**
 * Validator for grain transitions.
 */
object GrainValidator {

  /**
   * Validate grain transition.
   * Implements: REQ-TRV-02 (Grain Safety)
   */
  def validateTransition(
    sourceGrain: Grain,
    targetGrain: Grain,
    hasAggregation: Boolean
  ): Either[CdmeError, Unit] = {

    if (sourceGrain.level.level < targetGrain.level.level && !hasAggregation) {
      // Coarsening grain without aggregation
      Left(CdmeError.GrainSafetyError(
        sourceKey = "N/A",
        morphismPath = "grain_check",
        sourceGrain = sourceGrain.level.name,
        targetGrain = targetGrain.level.name,
        violation = "Cannot coarsen grain without explicit aggregation"
      ))
    } else {
      Right(())
    }
  }
}
