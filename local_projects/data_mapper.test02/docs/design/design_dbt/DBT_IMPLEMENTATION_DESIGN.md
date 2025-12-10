# CDME - dbt Implementation Design

**Document Type**: Implementation Design Specification
**Project**: Categorical Data Mapping & Computation Engine (CDME)
**Variant**: `design_dbt` (dbt + SQL)
**Version**: 1.0
**Date**: 2025-12-10
**Status**: Draft
**Derived From**:
- [Generic Reference Design](../data_mapper/AISDLC_IMPLEMENTATION_DESIGN.md)
- [Requirements](../../requirements/AISDLC_IMPLEMENTATION_REQUIREMENTS.md)

---

## Purpose

This document defines the **dbt-specific implementation** of the CDME architecture. It maps abstract components to dbt primitives (models, macros, tests, documentation) and documents technology decisions via ADRs.

**Target Use Cases**:
- SQL-first data teams
- Warehouse-native transformations
- Built-in testing and documentation
- Incremental/batch processing

---

## Technology Stack

| Layer | Technology | Version | Notes |
|-------|------------|---------|-------|
| Transformation | dbt-core | 1.7.x+ | Contracts require 1.5+ |
| Warehouse | TBD | - | Snowflake/BigQuery/Databricks (ADR-002) |
| Lineage | TBD | - | dbt lineage + OpenLineage (ADR-003) |
| Type Enforcement | dbt Contracts | - | See ADR-005 |
| Testing | dbt Tests | - | Built-in + custom |

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          CDME dbt Implementation                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────────┐│
│  │                          dbt PROJECT                                    ││
│  │                                                                         ││
│  │  models/                                                                ││
│  │  ├── staging/           # PDM → LDM (source to staging)                ││
│  │  ├── intermediate/      # Morphism compositions                        ││
│  │  ├── marts/             # Target entities                              ││
│  │  └── adjoint/           # Reverse-lookup models                        ││
│  │                                                                         ││
│  │  macros/                                                                ││
│  │  ├── morphisms/         # Reusable transformations                     ││
│  │  ├── adjoints/          # Backward transformation macros               ││
│  │  └── validation/        # Grain safety, type checks                    ││
│  │                                                                         ││
│  │  tests/                                                                 ││
│  │  ├── generic/           # Monoid laws, refinement types                ││
│  │  └── singular/          # Specific validation tests                    ││
│  │                                                                         ││
│  └─────────────────────────────────────────────────────────────────────────┘│
│                                      │                                       │
│                                      ▼                                       │
│  ┌─────────────────────────────────────────────────────────────────────────┐│
│  │                          WAREHOUSE                                      ││
│  │  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐     ││
│  │  │  Source Tables  │    │  Staging Views  │    │  Mart Tables    │     ││
│  │  │  (PDM)          │    │  (LDM Entities) │    │  (Targets)      │     ││
│  │  └─────────────────┘    └─────────────────┘    └─────────────────┘     ││
│  │                                                                         ││
│  │  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐     ││
│  │  │  Adjoint        │    │  Audit/History  │    │  Error          │     ││
│  │  │  Lookup Tables  │    │  Tables         │    │  Domain         │     ││
│  │  └─────────────────┘    └─────────────────┘    └─────────────────┘     ││
│  └─────────────────────────────────────────────────────────────────────────┘│
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Component Mapping

### TopologicalCompiler → dbt Model DAG + refs

**Generic Interface**: `TopologicalCompiler`
**dbt Implementation**: Model DAG with `ref()` and `source()` functions

```yaml
# models/staging/stg_orders.yml
version: 2

models:
  - name: stg_orders
    description: "Staging model for orders (LDM Entity: Order)"
    config:
      contract:
        enforced: true  # REQ-TYP-06: Type enforcement

    # LDM Entity metadata
    meta:
      cdme:
        entity_id: "Order"
        grain: "ATOMIC"
        cardinality: "1:1"  # From source

    # Type contracts (REQ-TYP-01)
    columns:
      - name: order_id
        data_type: string
        constraints:
          - type: not_null
          - type: unique
      - name: customer_id
        data_type: string
        constraints:
          - type: not_null
      - name: order_total
        data_type: numeric(18,2)
        constraints:
          - type: not_null
        meta:
          semantic_type: "MONEY"  # REQ-TYP-07
```

