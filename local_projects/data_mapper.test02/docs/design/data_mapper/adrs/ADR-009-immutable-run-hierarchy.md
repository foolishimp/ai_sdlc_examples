# ADR-009: Immutable Run Hierarchy

**Status**: Accepted
**Date**: 2025-12-10
**Implements**: REQ-TRV-05-A, REQ-TRV-05-B, REQ-TRV-05, REQ-INT-03, REQ-AI-02

---

## Context

For audit, compliance, debugging, and reproducibility, every execution must be tied to an **immutable snapshot** of:

1. **Configuration** - Mapping definition, source bindings, labels
2. **Code** - Transformation logic, UDFs, version of execution engine
3. **Design** - ADRs that governed the configuration decisions
4. **Dependencies** - Lookup table versions, schema registry state

Without this, we cannot answer:
- "What exact configuration was used for run X?"
- "Which ADR was in effect when this mapping was designed?"
- "Can I reproduce this run exactly?"

### The Problem

Configuration files in git can change. Code deploys update logic. ADRs evolve. If a run only references "mapping: order_enrichment", we lose the ability to know *which version* of that mapping was executed.

---

## Decision

**Every CDME run is identified by a hierarchical RunID that cryptographically binds execution to immutable snapshots of configuration, code, and design artifacts.**

### RunID Structure

```
RunID = {run_id}/{config_hash}/{code_hash}/{design_hash}

Example:
run_20240115_143022_a1b2c3/cfg_sha256_abc123/code_sha256_def456/design_sha256_789xyz
```

### Hierarchy Levels

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         RUN HIERARCHY                                    │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  Level 0: RUN_ID (Execution Instance)                                   │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │ run_id: run_20240115_143022_a1b2c3                              │   │
│  │ timestamp: 2024-01-15T14:30:22Z                                 │   │
│  │ epoch: 2024-01-15                                               │   │
│  │ executor: spark-3.4.1                                           │   │
│  │ triggered_by: airflow/dag_order_enrichment/task_1               │   │
│  └──────────────────────────┬──────────────────────────────────────┘   │
│                              │                                          │
│  Level 1: CONFIG_SNAPSHOT   ▼                                          │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │ config_id: cfg_sha256_abc123def456...                           │   │
│  │ mapping: order_enrichment@v1.2.3                                │   │
│  │ sources: [orders@cfg_001, customers@cfg_002]                    │   │
│  │ lookups: {ExchangeRate: v2024.01.15, CountryCode: v2024.01}    │   │
│  │ labels: {cdme.io/temporal-grain: daily, ...}                    │   │
│  │ git_commit: 7a8b9c0d...                                         │   │
│  └──────────────────────────┬──────────────────────────────────────┘   │
│                              │                                          │
│  Level 2: CODE_SNAPSHOT     ▼                                          │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │ code_id: code_sha256_def456ghi789...                            │   │
│  │ transformations_hash: sha256(all transformation expressions)    │   │
│  │ udf_versions: {calculate_tax: v1.0.2, normalize_name: v2.1.0}  │   │
│  │ engine_version: spark-3.4.1                                     │   │
│  │ cdme_version: 1.5.0                                             │   │
│  │ git_commit: 7a8b9c0d...                                         │   │
│  └──────────────────────────┬──────────────────────────────────────┘   │
│                              │                                          │
│  Level 3: DESIGN_SNAPSHOT   ▼                                          │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │ design_id: design_sha256_789xyzabc...                           │   │
│  │ adrs_in_effect: [ADR-001@v1, ADR-003@v2, ADR-007@v1]           │   │
│  │ ldm_version: ldm_sha256_111222...                               │   │
│  │ type_system_version: types_sha256_333444...                     │   │
│  │ grain_hierarchy_version: grains_sha256_555666...                │   │
│  │ requirements_trace: [REQ-TRV-05, REQ-INT-03, ...]              │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### Immutable Snapshot Storage

Each level is stored as an immutable artifact:

