package com.cdme.runtime.executor

// Implements: REQ-F-LDM-004, REQ-F-LDM-005

import com.cdme.model.monoid.Monoid
import com.cdme.runtime.backend.ExecutionBackend

/**
 * Performs monoidal aggregation (fold) over a dataset, grouping by specified
 * key columns.
 *
 * The fold operation uses the monoid's `combine` for reduction and `empty`
 * as the identity element.  This guarantees:
 *  - '''Associativity''': `combine(combine(a, b), c) == combine(a, combine(b, c))`
 *    (validated at compile time by the compiler module)
 *  - '''Identity''': empty groups produce `monoid.empty` rather than null or missing
 *
 * The monoid's associativity property ensures safe distributed execution —
 * partial folds on different partitions can be combined without ordering
 * dependencies.
 */
object MonoidFolder {

  /**
   * Fold the input dataset using the provided monoid, grouping by the
   * specified key columns.
   *
   * When the input is empty, the result contains only `monoid.empty`,
   * satisfying REQ-F-LDM-005 (empty aggregation identity).
   *
   * @param monoid    the monoid providing `combine` and `empty`
   * @param groupKeys column/field names to group by before folding
   * @param backend   the execution backend
   * @tparam F the container type
   * @tparam A the element type
   * @return a function from input container to folded output container
   */
  def fold[F[_], A](
    monoid: Monoid[A],
    groupKeys: List[String],
    backend: ExecutionBackend[F]
  ): F[A] => F[A] = { input =>
    backend.executeFold(monoid, groupKeys, input)
  }
}
