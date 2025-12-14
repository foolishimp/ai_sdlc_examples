package cdme.executor

import org.apache.spark.sql.SparkSession
import scala.collection.mutable

/**
 * Configuration for error handling thresholds and dead-letter queue.
 * Implements: REQ-TYP-03-A (Batch Failure Threshold)
 *
 * @param absoluteThreshold Maximum number of errors before halting (0 = disabled)
 * @param percentageThreshold Maximum error rate (0.0 to 1.0) before halting
 * @param bufferLimit Maximum number of errors to buffer in memory
 * @param dlqPath Path to dead-letter queue for writing error records
 */
case class ErrorConfig(
  absoluteThreshold: Long,
  percentageThreshold: Double,
  bufferLimit: Int,
  dlqPath: String
) {
  require(percentageThreshold >= 0.0 && percentageThreshold <= 1.0,
    "percentageThreshold must be between 0.0 and 1.0")
  require(bufferLimit > 0, "bufferLimit must be positive")
}

object ErrorConfig {
  /**
   * Default error configuration for most use cases.
   * Implements: REQ-TYP-03-A
   *
   * - 5% error rate threshold (typical batch tolerance)
   * - 10,000 absolute error limit (catch runaway failures)
   * - 1,000 buffered errors (balances memory vs diagnostics)
   *
   * @param dlqPath Path to dead-letter queue
   * @return ErrorConfig with sensible defaults
   */
  def default(dlqPath: String): ErrorConfig = ErrorConfig(
    absoluteThreshold = 10000,
    percentageThreshold = 0.05,
    bufferLimit = 1000,
    dlqPath = dlqPath
  )

  /**
   * Strict error configuration for critical pipelines.
   * Implements: REQ-TYP-03-A
   *
   * - 1% error rate threshold (strict quality)
   * - 100 absolute error limit (fail fast)
   * - 100 buffered errors (minimal memory)
   *
   * @param dlqPath Path to dead-letter queue
   * @return ErrorConfig with strict thresholds
   */
  def strict(dlqPath: String): ErrorConfig = ErrorConfig(
    absoluteThreshold = 100,
    percentageThreshold = 0.01,
    bufferLimit = 100,
    dlqPath = dlqPath
  )

  /**
   * Permissive error configuration for exploratory workloads.
   * Implements: REQ-TYP-03-A
   *
   * - 20% error rate threshold (exploratory tolerance)
   * - 100,000 absolute error limit (large datasets)
   * - 5,000 buffered errors (better diagnostics)
   *
   * @param dlqPath Path to dead-letter queue
   * @return ErrorConfig with permissive thresholds
   */
  def permissive(dlqPath: String): ErrorConfig = ErrorConfig(
    absoluteThreshold = 100000,
    percentageThreshold = 0.20,
    bufferLimit = 5000,
    dlqPath = dlqPath
  )
}

/**
 * Serializable error object for Spark accumulator storage.
 * Implements: REQ-ERR-01 (Minimal Error Object Content)
 *
 * Required fields per REQ-ERROR-01:
 * 1. The type of constraint or refinement that failed (errorType)
 * 2. The offending value(s) (actual)
 * 3. The source Entity and Epoch (sourceKey, context["entity"], context["epoch"])
 * 4. The morphism path at which the failure occurred (morphismPath)
 *
 * @param sourceKey Unique identifier for the source record (e.g., "order_123")
 * @param morphismPath Path through the morphism graph where error occurred
 * @param errorType Type of error (e.g., "refinement_error", "type_cast_error")
 * @param expected Expected value or constraint
 * @param actual Actual value that caused the error
 * @param context Additional context (epoch, entity, field name, etc.)
 */
case class ErrorObject(
  sourceKey: String,
  morphismPath: String,
  errorType: String,
  expected: String,
  actual: String,
  context: Map[String, String]
) extends Serializable

