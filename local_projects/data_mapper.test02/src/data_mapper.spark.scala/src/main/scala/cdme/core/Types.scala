package cdme.core

import cats.data.EitherT
import org.apache.spark.sql.Dataset

/**
 * Core type definitions for CDME.
 * Implements: ADR-007 (Either Monad for Error Handling)
 */
object Types {

  // Type alias for result types
  type Result[+A] = Either[CdmeError, A]

  // Dataset with Either for row-level error handling
  type DatasetResult[A] = Dataset[Either[CdmeError, A]]

  // EitherT for composing Dataset operations
  type DatasetEither[A] = EitherT[Dataset, CdmeError, A]
}

/**
 * Error domain ADT.
 * Implements: REQ-TYP-03, REQ-ERR-01, REQ-ERR-03
 */
sealed trait CdmeError {
  def sourceKey: String
  def morphismPath: String
  def context: Map[String, String]

  /** Returns snake_case error type for consistent DataFrame storage */
  def errorType: String = {
    val name = getClass.getSimpleName.stripSuffix("$")
    name.replaceAll("([A-Z])", "_$1").toLowerCase.stripPrefix("_")
  }
}

object CdmeError {

  case class RefinementError(
    sourceKey: String,
    morphismPath: String,
    field: String,
    expected: String,
    actual: String,
    context: Map[String, String] = Map.empty
  ) extends CdmeError

  case class TypeCastError(
    sourceKey: String,
    morphismPath: String,
    field: String,
    targetType: String,
    actualValue: String,
    context: Map[String, String] = Map.empty
  ) extends CdmeError

  case class JoinKeyNotFoundError(
    sourceKey: String,
    morphismPath: String,
    joinKey: String,
    targetEntity: String,
    context: Map[String, String] = Map.empty
  ) extends CdmeError

  case class ValidationError(
    sourceKey: String,
    morphismPath: String,
    rule: String,
    violation: String,
    context: Map[String, String] = Map.empty
  ) extends CdmeError

  case class NullFieldError(
    sourceKey: String,
    morphismPath: String,
    field: String,
    context: Map[String, String] = Map.empty
  ) extends CdmeError

  case class GrainSafetyError(
    sourceKey: String,
    morphismPath: String,
    sourceGrain: String,
    targetGrain: String,
    violation: String,
    context: Map[String, String] = Map.empty
  ) extends CdmeError

  // Compilation errors (not row-level)
  case class CompilationError(
    message: String,
    sourceKey: String = "N/A",
    morphismPath: String = "compilation",
    context: Map[String, String] = Map.empty
  ) extends CdmeError

  case class PathNotFoundError(
    path: String,
    sourceKey: String = "N/A",
    morphismPath: String = "compilation",
    context: Map[String, String] = Map.empty
  ) extends CdmeError
}

/**
 * Error record for DataFrame storage.
 * Implements: REQ-ERR-03 (Rich Error Context)
 */
case class CdmeErrorRecord(
  source_key: String,
  error_type: String,
  error_code: String,
  morphism_path: String,
  expected: String,
  actual: String,
  context: Map[String, String],
  run_id: String,
  epoch: String,
  timestamp: java.sql.Timestamp
)

object CdmeErrorRecord {

  def fromError(error: CdmeError, runId: String, epoch: String): CdmeErrorRecord = {
    val (expected, actual) = error match {
      case e: CdmeError.RefinementError => (e.expected, e.actual)
      case e: CdmeError.TypeCastError => (e.targetType, e.actualValue)
      case e: CdmeError.JoinKeyNotFoundError => (e.targetEntity, e.joinKey)
      case e: CdmeError.ValidationError => (e.rule, e.violation)
      case e: CdmeError.NullFieldError => ("non-null", "null")
      case e: CdmeError.GrainSafetyError => (e.targetGrain, e.sourceGrain)
      case e: CdmeError.CompilationError => ("valid config", e.message)
      case e: CdmeError.PathNotFoundError => ("valid path", e.path)
    }

    CdmeErrorRecord(
      source_key = error.sourceKey,
      error_type = error.errorType,
      error_code = deriveErrorCode(error),
      morphism_path = error.morphismPath,
      expected = expected,
      actual = actual,
      context = error.context,
      run_id = runId,
      epoch = epoch,
      timestamp = new java.sql.Timestamp(System.currentTimeMillis())
    )
  }

  private def deriveErrorCode(error: CdmeError): String = error match {
    case _: CdmeError.RefinementError => "CDME-001"
    case _: CdmeError.TypeCastError => "CDME-002"
    case _: CdmeError.JoinKeyNotFoundError => "CDME-003"
    case _: CdmeError.ValidationError => "CDME-004"
    case _: CdmeError.NullFieldError => "CDME-005"
    case _: CdmeError.GrainSafetyError => "CDME-006"
    case _: CdmeError.CompilationError => "CDME-100"
    case _: CdmeError.PathNotFoundError => "CDME-101"
  }
}
