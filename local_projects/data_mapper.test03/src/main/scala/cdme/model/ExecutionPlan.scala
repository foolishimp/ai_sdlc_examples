package cdme.model

// Implements: REQ-F-TRV-006, REQ-F-AI-003

/** Execution plan produced by the TopologicalCompiler.
  *
  * Contains the validated morphism chain, cost estimate, and execution mode.
  */
case class ExecutionPlan(
    topologyVersion: String,
    mappingVersion: String,
    stages: List[ExecutionStage],
    budget: Budget,
    dryRun: Boolean = false
)

case class ExecutionStage(
    morphism: Morphism,
    estimatedCardinality: Long,
    lineageMode: LineageMode
)

enum LineageMode:
  case Full          // Persist all intermediate keys
  case KeyDerivable  // Compressed key envelope
  case Sampled       // Sample or aggregate stats

/** Budget for computational cost governance (REQ-F-TRV-006). */
case class Budget(
    maxOutputRows: Long = Long.MaxValue,
    maxJoinDepth: Int = 10,
    maxIntermediateSize: Long = Long.MaxValue
)
