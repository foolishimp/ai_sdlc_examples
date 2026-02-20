# ADR-004: Docker Compose + Spark Standalone Deployment Model

**Status**: Accepted
**Date**: 2026-02-21
**Requirements Addressed**: REQ-NFR-DIST-001, REQ-NFR-PERF-001

---

## Context

The project constraints specify `docker-compose` as the deployment platform with `on-premise` cloud provider and `dev` + `test` environment tiers. The CDME requires a Spark runtime for distributed execution but must be testable locally without a full cluster. The deployment model must support developer iteration (fast startup) and automated testing (deterministic environment).

## Decision

Deploy using **Docker Compose with Spark standalone mode** for development and testing:

- **Spark master + worker(s)**: Containerized Spark standalone cluster managed by Docker Compose
- **CDME application**: Fat JAR submitted to the Spark cluster via `spark-submit`
- **Supporting services**: Minio (S3-compatible storage for PDM bindings) and a local filesystem mount for test data
- **Scaling**: Add worker containers to `docker-compose.yml` for horizontal scaling tests

## Rationale

- Docker Compose provides a single-command development environment (`docker-compose up`) that is deterministic and reproducible
- Spark standalone mode runs without YARN, Mesos, or Kubernetes dependencies, minimizing infrastructure requirements
- The same fat JAR that runs in Docker also runs on a production Spark cluster (YARN/K8s) — the deployment model does not affect application code
- Minio provides S3-compatible storage for testing PDM bindings without cloud credentials

## Alternatives Considered

### Alternative 1: Kubernetes + Spark Operator

Rejected for dev/test. Kubernetes adds significant operational complexity (cluster setup, RBAC, persistent volumes, Spark operator installation) that is disproportionate for a development environment. The project constraints specify `dev` and `test` tiers only. Kubernetes may be appropriate for production deployment in a future ADR.

### Alternative 2: Local Spark (no containers)

Rejected. Local Spark requires each developer to install a compatible Spark version, manage environment variables, and handle version conflicts. Docker Compose ensures all developers and CI agents run identical environments. It also simplifies multi-worker testing.

## Consequences

**Positive**:
- Developers can start a complete CDME environment with `docker-compose up`
- The environment is deterministic — same Docker images produce same behavior
- Integration tests run against a real Spark cluster (standalone mode) not just local mode
- Horizontal scaling tests are possible by adjusting worker count in compose file
- No cloud credentials required for development

**Negative**:
- Docker Compose resource usage (Spark master + workers) can be heavy on developer laptops
- Spark standalone mode does not exactly replicate YARN or Kubernetes behavior
- Storage (Minio) is ephemeral unless volumes are configured

**Mitigations**:
- Provide a `docker-compose.dev.yml` with minimal resources (1 worker) for laptop development
- Provide a `docker-compose.test.yml` with multiple workers for CI/pre-merge testing
- Document the differences between standalone mode and production deployment targets
