package com.cdme.model.config

// Implements: REQ-F-PDM-004, REQ-F-CTX-001

import java.time.Instant

/**
 * An Epoch defines a bounded time window for data processing.
 *
 * Epochs are the fundamental unit of temporal scoping in CDME. All data
 * reads are scoped to an epoch, and fiber compatibility checks ensure
 * that join partners share the same epoch or declare explicit temporal
 * semantics.
 *
 * @param id         unique identifier for this epoch instance (e.g. "2024-01-15")
 * @param start      inclusive start of the time window
 * @param end        exclusive end of the time window
 * @param parameters additional epoch metadata (e.g. business_date, region)
 */
case class Epoch(
  id: String,
  start: Instant,
  end: Instant,
  parameters: Map[String, String]
)
