// Validates: REQ-LDM-05
// Tests for AccessControl role-based access checking.
package com.cdme.model

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class AccessControlSpec extends AnyFlatSpec with Matchers {

  "AccessControl.open" should "allow all principals" in {
    AccessControl.open.isAccessible(Set("admin", "user")) shouldBe true
    AccessControl.open.isAccessible(Set.empty) shouldBe true
  }

  "AccessControl with allowed roles" should "grant access to matching principals" in {
    val ac = AccessControl(allowedRoles = Set("admin", "analyst"), deniedRoles = Set.empty)
    ac.isAccessible(Set("admin")) shouldBe true
    ac.isAccessible(Set("analyst")) shouldBe true
    ac.isAccessible(Set("admin", "analyst")) shouldBe true
  }

  it should "deny access to non-matching principals" in {
    val ac = AccessControl(allowedRoles = Set("admin"), deniedRoles = Set.empty)
    ac.isAccessible(Set("user")) shouldBe false
    ac.isAccessible(Set.empty) shouldBe false
  }

  "AccessControl with denied roles" should "block denied principals" in {
    val ac = AccessControl(allowedRoles = Set.empty, deniedRoles = Set("external"))
    ac.isAccessible(Set("external")) shouldBe false
    ac.isAccessible(Set("internal", "external")) shouldBe false
  }

  it should "allow non-denied principals" in {
    val ac = AccessControl(allowedRoles = Set.empty, deniedRoles = Set("external"))
    ac.isAccessible(Set("internal")) shouldBe true
  }

  "AccessControl with both allowed and denied" should "deny takes precedence" in {
    val ac = AccessControl(allowedRoles = Set("admin"), deniedRoles = Set("admin"))
    ac.isAccessible(Set("admin")) shouldBe false
  }

  it should "allow if in allowed set and not in denied set" in {
    val ac = AccessControl(allowedRoles = Set("admin", "analyst"), deniedRoles = Set("external"))
    ac.isAccessible(Set("admin")) shouldBe true
    ac.isAccessible(Set("external")) shouldBe false
  }
}
