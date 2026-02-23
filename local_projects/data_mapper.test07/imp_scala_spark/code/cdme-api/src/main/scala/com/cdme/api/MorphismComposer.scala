// Implements: REQ-LDM-03, REQ-INT-04
// Composes morphism chains with validation.
package com.cdme.api

import com.cdme.model._
import com.cdme.model.error.{CdmeError, CompositionError}
import com.cdme.compiler.{MappingDefinition, FieldMapping, MappingExpression, DotPath}

/**
 * Composes morphism chains and builds mapping definitions.
 * Implements: REQ-LDM-03, REQ-INT-04
 */
final case class MorphismComposer(
    name: String,
    sourceEntity: EntityId,
    targetEntity: EntityId,
    morphismPath: List[MorphismId],
    fieldMappings: List[FieldMapping]
) {

  /** Add a morphism to the chain. */
  def via(morphismId: MorphismId): MorphismComposer =
    copy(morphismPath = morphismPath :+ morphismId)

  /** Map a target field from a source path. */
  def mapField(targetField: AttributeName, sourcePath: String): MorphismComposer =
    copy(fieldMappings = fieldMappings :+ FieldMapping(targetField, MappingExpression.SourcePath(DotPath.parse(sourcePath))))

  /** Map a target field to a constant value. */
  def constField(targetField: AttributeName, value: String): MorphismComposer =
    copy(fieldMappings = fieldMappings :+ FieldMapping(targetField, MappingExpression.Constant(value)))

  /** Map a target field to a synthesis expression. */
  def synthField(targetField: AttributeName, expr: String): MorphismComposer =
    copy(fieldMappings = fieldMappings :+ FieldMapping(targetField, MappingExpression.Synthesis(expr)))

  /** Map a target field with conditional logic. */
  def conditionalField(
      targetField: AttributeName,
      condition: String,
      ifTrue: MappingExpression,
      ifFalse: MappingExpression
  ): MorphismComposer =
    copy(fieldMappings = fieldMappings :+
      FieldMapping(targetField, MappingExpression.Conditional(condition, ifTrue, ifFalse)))

  /** Map a target field with fallback logic. */
  def fallbackField(
      targetField: AttributeName,
      primaries: List[MappingExpression]
  ): MorphismComposer =
    copy(fieldMappings = fieldMappings :+
      FieldMapping(targetField, MappingExpression.Fallback(primaries)))

  /**
   * Build the mapping definition.
   */
  def build(): Either[CdmeError, MappingDefinition] = {
    if (sourceEntity.isEmpty || targetEntity.isEmpty) {
      Left(CompositionError("", "", "Source and target entities must be specified"))
    } else {
      Right(MappingDefinition(
        name = name,
        sourceEntity = sourceEntity,
        targetEntity = targetEntity,
        fieldMappings = fieldMappings,
        morphismPath = morphismPath
      ))
    }
  }
}

object MorphismComposer {
  def apply(name: String, sourceEntity: EntityId, targetEntity: EntityId): MorphismComposer =
    MorphismComposer(name, sourceEntity, targetEntity, Nil, Nil)
}
