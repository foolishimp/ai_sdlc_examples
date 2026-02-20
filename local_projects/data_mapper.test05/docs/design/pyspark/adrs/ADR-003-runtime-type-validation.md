# ADR-003: Runtime Type Validation via Pydantic

**Status**: Accepted
**Date**: 2026-02-21
**Deciders**: Architecture team
**Requirements Addressed**: REQ-F-TYP-001, REQ-F-TYP-002, REQ-F-TYP-004, REQ-F-LDM-007, REQ-F-LDM-002, REQ-NFR-VER-001

---

## Context

The CDME requirements demand strict type enforcement: every entity must have typed attributes, every morphism must have declared cardinality, type mismatches at composition boundaries must be rejected, and refinement type violations must route records to the Error Domain.

Scala achieves much of this at compile time via its type system. Python's dynamic typing means type enforcement must be implemented via other mechanisms. The question is: how do we enforce type contracts at system boundaries (configuration loading, API calls, data flow) without compile-time guarantees?

## Decision

Use **Pydantic v2** for runtime type validation at all system boundaries:
- Configuration loading (LDM YAML, PDM YAML, job config)
- API entry points (builder methods, executor calls)
- Data contracts between layers

Use **mypy in strict mode** for static analysis during development and CI.

Use **runtime assertion decorators** for invariant enforcement within hot paths.

## Rationale

- **Pydantic v2 performance**: Pydantic v2 uses Rust-compiled validators, making it fast enough for configuration validation and API boundary checks. It is NOT used for per-row data validation (that would be too slow at scale).
- **Fail-fast at boundaries**: Configuration errors (missing cardinality, invalid grain, type mismatch) are caught when YAML is loaded into Pydantic models, before any processing begins. This provides the "compile-time" guarantee equivalent.
- **Structured error messages**: Pydantic produces detailed error messages identifying the field, expected type, and received value -- matching the requirement for specific error identification (REQ-F-TYP-004).
- **Separation of concerns**: Pydantic handles configuration/API boundary validation. The CDME `TypeUnifier` handles morphism composition type checking. The `ErrorRouter` handles per-record refinement type violations. Each mechanism is appropriate for its context.

## Alternatives Considered

### Alternative 1: attrs + cattrs

- **Pros**: Lighter weight than Pydantic. Fast serialization/deserialization via cattrs. Good for frozen dataclasses.
- **Cons**: Less powerful validation (no `field_validator`, no model-level validation). Smaller ecosystem. No automatic JSON Schema generation for documentation.
- **Rejected because**: Pydantic's validation features (field validators, model validators, custom types) are needed for enforcing complex invariants like "all morphisms must have cardinality" and "grain hierarchy must be a total order."

### Alternative 2: Manual Validation in `__post_init__`

- **Pros**: No external dependency for the model layer. Full control over validation logic.
- **Cons**: Verbose boilerplate. No automatic serialization. Error messages must be manually crafted. Easy to forget validation in new classes.
- **Rejected because**: The model layer (`cdme.model`) uses frozen dataclasses with no external dependencies. Pydantic is used in the `cdme.config` module (which loads YAML into model types) and at API boundaries, not in the model layer itself. This preserves the "zero external deps" rule for `cdme.model`.

## Consequences

### Positive
- Configuration errors are caught at load time with clear messages
- API contract violations are caught at call time
- mypy catches ~60% of type errors during development
- Pydantic models serve as self-documenting API contracts
- JSON Schema auto-generation supports tooling and documentation

### Negative
- **Per-row validation is NOT Pydantic-based**: Refinement type checking on individual data records uses lightweight `Callable[[Any], bool]` predicates, not Pydantic models. Pydantic would be too slow at 1M+ records/hour.
- **Two validation systems**: Pydantic at boundaries, custom type-checking in the compiler. Developers must understand which applies where.
- **Runtime dependency**: Pydantic is a required dependency for `cdme.config` and API layers (though not for `cdme.model` itself).
- **Some errors only caught at runtime**: Despite mypy + Pydantic, certain type errors (especially in dynamically constructed morphism chains) will only surface during execution.
