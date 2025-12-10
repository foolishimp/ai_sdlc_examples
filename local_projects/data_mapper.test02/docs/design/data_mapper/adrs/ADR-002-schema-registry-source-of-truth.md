# ADR-002: Schema Registry as Single Source of Truth

**Status**: Accepted
**Date**: 2025-12-10
**Implements**: REQ-LDM-01, REQ-LDM-02, REQ-LDM-03, REQ-LDM-06, REQ-PDM-01

---

## Context

CDME requires a machine-readable representation of the Logical Data Model (LDM) topology - entities, relationships, cardinalities, grains, and types. This topology is used for:

1. **Compile-time validation** - Reject invalid paths before execution
2. **AI hallucination prevention** - Validate LLM-generated mappings against real schema
3. **Access control** - Determine which relationships a principal can traverse
4. **Cost estimation** - Predict cardinality explosion from 1:N relationships

The question: Where does this topology live, and what is its authoritative source?

### Options Considered

1. **DDL Files** - Extract from CREATE TABLE statements
2. **ERD Tools** - Export from design tools (ERwin, DbSchema)
3. **Data Catalog** - Use existing catalog (Glue, Unity, Collibra)
4. **Dedicated Schema Registry** - Purpose-built for CDME

---

## Decision

**The LDM topology is defined in a dedicated Schema Registry that is the single source of truth for all CDME operations.**

The Schema Registry:
- Stores entity definitions (attributes, types, grain)
- Stores relationship definitions (cardinality, join keys, access control)
- Is version-controlled and immutable per version
- Exposes an API for validation and querying
- Can be synchronized FROM other sources (Glue, DDL) but IS the authority

### Schema Registry Structure

```
schema_registry/
├── entities/
│   ├── order.yaml
│   ├── customer.yaml
│   └── product.yaml
├── relationships/
│   ├── order_customer.yaml
│   └── order_line_items.yaml
├── grains/
│   └── hierarchy.yaml
├── types/
│   ├── primitives.yaml
│   ├── semantic_types.yaml
│   └── refinements.yaml
└── manifest.yaml  # Version, checksums
```

### Entity Definition Format

```yaml
# entities/order.yaml
entity: Order
version: "2024.01.15"
grain: ATOMIC
primary_key: [order_id]

attributes:
  - name: order_id
    type: String
    nullable: false
  - name: customer_id
    type: String
    nullable: false
  - name: order_date
    type: Date
    nullable: false
  - name: total_amount
    type: Money
    semantic_type: true
    nullable: false
  - name: status
    type: OrderStatus  # Enum/refinement type
    nullable: false

relationships:
  - name: customer
    target: Customer
    cardinality: N:1
    join_key: customer_id
    access: [ANALYST, ADMIN]
  - name: line_items
    target: OrderLineItem
    cardinality: 1:N
    join_key: order_id
    access: [ANALYST, ADMIN]
```

---

## Consequences

### Positive

- **Single source of truth** - No ambiguity about what relationships exist
- **Version control** - Schema changes are tracked, auditable
- **Validation foundation** - All CDME validation uses this registry
- **AI grounding** - LLM mappings validated against authoritative schema
- **Decoupled from storage** - LDM topology independent of physical storage

### Negative

- **Synchronization burden** - Must keep registry in sync with actual databases
- **Additional infrastructure** - Requires registry service/storage
- **Migration effort** - Existing schemas must be imported

### Neutral

- Registry can be file-based (Git) or service-based (API)
- Can coexist with other catalogs (Glue for physical, Registry for logical)

---

## Cloud Implementation Notes

| Cloud | Schema Registry Implementation |
|-------|-------------------------------|
| AWS | S3 + DynamoDB, or Glue Data Catalog with custom properties |
| Azure | ADLS + Cosmos DB, or Purview with extensions |
| GCP | GCS + Firestore, or Data Catalog with custom entries |
| Multi-cloud | Git repository + CI/CD for validation |

### Recommended Pattern

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  Source DDL     │────▶│  Schema         │────▶│  CDME           │
│  (Glue, etc.)   │     │  Registry       │     │  Compiler       │
└─────────────────┘     │  (Authoritative)│     └─────────────────┘
                        └─────────────────┘
                               │
                               ▼
                        ┌─────────────────┐
                        │  Version        │
                        │  Control (Git)  │
                        └─────────────────┘
```

---

## Requirements Addressed

- **REQ-LDM-01**: LDM as directed multigraph - Registry stores graph structure
- **REQ-LDM-02**: Cardinality types - Registry stores 1:1, N:1, 1:N per relationship
- **REQ-LDM-03**: Path validation - Registry enables compile-time path checking
- **REQ-LDM-06**: Grain & type metadata - Registry stores grain and types per entity
- **REQ-PDM-01**: LDM/PDM separation - Registry is logical; PDM bindings are separate