```yaml
# Stored in: s3://cdme-artifacts/runs/{run_id}/manifest.yaml
apiVersion: cdme/v1
kind: RunManifest
metadata:
  run_id: run_20240115_143022_a1b2c3
  created_at: 2024-01-15T14:30:22Z

spec:
  # Level 0: Execution context
  execution:
    run_id: run_20240115_143022_a1b2c3
    epoch_timestamp: "2024-01-15T00:00:00Z"
    triggered_by: airflow/dag_order_enrichment/task_1
    executor:
      engine: spark
      version: "3.4.1"
      cluster: emr-cluster-prod-01

  # Level 1: Frozen configuration
  config:
    snapshot_id: cfg_sha256_abc123def456
    git_commit: 7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b
    git_tag: v1.2.3

    mapping:
      name: order_enrichment
      version: 1.2.3
      hash: sha256:mapping_content_hash
      # Full mapping definition embedded or referenced
      content_ref: s3://cdme-artifacts/configs/cfg_sha256_abc123/mapping.yaml

    sources:
      - name: orders
        version: cfg_001
        hash: sha256:source_orders_hash
        content_ref: s3://cdme-artifacts/configs/cfg_sha256_abc123/sources/orders.yaml
      - name: customers
        version: cfg_002
        hash: sha256:source_customers_hash

    lookups:
      ExchangeRate:
        version: "2024.01.15"
        hash: sha256:lookup_exchange_hash
        row_count: 195
      CountryCode:
        version: "2024.01"
        hash: sha256:lookup_country_hash
        row_count: 249

    labels:
      cdme.io/temporal-grain: daily
      cdme.io/data-domain: sales
      cdme.io/lineage-mode: key-derivable

  # Level 2: Frozen code
  code:
    snapshot_id: code_sha256_def456ghi789
    cdme_version: "1.5.0"

    transformations:
      hash: sha256:all_transformations_combined
      expressions:
        - name: order_total_usd
          expr_hash: sha256:expr_001
        - name: customer_name
          expr_hash: sha256:expr_002

    udfs:
      - name: calculate_tax
        version: "1.0.2"
        hash: sha256:udf_tax_hash
      - name: normalize_name
        version: "2.1.0"
        hash: sha256:udf_name_hash

    engine:
      name: spark
      version: "3.4.1"
      config_hash: sha256:spark_config_hash

  # Level 3: Frozen design
  design:
    snapshot_id: design_sha256_789xyzabc

    adrs:
      - id: ADR-001
        version: 1
        hash: sha256:adr001_hash
        title: "Adjoint over Dagger Category"
      - id: ADR-003
        version: 2
        hash: sha256:adr003_hash
        title: "Either Monad for Error Handling"
      - id: ADR-007
        version: 1
        hash: sha256:adr007_hash
        title: "Compile-Time vs Runtime Validation"

    ldm:
      version: ldm_sha256_111222
      entities_hash: sha256:all_entities
      relationships_hash: sha256:all_relationships

    type_system:
      version: types_sha256_333444
      semantic_types_hash: sha256:semantic_types
      refinement_types_hash: sha256:refinement_types

    grain_hierarchy:
      version: grains_sha256_555666
      hash: sha256:grain_hierarchy

    requirements_trace:
      - REQ-TRV-05  # Deterministic reproducibility
      - REQ-INT-03  # Full lineage
      - REQ-AI-02   # Triangulation

  # Checksums for verification
  checksums:
    manifest: sha256:entire_manifest_hash
    config_bundle: sha256:all_config_files
    code_bundle: sha256:all_code_artifacts
    design_bundle: sha256:all_design_artifacts
```

### Hash Computation

```python
def compute_config_hash(mapping, sources, lookups, labels) -> str:
    """Deterministic hash of all configuration."""
    canonical = canonicalize({
        "mapping": mapping.to_dict(),
        "sources": sorted([s.to_dict() for s in sources], key=lambda x: x["name"]),
        "lookups": sorted(lookups.items()),
        "labels": sorted(labels.items())
    })
    return f"cfg_sha256_{sha256(canonical)}"

def compute_code_hash(transformations, udfs, engine_config) -> str:
    """Deterministic hash of all executable code."""
    canonical = canonicalize({
        "transformations": sorted([t.to_dict() for t in transformations], key=lambda x: x["name"]),
        "udfs": sorted([u.to_dict() for u in udfs], key=lambda x: x["name"]),
        "engine": engine_config.to_dict()
    })
    return f"code_sha256_{sha256(canonical)}"

def compute_design_hash(adrs, ldm, types, grains) -> str:
    """Deterministic hash of all design artifacts."""
    canonical = canonicalize({
        "adrs": sorted([a.to_dict() for a in adrs], key=lambda x: x["id"]),
        "ldm": ldm.to_dict(),
        "types": types.to_dict(),
        "grains": grains.to_dict()
    })
    return f"design_sha256_{sha256(canonical)}"

def compute_run_id(timestamp, config_hash, code_hash, design_hash) -> str:
    """Composite run ID encoding full hierarchy."""
    ts = timestamp.strftime("%Y%m%d_%H%M%S")
    short_hash = sha256(f"{config_hash}/{code_hash}/{design_hash}")[:8]
    return f"run_{ts}_{short_hash}"
```

### Immutability Guarantees

1. **Config Snapshot**: Once created, never modified. New config = new hash.
2. **Code Snapshot**: Tied to git commit + engine version. Any change = new hash.
3. **Design Snapshot**: ADRs versioned. Any ADR update = new design hash.
4. **Run Manifest**: Write-once to immutable storage (S3 with object lock).

```yaml
# S3 bucket configuration for immutability
aws s3api put-object-lock-configuration \
  --bucket cdme-artifacts \
  --object-lock-configuration '{
    "ObjectLockEnabled": "Enabled",
    "Rule": {
      "DefaultRetention": {
        "Mode": "GOVERNANCE",
        "Years": 7
      }
    }
  }'
```

