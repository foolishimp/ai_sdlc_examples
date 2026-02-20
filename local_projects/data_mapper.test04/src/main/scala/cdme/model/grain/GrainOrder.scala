// Implements: REQ-F-LDM-004, REQ-F-LDM-006, REQ-F-LDM-007, REQ-BR-LDM-001
package cdme.model.grain

import cdme.model.error.ValidationError

/**
 * Partial order over grain levels.
 *
 * Defines the granularity ordering for a specific LDM. Grain ordering is
 * configurable (not hardcoded) -- different LDMs may have different grain
 * hierarchies.
 *
 * The order is used by the GrainChecker (compiler) to validate grain safety:
 * operations mixing incompatible grains without aggregation are rejected
 * (REQ-BR-LDM-001).
 *
 * @param orderedLevels grain levels from finest to coarsest
 */
final case class GrainOrder(orderedLevels: Vector[Grain]):

  private val indexMap: Map[Grain, Int] =
    orderedLevels.zipWithIndex.map((g, i) => (g, i)).toMap

  /**
   * Check whether grain `a` is strictly finer than grain `b`.
   *
   * @param a first grain level
   * @param b second grain level
   * @return true if a is finer than b in this ordering
   */
  def isFinerThan(a: Grain, b: Grain): Boolean =
    (indexMap.get(a), indexMap.get(b)) match
      case (Some(ia), Some(ib)) => ia < ib
      case _                    => false

  /**
   * Check whether grain `a` is coarser than or equal to grain `b`.
   *
   * @param a first grain level
   * @param b second grain level
   * @return true if a is coarser than or equal to b
   */
  def isCoarserOrEqual(a: Grain, b: Grain): Boolean =
    (indexMap.get(a), indexMap.get(b)) match
      case (Some(ia), Some(ib)) => ia >= ib
      case _                    => false

  /**
   * Check whether two grains are comparable in this order.
   *
   * @param a first grain level
   * @param b second grain level
   * @return true if both grains are known to this order
   */
  def areComparable(a: Grain, b: Grain): Boolean =
    indexMap.contains(a) && indexMap.contains(b)

  /**
   * Check whether two grains are at the same level.
   *
   * @param a first grain level
   * @param b second grain level
   * @return true if both are the same grain level
   */
  def areSameLevel(a: Grain, b: Grain): Boolean = a == b

  /**
   * Validate that an aggregation from source grain to target grain is legal.
   * Aggregation from finer to coarser is allowed; coarser to finer is rejected.
   *
   * @param source the source (finer) grain
   * @param target the target (coarser) grain
   * @return Right(()) if valid, Left(error) if illegal
   */
  def validateAggregation(source: Grain, target: Grain): Either[ValidationError, Unit] =
    if !areComparable(source, target) then
      Left(ValidationError.GrainViolation(
        s"Grains ${source.show} and ${target.show} are not comparable in this order"
      ))
    else if isFinerThan(source, target) || areSameLevel(source, target) then
      Right(())
    else
      Left(ValidationError.GrainViolation(
        s"Cannot aggregate from coarser ${source.show} to finer ${target.show}"
      ))

object GrainOrder:
  /**
   * The standard temporal grain order: Atomic < Daily < Monthly < Quarterly < Yearly.
   */
  val standardTemporal: GrainOrder = GrainOrder(Vector(
    Grain.Atomic,
    Grain.Daily,
    Grain.Monthly,
    Grain.Quarterly,
    Grain.Yearly
  ))