**Grain Safety via Tests**:

```yaml
# tests/generic/test_grain_safety.yml
version: 2

tests:
  - name: grain_safety
    description: "Validate grain compatibility in joins"
    test_metadata:
      cdme:
        implements: "REQ-TRV-02"
```

```sql
-- tests/generic/grain_safety.sql
{% test grain_safety(model, grain_column, expected_grain) %}
  -- Fail if grain mixing detected
  select *
  from {{ model }}
  where {{ grain_column }} != '{{ expected_grain }}'
{% endtest %}
```

---

### MorphismExecutor → SQL Transformations + Macros

**Generic Interface**: `MorphismExecutor`
**dbt Implementation**: Macros for reusable morphisms

```sql
-- macros/morphisms/apply_morphism.sql
{% macro apply_morphism(morphism_type, source_col, params={}) %}
  {% if morphism_type == '1:1' %}
    {{ source_col }}  -- Identity or simple transform
  {% elif morphism_type == 'N:1' %}
    -- Join-based lookup
    {{ lookup_morphism(source_col, params.lookup_table, params.lookup_key) }}
  {% elif morphism_type == '1:N' %}
    -- Lateral flatten / unnest
    {{ expand_morphism(source_col, params.array_col) }}
  {% endif %}
{% endmacro %}

-- macros/morphisms/lookup_morphism.sql
{% macro lookup_morphism(source_col, lookup_table, lookup_key) %}
  (
    select lookup_value
    from {{ ref(lookup_table) }}
    where {{ lookup_key }} = {{ source_col }}
  )
{% endmacro %}

-- macros/morphisms/aggregation_morphism.sql
{% macro aggregation_morphism(agg_type, value_col, group_cols) %}
  {% if agg_type == 'SUM' %}
    sum({{ value_col }})
  {% elif agg_type == 'COUNT' %}
    count({{ value_col }})
  {% elif agg_type == 'AVG' %}
    avg({{ value_col }})
  {% endif %}
{% endmacro %}
```

**Monoid Validation (REQ-LDM-04)**:

```sql
-- tests/generic/test_monoid_associativity.sql
{% test monoid_associativity(model, agg_column, group_column) %}
  -- Test that aggregation is associative
  -- Compare: agg(partition1) + agg(partition2) vs agg(all)
  with partitioned as (
    select
      {{ group_column }},
      sum({{ agg_column }}) as partial_sum,
      ntile(2) over (partition by {{ group_column }} order by random()) as partition_id
    from {{ model }}
    group by {{ group_column }}, partition_id
  ),
  combined as (
    select
      {{ group_column }},
      sum(partial_sum) as combined_sum
    from partitioned
    group by {{ group_column }}
  ),
  direct as (
    select
      {{ group_column }},
      sum({{ agg_column }}) as direct_sum
    from {{ model }}
    group by {{ group_column }}
  )
  select *
  from combined c
  join direct d on c.{{ group_column }} = d.{{ group_column }}
  where abs(c.combined_sum - d.direct_sum) > 0.001
{% endtest %}
```

---

### Adjoint Wrappers → Audit Tables + Window Functions

**Generic Interface**: `AdjointMetadata`
**dbt Implementation**: Companion audit models for reverse lookups

```sql
-- models/adjoint/adj_order_aggregation.sql
-- Reverse-join table for order aggregations
-- Implements: REQ-ADJ-04

{{ config(
    materialized='incremental',
    unique_key=['aggregation_id', 'source_order_id']
) }}

with aggregation_run as (
    select
        {{ dbt_utils.generate_surrogate_key(['customer_id', 'order_date']) }} as aggregation_id,
        customer_id,
        order_date,
        current_timestamp() as executed_at
    from {{ ref('mart_customer_daily_orders') }}
),

source_mapping as (
    select
        agg.aggregation_id,
        src.order_id as source_order_id,
        src.customer_id,
        src.order_date
    from {{ ref('stg_orders') }} src
    join aggregation_run agg
        on src.customer_id = agg.customer_id
        and src.order_date = agg.order_date
)

select * from source_mapping
```

