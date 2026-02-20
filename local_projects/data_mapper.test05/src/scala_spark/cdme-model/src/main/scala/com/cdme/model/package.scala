package com.cdme

/**
 * Root package object for the CDME domain model.
 *
 * Provides type aliases and convenience imports for the most commonly
 * used types across the model. Import `com.cdme.model._` to bring
 * all aliases into scope.
 */
package object model {

  // --- Identity types ---
  type EntityId   = entity.EntityId
  type MorphismId = morphism.MorphismId

  // --- Core domain ---
  type Entity      = entity.Entity
  type Attribute   = entity.Attribute
  type Morphism    = morphism.Morphism
  type LdmGraph    = graph.LdmGraph
  type PdmBinding  = pdm.PdmBinding

  // --- Type system ---
  type CdmeType = types.CdmeType
  type Predicate = types.Predicate

  // --- Grain ---
  type Grain = grain.Grain

  // --- Error domain ---
  type CdmeError = error.CdmeError
  type Principal = error.Principal

  // --- Configuration ---
  type Epoch           = config.Epoch
  type JobConfig       = config.JobConfig
  type ArtifactVersion = version.ArtifactVersion

  // --- Constructors for identity types ---
  val EntityId: entity.EntityId.type     = entity.EntityId
  val MorphismId: morphism.MorphismId.type = morphism.MorphismId
}
