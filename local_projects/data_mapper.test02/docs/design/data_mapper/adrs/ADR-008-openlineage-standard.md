# ADR-008: OpenLineage as Lineage Standard

**Status**: Accepted
**Date**: 2025-12-10
**Implements**: REQ-INT-03, REQ-TRV-04, REQ-AI-02

---

## Context

CDME captures rich lineage data:
- Input/output datasets
- Transformation paths (morphism sequences)
- Adjoint metadata (reverse-join tables)
- Telemetry (row counts, duration, errors)

This lineage needs to be:
1. **Exportable** - To external catalog/governance tools
2. **Queryable** - For impact analysis and debugging
3. **Interoperable** - Work with existing data ecosystem

### Options Considered

1. **Custom schema** - Purpose-built for CDME
2. **OpenLineage** - Linux Foundation standard
3. **Cloud-native** - AWS Glue lineage, Purview, etc.
4. **Hybrid** - OpenLineage base + CDME extensions

---

## Decision

**CDME uses OpenLineage as the lineage interchange standard, extended with CDME-specific facets for adjoint metadata and grain context.**

### Why OpenLineage

- **Open standard** - Linux Foundation, vendor-neutral
- **Wide adoption** - Marquez, Airflow, Spark, dbt integrations
- **Extensible** - Custom facets for domain-specific metadata
- **Cloud-agnostic** - Works across AWS, Azure, GCP

### OpenLineage Event Structure

```json
{
  "eventType": "COMPLETE",
  "eventTime": "2024-01-15T10:30:00Z",
  "producer": "cdme://jobs/order_aggregation",
  "schemaURL": "https://openlineage.io/spec/2-0-0/OpenLineage.json",

  "job": {
    "namespace": "cdme",
    "name": "order_aggregation",
    "facets": {
      "documentation": {
        "description": "Aggregate orders to daily customer summary"
      }
    }
  },

  "run": {
    "runId": "run_20240115_001",
    "facets": {
      "cdme_execution": {
        "epoch_timestamp": "2024-01-15T00:00:00Z",
        "lineage_mode": "FULL",
        "lookup_versions": {
          "CountryCode": "2024.01.15"
        }
      }
    }
  },

  "inputs": [
    {
      "namespace": "s3://data-lake",
      "name": "orders",
      "facets": {
        "schema": {...},
        "cdme_grain": {
          "grain": "ATOMIC",
          "grain_key": ["order_id"]
        }
      }
    }
  ],

  "outputs": [
    {
      "namespace": "s3://data-lake",
      "name": "daily_summary",
      "facets": {
        "schema": {...},
        "cdme_grain": {
          "grain": "DAILY",
          "grain_key": ["customer_id", "date"]
        },
        "cdme_adjoint": {
          "adjoint_table": "daily_summary_adjoint",
          "classification": "PROJECTION"
        }
      }
    }
  ]
}
```

---

## CDME Custom Facets

### `cdme_execution` (Run Facet)

```json
{
  "_producer": "cdme",
  "_schemaURL": "https://cdme.io/facets/execution.json",
  "epoch_timestamp": "2024-01-15T00:00:00Z",
  "lineage_mode": "FULL",
  "lookup_versions": {
    "CountryCode": "2024.01.15",
    "ExchangeRate": "AS_OF:order_date"
  },
  "determinism_verified": true
}
```

### `cdme_grain` (Dataset Facet)

```json
{
  "_producer": "cdme",
  "_schemaURL": "https://cdme.io/facets/grain.json",
  "grain": "DAILY",
  "grain_key": ["customer_id", "date"],
  "grain_dimensions": {
    "entity_key": "customer_id",
    "date": "date"
  }
}
```

### `cdme_adjoint` (Dataset Facet)

```json
{
  "_producer": "cdme",
  "_schemaURL": "https://cdme.io/facets/adjoint.json",
  "adjoint_table": "daily_summary_adjoint",
  "adjoint_location": "s3://data-lake/adjoint/daily_summary/",
  "classification": "PROJECTION",
  "storage_strategy": "SEPARATE_TABLE",
  "round_trip_bounds": {
    "lower_closure": true,
    "upper_closure": false
  }
}
```

