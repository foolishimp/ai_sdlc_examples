package com.cdme.adjoint

// Implements: REQ-ADJ-08 (Data Reconciliation via Adjoints)
case class ReconciliationResult[T](
  original: Set[T],
  roundTripped: Set[T],
  missing: Set[T],
  extra: Set[T],
  isExact: Boolean
)

object ReconciliationEngine {
  /** Reconcile: compute backward(forward(x)) and compare with x */
  def reconcile[T, U](adjoint: Adjoint[T, U], inputs: Set[T]): ReconciliationResult[T] = {
    val forwardResults = inputs.map(adjoint.forward)
    val roundTripped = forwardResults.map(adjoint.backward)
    val missing = inputs -- roundTripped
    val extra = roundTripped -- inputs
    ReconciliationResult(
      original = inputs,
      roundTripped = roundTripped,
      missing = missing,
      extra = extra,
      isExact = missing.isEmpty && extra.isEmpty
    )
  }
}
