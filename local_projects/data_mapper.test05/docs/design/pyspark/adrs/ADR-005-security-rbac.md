# ADR-005: RBAC on Morphisms

**Status**: Accepted
**Date**: 2026-02-21
**Deciders**: Architecture team
**Requirements Addressed**: REQ-F-LDM-006, REQ-BR-REG-003, REQ-BR-REG-004

---

## Context

The CDME must support Role-Based Access Control (RBAC) on individual morphisms (REQ-F-LDM-006). A denied morphism must effectively not exist for the requesting principal -- preventing both data access and path construction through it. This must be enforced at validation time (compile time), not only at runtime.

Additionally, GDPR/CCPA compliance (REQ-BR-REG-003) requires that data access can be controlled and audited, and the EU AI Act (REQ-BR-REG-004) requires human oversight of AI-generated mappings.

The design must determine:
- Where access control is enforced in the processing pipeline
- How roles are associated with morphisms
- How the access control model integrates with the topological compiler

## Decision

Implement RBAC as a **graph-filtering operation** applied before compilation. The `AccessFilter` component produces a filtered view of the category where denied morphisms are removed. The compiler then operates on this filtered view, naturally preventing path construction through denied morphisms.

Roles are stored as `frozenset[str]` on each `Morphism`. An empty set means unrestricted access. A `Principal` carries its roles. The filter is a pure function: `filter_category(category, principal) -> category`.

## Rationale

- **Graph-level enforcement**: By filtering morphisms before they reach the compiler, the design guarantees that denied morphisms cannot be used in any path, expression, or plan. The compiler never sees them. This is stronger than runtime checks that might be bypassed.
- **Simplicity**: The filter is a pure function that removes morphisms. No changes to the compiler, executor, or any downstream component are needed. The compiler's existing path validation naturally rejects paths through removed morphisms.
- **Auditability**: The filtered category can be logged/stored for audit purposes. A reviewer can see exactly which morphisms were available to a given principal.
- **Separation of concerns**: Access control is a separate concern from compilation. The `AccessFilter` is in `cdme.model.access`, not in `cdme.compiler`. This keeps the compiler focused on topological validation.
- **GDPR alignment**: The same filtering mechanism supports data privacy -- morphisms leading to sensitive entities can be restricted to authorized roles. Combined with deterministic key generation (REQ-F-SYN-007), this enables targeted data access control.

## Alternatives Considered

### Alternative 1: Runtime Enforcement Only

- **Pros**: Simpler implementation. All morphisms visible in the graph for documentation/discovery. Access checked at execution time.
- **Cons**: Violates REQ-F-LDM-006 which requires compile-time enforcement. A denied morphism could appear in a compiled plan and fail at runtime, wasting resources. Does not prevent path construction through denied morphisms.
- **Rejected because**: The requirement explicitly states that access control must be enforced at compile time (path validation), not only at runtime. Runtime-only enforcement would allow plans to be constructed that cannot be executed.

### Alternative 2: Aspect-Oriented Access Checks (Decorator Pattern)

- **Pros**: Flexible. Can be applied per-morphism at execution time. Does not modify the graph.
- **Cons**: Does not prevent path construction through denied morphisms. The compiler would construct paths that include denied morphisms, then the decorator would reject them at execution. This is a runtime check, not a compile-time check.
- **Rejected because**: The requirement states that a denied morphism must "effectively not exist" for the requesting principal. A decorator-based approach still allows the morphism to exist in the graph and appear in compiled plans.

## Consequences

### Positive
- Compile-time enforcement: denied morphisms are invisible to the compiler
- No changes needed to compiler, executor, or other downstream components
- Pure function implementation: easy to test, easy to audit
- Filtered category can be serialized for audit trails
- Natural integration with GDPR/CCPA data access controls

### Negative
- The filtered category is a copy of the original (memory overhead for large graphs)
- Role changes require recompilation of plans (no dynamic role updates mid-execution)
- The filter must be applied consistently at all entry points -- missing a filter call would bypass access control. The API layer must enforce this.
- No fine-grained attribute-level access control -- only morphism-level. If attribute-level control is needed, morphisms must be structured to separate controlled attributes.
