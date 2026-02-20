// Implements: REQ-NFR-MAINT-001
package cdme.integration.monitoring

import cdme.runtime.{TelemetryEntry, MetricsExporter => MetricsExporterTrait}

/**
 * Console-based metrics exporter for development and testing.
 *
 * Prints telemetry entries to standard output.
 *
 * TODO: Implement JMX and Prometheus-compatible exporters.
 */
class ConsoleMetricsExporter extends MetricsExporterTrait {

  override def export(entries: Vector[TelemetryEntry]): Unit =
    entries.foreach { entry =>
      println(
        s"[CDME Telemetry] morphism=${entry.morphismId} " +
        s"rows=${entry.rowCount} nullRate=${entry.nullRate} " +
        s"typeViolations=${entry.typeViolationCount} " +
        s"latencyMs=${entry.latencyMs}"
      )
    }

  override val intervalMs: Long = 30000L
}

/**
 * No-op metrics exporter for production environments where metrics are disabled.
 */
class NoOpMetricsExporter extends MetricsExporterTrait {
  override def export(entries: Vector[TelemetryEntry]): Unit = ()
}
