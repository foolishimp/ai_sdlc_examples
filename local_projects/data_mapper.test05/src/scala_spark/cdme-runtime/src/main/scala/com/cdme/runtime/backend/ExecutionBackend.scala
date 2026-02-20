package com.cdme.runtime.backend

// Implements: REQ-F-PDM-001, REQ-NFR-DIST-001

import com.cdme.model.error.CdmeError
import com.cdme.model.monoid.Monoid
import com.cdme.model.pdm.PdmBinding
import com.cdme.model.config.Epoch

/**
 * Abstract execution backend parameterised by a higher-kinded container `F[_]`.
 *
 * Concrete implementations (e.g. Spark DataFrame, local Seq) plug into this
 * trait to provide distributed or local execution semantics.  The runtime
 * dispatches all data operations through this interface, ensuring the core
 * pipeline logic remains independent of any specific execution engine.
 *
 * @tparam F the container type representing the execution context
 *           (e.g. `DataFrame`, `Seq`, `Iterator`)
 */
trait ExecutionBackend[F[_]] {

  /**
   * Read a source dataset from physical storage for the given epoch.
   *
   * @param binding the PDM binding that maps a logical entity to physical storage
   * @param epoch   the processing epoch to scope the read
   * @return the dataset loaded into the execution context
   */
  def readSource(binding: PdmBinding, epoch: Epoch): F[Any]

  /**
   * Write a dataset to the physical sink defined by the binding.
   *
   * @param binding the PDM binding that maps a logical entity to physical storage
   * @param data    the dataset to write
   */
  def writeSink(binding: PdmBinding, data: F[Any]): Unit

  /**
   * Execute a map operation over the dataset, producing `Either[CdmeError, B]` for
   * each element.  `Left` values represent per-record errors to be routed to the
   * Error Domain.
   *
   * @param f     the mapping function from A to Either[CdmeError, B]
   * @param input the input dataset
   * @tparam A input element type
   * @tparam B output element type
   * @return dataset of Either results
   */
  def executeMap[A, B](f: A => Either[CdmeError, B], input: F[A]): F[Either[CdmeError, B]]

  /**
   * Execute a Kleisli (flatMap) operation for 1:N morphisms.
   *
   * The function `f` may produce zero or more output elements per input.
   * The backend is responsible for flattening the `List[B]` into individual
   * elements in the output container.
   *
   * @param f     the Kleisli function from A to Either[CdmeError, List[B]]
   * @param input the input dataset
   * @tparam A input element type
   * @tparam B output element type
   * @return dataset of Either results (flattened)
   */
  def executeFlatMap[A, B](f: A => Either[CdmeError, List[B]], input: F[A]): F[Either[CdmeError, B]]

  /**
   * Execute a monoidal fold (aggregation) over the dataset, grouping by the
   * specified key columns.
   *
   * @param monoid    the monoid providing `combine` and `empty`
   * @param groupKeys column names to group by before folding
   * @param input     the input dataset
   * @tparam A the element type (must be compatible with the monoid)
   * @return the folded dataset
   */
  def executeFold[A](monoid: Monoid[A], groupKeys: List[String], input: F[A]): F[A]
}
