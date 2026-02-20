package com.cdme.model.types

// Implements: REQ-F-TYP-002

/**
 * A predicate constrains the values of a [[RefinementType]].
 *
 * Predicates are checked at runtime by the Error Router. When a value
 * fails a predicate check, a [[com.cdme.model.error.RefinementViolation]]
 * is emitted and routed to the Error Domain sink.
 *
 * The sealed hierarchy ensures exhaustive pattern matching in validators.
 */
sealed trait Predicate

/**
 * Value must be strictly greater than the given bound.
 *
 * @param value the exclusive lower bound
 */
case class GreaterThan(value: Any) extends Predicate

/**
 * Value must be strictly less than the given bound.
 *
 * @param value the exclusive upper bound
 */
case class LessThan(value: Any) extends Predicate

/**
 * Value must fall within the closed interval [min, max].
 *
 * @param min the inclusive lower bound
 * @param max the inclusive upper bound
 */
case class InRange(min: Any, max: Any) extends Predicate

/**
 * String value must match the given regular expression pattern.
 *
 * @param pattern a Java-compatible regular expression
 */
case class MatchesRegex(pattern: String) extends Predicate

/**
 * Collection or string value must be non-empty.
 */
case class NonEmpty() extends Predicate

/**
 * A user-defined predicate with a custom check function.
 *
 * Note: because `check` is a function, instances of [[CustomPredicate]]
 * are not serializable by default. Use named predicates where possible
 * for cross-boundary transport.
 *
 * @param name  human-readable name for the predicate (used in error messages)
 * @param check the predicate function returning true if the value is valid
 */
case class CustomPredicate(name: String, check: Any => Boolean) extends Predicate
