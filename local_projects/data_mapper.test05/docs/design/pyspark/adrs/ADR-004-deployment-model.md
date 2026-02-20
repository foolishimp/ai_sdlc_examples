# ADR-004: Docker Compose + PySpark Standalone Deployment

**Status**: Accepted
**Date**: 2026-02-21
**Deciders**: Architecture team
**Requirements Addressed**: REQ-NFR-DIST-001, REQ-NFR-PERF-001, REQ-F-TRV-005

---

## Context

The CDME must execute on a distributed compute framework (REQ-NFR-DIST-001), sustaining 1M records/hour throughput (REQ-NFR-PERF-001). The deployment model must support development, testing, and production environments. The system is a library/framework, not a hosted service (Assumption A-001), but it requires a Spark cluster to execute pipelines.

The deployment model must address:
- How developers run CDME locally during development
- How CI/CD tests execute with a Spark runtime
- How production pipelines execute at scale
- How deterministic reproducibility (REQ-F-TRV-005) is maintained across environments

## Decision

Use **Docker Compose** for local development and CI environments with a **PySpark Standalone cluster** (1 master + N workers). Production deployment targets existing Spark infrastructure (YARN, Kubernetes, or Databricks) via Spark's pluggable cluster manager.

The CDME library is distributed as a **Python wheel** installable via `pip install cdme`. The Spark runtime is a deployment-time concern, not a build-time concern.

## Rationale

- **Docker Compose for dev/CI**: Provides a reproducible Spark environment for development and testing. Developers run `docker compose up` to get a local Spark cluster. CI pipelines use the same compose file for integration tests.
- **Library distribution**: The CDME is packaged as a Python wheel. Users install it into their existing Spark environments. This decouples the library from any specific deployment platform.
- **Spark Standalone for simplicity**: The standalone cluster manager requires no external dependencies (no YARN, no Kubernetes). It is sufficient for development, testing, and small-scale production.
- **Production flexibility**: Spark's cluster manager is pluggable. Production environments can use YARN, Kubernetes, or Databricks without changing CDME code. Only the `SparkSession` configuration changes.
- **Reproducibility**: Docker images are pinned to specific versions. The same image used in CI is used for development. Poetry lockfile ensures identical Python dependencies.

## Alternatives Considered

### Alternative 1: Kubernetes + Spark Operator

- **Pros**: Cloud-native. Auto-scaling. Robust orchestration. Industry standard for production Spark.
- **Cons**: Complex to set up locally for development. Requires Kubernetes expertise. Overhead for small-scale testing. Not all teams have Kubernetes infrastructure.
- **Rejected because**: Kubernetes is the recommended production target but not the default development/CI environment. The Docker Compose setup is simpler for developers. Production teams can deploy CDME on Kubernetes using the Spark Operator without any code changes.

### Alternative 2: Databricks-native

- **Pros**: Managed Spark. No infrastructure management. Built-in notebook environment.
- **Cons**: Vendor lock-in. Not available in all environments (on-premise, air-gapped). Requires Databricks licensing. Cannot be used for CI testing without Databricks infrastructure.
- **Rejected because**: The CDME must be deployable on any Spark runtime, not just Databricks. However, the library must be compatible with Databricks (it is -- as a wheel installed on a Databricks cluster).

## Consequences

### Positive
- Developers can run the full CDME stack locally with `docker compose up`
- CI tests execute against a real Spark cluster, not mocks
- The library is portable across Spark deployment platforms
- Docker image pinning ensures reproducible environments
- Poetry lockfile ensures reproducible Python dependencies

### Negative
- Docker Compose adds ~30 seconds startup time for local development
- Developers must have Docker installed
- The standalone cluster manager does not provide dynamic scaling (fixed worker count)
- Production deployment requires Spark infrastructure (not a CDME concern, but a prerequisite)
