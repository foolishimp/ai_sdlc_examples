package com.cdme.compiler

import com.cdme.model.{Grain, Morphism, SemanticType}

case class CompiledPlan(
  steps: List[ExecutionStep],
  estimatedCost: CostEstimate,
  grainMap: Map[String, Grain],
  typeMap: Map[String, SemanticType],
  requiredLookups: Map[String, LookupVersion]
)

sealed trait ExecutionStep
case class TraverseStep(morphism: Morphism) extends ExecutionStep
case class AggregateStep(morphism: Morphism, monoid: String) extends ExecutionStep
case class FilterStep(predicate: String) extends ExecutionStep
case class SynthesisStep(expression: String, resultType: SemanticType) extends ExecutionStep
case class LookupStep(lookupName: String, version: String) extends ExecutionStep

case class CostEstimate(
  estimatedOutputRows: Long,
  maxJoinDepth: Int,
  maxIntermediateSize: Long
)

case class LookupVersion(
  name: String,
  version: String
)

// Implements: REQ-AI-03 (Dry Run)
case class DryRunResult(
  plan: CompiledPlan,
  typeReport: Map[String, SemanticType],
  grainReport: Map[String, Grain],
  costEstimate: CostEstimate,
  errors: List[CompilationError]
)
