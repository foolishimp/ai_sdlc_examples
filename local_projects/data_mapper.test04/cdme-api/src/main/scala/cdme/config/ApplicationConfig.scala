// Implements: REQ-NFR-MAINT-001, REQ-NFR-OBS-001
package cdme.config

/**
 * Application-level configuration (monitoring, OpenLineage, etc.).
 *
 * In a full implementation, this would be loaded from HOCON via Typesafe Config.
 * This structure defines the configuration schema.
 *
 * @param monitoring       monitoring configuration
 * @param openLineage      OpenLineage configuration
 */
final case class ApplicationConfig(
    monitoring: MonitoringConfig = MonitoringConfig(),
    openLineage: OpenLineageConfig = OpenLineageConfig()
)

/**
 * Monitoring configuration.
 *
 * @param enabled         whether monitoring is enabled
 * @param metricsEndpoint endpoint for metrics export
 * @param exportIntervalMs export interval in milliseconds (default: 30 seconds)
 */
final case class MonitoringConfig(
    enabled: Boolean = true,
    metricsEndpoint: String = "localhost:9090",
    exportIntervalMs: Long = 30000L
)

/**
 * OpenLineage configuration.
 *
 * @param enabled     whether OpenLineage emission is enabled
 * @param endpointUrl the OpenLineage API endpoint URL
 * @param apiKey      optional API key for authentication
 * @param namespace   the namespace for events
 */
final case class OpenLineageConfig(
    enabled: Boolean = false,
    endpointUrl: String = "",
    apiKey: Option[String] = None,
    namespace: String = "cdme"
)

object ApplicationConfig {
  /**
   * Load configuration from HOCON.
   *
   * TODO: Implement HOCON loading via Typesafe Config library.
   * For now, returns default configuration.
   */
  def load(): ApplicationConfig = ApplicationConfig()
}
