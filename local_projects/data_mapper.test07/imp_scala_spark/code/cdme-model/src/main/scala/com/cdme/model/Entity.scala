// Implements: REQ-LDM-01, REQ-LDM-06
// Entity: a typed product at a specific grain in the logical data model.
package com.cdme.model

import com.cdme.model.grain.Grain
import com.cdme.model.types.CdmeType

/**
 * An Entity is a typed product (set of named, typed attributes) at a specific grain.
 * Each entity has an identity morphism (Id_E: E -> E).
 *
 * Implements: REQ-LDM-01, REQ-LDM-06
 */
final case class Entity(
    id: EntityId,
    name: String,
    attributes: Map[AttributeName, CdmeType],
    grain: Grain,
    identityMorphismId: MorphismId,
    accessControl: AccessControl
)
