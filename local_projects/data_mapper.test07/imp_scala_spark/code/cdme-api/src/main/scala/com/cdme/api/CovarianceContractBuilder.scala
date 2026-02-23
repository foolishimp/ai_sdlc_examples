// Implements: REQ-COV-01, REQ-COV-02, REQ-COV-04, REQ-COV-05, REQ-COV-06
// Covariance contract builder for cross-domain fidelity.
package com.cdme.api

import com.cdme.model.error.{CdmeError, ValidationError}
import com.cdme.model.grain.Grain

/**
 * Builder for covariance contracts: cross-domain fidelity invariants.
 * Implements: REQ-COV-01, REQ-COV-02
 */
final case class CovarianceContractBuilder(
    id: String,
    sourceDomain: Option[String],
    targetDomain: Option[String],
    invariants: List[FidelityInvariant],
    enforcementMode: EnforcementMode,
    materialityThreshold: Option[MaterialityThreshold]
) {

  def withDomains(source: String, target: String): CovarianceContractBuilder =
    copy(sourceDomain = Some(source), targetDomain = Some(target))

  def withInvariant(invariant: FidelityInvariant): CovarianceContractBuilder =
    copy(invariants = invariants :+ invariant)

  def withEnforcementMode(mode: EnforcementMode): CovarianceContractBuilder =
    copy(enforcementMode = mode)

  def withMaterialityThreshold(threshold: MaterialityThreshold): CovarianceContractBuilder =
    copy(materialityThreshold = Some(threshold))

  def build(): Either[CdmeError, CovarianceContractDefinition] = {
    (sourceDomain, targetDomain) match {
      case (Some(src), Some(tgt)) =>
        Right(CovarianceContractDefinition(
          id = id,
          sourceDomain = src,
          targetDomain = tgt,
          invariants = invariants,
          enforcementMode = enforcementMode,
          materialityThreshold = materialityThreshold
        ))
      case _ =>
        Left(ValidationError("Source and target domains are required"))
    }
  }
}

object CovarianceContractBuilder {
  def apply(id: String): CovarianceContractBuilder =
    CovarianceContractBuilder(id, None, None, Nil, EnforcementMode.Strict, None)
}

/** Built covariance contract. */
final case class CovarianceContractDefinition(
    id: String,
    sourceDomain: String,
    targetDomain: String,
    invariants: List[FidelityInvariant],
    enforcementMode: EnforcementMode,
    materialityThreshold: Option[MaterialityThreshold]
)

/** Fidelity invariant types. Implements: REQ-COV-02 */
sealed trait FidelityInvariant
object FidelityInvariant {
  final case class Conservation(expr: String, grain: Grain)  extends FidelityInvariant
  final case class Coverage(expr: String, grain: Grain)      extends FidelityInvariant
  final case class Alignment(field: String, grain: Grain)    extends FidelityInvariant
  final case class Containment(expr: String, grain: Grain)   extends FidelityInvariant
}

/** Enforcement mode. Implements: REQ-COV-07 */
sealed trait EnforcementMode
object EnforcementMode {
  case object Strict   extends EnforcementMode
  case object Deferred extends EnforcementMode
  case object Advisory extends EnforcementMode
}

/** Materiality threshold for fidelity checks. */
final case class MaterialityThreshold(
    absoluteTolerance: Option[Double],
    relativeTolerance: Option[Double]
)

/** Volume threshold for data quality. Implements: REQ-DQ-01 */
final case class VolumeThreshold(
    minRecords: Option[Long],
    maxRecords: Option[Long],
    baselineWindow: Option[Int],
    violationResponse: ViolationResponse
)

/** Response to a volume threshold violation. */
sealed trait ViolationResponse
object ViolationResponse {
  case object Fail extends ViolationResponse
  case object Warn extends ViolationResponse
  case object Log  extends ViolationResponse
}

/** Distribution monitor config. Implements: REQ-DQ-02 */
final case class DistributionMonitorConfig(
    fields: List[String],
    nullRateThreshold: Option[Double],
    cardinalityThreshold: Option[Double],
    divergenceThreshold: Option[Double]
)
