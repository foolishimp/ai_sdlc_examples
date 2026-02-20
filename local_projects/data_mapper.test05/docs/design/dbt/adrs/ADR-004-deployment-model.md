# ADR-004: dbt-core CLI with Orchestrator

**Status**: Proposed
**Date**: 2026-02-21
**Requirements**: REQ-NFR-VER-001, REQ-F-ACC-004, REQ-F-TRV-005, REQ-F-AIA-003

## Context

dbt offers two deployment models:

1. **dbt Cloud**: A managed SaaS platform providing scheduling, artifact management, CI/CD integration, environment management, and a web IDE. It handles orchestration, monitoring, and artifact storage.

2. **dbt-core CLI**: The open-source command-line tool that compiles and runs dbt projects. Requires external orchestration (Airflow, GitHub Actions, cron) and external artifact storage.

The CDME dbt implementation has specific orchestration requirements beyond dbt model execution:
- Pre-run validation service must execute before `dbt run` (REQ-F-AIA-003)
- Post-run adjoint metadata capture must execute after `dbt run`
- Post-run accounting verification must gate run completion (REQ-F-ACC-004)
- OpenLineage events must be emitted at pipeline boundaries
- Artifact versions must be recorded (REQ-NFR-VER-001)
- Deterministic reproducibility requires full control over execution parameters (REQ-F-TRV-005)

## Decision

We will use **dbt-core CLI** (open source) with an external orchestrator (Airflow or GitHub Actions) rather than dbt Cloud. The orchestrator manages the 7-step pipeline:

1. Validation service pre-check
2. `dbt run`
3. `dbt test`
4. Adjoint service metadata capture
5. Lineage service ledger computation
6. Lineage service OpenLineage emission
7. Lineage service run completion gate

## Rationale

The CDME pipeline is not a simple "run dbt" — it requires pre-run and post-run steps with programmatic logic. dbt Cloud's orchestration model does not natively support arbitrary pre/post steps with conditional gating. While dbt Cloud supports webhooks and CI triggers, the tight coupling between validation, execution, adjoint capture, and accounting verification requires orchestrator-level control.

Key factors:
- **Full pipeline control**: Airflow/GitHub Actions can orchestrate the validation-run-test-capture-verify-emit sequence as dependent tasks.
- **Artifact management**: dbt-core produces artifacts locally; the orchestrator copies them to an artifact store (S3/GCS).
- **Cost**: dbt-core is free; dbt Cloud has per-seat licensing.
- **Reproducibility**: dbt-core with pinned versions and explicit `--vars` provides full control over execution parameters.

### Alternatives Considered

1. **dbt Cloud with webhooks**: Use dbt Cloud for model execution and trigger Python services via webhooks at job completion. Rejected because: webhooks provide eventual notification, not synchronous gating. The accounting verification (REQ-F-ACC-004) must block run completion — a webhook-triggered post-process cannot prevent dbt Cloud from marking a job as "Success" before accounting is verified. Additionally, dbt Cloud does not support pre-run validation gates without custom CI integration.

2. **dbt Cloud + Slim CI**: Use dbt Cloud's Slim CI feature for pre-merge validation and scheduled production runs. Rejected because: Slim CI only validates changed models, not the full topological graph. The validation service needs access to the complete manifest for grain safety and type unification checks. This could work as a supplementary check but not as the primary deployment model.

## Consequences

### Positive
- Full control over the 7-step pipeline with conditional gating
- No SaaS dependency or per-seat cost
- Artifact versions are managed explicitly (git SHA, dbt project version, epoch parameters)
- Reproducibility is fully controlled — same git SHA + same vars = same output
- Python services run in the same environment as dbt, simplifying integration

### Negative
- Operational burden: the team must manage orchestrator infrastructure (Airflow cluster or CI runners)
- No dbt Cloud IDE — developers use local development with `dbt run` and `dbt test`
- No built-in artifact browser — must build custom dashboards or use dbt docs locally
- Monitoring and alerting must be built on top of the orchestrator, not provided by dbt Cloud

### Mitigations
- Docker Compose for local development provides a consistent environment
- `run_cdme.sh` script wraps the full pipeline for single-command execution
- dbt docs generate provides a local lineage browser and documentation site
- GitHub Actions / Airflow provide built-in monitoring and alerting capabilities
- Artifact storage on S3/GCS with lifecycle policies handles retention

## References

- REQUIREMENTS.md v1.0.0 sections 3.8 (AI Assurance — dry run), 3.9 (Record Accounting — run gate)
- ADR-002 (hybrid architecture requiring multi-step orchestration)
- dbt-core CLI documentation: https://docs.getdbt.com/reference/commands
