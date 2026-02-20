// Implements: REQ-F-PDM-001, REQ-F-PDM-004
package cdme.pdm

import java.net.URI
import cdme.model.types.CdmeType

/**
 * Opaque type for physical source identifiers.
 */
opaque type SourceId = String

object SourceId:
  def apply(value: String): SourceId = value
  extension (id: SourceId)
    def value: String = id
    def show: String = s"SourceId($id)"

/**
 * Data format of a physical source.
 */
enum DataFormat:
  case Parquet
  case Csv
  case Json
  case Delta
  case Iceberg
  case Jdbc(connectionString: String)
  case Custom(name: String)

/**
 * Physical schema describing the column structure of a physical source.
 *
 * @param columns ordered list of (column name, column type)
 */
final case class PhysicalSchema(
    columns: Vector[(String, CdmeType)]
):
  /** Look up a column type by name. */
  def columnType(name: String): Option[CdmeType] =
    columns.find(_._1 == name).map(_._2)

  /** Number of columns. */
  def columnCount: Int = columns.size

  /** All column names. */
  def columnNames: Vector[String] = columns.map(_._1)

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