---

## Consequences

### Positive

- **Full Reproducibility**: Any run can be exactly reproduced given the manifest
- **Audit Trail**: Complete chain from execution → config → code → design → requirements
- **Debugging**: "What was different between run A and run B?" is answerable
- **Compliance**: Prove exactly what logic processed what data
- **Rollback**: Deploy previous config/code snapshots with confidence

### Negative

- **Storage Overhead**: Every run stores full manifest (mitigated by deduplication)
- **Complexity**: Hash computation must be deterministic (canonicalization required)
- **Migration**: Changing hash algorithm requires careful versioning

### Neutral

- Manifests can be stored separately from run outputs
- Hash algorithms are configurable (default SHA256)
- Compression reduces storage costs

---

## Triangulation Support (REQ-AI-02)

The run hierarchy enables complete triangulation:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         TRIANGULATION                                    │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   INTENT (Why)                                                          │
│   ├── Requirements: REQ-TRV-05, REQ-INT-03                             │
│   └── ADRs: ADR-001, ADR-003, ADR-007                                  │
│            ↓                                                            │
│   DESIGN (What)                                                         │
│   ├── LDM: Entity topology, relationships                              │
│   ├── Types: Semantic types, refinements                               │
│   └── Grains: Hierarchy definition                                     │
│            ↓                                                            │
│   CONFIG (How - Static)                                                 │
│   ├── Mapping: order_enrichment@v1.2.3                                 │
│   ├── Sources: orders, customers                                        │
│   └── Lookups: ExchangeRate@2024.01.15                                 │
│            ↓                                                            │
│   CODE (How - Dynamic)                                                  │
│   ├── Transformations: expressions, joins                               │
│   ├── UDFs: calculate_tax@v1.0.2                                       │
│   └── Engine: spark-3.4.1                                              │
│            ↓                                                            │
│   EXECUTION (Proof)                                                     │
│   ├── Run: run_20240115_143022_a1b2c3                                  │
│   ├── Inputs: orders (10M rows, hash: abc...)                          │
│   ├── Outputs: enriched_orders (10M rows, hash: def...)                │
│   └── Lineage: OpenLineage events                                       │
│                                                                          │
│   Given any output record, trace back through entire hierarchy          │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Cloud Implementation Notes

### AWS Reference

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    IMMUTABLE ARTIFACT STORAGE                            │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  S3 (Object Lock)                                                        │
│  └── cdme-artifacts/                                                     │
│      ├── runs/                                                           │
│      │   └── {run_id}/                                                  │
│      │       ├── manifest.yaml          # Immutable run manifest        │
│      │       ├── inputs/                # Input checksums               │
│      │       ├── outputs/               # Output data + checksums       │
│      │       └── lineage/               # OpenLineage events            │
│      │                                                                   │
│      ├── configs/                                                        │
│      │   └── {config_hash}/             # Deduplicated config snapshots │
│      │       ├── mapping.yaml                                           │
│      │       └── sources/                                               │
│      │                                                                   │
│      ├── code/                                                           │
│      │   └── {code_hash}/               # Deduplicated code snapshots   │
│      │       ├── transformations.json                                   │
│      │       └── udfs/                                                  │
│      │                                                                   │
│      └── design/                                                         │
│          └── {design_hash}/             # Deduplicated design snapshots │
│              ├── adrs/                                                  │
│              ├── ldm/                                                   │
│              └── types/                                                 │
│                                                                          │
│  DynamoDB (Index)                                                        │
│  └── cdme-run-index                                                      │
│      ├── PK: run_id                                                     │
│      ├── config_hash, code_hash, design_hash                            │
│      ├── epoch, timestamp, status                                       │
│      └── GSI: by_config_hash, by_epoch, by_mapping                     │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### Querying Run History

```python
# Find all runs for a specific config
runs = cdme.query_runs(config_hash="cfg_sha256_abc123")

# Find all runs in an epoch
runs = cdme.query_runs(epoch="2024-01-15")

# Compare two runs
diff = cdme.diff_runs("run_001", "run_002")
# Returns:
# {
#   "config_changed": True,
#   "config_diff": {"lookups.ExchangeRate.version": ("2024.01.14", "2024.01.15")},
#   "code_changed": False,
#   "design_changed": False
# }

# Reproduce a run
cdme.reproduce(run_id="run_20240115_143022_a1b2c3")
# Fetches manifest, recreates exact environment, re-executes
```

---

## Requirements Addressed

- **REQ-TRV-05-A**: Immutable Run Hierarchy - Core requirement for binding runs to artifact snapshots
- **REQ-TRV-05-B**: Artifact Version Binding - Version immutability and lineage tracking
- **REQ-TRV-05**: Deterministic reproducibility - Full hierarchy enables exact reproduction
- **REQ-INT-03**: Traceability - Run → Config → Code → Design → Requirements chain
- **REQ-AI-02**: Triangulation - Intent/Logic/Proof linkage via hierarchical snapshots
