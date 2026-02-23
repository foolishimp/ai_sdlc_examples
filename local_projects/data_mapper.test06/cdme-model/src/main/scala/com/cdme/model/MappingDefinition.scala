package com.cdme.model

// Implements: REQ-INT-01 through REQ-INT-08

case class MappingDefinition(
  name: String,
  version: String,
  sourceCategory: String,
  targetEntity: String,
  fieldMappings: List[FieldMapping],
  filters: List[FilterDefinition] = Nil,
  lookups: Map[String, LookupBinding] = Map.empty,
  costBudget: Option[CostBudget] = None
)

case class FieldMapping(
  targetField: String,
  expression: MappingExpression,
  targetType: SemanticType
)

sealed trait MappingExpression
object MappingExpression {
  case class Path(path: String) extends MappingExpression
  case class Function(fn: String, args: List[MappingExpression]) extends MappingExpression
  case class Aggregate(path: String, monoid: String) extends MappingExpression
  case class Conditional(
    condition: MappingExpression,
    ifTrue: MappingExpression,
    ifFalse: MappingExpression
  ) extends MappingExpression
  case class Literal(value: String, semanticType: SemanticType) extends MappingExpression
}

case class FilterDefinition(
  name: String,
  predicate: String
)

// Implements: REQ-INT-06 (Versioned Lookups)
case class LookupBinding(
  lookupName: String,
  version: String,
  bindingType: LookupBindingType
)

sealed trait LookupBindingType
object LookupBindingType {
  case object DataBacked extends LookupBindingType
  case object LogicBacked extends LookupBindingType
}

// Implements: REQ-TRV-06 (Cost Governance)
case class CostBudget(
  maxOutputRows: Long,
  maxJoinDepth: Int,
  maxIntermediateSize: Long
)

// Implements: REQ-LDM-05 (Access Control)
case class Principal(
  name: String,
  roles: Set[String]
)
