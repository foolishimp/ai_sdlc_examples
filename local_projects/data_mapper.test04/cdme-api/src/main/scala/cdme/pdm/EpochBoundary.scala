// Implements: REQ-F-PDM-002, REQ-F-PDM-003
package cdme.pdm

import java.time.Duration

/**
 * Epoch boundary definition: how continuous data is sliced into processing epochs.
 *
 * Missing generation grain or epoch boundary is a validation error.
 */
sealed trait EpochBoundary {
  /** Human-readable description for lineage. */
  def description: String
}

/**
 * Temporal window boundary: slice data by time duration.
 *
 * @param duration the window duration
 */
final case class TemporalWindow(duration: Duration) extends EpochBoundary {
  override def description: String = s"TemporalWindow(${duration.toHours}h)"
}

/**
 * Version-based boundary: slice data by a version field.
 *
 * @param versionField the column name containing version information
 */
final case class VersionBoundary(versionField: String) extends EpochBoundary {
  override def description: String = s"VersionBoundary($versionField)"
}

/**
 * Timestamp-based boundary: slice data by a timestamp field.
 *
 * @param timestampField the column name containing the timestamp
 */
final case class TimestampBoundary(timestampField: String) extends EpochBoundary {
  override def description: String = s"TimestampBoundary($timestampField)"
}
