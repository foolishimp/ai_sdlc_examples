package com.cdme.runtime.executor

// Implements: REQ-F-TRV-001

import com.cdme.model.error.CdmeError
import com.cdme.model.morphism.Morphism
import com.cdme.runtime.backend.ExecutionBackend

/**
 * Lifts a morphism into Kleisli composition for 1:N (one-to-many) edges.
 *
 * When a morphism produces multiple output records per input (e.g. explode,
 * unnest, or one-to-many joins), the Kleisli lifter converts the morphism's
 * `A => Either[CdmeError, List[B]]` function into a backend flatMap operation
 * that correctly flattens the result into the output container.
 *
 * This ensures that downstream morphisms see individual `B` elements rather
 * than `List[B]` containers.
 */
object KleisliLifter {

  /**
   * Lift a 1:N morphism into a Kleisli-composed backend operation.
   *
   * The morphism's adjoint forward function is expected to return
   * `Either[CdmeError, List[B]]`.  The lifter delegates to the backend's
   * `executeFlatMap` which handles flattening semantics (e.g. Spark `explode`).
   *
   * @param morphism the 1:N morphism to lift
   * @param backend  the execution backend providing flatMap semantics
   * @tparam F the container type
   * @tparam A input element type
   * @tparam B output element type (individual, not List)
   * @return a function from input container to output container of Either results
   */
  def lift[F[_], A, B](
    morphism: Morphism,
    backend: ExecutionBackend[F]
  ): F[A] => F[Either[CdmeError, B]] = { input =>
    val kleisliF: A => Either[CdmeError, List[B]] = { a =>
      morphism.adjoint.forward(a) match {
        case Right(result) =>
          // The forward function should return a List for 1:N morphisms.
          // Cast is safe here because the compiler has validated the
          // morphism's cardinality as OneToMany.
          Right(result.asInstanceOf[List[B]])
        case Left(err) =>
          Left(err)
      }
    }
    backend.executeFlatMap(kleisliF, input)
  }
}
