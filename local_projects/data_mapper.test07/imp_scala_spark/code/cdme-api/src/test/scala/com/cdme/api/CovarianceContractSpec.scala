// Validates: REQ-COV-01, REQ-COV-02
// Tests for covariance contract builder.
package com.cdme.api

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import com.cdme.model.grain.Grain

class CovarianceContractSpec extends AnyFlatSpec with Matchers {

  "CovarianceContractBuilder" should "build a valid contract" in {
    val result = CovarianceContractBuilder("cov-001")
      .withDomains("source_domain", "target_domain")
      .withInvariant(FidelityInvariant.Conservation("sum(amount)", Grain.Daily))
      .withEnforcementMode(EnforcementMode.Strict)
      .build()

    result.isRight shouldBe true
    val contract = result.toOption.get
    contract.id shouldBe "cov-001"
    contract.sourceDomain shouldBe "source_domain"
    contract.targetDomain shouldBe "target_domain"
    contract.invariants should have size 1
    contract.enforcementMode shouldBe EnforcementMode.Strict
  }

  it should "support multiple invariants" in {
    val result = CovarianceContractBuilder("cov-002")
      .withDomains("src", "tgt")
      .withInvariant(FidelityInvariant.Conservation("sum(amount)", Grain.Daily))
      .withInvariant(FidelityInvariant.Coverage("count(*)", Grain.Daily))
      .withInvariant(FidelityInvariant.Alignment("date", Grain.Daily))
      .withInvariant(FidelityInvariant.Containment("keys", Grain.Atomic))
      .build()

    result.isRight shouldBe true
    result.toOption.get.invariants should have size 4
  }

  it should "reject missing domains" in {
    val result = CovarianceContractBuilder("bad")
      .withInvariant(FidelityInvariant.Conservation("sum", Grain.Daily))
      .build()

    result.isLeft shouldBe true
  }

  it should "support enforcement modes" in {
    val modes = List(EnforcementMode.Strict, EnforcementMode.Deferred, EnforcementMode.Advisory)
    modes.foreach { mode =>
      val result = CovarianceContractBuilder("test")
        .withDomains("src", "tgt")
        .withEnforcementMode(mode)
        .build()

      result.isRight shouldBe true
      result.toOption.get.enforcementMode shouldBe mode
    }
  }

  it should "support materiality threshold" in {
    val result = CovarianceContractBuilder("mat")
      .withDomains("src", "tgt")
      .withMaterialityThreshold(MaterialityThreshold(Some(0.01), Some(0.001)))
      .build()

    result.isRight shouldBe true
    result.toOption.get.materialityThreshold shouldBe defined
  }

  "FidelityInvariant" should "have four variants" in {
    val invariants: Set[FidelityInvariant] = Set(
      FidelityInvariant.Conservation("sum", Grain.Daily),
      FidelityInvariant.Coverage("count", Grain.Daily),
      FidelityInvariant.Alignment("field", Grain.Daily),
      FidelityInvariant.Containment("keys", Grain.Atomic)
    )
    invariants should have size 4
  }

  "ViolationResponse" should "have three variants" in {
    val responses: Set[ViolationResponse] = Set(
      ViolationResponse.Fail,
      ViolationResponse.Warn,
      ViolationResponse.Log
    )
    responses should have size 3
  }
}
