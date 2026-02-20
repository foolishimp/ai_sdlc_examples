// Implements: REQ-F-PDM-001, REQ-F-PDM-004
package cdme.pdm

import java.net.URI
import cdme.model.types.CdmeType

/**
 * Wrapper type for physical source identifiers.
 */
final case class SourceId(value: String) {
  def show: String = s"SourceId($value)"
}

/**
 * Data format of a physical source.
 */
sealed trait DataFormat
object DataFormat {
  case object Parquet extends DataFormat
  case object Csv extends DataFormat
  case object Json extends DataFormat
  case object Delta extends DataFormat
  case object Iceberg extends DataFormat
  final case class Jdbc(connectionString: String) extends DataFormat
  final case class Custom(name: String) extends DataFormat
}

/**
 * Physical schema describing the column structure of a physical source.
 *
 * @param columns ordered list of (column name, column type)
 */
final case class PhysicalSchema(
    columns: Vector[(String, CdmeType)]
) {
  /** Look up a column type by name. */
  def columnType(name: String): Option[CdmeType] =
    columns.find(_._1 == name).map(_._2)

  /** Number of columns. */
  def columnCount: Int = columns.size

  /** All column names. */
  def columnNames: Vector[String] = columns.map(_._1)
}

/**
 * Describes a physical storage location for data.
 *
 * @param id       unique source identifier
 * @param location URI of the physical storage (file path, table reference, etc.)
 * @param format   data format
 * @param schema   physical column schema
 */
final case class PhysicalSource(
    id: SourceId,
    location: URI,
    format: DataFormat,
    schema: PhysicalSchema
)
