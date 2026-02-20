package com.cdme.runtime.error

// Implements: REQ-F-ERR-001, REQ-F-ERR-003

import com.cdme.model.error.CdmeError

/**
 * Routes `Either[CdmeError, T]` results into success and error partitions.
 *
 * The error router enforces strict `Either` monad semantics:
 *  - `Right` values flow to the success path for downstream processing
 *  - `Left` values are accumulated and routed to the Error Domain sink
 *
 * Error handling is deterministic and idempotent: the same failing input
 * with the same configuration always produces the same error output
 * (REQ-F-ERR-003).
 *
 * @tparam T the success value type
 */
class ErrorRouter[T] {

  private var _errors: List[CdmeError] = Nil
  private var _successes: List[T] = Nil
  private var _errorCount: Long = 0L
  private var _successCount: Long = 0L

  /**
   * Route a single Either result, accumulating it into the appropriate partition.
   *
   * @param result the result to route
   */
  def route(result: Either[CdmeError, T]): Unit = result match {
    case Right(value) =>
      _successes = value :: _successes
      _successCount += 1L
    case Left(error) =>
      _errors = error :: _errors
      _errorCount += 1L
  }

  /**
   * Route a batch of Either results.
   *
   * @param results the results to route
   */
  def routeAll(results: Iterable[Either[CdmeError, T]]): Unit =
    results.foreach(route)

  /** Accumulated errors (most recent first). */
  def errors: List[CdmeError] = _errors

  /** Accumulated successes (most recent first). */
  def successes: List[T] = _successes

  /** Total number of errors routed. */
  def errorCount: Long = _errorCount

  /** Total number of successes routed. */
  def successCount: Long = _successCount

  /** Total number of records routed (errors + successes). */
  def totalCount: Long = _errorCount + _successCount
}

/**
 * Companion object providing functional-style routing without mutable state.
 */
object ErrorRouter {

  /**
   * Result of partitioning a collection of Either values.
   *
   * @param successes the Right values
   * @param errors    the Left values (CdmeError instances)
   */
  case class RoutingResult[T](
    successes: List[T],
    errors: List[CdmeError]
  ) {
    /** Number of successful records. */
    def successCount: Long = successes.size.toLong

    /** Number of error records. */
    def errorCount: Long = errors.size.toLong

    /** Total records processed. */
    def totalCount: Long = successCount + errorCount
  }

  /**
   * Partition a collection of Either results into successes and errors.
   *
   * This is a pure function with no side effects — deterministic and idempotent.
   *
   * @param results the Either results to partition
   * @tparam T the success value type
   * @return a [[RoutingResult]] containing the partitioned values
   */
  def partition[T](results: Iterable[Either[CdmeError, T]]): RoutingResult[T] = {
    val (lefts, rights) = results.foldLeft((List.empty[CdmeError], List.empty[T])) {
      case ((errs, succs), Left(err))    => (err :: errs, succs)
      case ((errs, succs), Right(value)) => (errs, value :: succs)
    }
    RoutingResult(successes = rights.reverse, errors = lefts.reverse)
  }
}
