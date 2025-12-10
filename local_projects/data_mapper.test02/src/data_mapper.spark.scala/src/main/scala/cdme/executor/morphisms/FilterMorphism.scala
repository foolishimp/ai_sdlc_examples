package cdme.executor.morphisms

import cdme.core._
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.functions.expr

/**
 * Filter morphism.
 * Implements: REQ-INT-01 (Filter operations)
 */
class FilterMorphism(predicate: String, path: String) extends Morphism[DataFrame, DataFrame] {

  override def apply(input: DataFrame)(implicit ctx: ExecutionContext): Either[CdmeError, DataFrame] = {
    try {
      Right(input.filter(expr(predicate)))
    } catch {
      case e: Exception =>
        Left(CdmeError.ValidationError(
          sourceKey = "N/A",
          morphismPath = path,
          rule = predicate,
          violation = e.getMessage
        ))
    }
  }

  override def morphismPath: String = path
}
