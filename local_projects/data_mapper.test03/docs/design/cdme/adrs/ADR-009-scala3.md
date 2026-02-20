# ADR-009: Scala 3 as Implementation Language

**Status**: Accepted  
**Date**: 2026-02-20

## Context

CDME's categorical data mapping engine requires a language that can express its type-level constructs — functors, natural transformations, adjoint pairs, and parameterised morphisms — directly in the type system. The language must also integrate natively with Apache Spark (ADR-008) and support functional programming as the primary paradigm.

## Decision

Use **Scala 3** (Dotty) as the implementation language for CDME. Leverage Scala 3's advanced type system features — union types, match types, opaque types, and given/using clauses — to encode categorical constructs at the type level.

## Rationale

- **Union types**: `String | Int | Null` directly encodes sum types for schema fields without sealed trait boilerplate. Maps naturally to LDM attribute types.
- **Match types**: Type-level pattern matching enables compile-time morphism dispatch — the type system resolves which transformation applies based on source/target types.
- **Opaque types**: Zero-cost type wrappers for domain concepts (EntityId, FieldName, MorphismKey) without runtime boxing overhead.
- **FP-first**: Scala 3 treats functional programming as primary. Immutable data, higher-kinded types, and typeclasses (via given/using) align with CDME's categorical foundations.
- **Spark native**: Spark's core is Scala. No serialisation boundary, full API access, and Spark's own type system (Encoders, Datasets) integrates directly.

## Alternatives Considered

| Alternative | Why rejected |
|-------------|-------------|
| Scala 2 | Weaker type system (no union types, no match types); given/using is cleaner than implicit |
| Haskell | Superior type system but no Spark integration; small talent pool for enterprise |
| Kotlin | JVM-native but weaker HKT support; FP is secondary paradigm |
| Rust | No JVM; no Spark integration; ownership model adds friction for data pipeline code |
| Python (PySpark) | Dynamic types defeat the purpose of type-safe categorical encoding |

## Consequences

**Positive**:
- Categorical constructs (functors, natural transformations) are expressible at the type level
- Compile-time safety catches mapping errors before runtime
- Native Spark integration with no serialisation overhead
- Modern syntax reduces boilerplate compared to Scala 2

**Negative**:
- Scala 3 ecosystem is still maturing; some libraries lag behind Scala 2 versions
- Smaller developer pool compared to Java/Python — hiring and onboarding cost
- Spark's official Scala 3 support requires Spark 3.4+; version pinning needed

## Requirements Addressed

- **REQ-F-TYP-001**: Type-safe schema representation via Scala 3 type system
- **REQ-F-TYP-002**: Higher-kinded type encoding for functors and natural transformations
- **REQ-F-TYP-003**: Sum type representation for error handling (Either) and nullable fields
- **REQ-F-TYP-004**: Opaque type wrappers for domain identifiers
- **REQ-F-TYP-005**: Compile-time morphism type checking via match types
- **REQ-F-TYP-006**: Typeclass-based extensibility via given/using
