package com.cdme.compiler.plan
// Implements: REQ-F-SYN-001

import com.cdme.model._
import com.cdme.model.morphism.Expression
import com.cdme.compiler.path.DotPath
import com.cdme.compiler.lookup.LookupRef

/**
 * A single source-to-target mapping definition.
 *
 * Declares how a target attribute is populated: either by traversing a
 * dot-path through the LDM graph, optionally applying a synthesis expression,
 * and optionally referencing versioned lookups.
 *
 * @param id              unique mapping identifier
 * @param sourcePath      dot-notation path through the LDM graph
 * @param targetEntity    the entity receiving the mapped value
 * @param targetAttribute the attribute on the target entity
 * @param expression      optional synthesis expression (None = direct copy)
 * @param lookupRefs      versioned lookup references used by the expression
 * @param version         artifact version for this mapping definition
 */
case class MappingDef(
  id: String,
  sourcePath: DotPath,
  targetEntity: EntityId,
  targetAttribute: String,
  expression: Option[Expression],
  lookupRefs: List[LookupRef],
  version: ArtifactVersion
)
