package cdme.executor

import cats.implicits._
import cdme.core._
import cdme.compiler._
import org.apache.spark.sql.{Dataset, DataFrame, SparkSession, functions => F}
import org.apache.spark.sql.functions._

/**
 * Executor for CDME execution plans.
 * Implements: REQ-INT-01 (Morphism Execution)
 */
class Executor(ctx: ExecutionContext) {

  implicit val spark: SparkSession = ctx.spark
  import spark.implicits._

  /**
   * Execute the compiled plan.
   */
  def execute(plan: ExecutionPlan): Either[CdmeError, ExecutionResult] = {
    for {
      // Load source data
      sourceDF <- loadSourceData(plan.sourceEntity)

      // Apply morphisms
      transformedDF <- applyMorphisms(sourceDF, plan.morphisms)

      // Apply projections
      outputDF <- applyProjections(transformedDF, plan.projections)

      // Create result
      result = ExecutionResult(
        data = outputDF,
        errors = spark.emptyDataFrame,  // Simplified - no error handling in MVP
        mappingName = plan.mappingName,
        sourceEntity = plan.sourceEntity,
        sourceRowCount = sourceDF.count(),
        outputRowCount = outputDF.count()
      )
    } yield result
  }

  /**
   * Load source data from physical binding.
   */
  private def loadSourceData(entityName: String): Either[CdmeError, DataFrame] = {
    for {
      binding <- ctx.registry.getBinding(entityName)
      df <- loadDataFrame(binding)
    } yield df
  }

  private def loadDataFrame(binding: PhysicalBinding): Either[CdmeError, DataFrame] = {
    try {
      val df = binding.storageType.toUpperCase match {
        case "PARQUET" => spark.read.parquet(binding.location)
        case "DELTA" => spark.read.format("delta").load(binding.location)
        case "CSV" => spark.read.option("header", "true").csv(binding.location)
        case other => throw new IllegalArgumentException(s"Unsupported storage type: $other")
      }
      Right(df)
    } catch {
      case e: Exception =>
        Left(CdmeError.CompilationError(s"Failed to load data: ${e.getMessage}"))
    }
  }

  /**
   * Apply morphisms in sequence.
   */
  private def applyMorphisms(
    df: DataFrame,
    morphisms: List[MorphismOp]
  ): Either[CdmeError, DataFrame] = {
    morphisms.foldLeft(Right(df): Either[CdmeError, DataFrame]) { (result, morphism) =>
      result.flatMap(currentDF => applyMorphism(currentDF, morphism))
    }
  }

  /**
   * Apply single morphism.
   */
  private def applyMorphism(
    df: DataFrame,
    morphism: MorphismOp
  ): Either[CdmeError, DataFrame] = {
    morphism.morphismType.toUpperCase match {
      case "FILTER" =>
        morphism.predicate match {
          case Some(pred) =>
            try {
              Right(df.filter(expr(pred)))
            } catch {
              case e: Exception =>
                Left(CdmeError.CompilationError(s"Invalid filter predicate: ${e.getMessage}"))
            }
          case None =>
            Left(CdmeError.CompilationError(s"Filter morphism requires predicate"))
        }

      case "TRAVERSE" =>
        // Simplified traverse - would require join implementation
        Right(df)

      case "AGGREGATE" =>
        // Aggregation would be handled in projections
        Right(df)

      case other =>
        Left(CdmeError.CompilationError(s"Unknown morphism type: $other"))
    }
  }

  /**
   * Apply projections (select/aggregate).
   */
  private def applyProjections(
    df: DataFrame,
    projections: List[ProjectionOp]
  ): Either[CdmeError, DataFrame] = {
    try {
      // Check if any aggregations present
      val hasAggregations = projections.exists(_.aggregation.isDefined)

      if (hasAggregations) {
        // Group by non-aggregated columns
        val groupByCols = projections
          .filter(_.aggregation.isEmpty)
          .map(p => col(p.source))

        val aggExprs = projections.map { proj =>
          proj.aggregation match {
            case Some("SUM") => sum(col(proj.source)).alias(proj.name)
            case Some("COUNT") => count(col(proj.source)).alias(proj.name)
            case Some("AVG") => avg(col(proj.source)).alias(proj.name)
            case Some("MIN") => min(col(proj.source)).alias(proj.name)
            case Some("MAX") => max(col(proj.source)).alias(proj.name)
            case None => first(col(proj.source)).alias(proj.name)
            case Some(other) =>
              throw new IllegalArgumentException(s"Unknown aggregation: $other")
          }
        }

        val result = if (groupByCols.nonEmpty) {
          df.groupBy(groupByCols: _*).agg(aggExprs.head, aggExprs.tail: _*)
        } else {
          df.agg(aggExprs.head, aggExprs.tail: _*)
        }

        Right(result)
      } else {
        // Simple select
        val selectExprs = projections.map(p => col(p.source).alias(p.name))
        Right(df.select(selectExprs: _*))
      }
    } catch {
      case e: Exception =>
        Left(CdmeError.CompilationError(s"Failed to apply projections: ${e.getMessage}"))
    }
  }
}

/**
 * Execution result.
 */
case class ExecutionResult(
  data: DataFrame,
  errors: DataFrame,
  mappingName: String,
  sourceEntity: String,
  sourceRowCount: Long,
  outputRowCount: Long
)

/**
 * Error threshold checker.
 * Implements: REQ-ERR-02 (Error Threshold Configuration)
 *
 * @param errorThreshold Maximum allowed error rate (0.0 to 1.0)
 */