**Adjoint Backward Macro**:

```sql
-- macros/adjoints/backward_aggregation.sql
{% macro backward_aggregation(aggregation_model, adjoint_model, target_keys) %}
  -- REQ-ADJ-08: Data reconciliation via adjoints
  -- Returns source records that contributed to target aggregation
  select src.*
  from {{ ref(adjoint_model) }} adj
  join {{ source_model }} src
    on adj.source_key = src.primary_key
  where adj.aggregation_id in ({{ target_keys }})
{% endmacro %}

-- macros/adjoints/backward_filter.sql
{% macro backward_filter(model, filter_audit_model, include_filtered=true) %}
  -- REQ-ADJ-05: Backward for filters
  {% if include_filtered %}
    select * from {{ ref(model) }}
    union all
    select * from {{ ref(filter_audit_model) }}
  {% else %}
    select * from {{ ref(model) }}
  {% endif %}
{% endmacro %}
```

**Filter Audit Model**:

```sql
-- models/adjoint/adj_filtered_orders.sql
-- Captures orders filtered out by validation rules
-- Implements: REQ-ADJ-05

{{ config(materialized='incremental') }}

select
    order_id,
    customer_id,
    order_total,
    'VALIDATION_FAILED' as filter_reason,
    current_timestamp() as filtered_at
from {{ ref('stg_orders') }}
where order_total < 0  -- Example filter predicate
   or customer_id is null

{% if is_incremental() %}
  and order_date > (select max(order_date) from {{ this }})
{% endif %}
```

---

### SheafManager → Incremental Models + Partitions

**Generic Interface**: `SheafManager`
**dbt Implementation**: Incremental models with partition strategies

```sql
-- models/intermediate/int_orders_daily.sql
-- Sheaf: Daily epoch partitioning
-- Implements: REQ-PDM-03, REQ-SHF-01

{{ config(
    materialized='incremental',
    incremental_strategy='merge',
    unique_key='order_id',
    partition_by={
      "field": "order_date",
      "data_type": "date",
      "granularity": "day"
    },
    cluster_by=['customer_id']
) }}

select
    order_id,
    customer_id,
    order_total,
    order_date,
    '{{ run_started_at }}' as epoch_id  -- Sheaf epoch marker
from {{ ref('stg_orders') }}

{% if is_incremental() %}
  where order_date > (select max(order_date) from {{ this }})
{% endif %}
```

**Temporal Semantics Macro**:

```sql
-- macros/sheaf/temporal_lookup.sql
{% macro temporal_lookup(lookup_table, key_col, as_of_date=none) %}
  -- REQ-TRV-03: Temporal semantics for cross-boundary lookups
  {% if as_of_date %}
    -- AS_OF semantics
    select *
    from {{ ref(lookup_table) }}
    where valid_from <= '{{ as_of_date }}'
      and (valid_to is null or valid_to > '{{ as_of_date }}')
  {% else %}
    -- LATEST semantics
    select *
    from {{ ref(lookup_table) }}
    where valid_to is null
  {% endif %}
{% endmacro %}
```

---

### ErrorDomain → dbt Tests + Error Tables

**Generic Interface**: `ErrorDomain`
**dbt Implementation**: Tests + error capture models

