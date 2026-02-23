// Implements: REQ-COV-03, REQ-COV-08
// Fidelity certificates for cross-domain integrity proof.
package com.cdme.lineage

import com.cdme.model.RunId
import com.cdme.model.error.{CdmeError, ValidationError}

/**
 * A fidelity certificate: cryptographic proof of cross-domain data integrity.
 * Uses SHA-256 content hashing for tamper evidence.
 *
 * Implements: REQ-COV-03
 */
final case class FidelityCertificate(
    certificateId: String,
    previousCertificateHash: Option[String],
    sourceDomainHash: String,
    targetDomainHash: String,
    contractVersion: String,
    invariantResults: List[InvariantResult],
    timestamp: java.time.Instant,
    runId: RunId
) {
  /** Check if all invariants passed. */
  def allPassed: Boolean = invariantResults.forall(_.passed)

  /** Compute the hash of this certificate for chaining. */
  def computeHash: String = {
    val content = s"$certificateId|$sourceDomainHash|$targetDomainHash|$contractVersion|$timestamp|$runId"
    java.security.MessageDigest.getInstance("SHA-256")
      .digest(content.getBytes("UTF-8"))
      .map("%02x".format(_))
      .mkString
  }
}

/** Result of a single invariant check. */
final case class InvariantResult(
    invariantName: String,
    passed: Boolean,
    observedValue: String,
    expectedThreshold: String,
    detail: String
)

/**
 * Chain of fidelity certificates for audit trail.
 * Implements: REQ-COV-08
 */
final case class FidelityCertificateChain(
    certificates: List[FidelityCertificate],
    chainHash: String
) {
  /** Verify the chain integrity: each certificate references the previous hash. */
  def verifyChainIntegrity: Either[CdmeError, Unit] = {
    certificates.sliding(2).foreach {
      case List(prev, curr) =>
        if (curr.previousCertificateHash != Some(prev.computeHash)) {
          return Left(ValidationError(
            s"Chain integrity broken: certificate ${curr.certificateId} does not reference ${prev.certificateId}"
          ))
        }
      case _ => // single element, ok
    }
    Right(())
  }
}

object FidelityCertificateChain {
  /** Build a chain from a list of certificates. */
  def build(certificates: List[FidelityCertificate]): FidelityCertificateChain = {
    val chainContent = certificates.map(_.computeHash).mkString("|")
    val chainHash = java.security.MessageDigest.getInstance("SHA-256")
      .digest(chainContent.getBytes("UTF-8"))
      .map("%02x".format(_))
      .mkString
    FidelityCertificateChain(certificates, chainHash)
  }
}
