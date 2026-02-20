# Project Intent

**Project**: Categorical Data Mapping & Computation Engine (CDME)
**Date**: 2026-02-20
**Status**: Approved for Architecture Design
**Version**: 7.2
**Methodology**: AI SDLC Asset Graph Model v2.1

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

- **Cashflow Generators** - Trade to Cashflow morphisms
- **Regulatory Calculators** - Risk aggregation with grain safety
- **Risk Engines** - Monte Carlo simulations as morphisms
- **AI Pipeline Validation** - Deterministic compiler for AI-generated code

The topological constraints (Lineage, Typing, Sheaf Consistency) remain identical regardless of whether the morphism is a simple field rename or a complex simulation.

---

## INT-004: Strategic Value - AI Assurance Layer

As organizations adopt AI-generated pipelines, the CDME serves as:

- **Deterministic Compiler** - Validates AI-generated mappings before execution
- **Hallucination Prevention** - Rejects non-existent relationships, type violations, grain violations
- **Triangulation of Assurance** - Links Intent to Logic to Proof for real-time verification
- **EU AI Act Compliance** - Article 14 (Human Oversight) and Article 15 (Robustness)

---

## INT-005: Adjoint Morphisms for Reverse Transformations

### Problem / Opportunity

Current data pipelines are **unidirectional** - they transform source to target but cannot reverse the process. This creates challenges for:

- **Data Reconciliation** - Cannot verify outputs by reversing back to inputs
- **Impact Analysis** - Cannot compute which source records contributed to a target subset
- **Bidirectional Sync** - Requires maintaining two separate mappings that can drift
- **Schema Migration Rollback** - No principled way to undo migrations

**The Opportunity**: Extend the CDME with **Adjoint Morphisms** where every morphism `f: A -> B` has a corresponding backward morphism `f-: B -> A`, forming a **Galois Connection** that enables first-class reverse transformations.

### Expected Outcomes

- [ ] Every morphism in the LDM implements the Adjoint interface
- [ ] Isomorphisms are self-adjoint (exact inverses)
- [ ] Lossy morphisms have well-defined containment bounds
- [ ] Adjoint composition is validated at compile time
- [ ] Reverse execution path is computable for any forward path
- [ ] Data reconciliation via backward(forward(x)) containment validation
- [ ] Impact analysis via backward(target_subset) computation
- [ ] Bidirectional sync with single adjoint definition

### Constraints

- Backward metadata must be captured during forward execution
- Storage overhead for reverse-join tables must be bounded
- Adjoint execution must respect same epoch/context as forward execution
- Containment bounds must be computable or declared

---

## INT-006: Frobenius Algebra as Theoretical Foundation

**Status**: Speculative - deferred to design phase.

### Summary

Frobenius Algebras may provide a stronger theoretical foundation than Adjoints alone, capturing the **duality between aggregation and expansion** with compositional guarantees.

### Recommendation

Keep Adjoint as base interface. Consider Frobenius as optional enhancement for aggregate/expand pairs during design phase.

---

## Next Steps

1. Run `/aisdlc-iterate --edge "intent->requirements"` to generate REQ-* keys
2. Review and approve requirements
3. Run `/aisdlc-iterate --edge "requirements->design"` to generate design