```sql
-- models/error_domain/err_order_validation.sql
-- Error domain capture
-- Implements: REQ-TYP-03, REQ-ERROR-01

{{ config(materialized='incremental') }}

select
    order_id as source_key,
    'stg_orders' as source_entity,
    '{{ run_started_at }}' as epoch_id,
    case
        when order_total < 0 then 'REFINEMENT_VIOLATION: order_total must be positive'
        when customer_id is null then 'NOT_NULL_VIOLATION: customer_id'
        else 'UNKNOWN'
    end as constraint_type,
    to_json(object_construct(
        'order_id', order_id,
        'order_total', order_total,
        'customer_id', customer_id
    )) as offending_values,
    'stg_orders → mart_orders' as morphism_path,
    current_timestamp() as error_timestamp
from {{ ref('stg_orders') }}
where order_total < 0
   or customer_id is null
```

**Batch Failure Threshold**:

```sql
-- macros/error_domain/check_threshold.sql
{% macro check_error_threshold(error_model, source_model, threshold_pct=5) %}
  -- REQ-TYP-03-A: Batch failure threshold
  {% set error_count_query %}
    select count(*) from {{ ref(error_model) }}
    where epoch_id = '{{ run_started_at }}'
  {% endset %}

  {% set total_count_query %}
    select count(*) from {{ ref(source_model) }}
  {% endset %}

  {% set error_count = run_query(error_count_query).columns[0][0] %}
  {% set total_count = run_query(total_count_query).columns[0][0] %}
  {% set error_pct = (error_count / total_count) * 100 %}

  {% if error_pct > threshold_pct %}
    {{ exceptions.raise_compiler_error(
        "Batch failure threshold exceeded: " ~ error_pct ~ "% errors (threshold: " ~ threshold_pct ~ "%)"
    ) }}
  {% endif %}
{% endmacro %}
```

---

### ResidueCollector → dbt Artifacts + Lineage

**Generic Interface**: `ResidueCollector`
**dbt Implementation**: dbt artifacts + manifest.json

```yaml
# dbt_project.yml
# Configure lineage capture

vars:
  cdme_lineage_mode: 'FULL'  # FULL | KEY_DERIVABLE | SAMPLED
  cdme_capture_adjoint: true

on-run-end:
  - "{{ export_openlineage() }}"
```

```sql
-- macros/lineage/capture_lineage.sql
{% macro capture_lineage(model_name, input_models) %}
  -- REQ-INT-03: Traceability
  {% if var('cdme_lineage_mode') == 'FULL' %}
    -- Full lineage: capture all keys
    insert into {{ ref('cdme_lineage_full') }}
    select
        '{{ model_name }}' as target_model,
        '{{ input_models | join(",") }}' as source_models,
        '{{ run_started_at }}' as execution_id,
        -- Capture key mappings
        ...
  {% endif %}
{% endmacro %}

-- macros/lineage/export_openlineage.sql
{% macro export_openlineage() %}
  -- Export to OpenLineage format
  {% set manifest = load_manifest() %}
  -- Transform dbt manifest to OpenLineage events
  -- ...
{% endmacro %}
```

---

## dbt Project Structure

```
cdme_dbt/
├── dbt_project.yml
├── profiles.yml
│
├── models/
│   ├── staging/                    # PDM → LDM (sources)
│   │   ├── _staging.yml           # Source definitions
│   │   ├── stg_orders.sql
│   │   └── stg_customers.sql
│   │
│   ├── intermediate/              # Morphism compositions
│   │   ├── int_order_enriched.sql
│   │   └── int_customer_metrics.sql
│   │
│   ├── marts/                     # Target entities
│   │   ├── mart_customer_360.sql
│   │   └── mart_order_summary.sql
│   │
│   ├── adjoint/                   # Reverse-lookup tables
│   │   ├── adj_aggregation_mapping.sql
│   │   └── adj_filter_audit.sql
│   │
│   └── error_domain/              # Error capture
│       └── err_validation_failures.sql
│
├── macros/
│   ├── morphisms/                 # Transformation macros
│   │   ├── apply_morphism.sql
│   │   ├── lookup_morphism.sql
│   │   └── aggregation_morphism.sql
│   │
│   ├── adjoints/                  # Backward transformation macros
│   │   ├── backward_aggregation.sql
│   │   └── backward_filter.sql
│   │
│   ├── validation/                # Grain safety, type checks
│   │   ├── check_grain_safety.sql
│   │   └── validate_types.sql
│   │
│   ├── sheaf/                     # Epoch/temporal management
│   │   └── temporal_lookup.sql
│   │
│   ├── error_domain/              # Error handling
│   │   └── check_threshold.sql
│   │
│   └── lineage/                   # Lineage capture
│       ├── capture_lineage.sql
│       └── export_openlineage.sql
│
├── tests/
│   ├── generic/                   # Reusable tests
│   │   ├── test_grain_safety.sql
│   │   ├── test_monoid_laws.sql
│   │   └── test_type_refinement.sql
│   │
│   └── singular/                  # Model-specific tests
│       └── assert_no_orphan_orders.sql
│
├── seeds/                         # Reference data (lookups)
│   └── ref_country_codes.csv
│
└── docs/                          # Documentation
    └── cdme_overview.md
```

