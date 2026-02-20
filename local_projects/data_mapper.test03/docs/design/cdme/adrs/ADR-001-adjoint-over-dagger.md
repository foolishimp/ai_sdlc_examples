# ADR-001: Use Adjoint (Galois Connection) over Dagger Category for Reverse Transformations

**Status**: Accepted  
**Date**: 2026-02-20

## Context

CDME must support reverse (target-to-source) transformations for data reconciliation and accuracy checking. Two categorical constructs were considered:

- **Dagger Category (†)**: Every morphism `f : A → B` has an exact inverse `f† : B → A` such that `f†† = f`. Requires lossless, bijective mappings.
- **Adjoint Functors (Galois Connection)**: A pair `(L ⊣ R)` where `L` is left adjoint to `R`, satisfying `L(a) ≤ b ⟺ a ≤ R(b)`. Handles lossy morphisms via containment bounds.

Real-world data mappings are routinely lossy — many-to-one aggregations, type coercions with precision loss, nullable-to-non-nullable projections. Exact inverses do not exist for these cases.

## Decision

Use **Adjoint Functors (Galois Connections)** as the foundation for reverse transformations in CDME. Forward mappings define the left adjoint `L`; reverse mappings define the right adjoint `R`. Accuracy is quantified by the unit (`η : Id → R∘L`) and counit (`ε : L∘R → Id`) natural transformations.

## Rationale

- Adjoints naturally express lossy round-trips: `R∘L` yields a *containment bound*, not an exact recovery.
- The unit/counit pair provides built-in accuracy metrics — the "gap" between `Id` and `R∘L` quantifies information loss.
- Daggers require exact inverses that simply do not exist for most real data transformations (aggregations, type narrowing, denormalisations).
- Galois connections are well-studied in lattice theory and data abstraction, providing proven mathematical foundations.

## Alternatives Considered

| Alternative | Why rejected |
|-------------|-------------|
| Dagger Category | Requires exact inverses; cannot model lossy transformations |
| Ad-hoc reverse functions | No formal guarantees, no composability, no accuracy metrics |
| Partial inverses without framework | Loses compositional structure and formal accuracy bounds |

## Consequences

**Positive**:
- Lossy transformations have formal, composable reverse mappings
- Accuracy metrics (unit/counit gaps) are derivable from the structure itself
- Composition of adjoints yields adjoints — scales to complex pipelines

**Negative**:
- Higher conceptual complexity for developers unfamiliar with category theory
- Adjoint pairs must be explicitly constructed for each mapping type

## Requirements Addressed

- **REQ-F-ACC-001**: Accuracy metric definition via unit/counit natural transformations
- **REQ-F-ACC-002**: Round-trip accuracy quantification (`R∘L` vs `Id`)
- **REQ-F-ACC-003**: Composable accuracy propagation through adjoint composition
- **REQ-F-ACC-004**: Accuracy bounds for lossy transformations via containment
- **REQ-F-ACC-005**: Formal accuracy reporting from Galois connection structure
