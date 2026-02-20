package com.cdme.spark

import org.apache.spark.sql.DataFrame

/**
 * Package-level type definitions for the Spark backend module.
 */
package object backend {

  /**
   * Constant type constructor that maps every type to [[DataFrame]].
   *
   * Spark DataFrames are untyped at the Scala level — `DataFrame` is
   * `Dataset[Row]` regardless of the logical schema. This type alias
   * satisfies the `F[_]` parameter of
   * [[com.cdme.runtime.backend.ExecutionBackend]] without requiring
   * a kind-projector compiler plugin.
   *
   * Type safety is guaranteed by the CDME compiler module, not by
   * Spark's type system.
   *
   * @tparam A erased — always resolves to DataFrame at runtime
   */
  type SparkDF[A] = DataFrame
}
