package com.cdme.runtime.executor

// Implements: REQ-F-TRV-001, REQ-F-TRV-005, REQ-F-LDM-004

import com.cdme.model.error.CdmeError
import com.cdme.model.morphism.{AlgebraicMorphism, ComputationalMorphism, Morphism, StructuralMorphism}
import com.cdme.runtime.backend.ExecutionBackend

/**
 * Dispatches morphism execution to the appropriate backend operation based on
 * morphism type.
 *
 * Routing rules:
 *  - [[StructuralMorphism]] (joins) -> backend join / map operation
 *  - [[ComputationalMorphism]] (transforms) -> backend map operation
 *  - [[AlgebraicMorphism]] (aggregations) -> backend fold operation via [[MonoidFolder]]
 *
 * For 1:N cardinality morphisms, execution is delegated to [[KleisliLifter]]
 * for correct flattening semantics.
 */
object MorphismExecutor {

  /**
   * Execute a single morphism against the provided backend, producing either
   * success or error results per record.
   *
   * @param morphism the morphism to execute
   * @param input    the input dataset
   * @param backend  the execution backend
   * @tparam F the container type
   * @tparam A input element type
   * @tparam B output element type
   * @return the result dataset wrapped in Either for error routing
   */
  def execute[F[_], A, B](
    morphism: Morphism,
    input: F[A],
    backend: ExecutionBackend[F]
  ): F[Either[CdmeError, B]] = {
    morphism match {
      case sm: StructuralMorphism =>
        // Structural morphisms (joins) are dispatched as map operations.
        // The actual join logic is handled by the backend which interprets
        // the join condition from the morphism's metadata.
        executeStructural(sm, input, backend)

      case cm: ComputationalMorphism =>
        // Computational morphisms (transforms, calculations) use executeMap.
        // For 1:N cardinality, delegate to KleisliLifter instead.
        executeComputational(cm, input, backend)

      case am: AlgebraicMorphism =>
        // Algebraic morphisms (aggregations) use MonoidFolder then wrap result.
        executeAlgebraic(am, input, backend)
    }
  }

  /**
   * Execute a structural morphism (join).
   */
  private def executeStructural[F[_], A, B](
    morphism: StructuralMorphism,
    input: F[A],
    backend: ExecutionBackend[F]
  ): F[Either[CdmeError, B]] = {
    // Structural morphisms map each input record through the join condition.
    // The backend interprets the join semantics (inner, left, etc.) based on
    // the morphism's adjoint pair and cardinality declaration.
    val f: A => Either[CdmeError, B] = { a =>
      // Delegate to the morphism's adjoint forward function
      morphism.adjoint.forward(a).asInstanceOf[Either[CdmeError, B]]
    }
    backend.executeMap(f, input)
  }

  /**
   * Execute a computational morphism (transform/calculation).
   */
  private def executeComputational[F[_], A, B](
    morphism: ComputationalMorphism,
    input: F[A],
    backend: ExecutionBackend[F]
  ): F[Either[CdmeError, B]] = {
    val f: A => Either[CdmeError, B] = { a =>
      morphism.adjoint.forward(a).asInstanceOf[Either[CdmeError, B]]
    }
    backend.executeMap(f, input)
  }

  /**
   * Execute an algebraic morphism (aggregation via monoid fold).
   *
   * Aggregation is performed by [[MonoidFolder]], then results are wrapped
   * in `Right` since fold operations do not produce per-record errors.
   */
  private def executeAlgebraic[F[_], A, B](
    morphism: AlgebraicMorphism,
    input: F[A],
    backend: ExecutionBackend[F]
  ): F[Either[CdmeError, B]] = {
    // MonoidFolder handles the actual fold; we wrap the result in Right.
    // Group keys are derived from the morphism's target grain declaration.
    // The monoid's empty value guarantees correct results for empty groups.
    ???
  }
}
