// Implements: REQ-TYP-03, RIC-ERR-01
// Error routing: bifurcates records into valid/error streams.
package com.cdme.runtime

import com.cdme.model.error._
import com.cdme.model.MorphismId

/**
 * Routes errors to the Error Sink via Either bifurcation.
 * Errors are data, not exceptions (Axiom 10).
 *
 * Implements: REQ-TYP-03
 * See ADR-003: Either-Based Error Handling
 */
object ErrorRouter {

  /** An error record: the original record key + the error that occurred. */
  final case class ErrorRecord(
      recordKey: String,
      error: CdmeError,
      morphismId: Option[MorphismId]
  )

  /**
   * Bifurcate a sequence of records: valid records proceed, errors accumulate.
   * Returns (validRecords, errorRecords).
   *
   * Implements: REQ-TYP-03
   */
  def bifurcate[A](
      records: Seq[A],
      keyExtractor: A => String,
      validate: A => Either[CdmeError, A],
      morphismId: Option[MorphismId] = None
  ): (Seq[A], Seq[ErrorRecord]) = {
    val valid  = Seq.newBuilder[A]
    val errors = Seq.newBuilder[ErrorRecord]

    records.foreach { record =>
      validate(record) match {
        case Right(validRecord) => valid += validRecord
        case Left(error)        => errors += ErrorRecord(keyExtractor(record), error, morphismId)
      }
    }

    (valid.result(), errors.result())
  }

  /**
   * Check the circuit breaker: detect systemic failures early.
   * If error rate exceeds threshold in the first N records, halt execution.
   *
   * Implements: RIC-ERR-01
   */
  def checkCircuitBreaker(
      errorCount: Long,
      totalCount: Long,
      threshold: FailureThreshold
  ): Either[CdmeError, Unit] = {
    threshold match {
      case FailureThreshold.NoThreshold =>
        Right(())
      case FailureThreshold.AbsoluteCount(maxErrors) =>
        if (errorCount > maxErrors)
          Left(BatchThresholdExceeded(errorCount, totalCount))
        else
          Right(())
      case FailureThreshold.Percentage(maxPercent) =>
        if (totalCount > 0 && (errorCount.toDouble / totalCount * 100) > maxPercent)
          Left(CircuitBreakerTripped(errorCount, totalCount, maxPercent))
        else
          Right(())
    }
  }
}
