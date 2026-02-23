package com.cdme.spark

import com.cdme.model.Entity

// Implements: REQ-PDM-01 (Functorial Mapping — LDM → PDM)
trait SparkFunctor {
  def bind(entity: Entity, physical: PhysicalBinding): SparkSource
  def rebind(entity: Entity, newPhysical: PhysicalBinding): SparkSource
}

case class SparkSource(
  entityName: String,
  binding: PhysicalBinding,
  schema: Map[String, String]
)

class DefaultSparkFunctor extends SparkFunctor {
  override def bind(entity: Entity, physical: PhysicalBinding): SparkSource =
    SparkSource(
      entityName = entity.name,
      binding = physical,
      schema = entity.attributes.map { case (k, v) => k -> v.toString }
    )

  override def rebind(entity: Entity, newPhysical: PhysicalBinding): SparkSource =
    bind(entity, newPhysical)
}