object ErrorObject {
  /**
   * Create ErrorObject from CdmeError.
   * Bridges between CdmeError ADT and ErrorObject for accumulator storage.
   *
   * @param error CdmeError to convert
   * @param epoch Epoch identifier for context
   * @return ErrorObject ready for accumulator storage
   */
  def fromCdmeError(error: cdme.core.CdmeError, epoch: String = "unknown"): ErrorObject = {
    val (expected, actual) = error match {
      case e: cdme.core.CdmeError.RefinementError => (e.expected, e.actual)
      case e: cdme.core.CdmeError.TypeCastError => (e.targetType, e.actualValue)
      case e: cdme.core.CdmeError.JoinKeyNotFoundError => (e.targetEntity, e.joinKey)
      case e: cdme.core.CdmeError.ValidationError => (e.rule, e.violation)
      case e: cdme.core.CdmeError.NullFieldError => ("non-null", "null")
      case e: cdme.core.CdmeError.GrainSafetyError => (e.targetGrain, e.sourceGrain)
      case e: cdme.core.CdmeError.CompilationError => ("valid config", e.message)
      case e: cdme.core.CdmeError.PathNotFoundError => ("valid path", e.path)
      case e: cdme.core.CdmeError.ThresholdExceededError => (s"<= ${e.threshold}", s"${e.actualRate}")
    }

    ErrorObject(
      sourceKey = error.sourceKey,
      morphismPath = error.morphismPath,
      errorType = error.errorType,
      expected = expected,
      actual = actual,
      context = error.context + ("epoch" -> epoch)
    )
  }
}

/**
 * Reason for threshold halt.
 * Implements: REQ-TYP-03-A
 */
sealed trait ThresholdReason extends Serializable

object ThresholdReason {
  case object AbsoluteCount extends ThresholdReason
  case object PercentageRate extends ThresholdReason
}

/**
 * Result of threshold check.
 * Implements: REQ-TYP-03-A, RIC-ERR-01 (Circuit Breakers)
 */
sealed trait ThresholdResult

object ThresholdResult {
  /**
   * Processing can continue - under threshold.
   */
  case object Continue extends ThresholdResult

  /**
   * Processing must halt - threshold exceeded.
   *
   * @param reason Why threshold was exceeded (absolute count or percentage)
   * @param errorCount Number of errors encountered
   * @param threshold The threshold value that was exceeded
   */
  case class Halt(
    reason: ThresholdReason,
    errorCount: Long,
    threshold: Double
  ) extends ThresholdResult
}

/**
 * Abstract error domain interface.
 * Implements: REQ-TYP-03 (Error Domain Semantics)
 */
trait ErrorDomain {
  /**
   * Route an error to the error domain.
   * Called from executor (task) side.
   *
   * @param error Error object to route
   */
  def routeError(error: ErrorObject): Unit

  /**
   * Check if error threshold has been exceeded.
   * Called from driver side.
   *
   * @param totalRows Total rows processed
   * @return ThresholdResult indicating Continue or Halt
   */
  def checkThreshold(totalRows: Long): ThresholdResult

  /**
   * Get total error count.
   *
   * @return Total number of errors routed
   */
  def getErrorCount(): Long

  /**
   * Get buffered errors.
   *
   * @return List of buffered error objects
   */
  def getErrors(): List[ErrorObject]

  /**
   * Reset error accumulators for new batch.
   */
  def reset(): Unit
}

/**
 * Spark-based error domain using accumulators for distributed error collection.
 * Implements: REQ-TYP-03 (Error Domain Semantics), REQ-TYP-03-A (Batch Failure Threshold)
 *
 * Uses Spark accumulators to collect errors across distributed executors:
 * - errorCount accumulator tracks total error count
 * - errorBuffer accumulator collects error details (up to bufferLimit)
 *
 * @param spark SparkSession for creating accumulators
 * @param config Error threshold configuration
 */
class SparkErrorDomain(spark: SparkSession, config: ErrorConfig) extends ErrorDomain {

  // Accumulator for error count (driver-readable, executor-writable)
  private val errorCount = spark.sparkContext.longAccumulator("errors")

  // Accumulator for error details (limited buffer)
  // Note: Using Spark's CollectionAccumulator for distributed error collection
  private val errorBuffer = spark.sparkContext.collectionAccumulator[ErrorObject]("error_buffer")

  /**
   * Route error from executor to accumulator.
   * Implements: REQ-TYP-03
   *
   * @param error Error object to route
   */
  override def routeError(error: ErrorObject): Unit = {
    errorCount.add(1)

    // Only buffer if under limit (to avoid memory issues)
    if (errorBuffer.value.size < config.bufferLimit) {
      errorBuffer.add(error)
    }
  }

