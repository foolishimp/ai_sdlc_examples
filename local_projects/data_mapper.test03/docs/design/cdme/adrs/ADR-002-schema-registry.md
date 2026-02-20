# ADR-002: Schema Registry as Single Source of Truth for LDM

**Status**: Accepted  
**Date**: 2026-02-20

## Context

CDME operates on Logical Data Models (LDMs) that define entity types, relationships, and constraints. Multiple components need consistent access to schema definitions: the mapping compiler, validation layer, execution engine, and accuracy checker. Without a centralised authority, schema drift and inconsistency become inevitable.

Two approaches were evaluated: distributed schema files co-located with each component, or a centralised Schema Registry that all components query.

## Decision

Adopt a **centralised Schema Registry** as the single source of truth for all Logical Data Model definitions. All components resolve schemas by querying the registry. Physical Data Model (PDM) bindings reference registry-managed LDM schemas.

## Rationale

- Single point of validation — schema correctness is checked once at registration, not at each consumer.
- Topology truth — the registry defines the canonical set of entities, attributes, and relationships. No component can invent or shadow schema elements.
- Version management — schema evolution (additive fields, deprecations) is tracked centrally with compatibility checks.
- PDM bindings become declarative references to registry-managed LDM types rather than duplicated definitions.

## Alternatives Considered

| Alternative | Why rejected |
|-------------|-------------|
| Co-located schema files per component | Schema drift across components; no single source of truth |
| Convention-based naming without registry | Fragile; no validation; silent breakage on rename |
| External catalog (e.g., Apache Atlas) | Heavyweight dependency; CDME needs tight integration with its type system |

## Consequences

**Positive**:
- All components share identical, validated schema definitions
- Schema evolution is managed and auditable in one place
- PDM-to-LDM binding validation is straightforward — check PDM references against registry

**Negative**:
- Registry becomes a critical dependency — must be available at compile time and runtime
- Adds an infrastructure component that must be operated and maintained

## Requirements Addressed

- **REQ-F-LDM-001**: Logical Data Model entity and attribute definition
- **REQ-F-LDM-002**: Relationship and constraint representation in LDM
- **REQ-F-LDM-003**: Schema validation and consistency enforcement
- **REQ-F-PDM-001**: Physical Data Model binding to LDM types
