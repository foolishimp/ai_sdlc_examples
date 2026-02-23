package com.cdme.spark

// Implements: REQ-PDM-06, REQ-DQ-01 through REQ-DQ-04

sealed trait LateArrivalStrategy
object LateArrivalStrategy {
  case object Reject extends LateArrivalStrategy
  case object Reprocess extends LateArrivalStrategy
  case object Accumulate extends LateArrivalStrategy
  case object Backfill extends LateArrivalStrategy
}

case class VolumeThreshold(
  minRecords: Option[Long] = None,
  maxRecords: Option[Long] = None,
  baselineWindow: Int = 7,
  action: ThresholdAction = ThresholdAction.Warn
)

sealed trait ThresholdAction
object ThresholdAction {
  case object Warn extends ThresholdAction
  case object Halt extends ThresholdAction
  case object Quarantine extends ThresholdAction
}

case class CustomValidationRule(
  id: String,
  description: String,
  predicate: String,
  severity: ValidationSeverity = ValidationSeverity.Error,
  errorMessageTemplate: String
)

sealed trait ValidationSeverity
object ValidationSeverity {
  case object Error extends ValidationSeverity
  case object Warning extends ValidationSeverity
}

case class DistributionBaseline(
  fieldName: String,
  nullRateThreshold: Double = 0.1,
  cardinalityThreshold: Double = 0.2,
  divergenceThreshold: Double = 0.1
)