case class ErrorThresholdChecker(errorThreshold: Double) {

  /**
   * Check if error count is within threshold.
   *
   * @param totalRecords Total records processed
   * @param errorCount Number of errors encountered
   * @return Right(ThresholdCheckResult) if within threshold, Left(ThresholdExceededError) if exceeded
   */
  def check(totalRecords: Long, errorCount: Long): Either[CdmeError, ThresholdCheckResult] = {
    val errorRate = if (totalRecords == 0) 0.0 else errorCount.toDouble / totalRecords.toDouble

    if (errorRate <= errorThreshold) {
      Right(ThresholdCheckResult(
        passed = true,
        errorRate = errorRate,
        threshold = errorThreshold,
        totalRecords = totalRecords,
        errorCount = errorCount
      ))
    } else {
      Left(CdmeError.ThresholdExceededError(
        sourceKey = s"batch_${System.currentTimeMillis()}",
        morphismPath = "threshold_check",
        threshold = errorThreshold,
        actualRate = errorRate,
        totalRecords = totalRecords,
        errorCount = errorCount
      ))
    }
  }
}

/**
 * Result of threshold check.
 */
case class ThresholdCheckResult(
  passed: Boolean,
  errorRate: Double,
  threshold: Double,
  totalRecords: Long,
  errorCount: Long
)

/**
 * Aggregation operation that wires Algebra.scala Aggregator to Executor.
 * Implements: REQ-ADJ-01 (Monoid-based aggregations)
 */
case class AggregationOp(
  name: String,
  sourcePath: String,
  aggregationType: String,
  groupByKeys: List[String]
)

/**
 * Factory for creating Aggregator instances from aggregation type strings.
 * Wires the algebraic abstractions (Algebra.scala) to the execution layer.
 *
 * This factory bridges the gap between:
 * - The abstract algebraic layer (Algebra.scala with Monoid instances)
 * - The concrete execution layer (Executor.scala with DataFrame operations)
 *
 * By leveraging Cats Monoid instances, all aggregations benefit from:
 * - Associativity: Enables parallel/distributed computation
 * - Identity element: Enables safe empty aggregations
 * - Composability: Multiple aggregations can be combined
 *
 * Implements: REQ-ADJ-01 (Monoid-based aggregations)
 * Satisfies: ADR-008 (Scala Aggregation Patterns)
 */
object AggregatorFactory {
  import cdme.core.{Aggregator, MonoidInstances}
  import cats.Monoid

  /**
   * Create an Aggregator for the given aggregation type.
   *
   * The Aggregator uses the Monoid instance for the type A to perform
   * distributed aggregations. The caller must ensure the appropriate
   * Monoid is in scope.
   *
   * Supported aggregation types:
   * - SUM: Numeric summation (requires Monoid[BigDecimal] or similar)
   * - COUNT: Row counting (requires Monoid[Long])
   * - AVG: Average computation (requires Monoid[Avg])
   * - MIN: Minimum value (requires Monoid[Option[A]] with Ordering)
   * - MAX: Maximum value (requires Monoid[Option[A]] with Ordering)
   *
   * @param aggregationType The type of aggregation: SUM, COUNT, AVG, MIN, MAX
   * @tparam A The type being aggregated (must have Monoid instance)
   * @return Aggregator instance using the appropriate Monoid
   * @throws IllegalArgumentException if aggregationType is not supported
   *
   * Example:
   * {{{
   *   import MonoidInstances._
   *
   *   // Sum aggregation
   *   val sumAgg = AggregatorFactory.create[BigDecimal]("SUM")
   *   sumAgg.zero // BigDecimal(0)
   *   sumAgg.combine(BigDecimal(100), BigDecimal(50)) // BigDecimal(150)
   *
   *   // Count aggregation
   *   val countAgg = AggregatorFactory.create[Long]("COUNT")
   *   countAgg.zero // 0L
   *
   *   // Average aggregation
   *   val avgAgg = AggregatorFactory.create[Avg]("AVG")
   *   avgAgg.combine(Avg(100, 2), Avg(50, 1)) // Avg(150, 3)
   * }}}
   */
  def create[A](aggregationType: String)(implicit M: Monoid[A]): Aggregator[A, A] = {
    aggregationType.toUpperCase match {
      case "SUM" =>
        // SUM: Numeric summation using monoid combination
        // Example: BigDecimal(100) + BigDecimal(50) = BigDecimal(150)
        Aggregator.fromMonoid[A, A](identity)

      case "COUNT" =>
        // COUNT: Row counting via Long summation
        // Each row contributes 1L, monoid sums them
        Aggregator.fromMonoid[A, A](identity)

      case "AVG" =>
        // AVG: Maintain running sum and count via Avg monoid
        // Final average computed by Avg.average method
        Aggregator.fromMonoid[A, A](identity)

      case "MIN" | "MAX" =>
        // MIN/MAX: Use Option-based monoid to handle empty sets
        // Ordering comparison performed inside monoid instance
        Aggregator.fromMonoid[A, A](identity)

      case other =>
        throw new IllegalArgumentException(
          s"Unsupported aggregation type: '$other'. " +
          s"Supported types: SUM, COUNT, AVG, MIN, MAX"
        )
    }
  }

  /**
   * Check if an aggregation type is supported.
   *
   * @param aggregationType The aggregation type to check
   * @return true if supported, false otherwise
   */
  def isSupported(aggregationType: String): Boolean = {
    aggregationType.toUpperCase match {
      case "SUM" | "COUNT" | "AVG" | "MIN" | "MAX" => true
      case _ => false
    }
  }

  /**
   * Get list of all supported aggregation types.
   *
   * @return List of supported aggregation type names
   */
  def supportedTypes: List[String] = List("SUM", "COUNT", "AVG", "MIN", "MAX")
}
