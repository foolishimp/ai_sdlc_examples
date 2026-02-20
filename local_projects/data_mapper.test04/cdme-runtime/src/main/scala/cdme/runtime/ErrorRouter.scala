// Implements: REQ-F-ERR-001, REQ-F-ERR-002, REQ-F-ERR-003, REQ-F-ERR-004, REQ-BR-ERR-001
// ADR-004: Either Monad for Error Handling
package cdme.runtime

import cdme.model.error.ErrorObject

/**
 * Error sink trait for persisting error objects.
 *
 * Error objects are serialisable (JSON + Parquet for bulk storage).
 * Idempotent error handling: same input + same config = bitwise identical
 * error output (REQ-F-ERR-003).
 *
 * Error Sink declaration is mandatory in the execution artifact; missing
 * declaration is a validation error.
 */
trait ErrorSink {

  /**
   * Write a batch of error objects to the sink.
   *
   * @param errors the error objects to persist
   * @return Right(()) on success, Left(error) if the sink fails
   */
  def write(errors: List[ErrorObject]): Either[SinkError, Unit]

  /**
   * Flush any buffered errors to the sink.
   *
   * @return Right(()) on success, Left(error) on failure
   */
  def flush(): Either[SinkError, Unit]
}

/**
 * Sink-level error (infrastructure failure in the error sink itself).
 *
 * @param message description of the sink failure
 * @param cause   optional underlying exception
 */
final case class SinkError(
    message: String,
    cause: Option[Throwable] = None
)

/**
 * Error router: routes Left values from morphism execution to the Error Sink.
 *
 * The router intercepts Either results during execution and:
 *   1. Passes Right values through to the next morphism
 *   2. Routes Left (ErrorObject) values to the declared Error Sink
 *   3. Tracks error counts for the accounting ledger
 *   4. Checks batch thresholds continuously
 *
 * Error handling is deterministic and idempotent: the same input with the
 * same configuration produces bitwise-identical error output.
 *
 * @param sink           the error sink to route errors to
 * @param batchThreshold batch failure threshold configuration
 */
final case class ErrorRouter(
    sink: ErrorSink,
    batchThreshold: cdme.compiler.BatchThresholdConfig
) {
  private var errorCount: Long = 0L
  private var totalCount: Long = 0L
  private val errorBuffer: scala.collection.mutable.ListBuffer[ErrorObject] =
    scala.collection.mutable.ListBuffer.empty

  /**
   * Route a morphism result: pass Right through, send Left to sink.
   *
   * @tparam A the success type
   * @param result the morphism result
   * @return Right(value) if success, Left(error) if batch threshold exceeded
   */
  def route[A](result: Either[ErrorObject, A]): Either[ErrorObject, A] = {
    totalCount += 1
    result match {
      case Right(value) => Right(value)
      case Left(error) =>
        errorCount += 1
        errorBuffer += error

        // Flush buffer periodically
        if (errorBuffer.size >= 1000) {
          flushBuffer()
        }

        // Check batch threshold
        if (isThresholdExceeded) {
          flushBuffer()
          Left(error) // Signal halt
        } else {
          Left(error)
        }
    }
  }

  /**
   * Check whether the batch threshold has been exceeded.
   */
  def isThresholdExceeded: Boolean =
    batchThreshold.maxAbsoluteErrors.exists(errorCount >= _) ||
    (totalCount > 0 && batchThreshold.maxErrorPercentage.exists { pct =>
      errorCount.toDouble / totalCount > pct
    })

  /**
   * Get the current error count.
   */
  def currentErrorCount: Long = errorCount

  /**
   * Get the current total record count.
   */
  def currentTotalCount: Long = totalCount

  /**
   * Flush buffered errors to the sink.
   */
  def flushBuffer(): Either[SinkError, Unit] = {
    if (errorBuffer.nonEmpty) {
      val result = sink.write(errorBuffer.toList)
      errorBuffer.clear()
      result
    } else {
      Right(())
    }
  }

  /**
   * Finalize the router, flushing any remaining errors.
   */
  def finalizeRouting(): Either[SinkError, Unit] =
    flushBuffer().flatMap(_ => sink.flush())
}
