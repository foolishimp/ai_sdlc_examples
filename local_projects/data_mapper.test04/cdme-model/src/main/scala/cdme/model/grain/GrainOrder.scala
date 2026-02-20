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
 * @param orderedLevels grain levels from finest to coarsest
 */
final case class GrainOrder(orderedLevels: Vector[Grain]) {

  private val indexMap: Map[Grain, Int] =
    orderedLevels.zipWithIndex.map { case (g, i) => (g, i) }.toMap

  def isFinerThan(a: Grain, b: Grain): Boolean =
    (indexMap.get(a), indexMap.get(b)) match {
      case (Some(ia), Some(ib)) => ia < ib
      case _                    => false
    }

  def isCoarserOrEqual(a: Grain, b: Grain): Boolean =
    (indexMap.get(a), indexMap.get(b)) match {
      case (Some(ia), Some(ib)) => ia >= ib
      case _                    => false
    }

  def areComparable(a: Grain, b: Grain): Boolean =
    indexMap.contains(a) && indexMap.contains(b)

  def areSameLevel(a: Grain, b: Grain): Boolean = a == b

  def validateAggregation(source: Grain, target: Grain): Either[ValidationError, Unit] =
    if (!areComparable(source, target))
      Left(ValidationError.GrainViolation(
        s"Grains ${source.show} and ${target.show} are not comparable in this order"
      ))
    else if (isFinerThan(source, target) || areSameLevel(source, target))
      Right(())
    else
      Left(ValidationError.GrainViolation(
        s"Cannot aggregate from coarser ${source.show} to finer ${target.show}"
      ))
}

object GrainOrder {
  val standardTemporal: GrainOrder = GrainOrder(Vector(
    Grain.Atomic,
    Grain.Daily,
    Grain.Monthly,
    Grain.Quarterly,
    Grain.Yearly
  ))
}
