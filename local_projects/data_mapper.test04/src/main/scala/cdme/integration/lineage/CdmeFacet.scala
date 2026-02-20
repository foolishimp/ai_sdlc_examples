// Implements: REQ-NFR-OBS-001
package cdme.integration.lineage

/**
 * CDME-specific custom facet for OpenLineage events.
 *
 * Contains domain-specific metadata that extends the standard OpenLineage
 * dataset and run facets.
 *
 * @param grainMetadata    grain level information
 * @param typeMetadata     type system metadata
 * @param adjointMetadata  adjoint/backward metadata locations
 * @param accountingRef    reference to the accounting ledger
 */
final case class CdmeFacet(
    grainMetadata: Map[String, String],
    typeMetadata: Map[String, String],
    adjointMetadata: Map[String, String],
    accountingRef: Option[String]
):
  /**
   * Convert to a JSON-compatible map for OpenLineage integration.
   */
  def toMap: Map[String, Any] = Map(
    "_producer" -> "cdme",
    "_schemaURL" -> "https://cdme.io/facets/v1/CdmeFacet.json",
    "grain" -> grainMetadata,
    "types" -> typeMetadata,
    "adjoint" -> adjointMetadata,
    "accounting" -> accountingRef.getOrElse("")
  )
