# Project Intent

**Project**: Categorical Data Mapping & Computation Engine (CDME)
**Date**: 2025-12-10
**Status**: Approved for Architecture Design
**Version**: 7.2

---

## INT-001: Categorical Data Mapping Engine

### Problem / Opportunity

Traditional ETL tools treat data as "buckets of bytes," creating an **impedance mismatch** between logical business requirements and physical data execution. This leads to:

- Pipelines that compile but produce semantically incorrect results
- Grain mixing errors that go undetected until production
- Type coercion bugs that silently corrupt data
- Lineage that cannot be reconstructed for regulatory audits
- AI-generated mappings that hallucinate non-existent relationships

**The Opportunity**: Build a data mapping and computation engine from first principles using **Category Theory** to provide mathematical guarantees that if a mapping definition is valid, the resulting pipeline is:
- Topologically correct
- Lineage is preserved
- Granularities are respected
- Types are enforced

### Expected Outcomes

- [ ] Schemas treated as Topologies (Categories of Objects and Morphisms)
- [ ] Transformations treated as Continuous Maps (Functors)
- [ ] Mathematical guarantee of pipeline correctness at definition time
- [ ] Full lineage traceability from target to source
- [ ] AI Assurance Layer preventing hallucinated mappings from executing
- [ ] Regulatory compliance (BCBS 239, FRTB, GDPR/CCPA, EU AI Act)

### Stakeholders

| Role | Name | Interest |
|------|------|----------|
| Data Architects | ER-Modelers | Logical Data Model design |
| Engineering Team | CT-Implementers | Category Theory implementation |
| Risk/Compliance | Auditors | BCBS 239, FRTB compliance |
| AI Teams | ML Engineers | Validated AI-generated pipelines |

### Constraints

- Must support distributed compute frameworks
- Functional paradigm with immutability for business logic
- Strongly typed internal representation
- OpenLineage API support for observability
- Must separate LDM (logical) from PDM (physical) concerns

---

## INT-002: Core Philosophy (The Axioms)

The system is built on 10 foundational axioms:

1. **Schema is a Topology** - Logical data model is a Category of Objects (Entities) and Morphisms (Relationships)
2. **Separation of Concerns** - LDM (What), PDM (Where), Binding (Functor mapping)
3. **Transformations are Paths** - Deriving a value = traversing a path and applying morphisms
4. **Data Has Contextual Extent** - Data exists within specific Context (Epoch/Batch) - sheaf-like constraint
5. **Grain is a Dimension** - Mixing grains without explicit aggregation is topologically forbidden
6. **Lookups are Arguments** - Reference data must be versioned and immutable within execution context
7. **Types are Contracts** - Semantic guarantees, not just storage formats
8. **Topology is the Guardrail** - Verification over Generation (reject AI hallucinations at definition)
9. **Calculations are Morphisms** - Any computation (even Monte Carlo) is a standard Morphism
10. **Failures are Data** - Errors are valid Objects in the Error Domain, not exceptions

---

## INT-003: Universal Applicability

While described as a "Data Mapper," this architecture applies equally to:

- **Cashflow Generators** - Trade → Cashflow morphisms
- **Regulatory Calculators** - Risk aggregation with grain safety
- **Risk Engines** - Monte Carlo simulations as morphisms
- **AI Pipeline Validation** - Deterministic compiler for AI-generated code

The topological constraints (Lineage, Typing, Sheaf Consistency) remain identical regardless of whether the morphism is a simple field rename or a complex simulation.

---

## INT-004: Strategic Value - AI Assurance Layer

As organizations adopt AI-generated pipelines, the CDME serves as:

- **Deterministic Compiler** - Validates AI-generated mappings before execution
- **Hallucination Prevention** - Rejects non-existent relationships, type violations, grain violations
- **Triangulation of Assurance** - Links Intent → Logic → Proof for real-time verification
- **EU AI Act Compliance** - Article 14 (Human Oversight) and Article 15 (Robustness)

---

## INT-005: Adjoint Morphisms for Reverse Transformations

### Problem / Opportunity

Current data pipelines are **unidirectional** - they transform source to target but cannot reverse the process. This creates challenges for:

- **Data Reconciliation** - Cannot verify outputs by reversing back to inputs
- **Impact Analysis** - Cannot compute which source records contributed to a target subset
- **Bidirectional Sync** - Requires maintaining two separate mappings that can drift
- **Schema Migration Rollback** - No principled way to undo migrations

**The Opportunity**: Extend the CDME with **Adjoint Morphisms** where every morphism `f: A → B` has a corresponding backward morphism `f⁻: B → A`, forming a **Galois Connection** that enables first-class reverse transformations.

### Core Concept: The Adjoint Interface

```typescript
interface Adjoint<T, U> {
  forward: T → U
  backward: U → T
  laws: compositionalConsistency
}
```

Every morphism is modeled as an **Adjoint pair** with round-trip containment laws:

