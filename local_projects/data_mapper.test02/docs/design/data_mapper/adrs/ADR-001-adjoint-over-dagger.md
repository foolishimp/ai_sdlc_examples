# ADR-001: Adjoint (Galois Connection) over Dagger Category

**Status**: Proposed
**Date**: 2025-12-10
**Deciders**: [TBD]
**Implements**: REQ-ADJ-01, REQ-ADJ-02

---

## Context

CDME requires bidirectional transformations to support:
- Data reconciliation
- Impact analysis
- Bidirectional sync

Two mathematical frameworks were considered:
1. **Dagger Categories**: Strict inverses where `f† = f⁻¹`
2. **Adjoint Functors / Galois Connections**: Approximate inverses with containment bounds

---

## Decision

**Use Adjoint (Galois Connection) over Dagger Category**

Every morphism implements the `Adjoint<T, U>` interface with round-trip containment laws:
- `backward(forward(x)) ⊇ x` (lower closure)
- `forward(backward(y)) ⊆ y` (upper closure)

---

## Rationale

| Aspect | Dagger | Adjoint |
|--------|--------|---------|
| Lossy operations | Requires synthetic workarounds | Naturally models information loss |
| Round-trip | Exact: `f†(f(x)) = x` | Containment: `f⁻(f(x)) ⊇ x` |
| Aggregations | Cannot express "all contributing records" | Expresses superset of inputs |
| Filters | Cannot express "passed + filtered out" | Expresses union |
| Isomorphisms | Special case | Self-adjoint morphism |

Daggers are a **special case** of adjoints (self-adjoint morphisms).

---

## Consequences

### Positive
- Naturally handles lossy operations (aggregations, filters)
- Isomorphisms work as expected (exact inverses)
- Compositional: `(g ∘ f)⁻ = f⁻ ∘ g⁻`
- Enables impact analysis and reconciliation

### Negative
- More complex interface than simple inverses
- Requires classification of each morphism
- Storage overhead for containment metadata

---

## Alternatives Considered

1. **Dagger Category Only**: Rejected because data pipelines are inherently lossy
2. **No Backward Support**: Rejected because reconciliation/impact analysis are key requirements

---

## References

- [Galois Connections](https://en.wikipedia.org/wiki/Galois_connection)
- [Adjoint Functors](https://en.wikipedia.org/wiki/Adjoint_functors)
- INT-005: Adjoint Morphisms for Reverse Transformations
