# ADR-005: Type System Mapping - dbt Contracts

**Status**: Proposed
**Date**: 2025-12-10
**Deciders**: [TBD]
**Depends On**: ADR-001 (dbt as Execution Engine), ADR-002 (Warehouse Target)
**Implements**: REQ-TYP-01, REQ-TYP-02, REQ-TYP-05, REQ-TYP-06, REQ-TYP-07

---

## Context

CDME requires a type system that enforces:
- Primitive types (REQ-TYP-01)
- Refinement types (REQ-TYP-02)
- Semantic types (REQ-TYP-07)
- No implicit casting (REQ-TYP-05)
- Type unification (REQ-TYP-06)

dbt Contracts (v1.5+) provide compile-time type enforcement.

---

## Decision

**Use dbt Contracts + Custom Tests for CDME Type System**

---

## Implementation

### Primitive Types (REQ-TYP-01)

```yaml
# models/staging/stg_orders.yml
version: 2

models:
  - name: stg_orders
    config:
      contract:
        enforced: true
    columns:
      - name: order_id
        data_type: string
      - name: order_total
        data_type: numeric(18,2)
      - name: order_date
        data_type: date
      - name: is_fulfilled
        data_type: boolean
```

### Refinement Types (REQ-TYP-02)

dbt tests enforce refinement predicates:

```yaml
# models/staging/stg_orders.yml
models:
  - name: stg_orders
    columns:
      - name: order_total
        data_type: numeric(18,2)
        tests:
          - dbt_expectations.expect_column_values_to_be_between:
              min_value: 0
              # Refinement: PositiveNumeric
        meta:
          cdme:
            refinement: "order_total > 0"
```

Custom test for refinement:

```sql
-- tests/generic/test_refinement.sql
{% test refinement(model, column_name, predicate) %}
select *
from {{ model }}
where not ({{ predicate }})
{% endtest %}
```

```yaml
# Usage
columns:
  - name: age
    tests:
      - refinement:
          predicate: "age >= 0 and age <= 150"
```

### Semantic Types (REQ-TYP-07)

Use column meta + custom tests:

```yaml
columns:
  - name: order_total
    data_type: numeric(18,2)
    meta:
      cdme:
        semantic_type: MONEY
        currency: USD

  - name: order_date
    data_type: date
    meta:
      cdme:
        semantic_type: DATE

  - name: discount_rate
    data_type: numeric(5,4)
    meta:
      cdme:
        semantic_type: PERCENT
```

Semantic type validation macro:

```sql
-- macros/validation/check_semantic_types.sql
{% macro check_semantic_compatibility(left_col, right_col, operation) %}
  {% set left_type = get_column_meta(left_col, 'semantic_type') %}
  {% set right_type = get_column_meta(right_col, 'semantic_type') %}

  {% if operation == 'ADD' and left_type != right_type %}
    {{ exceptions.raise_compiler_error(
        "Semantic type mismatch: Cannot add " ~ left_type ~ " and " ~ right_type
    ) }}
  {% endif %}
{% endmacro %}
```

### No Implicit Casting (REQ-TYP-05)

dbt contracts with `enforced: true` reject type mismatches:

```yaml
models:
  - name: mart_orders
    config:
      contract:
        enforced: true  # Fails if upstream types don't match
    columns:
      - name: order_total
        data_type: numeric(18,2)  # Must match exactly
```

For explicit casts, use macros:

```sql
-- macros/morphisms/cast_morphism.sql
{% macro safe_cast(column, from_type, to_type) %}
  -- Explicit cast morphism (REQ-TYP-05)
  -- Logs the cast in lineage
  cast({{ column }} as {{ to_type }})
{% endmacro %}
```

### Type Unification (REQ-TYP-06)

Pre-hook validation:

```sql
-- macros/validation/validate_type_unification.sql
{% macro validate_type_unification(model_name) %}
  {% set model_config = graph.nodes['model.' ~ project_name ~ '.' ~ model_name] %}
  {% set upstream_models = model_config.depends_on.nodes %}

  {# Check that all upstream types unify with expected types #}
  {% for upstream in upstream_models %}
    {# Type unification logic #}
  {% endfor %}
{% endmacro %}
```

---

## Type Mapping: CDME → dbt

| CDME Type | dbt/SQL Type | Notes |
|-----------|--------------|-------|
| Int | integer / bigint | Use bigint for IDs |
| Float | numeric(p,s) | Specify precision |
| String | string / varchar | Use string for portability |
| Boolean | boolean | |
| Date | date | |
| Timestamp | timestamp_tz | With timezone |
| Option<T> | nullable T | NULL handling |
| Either<L,R> | - | Use tests + error capture |
| Product<A,B> | struct | Warehouse-specific |
| Sum<A,B> | - | Use discriminator column |

---

## Grain Metadata in Contracts

```yaml
models:
  - name: mart_customer_daily
    meta:
      cdme:
        grain: DAILY_AGGREGATE
        grain_columns: [customer_id, order_date]
    columns:
      - name: customer_id
        data_type: string
      - name: order_date
        data_type: date
      - name: daily_total
        data_type: numeric(18,2)
        meta:
          cdme:
            aggregation: SUM
            source_grain: ATOMIC
```

---

## Error Handling for Type Violations

Type violations route to Error Domain:

```sql
-- models/error_domain/err_type_violations.sql
select
    '{{ run_started_at }}' as epoch_id,
    'TYPE_VIOLATION' as error_type,
    order_id as source_key,
    'order_total' as column_name,
    order_total::string as offending_value,
    'Expected numeric(18,2), got string' as message
from {{ ref('stg_orders_raw') }}
where try_cast(order_total as numeric(18,2)) is null
  and order_total is not null
```

---

## Consequences

### Positive
- Compile-time type checking via contracts
- Refinements via tests
- Semantic types via meta + custom tests
- Works with existing dbt ecosystem

### Negative
- Semantic type checks require custom macros
- Sum types not natively supported
- Some runtime checks unavoidable

---

## References

- [dbt Contracts](https://docs.getdbt.com/docs/collaborate/govern/model-contracts)
- [dbt-expectations](https://github.com/calogica/dbt-expectations)
- [dbt Column Meta](https://docs.getdbt.com/reference/resource-properties/meta)
