# ADR-008: Apache Spark as Execution Engine

**Status**: Accepted  
**Date**: 2026-02-20

## Context

CDME must execute data transformations at scale — datasets ranging from thousands to billions of rows, across distributed storage systems (HDFS, S3, Delta Lake). The execution engine must support distributed compute, provide a structured data API, integrate with the Scala ecosystem, and be an industry-recognised platform for enterprise adoption.

## Decision

Use **Apache Spark** as the execution engine for CDME data transformations. Mappings compile to Spark DataFrame operations. The Spark session is the runtime boundary between CDME's categorical mapping layer and physical compute.

## Rationale

- **Distributed compute**: Spark handles partitioning, shuffling, and fault tolerance transparently. CDME mappings scale without manual distribution logic.
- **DataFrame API**: Structured, typed operations on columnar data align naturally with CDME's morphism-based transformations. Column expressions map to functorial operations.
- **Scala native**: Spark is written in Scala; the API is first-class. No serialisation overhead or language bridge friction (unlike PySpark).
- **Industry standard**: Spark is the dominant distributed data processing engine. Enterprise teams have existing infrastructure, expertise, and tooling.

## Alternatives Considered

| Alternative | Why rejected |
|-------------|-------------|
| Apache Flink | Stronger for streaming; weaker ecosystem for batch ETL; smaller talent pool |
| Trino/Presto | SQL-only query engine; no programmable transformation API |
| Polars | Single-node only; no distributed compute |
| Custom engine | Massive engineering effort; no ecosystem; unacceptable time-to-market |

## Consequences

**Positive**:
- Proven scalability from megabytes to petabytes
- Rich connector ecosystem (JDBC, Parquet, Delta, Iceberg, Kafka)
- Catalyst optimizer provides automatic query planning and optimisation
- Large community, extensive documentation, enterprise support options

**Negative**:
- JVM startup overhead for small datasets; Spark is heavyweight for trivial transforms
- Version coupling — Spark major versions can introduce breaking API changes
- Cluster management adds operational complexity (YARN, Kubernetes, Databricks)

## Requirements Addressed

- **REQ-NFR-PERF-001**: Distributed execution for large-scale data transformation performance