  /**
   * Check if error threshold exceeded.
   * Implements: REQ-TYP-03-A (Circuit Breaker)
   *
   * Checks thresholds in order:
   * 1. Absolute count threshold (if configured)
   * 2. Percentage threshold (if configured)
   *
   * @param totalRows Total rows processed
   * @return ThresholdResult.Continue or ThresholdResult.Halt
   */
  override def checkThreshold(totalRows: Long): ThresholdResult = {
    val count = errorCount.value

    // Check absolute threshold first (if configured)
    if (config.absoluteThreshold > 0 && count > config.absoluteThreshold) {
      return ThresholdResult.Halt(
        reason = ThresholdReason.AbsoluteCount,
        errorCount = count,
        threshold = config.absoluteThreshold.toDouble
      )
    }

    // Check percentage threshold (if we have rows)
    if (totalRows > 0) {
      val errorRate = count.toDouble / totalRows.toDouble

      if (errorRate > config.percentageThreshold) {
        return ThresholdResult.Halt(
          reason = ThresholdReason.PercentageRate,
          errorCount = count,
          threshold = config.percentageThreshold
        )
      }
    }

    ThresholdResult.Continue
  }

  /**
   * Get total error count from accumulator.
   *
   * @return Total errors routed
   */
  override def getErrorCount(): Long = errorCount.value

  /**
   * Get buffered errors from accumulator.
   *
   * @return List of buffered error objects
   */
  override def getErrors(): List[ErrorObject] = {
    import scala.jdk.CollectionConverters._
    errorBuffer.value.asScala.toList
  }

  /**
   * Reset accumulators for new batch.
   */
  override def reset(): Unit = {
    errorCount.reset()
    errorBuffer.reset()
  }

  /**
   * Flush errors to dead-letter queue (DLQ).
   * Implements: REQ-TYP-03
   *
   * Note: Simplified implementation for MVP - writes buffered errors to Delta table.
   * Production implementation should handle partitioning, retention, etc.
   */
  def flushToDLQ(): Unit = {
    val errors = getErrors()

    if (errors.nonEmpty) {
      import spark.implicits._

      // Convert to DataFrame and write to DLQ
      val errorsDF = errors.toDF()

      errorsDF.write
        .format("delta")
        .mode("append")
        .save(config.dlqPath)
    }
  }
}

/**
 * Mock error domain for unit testing (no Spark dependency).
 * Implements: REQ-TYP-03
 *
 * Simulates accumulator behavior using in-memory collections.
 * Used for unit tests that don't require full Spark initialization.
 */
class MockErrorDomain(config: ErrorConfig) extends ErrorDomain {

  // In-memory error tracking
  private var errorCountValue: Long = 0
  private val errorBufferValue: mutable.ListBuffer[ErrorObject] = mutable.ListBuffer.empty

  /**
   * Route error to in-memory buffer.
   *
   * @param error Error object to route
   */
  override def routeError(error: ErrorObject): Unit = {
    errorCountValue += 1

    // Only buffer if under limit
    if (errorBufferValue.size < config.bufferLimit) {
      errorBufferValue += error
    }
  }

  /**
   * Check threshold using in-memory count.
   *
   * @param totalRows Total rows processed
   * @return ThresholdResult.Continue or ThresholdResult.Halt
   */
  override def checkThreshold(totalRows: Long): ThresholdResult = {
    val count = errorCountValue

    // Check absolute threshold first (if configured)
    if (config.absoluteThreshold > 0 && count > config.absoluteThreshold) {
      return ThresholdResult.Halt(
        reason = ThresholdReason.AbsoluteCount,
        errorCount = count,
        threshold = config.absoluteThreshold.toDouble
      )
    }

    // Check percentage threshold (if we have rows)
    if (totalRows > 0) {
      val errorRate = count.toDouble / totalRows.toDouble

      if (errorRate > config.percentageThreshold) {
        return ThresholdResult.Halt(
          reason = ThresholdReason.PercentageRate,
          errorCount = count,
          threshold = config.percentageThreshold
        )
      }
    }

    ThresholdResult.Continue
  }

  /**
   * Get total error count.
   *
   * @return Total errors routed
   */
  override def getErrorCount(): Long = errorCountValue

  /**
   * Get buffered errors.
   *
   * @return List of buffered error objects
   */
  override def getErrors(): List[ErrorObject] = errorBufferValue.toList

  /**
   * Reset for new batch.
   */
  override def reset(): Unit = {
    errorCountValue = 0
    errorBufferValue.clear()
  }
}
