// Implements: REQ-DATA-QUAL-003
package cdme.integration.spark

import cdme.pdm.PhysicalSchema
import cdme.model.error.ValidationError

/**
 * Schema validation diagnostic.
 */
sealed trait SchemaIssue
object SchemaIssue {
  final case class MissingColumn(name: String) extends SchemaIssue
  final case class TypeMismatch(column: String, expected: String, actual: String) extends SchemaIssue
  final case class ExtraColumn(name: String) extends SchemaIssue // Warning, not error
}

/**
 * Validates physical DataFrame schema against expected LDM entity schema.
 *
 * Schema validation runs before data processing. Missing or mismatched
 * columns are errors; extra columns trigger a warning (schema drift)
 * but do not halt execution.
 *
 * TODO: Implement with actual Spark StructType validation.
 */
object SparkSchemaValidator {

  /**
   * Validate a physical schema against the expected schema.
   *
   * @param expected the expected schema from PDM binding
   * @param actual   the actual physical schema (from DataFrame)
   * @return list of schema issues
   */
  def validate(expected: PhysicalSchema, actual: PhysicalSchema): List[SchemaIssue] = {
    val expectedNames = expected.columnNames.toSet
    val actualNames = actual.columnNames.toSet

    val missing = (expectedNames -- actualNames).toList.map(SchemaIssue.MissingColumn)
    val extra = (actualNames -- expectedNames).toList.map(SchemaIssue.ExtraColumn)

    // TODO: Type comparison when Spark types are available
    missing ++ extra
  }
}