### `cdme_morphism` (Job Facet)

```json
{
  "_producer": "cdme",
  "_schemaURL": "https://cdme.io/facets/morphism.json",
  "morphism_path": [
    {"name": "orders_to_daily", "type": "aggregation", "cardinality": "N:1"},
    {"name": "enrich_customer", "type": "join", "cardinality": "N:1"}
  ],
  "lossless": false,
  "checkpoints": ["input", "after_aggregation", "output"]
}
```

### `cdme_telemetry` (Run Facet)

```json
{
  "_producer": "cdme",
  "_schemaURL": "https://cdme.io/facets/telemetry.json",
  "input_rows": 10000000,
  "output_rows": 100000,
  "error_rows": 150,
  "duration_ms": 480000,
  "peak_memory_mb": 8192,
  "morphism_stats": [
    {"morphism": "orders_to_daily", "input": 10000000, "output": 100000}
  ]
}
```

---

## Consequences

### Positive

- **Interoperability** - Works with Marquez, Atlan, DataHub, etc.
- **Ecosystem** - Leverage existing OpenLineage integrations
- **Extensible** - CDME facets add value without breaking standard
- **Future-proof** - Standard evolving with community

### Negative

- **Facet overhead** - Custom facets require documentation/tooling
- **Schema evolution** - Must track OpenLineage spec changes
- **Not all tools support facets** - Some catalogs ignore extensions

### Neutral

- Internal CDME storage may use different format (optimized for queries)
- OpenLineage is interchange format, not storage format

---

## Cloud Implementation Notes

### OpenLineage Backends

| Backend | AWS | Azure | GCP |
|---------|-----|-------|-----|
| Marquez | ECS/EKS + RDS | AKS + PostgreSQL | GKE + Cloud SQL |
| DataHub | MSK + OpenSearch | Event Hubs + Search | Pub/Sub + Elastic |
| Atlan | SaaS | SaaS | SaaS |
| Custom | S3 + Athena | ADLS + Synapse | GCS + BigQuery |

### Event Emission Pattern

```python
from openlineage.client import OpenLineageClient

client = OpenLineageClient(url="https://lineage.company.com")

def emit_cdme_lineage(job_result):
    event = RunEvent(
        eventType=EventType.COMPLETE,
        eventTime=datetime.utcnow().isoformat(),
        job=Job(namespace="cdme", name=job_result.job_name),
        run=Run(
            runId=job_result.run_id,
            facets={
                "cdme_execution": CdmeExecutionFacet(
                    epoch_timestamp=job_result.epoch_timestamp,
                    lineage_mode=job_result.lineage_mode
                ),
                "cdme_telemetry": CdmeTelemetryFacet(
                    input_rows=job_result.input_rows,
                    output_rows=job_result.output_rows
                )
            }
        ),
        inputs=[...],
        outputs=[...]
    )
    client.emit(event)
```

### Querying Lineage (Marquez Example)

```bash
# Get lineage for a dataset
curl "https://marquez/api/v1/lineage?nodeId=dataset:s3://data-lake:daily_summary"

# Response includes CDME facets
{
  "graph": [...],
  "datasets": [{
    "name": "daily_summary",
    "facets": {
      "cdme_grain": {"grain": "DAILY"},
      "cdme_adjoint": {"adjoint_table": "daily_summary_adjoint"}
    }
  }]
}
```

---

## Triangulation Support (REQ-AI-02)

OpenLineage events enable the triangulation of:

1. **Intent** - Captured in job description/documentation facet
2. **Logic** - Captured in `cdme_morphism` path
3. **Proof** - Captured in telemetry, outputs, lineage graph

```
Intent: "Aggregate daily orders per customer"
    │
    ▼
Logic: morphism_path = [orders_to_daily(N:1), enrich_customer(N:1)]
    │
    ▼
Proof: input=10M, output=100K, lineage_graph shows Order→DailySummary
```

---

## Requirements Addressed

- **REQ-INT-03**: Traceability - OpenLineage captures full lineage
- **REQ-TRV-04**: Operational telemetry - `cdme_telemetry` facet
- **REQ-AI-02**: Triangulation - Intent/Logic/Proof linkage via facets
