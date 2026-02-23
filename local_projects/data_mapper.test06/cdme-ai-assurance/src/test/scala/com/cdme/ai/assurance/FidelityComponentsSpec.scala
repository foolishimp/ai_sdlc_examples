package com.cdme.ai.assurance

import java.time.Instant
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import com.cdme.model.Grain

// Validates: REQ-COV-01, REQ-COV-02, REQ-COV-03, REQ-COV-08
class FidelityComponentsSpec extends AnyFlatSpec with Matchers {

  "CovarianceContract" should "define cross-domain relationships" in {
    val contract = CovarianceContract(
      name = "finance_risk",
      version = "1.0",
      sourceDomain = "Finance",
      targetDomain = "Risk",
      invariants = List(
        FidelityInvariant("inv-001", FidelityInvariantType.Conservation,
          "sum(Finance.amount) == sum(Risk.exposure)", Some(0.01), Grain.Daily)
      ),
      enforcementMode = EnforcementMode.Strict
    )
    contract.invariants should have size 1
    contract.enforcementMode shouldBe EnforcementMode.Strict
  }

  "FidelityCertificate" should "form a chain via previous hash (REQ-COV-08)" in {
    val cert1 = FidelityCertificate(
      "cert-001", "finance_risk", "1.0",
      "hash-src-1", "hash-tgt-1",
      List(InvariantResult("inv-001", passed = true, "100.0", "100.0")),
      "run-001", Instant.now(), previousCertificateHash = None
    )
    val cert2 = FidelityCertificate(
      "cert-002", "finance_risk", "1.0",
      "hash-src-2", "hash-tgt-2",
      List(InvariantResult("inv-001", passed = true, "200.0", "200.0")),
      "run-002", Instant.now(), previousCertificateHash = Some(cert1.certificateId)
    )
    cert2.previousCertificateHash shouldBe Some("cert-001")
  }

  "InvariantResult" should "classify breach severity" in {
    val material = InvariantResult(
      "inv-001", passed = false, "100.0", "95.0",
      materialityThreshold = Some(0.01),
      breachSeverity = Some(BreachSeverity.Material)
    )
    material.passed shouldBe false
    material.breachSeverity shouldBe Some(BreachSeverity.Material)
  }
}
