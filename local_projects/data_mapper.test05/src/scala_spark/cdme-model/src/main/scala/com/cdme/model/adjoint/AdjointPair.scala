package com.cdme.model.adjoint

// Implements: REQ-F-ADJ-001, REQ-F-ADJ-002, REQ-F-ADJ-003

import com.cdme.model.error.CdmeError

/**
 * An adjoint pair encapsulates the forward and backward functions
 * of a morphism, together with a [[ContainmentType]] declaring the
 * nature of the inverse relationship.
 *
 * Every morphism in CDME carries an adjoint pair to support bidirectional
 * traversal. Forward traversal computes new data; backward traversal
 * uses persisted metadata (never recomputation) to trace lineage.
 *
 * The containment type determines the guarantees on the backward function:
 *  - [[SelfAdjoint]]: backward is an exact inverse (`backward(forward(x)) == {x}`)
 *  - [[LossyContainment]]: backward returns a superset (`backward(forward(x)) supseteq {x}`)
 *
 * @tparam A the domain type
 * @tparam B the codomain type
 * @param forward         the forward function producing a value or an error
 * @param backward        the backward function returning the set of contributing domain values
 * @param containmentType declares whether the adjoint is exact or lossy
 */
case class AdjointPair[A, B](
  forward: A => Either[CdmeError, B],
  backward: B => Set[A],
  containmentType: ContainmentType
)

/**
 * Declares the nature of the inverse relationship in an [[AdjointPair]].
 *
 * The containment type drives checkpointing policy: lossy morphisms
 * require checkpoint persistence of key envelopes, while self-adjoint
 * morphisms can reconstruct lineage from the forward direction alone.
 */
sealed trait ContainmentType

/**
 * Self-adjoint (exact inverse): the backward function perfectly inverts
 * the forward function. Applies to 1:1 isomorphic transformations.
 *
 * Invariant: `backward(forward(x)) == Set(x)` for all valid x.
 */
case object SelfAdjoint extends ContainmentType

/**
 * Lossy containment: the backward function returns a superset of the
 * original inputs. Applies to N:1 aggregations, filters, and any
 * transformation that discards information.
 *
 * Invariant: `backward(forward(x)) supseteq Set(x)` for all valid x.
 *
 * Morphisms with lossy containment require key envelope checkpointing
 * to support precise backward traversal.
 */
case object LossyContainment extends ContainmentType
