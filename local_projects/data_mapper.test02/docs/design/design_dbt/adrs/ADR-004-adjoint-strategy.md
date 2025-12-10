# ADR-004: SQL-based Adjoint Backward Strategy

**Status**: Proposed
**Date**: 2025-12-10
**Deciders**: [TBD]
**Depends On**: ADR-001 (dbt as Execution Engine), ADR-002 (Warehouse Target)
**Implements**: REQ-ADJ-04, REQ-ADJ-05, REQ-ADJ-06, REQ-ADJ-08, REQ-ADJ-09

---

## Context

CDME requires adjoint backward transformations in dbt:
- Aggregations: Find contributing source records
- Filters: Reconstruct filtered-out records
- Explosions: Map children back to parents

dbt is SQL-only, so adjoints must be implemented via SQL patterns.

---

## Decision

**[TBD - Options below for discussion]**

---

## Options

### Option A: Companion Audit Models

Create a dbt model for each morphism that captures reverse mapping.

```
models/
├── marts/
│   └── mart_customer_orders.sql       # Forward transformation
└── adjoint/
    └── adj_mart_customer_orders.sql   # Reverse-join table
```

**Pros**:
- Clear separation
- dbt-native
- Testable

**Cons**:
- Model proliferation
- Sync complexity

### Option B: Audit Columns in Output

Add hidden audit columns to output models.

```sql
-- mart_customer_orders.sql
select
    customer_id,
    order_date,
    sum(order_total) as total,
    array_agg(order_id) as _adjoint_source_keys  -- Hidden audit column
from {{ ref('stg_orders') }}
group by 1, 2
```

**Pros**:
- Single model
- Always in sync

**Cons**:
- Larger output
- Schema pollution
- May hit warehouse limits on arrays

### Option C: Window Functions for History

Use warehouse history/time travel + window functions.

```sql
-- Reconstruct source keys using time travel
select *
from {{ ref('stg_orders') }}
for system_time as of timestamp '{{ execution_timestamp }}'
where (customer_id, order_date) in (
    select customer_id, order_date
    from {{ ref('mart_customer_orders') }}
    where customer_id = '{{ target_customer }}'
)
```

**Pros**:
- No additional storage
- Uses warehouse capabilities

**Cons**:
- Relies on time travel retention
- Complex queries
- Performance concerns

### Option D: Hybrid (Strategy per Morphism Type)

| Morphism | Strategy | Implementation |
|----------|----------|----------------|
| Aggregation | Companion Model | `adj_*` models |
| Filter | Audit Table | Filtered-out capture |
| Explosion | Inline Column | Parent key reference |

---

## Implementation Patterns

### Aggregation Adjoint (Companion Model)

```sql
-- models/adjoint/adj_customer_daily_orders.sql
{{ config(materialized='incremental') }}

select
    {{ dbt_utils.generate_surrogate_key(['customer_id', 'order_date']) }} as aggregation_key,
    order_id as source_key,
    customer_id,
    order_date,
    current_timestamp() as captured_at
from {{ ref('stg_orders') }}
{% if is_incremental() %}
  where order_date > (select max(order_date) from {{ this }})
{% endif %}
```

### Filter Adjoint (Audit Capture)

```sql
-- models/adjoint/adj_filtered_orders.sql
-- Captures orders that failed validation

select
    order_id,
    'order_total_negative' as filter_reason,
    order_total as failed_value
from {{ ref('stg_orders') }}
where order_total < 0
```

### Explosion Adjoint (Inline)

```sql
-- models/intermediate/int_order_line_items.sql
select
    order_id as parent_key,  -- Adjoint: child → parent
    line_item_id,
    product_id,
    quantity
from {{ ref('stg_orders') }},
lateral flatten(input => line_items)
```

---

## Backward Query Patterns

### Impact Analysis (REQ-ADJ-09)

```sql
-- Macro: Find all orders contributing to a customer's total
{% macro impact_analysis(target_customer, target_date) %}
select src.*
from {{ ref('stg_orders') }} src
join {{ ref('adj_customer_daily_orders') }} adj
  on src.order_id = adj.source_key
where adj.customer_id = '{{ target_customer }}'
  and adj.order_date = '{{ target_date }}'
{% endmacro %}
```

### Reconciliation (REQ-ADJ-08)

```sql
-- Macro: Validate round-trip containment
{% macro reconcile_aggregation(target_model, adjoint_model, source_model) %}
with forward_output as (
    select * from {{ ref(target_model) }}
),
backward_reconstructed as (
    select distinct src.*
    from {{ ref(source_model) }} src
    join {{ ref(adjoint_model) }} adj
      on src.order_id = adj.source_key
),
original_source as (
    select * from {{ ref(source_model) }}
)
-- Containment check: reconstructed ⊇ original
select 'missing' as issue, o.*
from original_source o
left join backward_reconstructed b on o.order_id = b.order_id
where b.order_id is null
{% endmacro %}
```

---

## Storage Overhead

| Strategy | Storage | Performance |
|----------|---------|-------------|
| Companion Models | ~1x source size | Fast lookup |
| Inline Columns | +20-50% output | No join needed |
| Time Travel | 0 (warehouse managed) | Query-time cost |

---

## Consequences

### If Companion Models
- Clean separation
- More models to maintain
- Independent lifecycle

### If Inline Columns
- Simpler architecture
- Larger outputs
- Schema complexity

### If Time Travel
- No storage overhead
- Relies on warehouse retention
- Complex queries

### If Hybrid
- Optimized per use case
- More implementation patterns
- Configuration complexity

---

## References

- [dbt Incremental Models](https://docs.getdbt.com/docs/build/incremental-models)
- [Snowflake Time Travel](https://docs.snowflake.com/en/user-guide/data-time-travel)
