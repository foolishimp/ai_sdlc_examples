package com.cdme.model.entity

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Validates: REQ-LDM-01
 */
class EntitySpec extends AnyFlatSpec with Matchers {
  "An Entity" should "have an identity" in {
    val e = Entity("E1", "Test Entity", "Atomic")
    e.id should be("E1")
  }
}
