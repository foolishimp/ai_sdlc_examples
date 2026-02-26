package com.cdme.model.adjoint

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Validates: REQ-ADJ-01
 */
class AdjointSpec extends AnyFlatSpec with Matchers {
  "An Adjoint" should "obey the lower adjoint law" in {
    val identityAdjoint = new Adjoint[String, String] {
      def forward(a: String) = Right(a)
      def backward(b: String) = Set(b)
    }
    identityAdjoint.verifyLowerAdjoint("test") should be(true)
  }
}
