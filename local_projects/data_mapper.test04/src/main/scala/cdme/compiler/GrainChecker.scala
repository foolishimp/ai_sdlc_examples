// Implements: REQ-F-LDM-004, REQ-F-LDM-006, REQ-F-LDM-007, REQ-F-TRV-002, REQ-BR-LDM-001
package cdme.compiler

import cdme.model.grain.{Grain, GrainOrder}
import cdme.model.category.CardinalityType
import cdme.model.error.ValidationError

/**
 * Grain safety checker for path compilation.
 *
 * Enforces grain safety rules:
 *   - Operations mixing incompatible grains without aggregation are rejected
 *   - Coarser-to-finer aggregation is rejected
 *   - Multi-level aggregation is supported (finer to coarser only)
 *
 * The grain checker validates each transition in a path independently,
 * as multi-level aggregation is implemented as composition of single-level
 * aggregation morphisms.
 */
object GrainChecker:

  /**
   * Check whether a grain transition at a specific morphism is valid.
   *
   * @param sourceGrain the grain of the source entity
   * @param targetGrain the grain of the target entity
   * @param cardinality the cardinality of the morphism
   * @param order       the grain ordering
   * @return Right(()) if valid, Left(error) if the transition is unsafe
   */
  def checkTransition(
      sourceGrain: Grain,
      targetGrain: Grain,
      cardinality: CardinalityType,
      order: GrainOrder
  ): Either[ValidationError, Unit] =
    if order.areSameLevel(sourceGrain, targetGrain) then
      // Same grain level: always safe
      Right(())
    else if !order.areComparable(sourceGrain, targetGrain) then
      Left(ValidationError.GrainViolation(
        s"Grains ${sourceGrain.show} and ${targetGrain.show} are not comparable"
      ))
    else if order.isFinerThan(sourceGrain, targetGrain) then
      // Finer to coarser: aggregation required
      cardinality match
        case CardinalityType.ManyToOne =>
          // Aggregation morphism: this is the correct way to go finer -> coarser
          Right(())
        case _ =>
          Left(ValidationError.GrainViolation(
            s"Grain transition from ${sourceGrain.show} (finer) to ${targetGrain.show} (coarser) " +
            s"requires an aggregation morphism (ManyToOne), but got $cardinality"
          ))
    else
      // Coarser to finer: always rejected for aggregation
      cardinality match
        case CardinalityType.OneToMany =>
          // Expansion from coarser to finer is allowed (e.g., lookup)
          Right(())
        case CardinalityType.ManyToOne =>
          Left(ValidationError.GrainViolation(
            s"Cannot aggregate from coarser ${sourceGrain.show} to finer ${targetGrain.show}"
          ))
        case _ =>
          // Other cardinalities at different grain levels need explicit handling
          Right(())

  /**
   * Validate an entire path for grain safety.
   *
   * @param transitions list of (sourceGrain, targetGrain, cardinality) for each step
   * @param order       the grain ordering
   * @return list of grain violations (empty if all transitions are safe)
   */
  def validatePath(
      transitions: List[(Grain, Grain, CardinalityType)],
      order: GrainOrder
  ): List[ValidationError] =
    transitions.flatMap { (src, tgt, card) =>
      checkTransition(src, tgt, card, order).left.toOption
    }
