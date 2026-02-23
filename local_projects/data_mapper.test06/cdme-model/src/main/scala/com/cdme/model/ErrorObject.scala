package com.cdme.model

import java.time.Instant

// Implements: REQ-TYP-03 (Error Domain Semantics), REQ-ERROR-01 (Minimal Error Object)
case class ErrorObject(
  failureType: String,
  offendingValues: Map[String, String],
  sourceEntity: String,
  sourceEpoch: String,
  morphismPath: List[String],
  constraintViolated: String,
  timestamp: Instant
)

object ErrorObject {
  def typeViolation(
    expected: SemanticType,
    actual: String,
    entity: String,
    epoch: String,
    path: List[String]
  ): ErrorObject = ErrorObject(
    failureType = "TYPE_VIOLATION",
    offendingValues = Map("expected" -> expected.toString, "actual" -> actual),
    sourceEntity = entity,
    sourceEpoch = epoch,
    morphismPath = path,
    constraintViolated = s"REQ-TYP-06: Type unification failed",
    timestamp = Instant.now()
  )

  def refinementViolation(
    predicateName: String,
    value: String,
    entity: String,
    epoch: String,
    path: List[String]
  ): ErrorObject = ErrorObject(
    failureType = "REFINEMENT_VIOLATION",
    offendingValues = Map("predicate" -> predicateName, "value" -> value),
    sourceEntity = entity,
    sourceEpoch = epoch,
    morphismPath = path,
    constraintViolated = s"REQ-TYP-02: Refinement predicate '$predicateName' failed",
    timestamp = Instant.now()
  )
}
