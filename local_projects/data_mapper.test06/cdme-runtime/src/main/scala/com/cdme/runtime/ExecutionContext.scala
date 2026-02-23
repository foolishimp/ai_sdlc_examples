package com.cdme.runtime

import java.time.Instant
import com.cdme.compiler.CompiledPlan

// Implements: REQ-TRV-05-A (Immutable Run Hierarchy)
case class RunManifest(
  runId: String,
  configHash: String,
  codeHash: String,
  designHash: String,
  mappingVersion: String,
  sourceBindings: Map[String, String],
  lookupVersions: Map[String, String],
  cdmeVersion: String,
  timestamp: Instant,
  inputChecksums: Map[String, String] = Map.empty,
  outputChecksums: Map[String, String] = Map.empty
)

// Implements: REQ-TRV-05-B (Artifact Version Binding)
case class ArtifactVersion(
  artifactId: String,
  version: String,
  contentHash: String,
  createdAt: Instant
)

// Implements: REQ-TRV-01, REQ-TRV-03
case class ExecutionContext(
  runManifest: RunManifest,
  epoch: Epoch,
  compiledPlan: CompiledPlan,
  failureThreshold: Option[FailureThreshold] = None,
  lineageMode: LineageMode = LineageMode.Full
)

case class Epoch(
  id: String,
  startTime: Instant,
  endTime: Instant,
  generationGrain: GenerationGrain
)

// Implements: REQ-PDM-02
sealed trait GenerationGrain
object GenerationGrain {
  case object Event extends GenerationGrain
  case object Snapshot extends GenerationGrain
}

// Implements: RIC-LIN-01
sealed trait LineageMode
object LineageMode {
  case object Full extends LineageMode
  case object KeyDerivable extends LineageMode
  case object Sampled extends LineageMode
}

// Implements: REQ-TYP-03-A (Batch Failure Threshold)
case class FailureThreshold(
  maxAbsolute: Option[Long] = None,
  maxPercentage: Option[Double] = None,
  commitOnBreach: Boolean = false
)
