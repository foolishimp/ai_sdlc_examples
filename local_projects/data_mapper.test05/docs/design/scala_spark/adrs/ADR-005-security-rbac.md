# ADR-005: Role-Based Access Control on Morphisms

**Status**: Accepted
**Date**: 2026-02-21
**Requirements Addressed**: REQ-F-LDM-006, REQ-F-LDM-003, REQ-BR-REG-001, REQ-BR-REG-003

---

## Context

The CDME requires topological access control (REQ-F-LDM-006) where a denied morphism effectively does not exist for the requesting principal. This prevents both data access and path construction through restricted morphisms. The access control must be enforced at compile time during path validation, not only at runtime. Regulatory requirements (BCBS 239, GDPR) demand auditable data access controls.

The project constraints specify `rbac` as the authorisation model with no authentication layer (authentication is assumed to be handled externally).

## Decision

Implement **Role-Based Access Control (RBAC) at the morphism level**:

- Each morphism carries an optional `AccessControlList` (ACL) specifying permitted roles
- The `AccessControlChecker` in `cdme-compiler` filters the LDM graph to remove morphisms the principal cannot traverse before path validation
- A principal with insufficient permissions sees a reduced graph where denied morphisms do not exist
- Access control is evaluated at compile time as part of `TopologicalCompiler.compile()`

## Rationale

- Morphism-level RBAC aligns with the LDM's graph structure — access is a topological property of the graph, consistent with the Category Theory model
- Compile-time enforcement means invalid paths are rejected before execution, not caught at runtime when data has already been read
- RBAC is simpler to reason about than ABAC for a library/framework where the consuming application manages principal identity
- Morphism-level granularity is sufficient: if a principal cannot traverse a morphism, they cannot reach the data on the other side (topological guarantee)
- GDPR compliance (REQ-BR-REG-003) benefits from deterministic key-based data subject identification combined with RBAC-controlled access paths

## Alternatives Considered

### Alternative 1: Attribute-Based Access Control (ABAC)

Rejected. ABAC evaluates policies based on attributes of the principal, resource, and environment at runtime. This conflicts with the compile-time enforcement requirement. ABAC's expressiveness is unnecessary when the access boundary is a graph edge (morphism) — RBAC on edges provides equivalent security for this topology. ABAC also requires a policy evaluation engine, adding runtime dependencies to the compiler.

### Alternative 2: Row-Level Security

Rejected as the primary mechanism. Row-level security operates at the data level, not the topology level. It cannot prevent a principal from constructing a path through a morphism — it only restricts which rows they see after the path is traversed. This violates REQ-F-LDM-006's requirement that a denied morphism "effectively does not exist." Row-level filtering may be implemented as a complementary mechanism in `cdme-spark` for data-level restrictions, but it does not replace topological RBAC.

## Consequences

**Positive**:
- Access control is a structural property of the graph, consistent with the formal model
- Compile-time enforcement gives fast, clear error messages ("path invalid: morphism X does not exist for role Y")
- The reduced-graph approach is elegant — the principal sees a valid sub-graph, and all normal validation applies to that sub-graph
- Audit trail is automatic: the compiled plan records the principal and the effective graph used

**Negative**:
- RBAC is coarser than ABAC — no support for contextual policies (e.g., "allow access only during business hours")
- The CDME does not handle principal identity or authentication — the consuming application must provide a `Principal` with resolved roles
- Role management (who has which roles) is outside the CDME's scope

**Mitigations**:
- Document that authentication and role resolution are the responsibility of the host application
- The `AccessControlList` format is extensible — future versions could add ABAC-like conditions if needed
- Row-level filtering in `cdme-spark` can complement morphism-level RBAC for data-level restrictions
