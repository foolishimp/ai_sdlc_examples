# ADR-005: Grain Hierarchy as First-Class Concept

**Status**: Accepted
**Date**: 2025-12-10
**Implements**: REQ-LDM-06, REQ-TRV-02, REQ-INT-02, REQ-INT-05

---

## Context

Data exists at different levels of aggregation:
- **Atomic**: One row per event/transaction
- **Daily**: One row per entity per day
- **Monthly**: One row per entity per month
- **Yearly**: One row per entity per year

The #1 cause of incorrect ETL results is **grain mixing** - joining or combining data at incompatible grains without proper aggregation.

### Common Grain Bugs

```sql
-- BUG: Joining atomic to daily without aggregation
SELECT o.order_id, d.daily_revenue
FROM orders o  -- ATOMIC
JOIN daily_summary d ON o.customer_id = d.customer_id
-- Result: daily_revenue duplicated for each order!

-- BUG: Aggregating already-aggregated data incorrectly
SELECT customer_id, SUM(monthly_total)  -- Summing monthly
FROM monthly_summary
GROUP BY customer_id
-- May be valid (yearly rollup) or invalid (re-aggregating a MAX)
```

---

## Decision

**Grain is a first-class concept in CDME. Every entity declares its grain, and the compiler validates grain compatibility at definition time.**

### Grain Hierarchy Definition

```yaml
# grains/hierarchy.yaml
grains:
  - name: ATOMIC
    level: 0
    description: Raw events, one row per occurrence
    dimensions: []  # No aggregation dimensions

  - name: HOURLY
    level: 1
    aggregates: ATOMIC
    dimensions: [entity_key, hour]

  - name: DAILY
    level: 2
    aggregates: [ATOMIC, HOURLY]
    dimensions: [entity_key, date]

  - name: MONTHLY
    level: 3
    aggregates: [ATOMIC, HOURLY, DAILY]
    dimensions: [entity_key, year_month]

  - name: YEARLY
    level: 4
    aggregates: [ATOMIC, HOURLY, DAILY, MONTHLY]
    dimensions: [entity_key, year]

  - name: LIFETIME
    level: 5
    aggregates: [ATOMIC, HOURLY, DAILY, MONTHLY, YEARLY]
    dimensions: [entity_key]
```

### Entity Grain Declaration

```yaml
# entities/order.yaml
entity: Order
grain: ATOMIC
grain_key: [order_id]  # What makes a row unique at this grain

# entities/daily_summary.yaml
entity: DailySummary
grain: DAILY
grain_key: [customer_id, date]
grain_dimensions:
  entity_key: customer_id
  date: date
```

### Grain Validation Rules

**Rule 1: Projection Compatibility**
```
Cannot project attributes from incompatible grains into same output
without explicit aggregation.

INVALID:
  SELECT o.order_id (ATOMIC), d.daily_total (DAILY)
  -- Cannot mix ATOMIC and DAILY attributes

VALID:
  SELECT d.date, d.daily_total (DAILY), SUM(o.amount) (ATOMIC→DAILY)
  GROUP BY d.date, d.daily_total
```

**Rule 2: Join Compatibility**
```
Joins between different grains require:
- Aggregation morphism on finer grain, OR
- Explicit grain alignment declaration

INVALID:
  orders (ATOMIC) JOIN daily_summary (DAILY) ON customer_id
  -- ATOMIC.customer_id is not unique, causes fan-out

VALID:
  orders (ATOMIC)
    |> AGGREGATE BY (customer_id, date) AS order_daily (DAILY)
    |> JOIN daily_summary (DAILY) ON (customer_id, date)
```

**Rule 3: Aggregation Direction**
```
Can only aggregate from finer to coarser grain.

VALID: ATOMIC → DAILY → MONTHLY → YEARLY
INVALID: MONTHLY → DAILY (disaggregation requires different pattern)
```

---

## Consequences

### Positive

- **Prevents grain mixing bugs** - Caught at compile time, not runtime
- **Self-documenting** - Entity grain is explicit in schema
- **Aggregation validation** - Ensures monoid compatibility at each level
- **AI safety** - LLM can't generate invalid grain combinations

### Negative

- **Schema overhead** - Every entity needs grain metadata
- **Rigidity** - Some edge cases may need escape hatches
- **Learning curve** - Teams must think explicitly about grain

### Neutral

- Custom grains can be defined (not just temporal)
- Grain hierarchy is per-domain (finance may differ from marketing)

---

## Cloud Implementation Notes

### Grain Metadata Storage

Grain is stored in Schema Registry (ADR-002):

```yaml
# In entity definition
entity: CustomerMetrics
grain: MONTHLY
grain_key: [customer_id, year_month]
grain_dimensions:
  entity_key: customer_id
  year_month: metrics_month
```

### Compiler Validation

```python
def validate_grain_compatibility(projection: List[Attribute]) -> Result:
    grains = {attr.entity.grain for attr in projection}

    if len(grains) > 1:
        # Multiple grains - check for aggregation
        finest = min(grains, key=lambda g: g.level)
        for attr in projection:
            if attr.entity.grain == finest and not attr.is_aggregated:
                return Error(f"Grain mixing: {attr} is {finest}, needs aggregation")

    return Ok()
```

### Grain in Lineage

Lineage records include grain context:

```json
{
  "target_entity": "monthly_summary",
  "target_grain": "MONTHLY",
  "source_entities": [
    {"entity": "orders", "grain": "ATOMIC", "aggregation": "SUM"},
    {"entity": "daily_summary", "grain": "DAILY", "aggregation": "ROLLUP"}
  ]
}
```

---

## Multi-Level Aggregation (REQ-INT-02)

Subsequent aggregation is valid when:

1. Each level satisfies monoid laws (REQ-LDM-04)
2. Grain hierarchy permits the aggregation path
3. Morphism path explicitly encodes each level

```yaml
# Valid multi-level aggregation
path:
  - source: orders (ATOMIC)
    morphism: sum_by_day
    target: daily_orders (DAILY)
    aggregation: SUM  # Monoidal ✓

  - source: daily_orders (DAILY)
    morphism: sum_by_month
    target: monthly_orders (MONTHLY)
    aggregation: SUM  # Monoidal, composable ✓
```

---

## Requirements Addressed

- **REQ-LDM-06**: Grain & type metadata - Grain is explicit on every entity
- **REQ-TRV-02**: Grain safety - Compiler validates grain compatibility
- **REQ-INT-02**: Subsequent aggregation - Grain hierarchy enables valid rollups
- **REQ-INT-05**: Multi-grain formulation - Explicit aggregation required
