// Implements: REQ-F-AI-003
package cdme.assurance

/**
 * An entry in the triangulation report linking intent to logic to proof.
 *
 * @param intentKey    the intent or requirement key (e.g., REQ-F-LDM-001)
 * @param morphismIds  morphism IDs that implement this intent
 * @param traceIds     execution trace IDs that prove this intent was exercised
 */
final case class TriangulationEntry(
    intentKey: String,
    morphismIds: List[String],
    traceIds: List[String]
)

/**
 * Intent-Logic-Proof triangulation report.
 *
 * Links intent keys to morphism definitions to execution traces, providing
 * a queryable structure for audit and compliance.
 *
 * @param entries the triangulation entries
 */
final case class TriangulationReport(
    entries: List[TriangulationEntry]
):
  /**
   * Find all morphisms implementing a given intent key.
   *
   * @param intentKey the intent key to query
   * @return morphism IDs implementing this intent
   */
  def morphismsForIntent(intentKey: String): List[String] =
    entries.filter(_.intentKey == intentKey).flatMap(_.morphismIds)

  /**
   * Find all execution traces for a given intent key.
   *
   * @param intentKey the intent key to query
   * @return trace IDs proving this intent was exercised
   */
  def tracesForIntent(intentKey: String): List[String] =
    entries.filter(_.intentKey == intentKey).flatMap(_.traceIds)

  /**
   * Check for intents without any implementing morphisms.
   *
   * @return intent keys that have no morphism implementations
   */
  def unimplementedIntents: List[String] =
    entries.filter(_.morphismIds.isEmpty).map(_.intentKey)

  /**
   * Check for intents without any execution traces.
   *
   * @return intent keys that have no execution proof
   */
  def untestedIntents: List[String] =
    entries.filter(_.traceIds.isEmpty).map(_.intentKey)