```
backward(forward(x)) ⊇ x    // Lower adjoint: preserves or expands
forward(backward(y)) ⊆ y    // Upper adjoint: contracts or preserves
```

### Why Adjoints over Daggers

| Concept | Dagger Category | Adjoint/Galois Connection |
|---------|----------------|--------------------------|
| Inverse type | Strict inverse `f† = f⁻¹` | Approximate inverse with bounds |
| Lossy handling | Requires "synthetic" workarounds | Naturally models information loss |
| Composition | `(g ∘ f)† = f† ∘ g†` | `(g ∘ f)⁻ = f⁻ ∘ g⁻` (same!) |
| Round-trip | `f†(f(x)) = x` (exact) | `f⁻(f(x)) ⊇ x` (containment) |
| Self-adjoint | Special case: `f† = f` | Special case: `f⁻ = f` (isomorphism) |

Daggers are a **special case** of adjoints where the forward and backward form an exact inverse (self-adjoint morphism).

### Adjoint Strategies by Morphism Type

| Morphism Type | Forward | Backward | Round-trip Property |
|---------------|---------|----------|---------------------|
| 1:1 (Isomorphism) | `f(x)` | `f⁻¹(y)` | Exact: `f⁻(f(x)) = x` |
| N:1 (Pure Function) | `f(x)` | Preimage lookup | `f⁻(f(x)) ⊇ x` (all inputs mapping to same output) |
| 1:N (Kleisli) | Expand | Collect parent | `f⁻(f(x)) = x` (if parent tracked) |
| Aggregation (Fold) | `sum(xs)` | Reverse-join table | `f⁻(f(xs)) ⊇ xs` (all contributing records) |
| Filter | `filter(p, xs)` | Include filtered-out | `f⁻(f(xs)) ⊇ xs` (passed + filtered) |

**Example**: For Spark `groupBy`:
- **Forward**: `groupBy(key).agg(sum)` produces aggregated rows
- **Backward**: Reverse-join table maps group key → constituent record keys
- **Round-trip**: `backward(forward(records)) ⊇ records` (returns all records that contributed)

### Compositional Consistency Laws

For adjoints to compose correctly:

1. **Monotonicity**: Both `forward` and `backward` preserve ordering (if applicable)
2. **Round-trip Containment**:
   - `backward(forward(x)) ⊇ x` (lower closure)
   - `forward(backward(y)) ⊆ y` (upper closure)
3. **Contravariant Composition**: `(g ∘ f)⁻ = f⁻ ∘ g⁻`
4. **Identity**: `id⁻ = id`

### Expected Outcomes

- [ ] Every morphism in the LDM implements the `Adjoint` interface
- [ ] Isomorphisms are self-adjoint (exact inverses)
- [ ] Lossy morphisms have well-defined containment bounds
- [ ] Adjoint composition is validated at compile time
- [ ] Reverse execution path is computable for any forward path
- [ ] Data reconciliation via `backward(forward(x)) ⊇ x` validation
- [ ] Impact analysis via `backward(target_subset)` computation
- [ ] Bidirectional sync with single adjoint definition

### Constraints

- Backward metadata must be captured during forward execution
- Storage overhead for reverse-join tables must be bounded
- Adjoint execution must respect same epoch/context as forward execution
- Containment bounds must be computable or declared

---

## INT-006: Frobenius Algebra as Theoretical Foundation

**Status**: Speculative - See [Appendix A](appendices/APPENDIX_A_FROBENIUS_ALGEBRAS.md) for full analysis.

### Summary

Frobenius Algebras may provide a stronger theoretical foundation than Adjoints alone, capturing the **duality between aggregation (μ) and expansion (δ)** with compositional guarantees.

### Key Insight

The current Spark wrapper approach (reverse-join tables for groupBy) is already **Frobenius-shaped** - we're implementing comultiplication (δ) via metadata capture. Formalizing this could enable:

- Algebraic pipeline optimization via Frobenius rewrite rules
- Derived (not checked) distributed execution correctness
- Canonical backward operations (δ is *the* dual of μ, not arbitrary)

### Open Questions (Deferred to Design)

1. Is Frobenius overkill for reconciliation/impact analysis use cases?
2. What is the comultiplication for each aggregation type?
3. Lax Frobenius (with containment) vs Strict Frobenius?
4. How to handle non-Frobenius morphisms?

### Recommendation

Keep `Adjoint` as base interface. Consider `Frobenius` as optional enhancement for aggregate/expand pairs during design phase.

**Full Analysis**: [Appendix A: Frobenius Algebras](appendices/APPENDIX_A_FROBENIUS_ALGEBRAS.md)

---

## Next Steps

1. Generate formal requirements (REQ-*) from this intent
2. Design component architecture based on the Logical Ontology (Section 3)
3. Define the Abstract Machine components (Section 5)
4. Break down into implementation tasks following TDD

---

**Source Document**: [mapper_requirements.md](mapper_requirements.md) (v7.2)
