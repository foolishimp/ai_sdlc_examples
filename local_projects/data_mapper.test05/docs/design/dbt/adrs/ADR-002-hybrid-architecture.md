# ADR-002: Hybrid dbt + Python Architecture

**Status**: Proposed
**Date**: 2026-02-21
**Requirements**: REQ-F-ADJ-001, REQ-F-ADJ-002, REQ-F-ADJ-003, REQ-F-ADJ-004, REQ-F-ADJ-005, REQ-F-ADJ-006, REQ-F-ADJ-007, REQ-F-ACC-001, REQ-F-ACC-002, REQ-F-ACC-003, REQ-F-ACC-004, REQ-F-ACC-005, REQ-F-AIA-001, REQ-F-AIA-002, REQ-F-AIA-003, REQ-NFR-OBS-001

## Context

The CDME requirements include several capability domains that dbt cannot satisfy on its own:

1. **Adjoint morphisms** (REQ-F-ADJ-001..007): dbt has no concept of "backward" transformations. Every dbt model produces a forward result; there is no mechanism to declare or execute a reverse function. Reverse-join metadata, backward traversal, and containment law verification all require programmatic logic.

2. **Record accounting** (REQ-F-ACC-001..005): dbt does not track individual records through its DAG. The accounting invariant (`|input| = |output| + |filtered| + |errors|`) requires reading multiple tables and computing set differences — a task that exceeds dbt's SQL-model-at-a-time paradigm.

3. **AI assurance** (REQ-F-AIA-001..003): Validating AI-generated mappings requires parsing SQL, analyzing graph topology, and performing type unification — programmatic tasks that are beyond dbt's Jinja templating.

4. **OpenLineage emission** (REQ-NFR-OBS-001): dbt produces artifacts but does not natively emit OpenLineage events. A translation layer is needed.

The question is whether to extend dbt (via plugins/hooks) or to use separate Python services.

## Decision

We will implement a **hybrid architecture** with three Python companion services alongside the dbt project:

1. **cdme_lineage_service**: Reads dbt artifacts, computes accounting ledgers, emits OpenLineage events, gates run completion.
2. **cdme_validation_service**: Pre-run topological validation, AI mapping checks, cost estimation, dry-run mode.
3. **cdme_adjoint_service**: Manages reverse-join metadata tables, backward traversal queries, reconciliation, impact analysis.

These services run as part of the orchestrated pipeline (before and after `dbt run`), not embedded within dbt.

## Rationale

dbt's extension points (hooks, macros, custom materializations) are insufficient for the required capabilities:
- **on-run-end hooks** can execute SQL but cannot perform complex programmatic logic like graph traversal or OpenLineage event construction.
- **Custom materializations** control how models are built but cannot add backward metadata capture alongside forward execution.
- **Python models** (dbt 1.5+) run Python within the warehouse but are designed for data transformations, not infrastructure services.

Separate Python services provide:
- Full programmatic flexibility for graph analysis, metadata management, and event emission
- Clear separation of concerns: dbt owns data, Python owns control
- Independent scalability and deployment
- Testability via standard Python testing frameworks

### Alternatives Considered

1. **dbt-only with extensive hooks and macros**: Attempt to implement all capabilities within dbt using on-run-end hooks, custom materializations, and pre-hook/post-hook SQL. Rejected because: hooks run SQL in the warehouse context and cannot perform HTTP calls (for OpenLineage), graph analysis, or complex metadata computation. The resulting Jinja code would be unmaintainable.

2. **Single monolithic Python service wrapping dbt**: A Python orchestrator that calls dbt programmatically (via `dbt.cli.main`) and intercepts all inputs/outputs. Rejected because: this eliminates the benefits of dbt's declarative model (developers would interact with the Python service, not dbt directly), and it creates a single point of failure. The hybrid approach preserves dbt's native developer experience.

## Consequences

### Positive
- Each component has a clear responsibility boundary
- dbt remains the primary developer interface for data transformations
- Python services are independently deployable and testable
- The architecture is extensible — new services can be added without modifying the dbt project
- Standard Python libraries (FastAPI, SQLAlchemy, openlineage-python) can be leveraged

### Negative
- Operational complexity increases — three services must be deployed and managed alongside dbt
- The pipeline has more moving parts and potential failure points
- Adjoint metadata is captured post-run, not during execution — this is less efficient and can be incomplete if a run fails partway
- Developers must understand both dbt and the Python services to fully operate the system
- Testing requires integration tests across dbt and Python services

### Mitigations
- A single orchestration script (`run_cdme.sh`) wraps the full pipeline into one command
- Docker Compose for local development bundles all services
- Each Python service has health checks and graceful degradation
- The adjoint service handles partial runs by only processing models that completed successfully
- Comprehensive documentation maps each service to the requirements it satisfies

## References

- REQUIREMENTS.md v1.0.0 sections 3.8 (AI Assurance), 3.9 (Record Accounting), 3.10 (Adjoint Morphisms)
- ADR-001 (dbt ecosystem selection)
- ADR-003 (descoped requirements driven by this hybrid split)
