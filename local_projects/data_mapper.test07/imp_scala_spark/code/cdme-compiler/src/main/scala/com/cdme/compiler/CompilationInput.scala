// Implements: REQ-LDM-03, REQ-AI-01
// Input types for the topological compiler.
package com.cdme.compiler

import com.cdme.model._

/**
 * Input to the topological compiler: the 4 configuration artifacts.
 * Implements: REQ-LDM-03, REQ-AI-01
 */
final case class CompilationInput(
    ldm: Category,
    pdm: PhysicalDataModel,
    mapping: MappingDefinition,
    jobConfig: JobConfiguration,
    principal: Principal
)

/** Principal identity for access control evaluation. */
final case class Principal(
    name: String,
    roles: Set[String]
)

object Principal {
  val system: Principal = Principal("system", Set("system"))
}

/** Physical data model: bindings from logical entities to physical targets. */
final case class PhysicalDataModel(
    bindings: Map[EntityId, PhysicalTarget],
    lookups: Map[LookupId, LookupBinding]
)

/** Physical target: where data lives. */
final case class PhysicalTarget(
    format: String,
    path: String,
    options: Map[String, String]
)

/** Lookup binding: reference data source. */
final case class LookupBinding(
    lookupId: LookupId,
    target: PhysicalTarget,
    version: LookupVersion
)

/** Mapping definition: source-to-target transformation specification. */
final case class MappingDefinition(
    name: String,
    sourceEntity: EntityId,
    targetEntity: EntityId,
    fieldMappings: List[FieldMapping],
    morphismPath: List[MorphismId]
)

/** Individual field mapping within a mapping definition. */
final case class FieldMapping(
    targetField: AttributeName,
    expression: MappingExpression
)

/** Sealed hierarchy for mapping expressions. */
sealed trait MappingExpression
object MappingExpression {
  final case class SourcePath(path: DotPath)                            extends MappingExpression
  final case class Synthesis(expr: String)                               extends MappingExpression
  final case class Conditional(condition: String, ifTrue: MappingExpression, ifFalse: MappingExpression) extends MappingExpression
  final case class Fallback(primaries: List[MappingExpression])          extends MappingExpression
  final case class Constant(value: String)                               extends MappingExpression
  final case class LookupRef(lookupId: LookupId, key: String)           extends MappingExpression
}

/** Dot-notation path through the LDM topology. */
final case class DotPath(segments: List[String]) {
  def asString: String = segments.mkString(".")
}

object DotPath {
  def parse(path: String): DotPath = DotPath(path.split("\\.").toList)
}

/** Job configuration for execution control. */
final case class JobConfiguration(
    maxOutputRows: Option[Long],
    maxJoinDepth: Option[Int],
    maxIntermediateSize: Option[Long],
    failureThresholdPercent: Option[Double],
    failureThresholdAbsolute: Option[Long],
    dryRun: Boolean,
    lineageMode: String
)
