# ADR-002: Protocol-Based Architecture

**Status**: Accepted
**Date**: 2026-02-21
**Deciders**: Architecture team
**Requirements Addressed**: REQ-F-LDM-001, REQ-F-ADJ-001, REQ-NFR-DIST-001, REQ-F-TRV-001

---

## Context

The CDME domain model includes multiple extension points: morphism executors (different backends), aggregation monoids (user-defined), adjoint morphisms (per-type backward functions), and type system extensions. The design must support extensibility without coupling to specific implementations.

Python offers two primary mechanisms for defining interfaces:
1. Abstract Base Classes (ABC) -- nominal typing via inheritance
2. Protocol classes (PEP 544) -- structural typing via duck typing with static checking

The choice affects how components are composed, tested, and extended.

## Decision

Use **Protocol classes** from `typing.Protocol` as the primary interface mechanism, combined with **frozen dataclasses** for value types. Reserve ABC only for cases where shared implementation logic is needed (not just interface definition).

## Rationale

- **Structural subtyping**: Protocol classes enable duck typing with mypy verification. An implementation satisfies a Protocol without explicitly inheriting from it. This decouples packages -- `cdme.spark.SparkExecutor` satisfies `MorphismExecutor` without importing the Protocol at runtime.
- **Test doubles**: Protocol-based interfaces are trivially mockable. Test doubles need only implement the required methods -- no inheritance hierarchy to navigate.
- **Frozen dataclasses for immutability**: All domain model types (Entity, Morphism, Grain, CdmeType variants) are frozen dataclasses, providing immutability, `__hash__`, and `__eq__` automatically. This supports the requirement for versioned, immutable schema definitions.
- **Composition over inheritance**: The CDME domain has composition relationships (Category contains Entities and Morphisms), not inheritance hierarchies. Protocols naturally express "what operations does this support?" rather than "what class does this descend from?"

## Alternatives Considered

### Alternative 1: ABC-based Inheritance Hierarchy

- **Pros**: Familiar OOP pattern. `@abstractmethod` enforces implementation at class definition time. Can provide shared implementation in base classes.
- **Cons**: Creates coupling -- implementations must import and inherit from the ABC. Deep inheritance hierarchies become rigid. Multiple inheritance in Python is fragile (MRO issues). Testing requires inheriting from the ABC even for test doubles.
- **Rejected because**: The CDME architecture has strict layer separation (model, compiler, runtime, spark). ABCs would create import dependencies between layers. Protocol classes allow `cdme.spark` to satisfy `cdme.runtime` interfaces without importing them at runtime.

### Alternative 2: Plain Duck Typing (no type annotations)

- **Pros**: Maximum flexibility. No boilerplate.
- **Cons**: No static analysis support. Errors caught only at runtime. No documentation of expected interfaces. Violates the project's mypy strict mode requirement.
- **Rejected because**: The CDME compensates for Python's dynamic typing via static analysis (mypy). Plain duck typing provides no static safety net.

## Consequences

### Positive
- Clean layer separation: `cdme.spark` does not import from `cdme.runtime` at runtime
- mypy verifies Protocol satisfaction statically
- Test doubles are trivial to create -- just implement the methods
- Frozen dataclasses provide automatic `__hash__`, `__eq__`, and immutability
- No deep inheritance hierarchies to maintain

### Negative
- Protocols cannot enforce method implementation at class definition time (unlike `@abstractmethod`). Violations are caught by mypy or at first call, not at class creation.
- No shared implementation via base classes. Code reuse must use composition or utility functions.
- Developers unfamiliar with `typing.Protocol` may find the pattern less intuitive than ABC inheritance.
