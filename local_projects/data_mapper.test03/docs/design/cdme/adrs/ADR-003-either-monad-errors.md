# ADR-003: Either Monad for Error Handling

**Status**: Accepted  
**Date**: 2026-02-20

## Context

Data transformation pipelines encounter errors at every stage: schema mismatches, type coercion failures, null constraint violations, and runtime data quality issues. The error handling strategy must be composable (errors propagate through `flatMap` chains), inspectable (errors carry structured context), and compatible with bulk processing (one bad record must not halt millions of good ones).

Traditional exception-based error handling breaks composition, is invisible in type signatures, and forces immediate handling or global try/catch blocks.

## Decision

Use the **Either Monad** (`Either[Error, Value]`) as the primary error handling mechanism throughout CDME. `Left` carries structured error values; `Right` carries successful results. Errors accumulate via `Left` collection into a dead letter queue for downstream inspection.

## Rationale

- **Composable**: `Either` composes via `flatMap` / `for`-comprehensions — error short-circuits are automatic and type-safe.
- **No exceptions**: Error paths are explicit in type signatures. Every function declares whether it can fail.
- **Dead letter queue**: `Left` values are collected rather than thrown, enabling bulk processing where valid records proceed and invalid records accumulate for reporting.
- **Pattern matching**: Scala 3 exhaustive match on `Left`/`Right` ensures all error cases are handled at consumption points.

## Alternatives Considered

| Alternative | Why rejected |
|-------------|-------------|
| Exceptions (try/catch) | Not composable; invisible in types; breaks `flatMap` chains |
| Option/None | Loses error information — `None` says "failed" but not why |
| Validated (Applicative) | Better for accumulating independent errors, but CDME pipelines are sequential; Either suffices and is simpler |
| Custom Result type | Reinvents Either without ecosystem support |

## Consequences

**Positive**:
- Error handling is explicit, composable, and type-safe
- Dead letter queues fall out naturally from `Left` accumulation
- No hidden control flow — developers see error paths in signatures

**Negative**:
- Developers must use monadic style (`flatMap` / `for`-comprehensions) consistently
- Interop with exception-throwing libraries (e.g., Spark internals) requires wrapping at boundaries

## Requirements Addressed

- **REQ-F-TYP-003**: Type-safe error representation in transformation pipelines
- **REQ-F-ERR-001**: Structured error handling with dead letter queue support
