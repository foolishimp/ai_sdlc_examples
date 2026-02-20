package com.cdme.model.version

// Implements: REQ-NFR-VER-001

/**
 * Version metadata for any configuration artifact (LDM, PDM, mapping, job config).
 *
 * Every versioned artifact in CDME carries an [[ArtifactVersion]] to enable
 * deterministic reproducibility and audit trail. The optional `hash` field
 * supports content-addressable verification.
 *
 * @param artifactId Unique identifier for the artifact (e.g. "ldm/trading")
 * @param version    Semantic version string (e.g. "1.2.0")
 * @param hash       Optional content hash for integrity verification
 */
case class ArtifactVersion(
  artifactId: String,
  version: String,
  hash: Option[String] = None
)
