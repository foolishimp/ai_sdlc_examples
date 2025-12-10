package cdme.executor.morphisms

import cdme.core._
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.functions._

/**
 * Aggregate morphism.
 * Implements: REQ-ADJ-01 (Aggregations)
 */
class AggregateMorphism(
  groupKeys: List[String],
  aggregations: Map[String, String],
  path: String
) extends Morphism[DataFrame, DataFrame] {

  override def apply(input: DataFrame)(implicit ctx: ExecutionContext): Either[CdmeError, DataFrame] = {
    try {
      val groupCols = groupKeys.map(col)

      val aggExprs = aggregations.map { case (colName, aggType) =>
        aggType.toUpperCase match {
          case "SUM" => sum(col(colName)).alias(s"${colName}_sum")
          case "COUNT" => count(col(colName)).alias(s"${colName}_count")
          case "AVG" => avg(col(colName)).alias(s"${colName}_avg")
          case "MIN" => min(col(colName)).alias(s"${colName}_min")
          case "MAX" => max(col(colName)).alias(s"${colName}_max")
          case other => throw new IllegalArgumentException(s"Unknown aggregation: $other")
        }
      }.toSeq

      val result = if (groupCols.nonEmpty) {
        input.groupBy(groupCols: _*).agg(aggExprs.head, aggExprs.tail: _*)
      } else {
        input.agg(aggExprs.head, aggExprs.tail: _*)
      }

      Right(result)
    } catch {
      case e: Exception =>
        Left(CdmeError.CompilationError(s"Aggregation failed: ${e.getMessage}"))
    }
  }

  override def morphismPath: String = path
}
