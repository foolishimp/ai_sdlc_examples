package com.cdme.model.adjoint

import com.cdme.model.entity.Entity

/**
 * Implements: REQ-ADJ-01
 * A Galois Connection between two entities.
 */
trait Adjoint[A, B] {
  def forward(a: A): Either[String, B]
  def backward(b: B): Set[A]

  // Law: backward(forward(a)) contains a
  def verifyLowerAdjoint(a: A): Boolean = {
    forward(a).map(b => backward(b).contains(a)).getOrElse(true)
  }
}
