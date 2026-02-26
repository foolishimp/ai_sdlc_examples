package com.cdme.model.morphism

import com.cdme.model.entity.Entity

/**
 * Implements: REQ-LDM-01, REQ-ADJ-01
 * An Arrow in the LDM Category.
 */
sealed trait Morphism[A, B] {
  def source: Entity
  def target: Entity
  def forward(a: A): Either[String, B]
  def backward(b: B): Set[A]
}
