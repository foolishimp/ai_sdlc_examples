package com.cdme.ai.assurance

import java.time.Instant
import com.cdme.model.Grain

// Implements: REQ-COV-01 through REQ-COV-08

case class CovarianceContract(
  name: String,
  version: String,
  sourceDomain: String,
  targetDomain: String,
  invariants: List[FidelityInvariant],
  enforcementMode: EnforcementMode,
  propagationDirection: PropagationDirection = PropagationDirection.Forward
)

sealed trait FidelityInvariantType
object FidelityInvariantType {
  case object Conservation extends FidelityInvariantType
  case object Coverage extends FidelityInvariantType
  case object Alignment extends FidelityInvariantType
  case object Containment extends FidelityInvariantType
}

case class FidelityInvariant(
  id: String,
  invariantType: FidelityInvariantType,
  expression: String,
  materialityThreshold: Option[Double] = None,
  grain: Grain
)

sealed trait EnforcementMode
object EnforcementMode {
  case object Strict extends EnforcementMode
  case object Deferred extends EnforcementMode
  case object Advisory extends EnforcementMode
}

sealed trait PropagationDirection
object PropagationDirection {
  case object Forward extends PropagationDirection
  case object Backward extends PropagationDirection
  case object Bidirectional extends PropagationDirection
}

case class FidelityCertificate(
  certificateId: String,
  contractName: String,
  contractVersion: String,
  sourceStateHash: String,
  targetStateHash: String,
  invariantResults: List[InvariantResult],
  runId: String,
  timestamp: Instant,
  previousCertificateHash: Option[String] = None
)

case class InvariantResult(
  invariantId: String,
  passed: Boolean,
  expectedValue: String,
  actualValue: String,
  materialityThreshold: Option[Double] = None,
  breachSeverity: Option[BreachSeverity] = None
)

sealed trait BreachSeverity
object BreachSeverity {
  case object Immaterial extends BreachSeverity
  case object Material extends BreachSeverity
  case object Critical extends BreachSeverity
}

sealed trait BreachResponse
object BreachResponse {
  case object Alert extends BreachResponse
  case object Quarantine extends BreachResponse
  case object Halt extends BreachResponse
  case object Rollback extends BreachResponse
}
