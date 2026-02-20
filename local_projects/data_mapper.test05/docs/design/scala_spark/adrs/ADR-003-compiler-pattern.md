# ADR-003: Validate-Then-Execute Compiler Pattern

**Status**: Accepted
**Date**: 2026-02-21
**Requirements Addressed**: REQ-F-LDM-003, REQ-F-TRV-002, REQ-F-TRV-006, REQ-F-TYP-004, REQ-F-CTX-001, REQ-BR-GRN-001, REQ-F-AIA-001

---

## Context

The CDME requires that all structural validation (path existence, type unification, grain safety, access control, fiber compatibility) occurs before any data is processed. Invalid pipelines must be rejected at definition time, not at runtime when data is already flowing. The grain mixing prohibition (REQ-BR-GRN-001) is a topological invariant with no override mechanism. AI-generated mappings (REQ-F-AIA-001) must pass the same validation as human-authored mappings.

This means the system must distinguish between a "compilation" phase (structural validation) and an "execution" phase (data processing).

## Decision

Adopt a **compiler pattern** where:

1. The `TopologicalCompiler` validates all mappings against the LDM graph, producing either a list of `CompilationError` or a compiled `ExecutionPlan`
2. The `ExecutionPlan` is an opaque, validated artifact that the runtime trusts without re-validation
3. The runtime only processes compiled plans — there is no "interpret and validate on the fly" path

## Rationale

- Path validation, type unification, and grain safety are graph-structural checks that do not require data — they must complete before execution begins, per REQ-F-LDM-003
- Cost estimation (REQ-F-TRV-006) must occur before execution so that budget-exceeding plans can be rejected without wasting compute
- The compiler produces a single `Either[List[CompilationError], ExecutionPlan]`, making the success/failure boundary explicit
- AI-generated mappings pass through the same compiler entry point, guaranteeing identical validation (REQ-F-AIA-001)
- The compiled plan can be inspected, serialized, and audited before execution (supports dry-run mode, REQ-F-AIA-003)

## Alternatives Considered

### Alternative 1: Runtime Validation (validate during execution)

Rejected. Runtime validation means errors are discovered after data has been partially processed, leading to wasted compute and complex rollback scenarios. Grain violations detected mid-pipeline would require halting execution and discarding partial results. This conflicts with the requirement that all rejections occur at definition/compile time (REQ-F-LDM-003).

### Alternative 2: Dynamic Interpretation (no compiled plan)

Rejected. A dynamic interpreter would re-validate each morphism as it executes, adding per-record overhead. It would also make the dry-run mode (REQ-F-AIA-003) impossible — there would be no way to "run validation without execution" because validation and execution are interleaved. The compiler pattern cleanly separates these concerns.

## Consequences

**Positive**:
- All structural errors surface before any data processing, giving fast feedback
- The compiled `ExecutionPlan` is serializable and auditable (supports proof generation, REQ-F-AIA-002)
- The runtime is simpler because it trusts the plan — no defensive re-validation at every step
- Cost estimation is a natural part of the compilation phase
- Dry-run mode is trivially implemented: compile the plan, return the result, do not execute

**Negative**:
- Two-phase execution means the system cannot begin processing while still validating later stages
- The compiler must model all execution concerns (checkpoint placement, lossiness classification) at compile time, increasing compiler complexity
- Plan invalidation: if the LDM changes between compile and execute, the plan is stale

**Mitigations**:
- Plan staleness is mitigated by artifact versioning (REQ-NFR-VER-001) — the plan records artifact versions, and the runtime rejects execution if versions have changed
- Compiler complexity is managed by decomposing into focused validators (PathValidator, GrainChecker, TypeUnifier, etc.) composed via the `TopologicalCompiler` facade