---

## Architecture Decision Records

See [adrs/](adrs/) for all technology decisions.

| ADR | Decision | Status |
|-----|----------|--------|
| [ADR-001](adrs/ADR-001-execution-engine.md) | Why dbt | Proposed |
| [ADR-002](adrs/ADR-002-warehouse-target.md) | Snowflake vs BigQuery vs Databricks | Proposed |
| [ADR-003](adrs/ADR-003-lineage-integration.md) | dbt Lineage + OpenLineage | Proposed |
| [ADR-004](adrs/ADR-004-adjoint-strategy.md) | SQL-based Adjoint Backward | Proposed |
| [ADR-005](adrs/ADR-005-type-system.md) | dbt Contracts for Type Enforcement | Proposed |

---

## Design-to-Requirement Traceability

| dbt Component | Generic Component | Requirements |
|---------------|-------------------|--------------|
| Model DAG + refs | TopologicalCompiler | REQ-LDM-01..03, REQ-AI-01 |
| SQL macros | MorphismExecutor | REQ-LDM-04, REQ-TRV-01, REQ-INT-01..08 |
| Adjoint models | AdjointCompiler + ResidueCollector | REQ-ADJ-01..11 |
| Incremental models | SheafManager | REQ-PDM-03, REQ-TRV-03, REQ-SHF-01 |
| dbt tests | ErrorDomain + TopologicalCompiler | REQ-TYP-02..06, REQ-ERROR-01 |
| dbt contracts | Type system | REQ-TYP-01, REQ-TYP-06, REQ-TYP-07 |
| Sources + seeds | ImplementationFunctor | REQ-PDM-01..05 |
| manifest.json | ResidueCollector | REQ-INT-03, RIC-LIN-01 |

---

## dbt-Specific Considerations

### Strengths for CDME

| CDME Requirement | dbt Strength |
|------------------|--------------|
| REQ-LDM-03 (Composition) | `ref()` enforces DAG validity |
| REQ-TYP-01 (Types) | Contracts (1.5+) enforce column types |
| REQ-INT-03 (Lineage) | Built-in lineage in manifest.json |
| REQ-AI-01 (Validation) | Tests run before data lands |

### Limitations + Mitigations

| Limitation | Mitigation |
|------------|------------|
| No native adjoint support | Companion adjoint models + macros |
| SQL-only transformations | External Python models for complex logic |
| No streaming | Use with batch/incremental only |
| Limited type system | Contracts + custom tests |

---

## Performance Considerations

### Adjoint Overhead in dbt

| Operation | Forward | Adjoint Capture | Notes |
|-----------|---------|-----------------|-------|
| Aggregation | 1 model run | +1 adjoint model | Run in parallel |
| Filter | 1 model run | +1 audit model | Incremental |
| Join | 1 model run | +1 mapping model | Key-only capture |

### Optimization Strategies

1. **Incremental adjoint models**: Only capture new mappings
2. **Partition pruning**: Use partition keys in adjoint lookups
3. **Deferred adjoint capture**: Run adjoint models only when reconciliation needed
4. **Materialized views**: For frequently-accessed reverse lookups

---

**Document Status**: Draft
**Last Updated**: 2025-12-10
