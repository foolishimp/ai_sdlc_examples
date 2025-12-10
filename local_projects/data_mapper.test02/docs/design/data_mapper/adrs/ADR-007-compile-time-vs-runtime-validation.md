# ADR-007: Compile-Time vs Runtime Validation

**Status**: Accepted
**Date**: 2025-12-10
**Implements**: REQ-LDM-03, REQ-TRV-02, REQ-TYP-05, REQ-TYP-06, REQ-AI-01, REQ-AI-03

---

## Context

CDME performs extensive validation. The question: When should validation occur?

**Compile-time** (at mapping definition):
- Fast feedback
- No data required
- Structural validation only

**Runtime** (during execution):
- Has actual data
- Can validate values
- Performance cost

The goal: **Shift as much validation left as possible** - catch errors at definition time, not 3am during production.

---

## Decision

**CDME implements a two-phase validation model: Compile-time for structural correctness, Runtime for value correctness.**

### Compile-Time Validation

These checks occur when a mapping is defined/registered, before any data is processed:

| Validation | Description | Requirement |
|------------|-------------|-------------|
| **Path existence** | All entities and relationships exist in Schema Registry | REQ-LDM-03 |
| **Cardinality tracking** | 1:N expansions are flagged, context lifting required | REQ-LDM-02 |
| **Grain compatibility** | No mixing grains without aggregation | REQ-TRV-02 |
| **Type unification** | Join types match, no implicit casts | REQ-TYP-05, REQ-TYP-06 |
| **Access control** | User can traverse all morphisms in path | REQ-LDM-05 |
| **Aggregation validity** | Aggregations are monoidal | REQ-LDM-04 |
| **Lookup versioning** | All lookups have version specification | REQ-INT-06 |
| **Determinism check** | No forbidden non-deterministic operations | REQ-TRV-05 |
| **AI hallucination** | AI-generated paths exist in topology | REQ-AI-01 |

**Compile-time output**: Valid mapping artifact OR compilation errors

### Runtime Validation

These checks occur during execution, on actual data:

| Validation | Description | Requirement |
|------------|-------------|-------------|
| **Refinement types** | Values satisfy predicates (e.g., positive integer) | REQ-TYP-02 |
| **Type casting** | Explicit cast succeeds (string→int parseable) | REQ-TYP-05 |
| **Lookup success** | Key found in lookup table | REQ-PDM-04 |
| **Sheaf consistency** | Joined data from same epoch/partition | REQ-SHF-01 |
| **Threshold monitoring** | Error rate within limits | REQ-TYP-03-A |
| **Cost limits** | Row counts within budget | REQ-TRV-06 |

**Runtime output**: Data + Error Domain (failures as data)

---

## Validation Pipeline

```
┌─────────────────────────────────────────────────────────────────┐
│                     COMPILE TIME                                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│   Mapping          Schema           Compiler                    │
│   Definition  ───▶ Registry   ───▶  Validation  ───▶ Valid? ─┬─▶ Mapping Artifact
│   (YAML/DSL)       (LDM)            (No Data)        │       │
│                                                       │       │
│                                      ┌────────────────┘       │
│                                      ▼                        │
│                               Compilation Errors               │
│                               (Path not found,                │
│                                Type mismatch,                  │
│                                Grain violation)                │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                      RUNTIME                                     │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│   Mapping           Data            Executor                    │
│   Artifact    ───▶  (S3/DB)   ───▶  Validation  ───┬──▶ Output Data
│   (Compiled)                        (With Data)     │
│                                           │         │
│                                           ▼         │
│                                     Error Domain    │
│                                     (Refinement     │
│                                      violations,    │
│                                      Cast failures) │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Consequences

### Positive

- **Fast feedback** - Structural errors caught immediately
- **No data required** - Can validate mappings without production access
- **AI safety** - Hallucinations caught before execution
- **Cost savings** - Don't start expensive jobs that will fail
- **Clear separation** - Structural vs value errors distinct

### Negative

- **Two validation systems** - More implementation complexity
- **False confidence** - Compile success doesn't guarantee runtime success
- **Schema dependency** - Compile-time requires up-to-date Schema Registry

### Neutral

- Dry run mode (REQ-AI-03) adds optional data validation at compile time
- Some validations could be either (configurable)

---

## Dry Run Mode (REQ-AI-03)

Hybrid: Compile-time validation + data sampling without committing results.

```yaml
job:
  mode: DRY_RUN
  sample_size: 10000  # Validate against sample
  validations:
    - structural  # Always
    - type_sampling  # Check types on sample
    - cost_estimate  # Predict output size
    - lineage_preview  # Generate lineage graph
```

**Dry Run Output**:
```
┌──────────────────────────────────────────────────┐
│ CDME Dry Run Report                              │
├──────────────────────────────────────────────────┤
│ Structural Validation   ✓ All paths valid        │
│ Type Unification        ✓ All types compatible   │
│ Grain Safety            ✓ No grain violations    │
│ Access Control          ✓ All morphisms allowed  │
│                                                  │
│ Sample Validation (10K rows):                    │
│   Refinement Errors     23 (0.23%)               │
│   Cast Failures         0                        │
│   Lookup Misses         5 (0.05%)                │
│                                                  │
│ Cost Estimate:                                   │
│   Input Rows            10,000,000               │
│   Expected Output       10,000,000               │
│   Estimated Duration    ~8 minutes               │
│                                                  │
│ Lineage Graph           Generated (attached)     │
└──────────────────────────────────────────────────┘
```

---

## Cloud Implementation Notes

### Compile-Time Service

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  CI/CD Pipeline │────▶│  CDME Compiler  │────▶│  Artifact Store │
│  (GitHub Action)│     │  (Lambda/K8s)   │     │  (S3)           │
└─────────────────┘     └─────────────────┘     └─────────────────┘
        │                       │
        │                       ▼
        │               ┌─────────────────┐
        │               │  Schema         │
        └──────────────▶│  Registry       │
                        └─────────────────┘
```

### Validation Error Categories

| Category | Phase | Action |
|----------|-------|--------|
| `STRUCTURAL_ERROR` | Compile | Reject mapping, fix required |
| `TYPE_ERROR` | Compile | Reject mapping, fix required |
| `GRAIN_ERROR` | Compile | Reject mapping, add aggregation |
| `ACCESS_ERROR` | Compile | Reject mapping, request access |
| `REFINEMENT_ERROR` | Runtime | Route to Error Domain |
| `CAST_ERROR` | Runtime | Route to Error Domain |
| `THRESHOLD_ERROR` | Runtime | Halt job |

---

## Requirements Addressed

- **REQ-LDM-03**: Path validation at compile time
- **REQ-TRV-02**: Grain safety at compile time
- **REQ-TYP-05**: No implicit casts - rejected at compile time
- **REQ-TYP-06**: Type unification at compile time
- **REQ-AI-01**: Topological validity at compile time
- **REQ-AI-03**: Dry run mode for pre-execution validation
