package com.cdme.spark

import com.cdme.runtime.GenerationGrain

// Implements: REQ-PDM-01 (Functorial Mapping), REQ-PDM-02, REQ-PDM-03

case class PhysicalBinding(
  storageType: StorageType,
  path: String,
  format: String,
  partitionKeys: List[String] = Nil,
  generationGrain: GenerationGrain,
  boundary: BoundaryDefinition
)

sealed trait StorageType
object StorageType {
  case object File extends StorageType
  case object Table extends StorageType
  case object Api extends StorageType
}

case class BoundaryDefinition(
  strategy: BoundaryStrategy,
  parameters: Map[String, String] = Map.empty
)

sealed trait BoundaryStrategy
object BoundaryStrategy {
  case object Temporal extends BoundaryStrategy
  case object Version extends BoundaryStrategy
  case object BatchId extends BoundaryStrategy
}

// Implements: REQ-PDM-04 (Lookup Binding)
case class LookupPhysicalBinding(
  lookupName: String,
  bindingType: LookupPhysicalType,
  path: Option[String] = None
)

sealed trait LookupPhysicalType
object LookupPhysicalType {
  case object DataBacked extends LookupPhysicalType
  case object LogicBacked extends LookupPhysicalType
}

// Implements: REQ-PDM-05 (Temporal Binding)
case class TemporalBinding(
  entityName: String,
  epochResolver: String,
  physicalTargets: Map[String, String]
)
