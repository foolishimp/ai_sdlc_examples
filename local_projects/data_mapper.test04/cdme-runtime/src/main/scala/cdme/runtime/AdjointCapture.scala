// Implements: REQ-F-ADJ-005, REQ-F-ADJ-008, REQ-NFR-PERF-003
// ADR-011: Reverse-Join Table Strategy for Aggregation Adjoints
package cdme.runtime

import cdme.model.adjoint.{ReverseJoinTable, FilteredKeyStore}

/**
 * Adjoint metadata capture during forward execution.
 *
 * During forward execution, adjoint metadata (reverse-join tables, filtered
 * keys, parent maps) is captured and persisted within the same epoch context
 * and storage location as forward execution results.
 *
 * This enables backward traversal proof (output to source traceability)
 * without re-executing the pipeline.
 */
trait AdjointCapture {

  /**
   * Capture a reverse-join table entry for an aggregation morphism.
   *
   * @param morphismId the aggregation morphism identifier
   * @param table      the reverse-join table
   */
  def captureReverseJoinTable(morphismId: String, table: ReverseJoinTable): Unit

  /**
   * Capture filtered keys for a filter morphism.
   *
   * @param morphismId the filter morphism identifier
   * @param store      the filtered key store
   */
  def captureFilteredKeys(morphismId: String, store: FilteredKeyStore): Unit

  /**
   * Capture a parent map for a Kleisli (1:N) morphism.
   *
   * @param morphismId the Kleisli morphism identifier
   * @param parentMap  mapping from child record key to parent record key
   */
  def captureParentMap(morphismId: String, parentMap: Map[String, String]): Unit

  /**
   * Persist all captured metadata to storage.
   *
   * @param location the storage location (URI)
   * @return Right(locations) mapping morphism IDs to their metadata locations,
   *         or Left(error) on failure
   */
  def persist(location: String): Either[String, Map[String, String]]
}

/**
 * In-memory implementation of AdjointCapture for testing and small datasets.
 */
class InMemoryAdjointCapture extends AdjointCapture {

  private val reverseJoinTables: scala.collection.mutable.Map[String, ReverseJoinTable] =
    scala.collection.mutable.Map.empty

  private val filteredKeyStores: scala.collection.mutable.Map[String, FilteredKeyStore] =
    scala.collection.mutable.Map.empty

  private val parentMaps: scala.collection.mutable.Map[String, Map[String, String]] =
    scala.collection.mutable.Map.empty

  override def captureReverseJoinTable(morphismId: String, table: ReverseJoinTable): Unit =
    reverseJoinTables(morphismId) = table

  override def captureFilteredKeys(morphismId: String, store: FilteredKeyStore): Unit =
    filteredKeyStores(morphismId) = store

  override def captureParentMap(morphismId: String, parentMap: Map[String, String]): Unit =
    parentMaps(morphismId) = parentMap

  override def persist(location: String): Either[String, Map[String, String]] = {
    // In-memory: just return the morphism IDs as "locations"
    val locations = reverseJoinTables.keys.map(id => id -> s"$location/rjt/$id").toMap ++
      filteredKeyStores.keys.map(id => id -> s"$location/fks/$id").toMap ++
      parentMaps.keys.map(id => id -> s"$location/pm/$id").toMap
    Right(locations)
  }

  /** Access captured reverse-join tables (for testing). */
  def getReverseJoinTable(morphismId: String): Option[ReverseJoinTable] =
    reverseJoinTables.get(morphismId)

  /** Access captured filtered key stores (for testing). */
  def getFilteredKeyStore(morphismId: String): Option[FilteredKeyStore] =
    filteredKeyStores.get(morphismId)
}
