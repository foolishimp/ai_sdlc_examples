// Implements: REQ-F-API-001, REQ-F-PDM-001, REQ-F-PDM-002
// ADR-010: Programmatic API over Configuration Files
package cdme.api

import cdme.model.category.EntityId
import cdme.model.error.ValidationError
import cdme.pdm.*
import java.net.URI

/**
 * Fluent API for configuring PDM bindings.
 *
 * Usage:
 * {{{
 *   val binding = PdmBinder("Trade")
 *     .to(SparkSource("trades_table"))
 *     .generationGrain(GenerationGrain.Event)
 *     .epochBoundary(TimestampBoundary("trade_timestamp"))
 *     .build()
 * }}}
 *
 * @param entityName the LDM entity name being bound
 */
final case class PdmBinder(
    entityName: String,
    private val physSource: Option[PhysicalSource] = None,
    private val genGrain: Option[GenerationGrain] = None,
    private val epochBound: Option[EpochBoundary] = None
):
  /**
   * Bind to a physical source.
   *
   * @param source the physical source
   * @return updated binder
   */
  def to(source: PhysicalSource): PdmBinder =
    copy(physSource = Some(source))

  /**
   * Set the generation grain.
   *
   * @param gg the generation grain (Event or Snapshot)
   * @return updated binder
   */
  def generationGrain(gg: GenerationGrain): PdmBinder =
    copy(genGrain = Some(gg))

  /**
   * Set the epoch boundary.
   *
   * @param eb the epoch boundary definition
   * @return updated binder
   */
  def epochBoundary(eb: EpochBoundary): PdmBinder =
    copy(epochBound = Some(eb))

  /**
   * Build the PDM binding.
   *
   * @return Right(binding) or Left(error) if required fields are missing
   */
  def build(): Either[ValidationError, PdmBinding] =
    for
      source <- physSource.toRight(
        ValidationError.General(s"PDM binding for '$entityName' requires a physical source")
      )
      grain <- genGrain.toRight(
        ValidationError.General(s"PDM binding for '$entityName' requires a generation grain")
      )
      boundary <- epochBound.toRight(
        ValidationError.General(s"PDM binding for '$entityName' requires an epoch boundary")
      )
    yield
      PdmBinding(
        entityId = EntityId(entityName.toLowerCase.replaceAll("\\s+", "_")),
        physicalSource = source,
        generationGrain = grain,
        epochBoundary = boundary
      )

object PdmBinder:
  /**
   * Start binding an entity.
   *
   * @param entityName the entity to bind
   * @return a new PdmBinder
   */
  def bind(entityName: String): PdmBinder = PdmBinder(entityName)

  /**
   * Convenience: create a Spark table physical source.
   *
   * @param tableName the Spark table name
   * @param schema    the physical schema
   * @return a PhysicalSource for a Spark table
   */
  def sparkSource(tableName: String, schema: PhysicalSchema): PhysicalSource =
    PhysicalSource(
      id = SourceId(tableName),
      location = URI.create(s"spark://$tableName"),
      format = DataFormat.Parquet,
      schema = schema
    )
