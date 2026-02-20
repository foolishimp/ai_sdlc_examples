package com.cdme.model.entity

// Implements: REQ-F-LDM-001, REQ-F-LDM-007

import com.cdme.model.grain.Grain
import com.cdme.model.types.CdmeType
import com.cdme.model.version.ArtifactVersion

/**
 * Unique identifier for an [[Entity]] within an LDM graph.
 *
 * @param value the string identifier (e.g. "trade", "counterparty")
 */
case class EntityId(value: String)

/**
 * A single typed attribute belonging to an [[Entity]].
 *
 * @param name     the attribute name (e.g. "trade_id", "notional")
 * @param cdmeType the CDME type of this attribute
 * @param nullable whether this attribute permits null values (default: false)
 */
case class Attribute(
  name: String,
  cdmeType: CdmeType,
  nullable: Boolean = false
)

/**
 * An Entity is a node in the LDM directed multigraph.
 *
 * Each entity represents a logical data concept (e.g. Trade, Counterparty,
 * RiskFactor) with a defined grain, typed attributes, optional RBAC, and
 * version metadata.
 *
 * @param id         unique identifier within the graph
 * @param name       human-readable entity name
 * @param grain      the level of detail (granularity) of this entity
 * @param attributes ordered list of typed attributes
 * @param acl        optional access control list restricting which roles may traverse morphisms touching this entity
 * @param version    artifact version metadata for audit and reproducibility
 */
case class Entity(
  id: EntityId,
  name: String,
  grain: Grain,
  attributes: List[Attribute],
  acl: Option[AccessControlList] = None,
  version: ArtifactVersion
)

/**
 * An access control list defining which roles are permitted to access
 * an [[Entity]] or traverse a morphism.
 *
 * @param allowedRoles the set of roles that have access
 */
case class AccessControlList(allowedRoles: Set[Role])

/**
 * A named security role used in RBAC access control.
 *
 * @param name the role name (e.g. "risk_analyst", "data_engineer")
 */
case class Role(name: String)
