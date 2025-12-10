# CDME - Core Design Specification

**For Senior Data Engineers & Architects**

**Document Type**: Core Design Specification
**Project**: Categorical Data Mapping & Computation Engine (CDME)
**Variant**: `data_mapper` (Reference Architecture)
**Version**: 2.0
**Date**: 2025-12-10
**Status**: Draft

---

## Executive Summary

CDME is a **compile-time validated data transformation framework** that prevents the class of bugs that traditional ETL can't catch:

- **Grain mixing** → You can't accidentally join daily aggregates to atomic transactions
- **Silent type coercion** → No implicit `String → Int` that fails at 3am
- **Broken lineage** → Every output row traces back to specific source records
- **AI hallucinations** → LLM-generated mappings are validated against the actual schema

**The core insight**: Your data schema is already a graph. CDME makes that graph explicit and uses it to validate transformations at definition time, not runtime.

---

## How to Read This Document

This document translates the 60 CDME requirements into concepts you already know:

| CDME Term | Data Engineering Equivalent |
|-----------|----------------------------|
| Entity | Table, View, Dataset, DataFrame |
| Morphism | Transformation, JOIN, SELECT, UDF |
| Topology | ERD, Schema Registry, Data Catalog |
| Grain | Aggregation level (atomic, daily, monthly) |
| Epoch | Batch window, partition, snapshot version |
| Adjoint | Reverse lookup, impact analysis query |

**AWS Reference Architecture**:
- S3 + Glue Data Catalog = Physical storage + LDM registry
- Glue/EMR/Athena = Morphism execution engines
- Lake Formation = Access control on morphisms
- OpenLineage = Lineage capture

---

## Architecture Decision Records

Key architectural decisions for this design:

| ADR | Decision | Requirements |
|-----|----------|--------------|
| [ADR-001](adrs/ADR-001-adjoint-over-dagger.md) | Adjoint (Galois Connection) over Dagger Category | REQ-ADJ-01, REQ-ADJ-02 |
| [ADR-002](adrs/ADR-002-schema-registry-source-of-truth.md) | Schema Registry as Single Source of Truth | REQ-LDM-01..03, REQ-PDM-01 |
| [ADR-003](adrs/ADR-003-either-monad-error-handling.md) | Either Monad for Error Handling | REQ-TYP-03, REQ-ERROR-01 |
| [ADR-004](adrs/ADR-004-lineage-capture-strategy.md) | Three Lineage Modes (Full/Key-Derivable/Sampled) | REQ-INT-03, RIC-LIN-* |
| [ADR-005](adrs/ADR-005-grain-hierarchy-first-class.md) | Grain Hierarchy as First-Class Concept | REQ-LDM-06, REQ-TRV-02 |
| [ADR-006](adrs/ADR-006-deterministic-execution-contract.md) | Deterministic Execution Contract | REQ-TRV-05, REQ-INT-06 |
| [ADR-007](adrs/ADR-007-compile-time-vs-runtime-validation.md) | Compile-Time vs Runtime Validation | REQ-AI-01, REQ-TYP-06 |
| [ADR-008](adrs/ADR-008-openlineage-standard.md) | OpenLineage as Lineage Standard | REQ-INT-03, REQ-TRV-04 |
| [ADR-009](adrs/ADR-009-immutable-run-hierarchy.md) | Immutable Run Hierarchy | REQ-TRV-05-A, REQ-TRV-05-B, REQ-TRV-05 |

---

## Part 1: The Logical Data Model (LDM)

### What It Is

The LDM is your **schema as a directed graph** where:
- **Nodes** = Tables/Entities (Orders, Customers, Products)
- **Edges** = Relationships/Transformations (Order.customer_id → Customer.id)

This isn't new - it's what your ERD already represents. CDME just makes it machine-readable and uses it to validate transformations.

### REQ-LDM-01: Schema as a Graph

**The Problem**: Your schemas live in DDL files, ERD diagrams, and tribal knowledge. When someone writes a query joining `orders` to `customers`, there's no automated check that this join makes sense.

**The Solution**: Register your schema as a graph in a **Schema Registry** (like AWS Glue Data Catalog, but with relationship metadata).

```yaml
# ldm/orders.yaml - Entity definition
entity: Order
grain: ATOMIC  # One row per order
attributes:
  - name: order_id
    type: String
    primary_key: true
  - name: customer_id
    type: String
    foreign_key: Customer.customer_id
  - name: order_date
    type: Date
  - name: total_amount
    type: Decimal(18,2)
    semantic_type: Money

relationships:
  - name: customer
    target: Customer
    cardinality: N:1  # Many orders per customer
    join_key: customer_id
  - name: line_items
    target: OrderLineItem
    cardinality: 1:N  # One order has many line items
    join_key: order_id
```

**AWS Equivalent**: Glue Data Catalog + custom relationship metadata in table properties.

**What You Get**:
- Auto-complete in query builders (only show valid joins)
- Validation that `Order.product_id → Product` is a real relationship
- AI mapping validation (reject "Order.customer_name" if it doesn't exist)

---

### REQ-LDM-02: Cardinality Types (The Three Join Types)

Every relationship is one of three types. Getting this wrong causes the most common ETL bugs.

| Cardinality | SQL Equivalent | Data Behavior | Example |
|-------------|---------------|---------------|---------|
| **1:1** | Unique JOIN | Row count unchanged | `order_header` → `order_extended_info` |
| **N:1** | INNER/LEFT JOIN | Row count unchanged | `order` → `customer` (lookup) |
| **1:N** | EXPLODE/LATERAL | Row count multiplies | `order` → `line_items` |

**The Bug This Prevents**:

```sql
-- Looks innocent, but blows up row count
SELECT o.*, li.*
FROM orders o
JOIN line_items li ON o.order_id = li.order_id
-- If order has 5 line items, you now have 5x rows

-- Then someone aggregates without realizing:
SELECT customer_id, SUM(order_total)  -- WRONG! order_total counted 5x
```

**CDME Validation**:
- 1:N relationships are flagged as "context-lifting"
- You must explicitly handle the fan-out (aggregate back down, or intentionally explode)
- Compiler rejects mixing grains in the same SELECT

**AWS Equivalent**: This is what Spark's `explode()` does, but with validation.

---

### REQ-LDM-03: Path Validation (The Dot Notation Compiler)

**The Problem**: Data lineage tools show you what happened. CDME prevents bad things from happening.

**The Solution**: Every transformation path is validated at definition time.

```python
# This path: Order.customer.region.name
# Is validated as:
#   Order --(N:1)--> Customer --(N:1)--> Region --(attribute)--> name
#
# Compiler checks:
# 1. Does Order.customer relationship exist? ✓
# 2. Does Customer.region relationship exist? ✓
# 3. Does Region.name attribute exist? ✓
# 4. Are grains compatible? ✓ (all N:1, no fan-out)
# 5. Does user have access to each hop? ✓
```

**Invalid Path Example**:
```python
# Order.line_items.product.name
#   Order --(1:N)--> LineItem --(N:1)--> Product --(attr)--> name
#
# Compiler WARNING: 1:N expansion at line_items
# You're in List context after line_items
# Cannot project scalar attribute without aggregation
```

**AWS Equivalent**: Think of this as a schema-aware SQL linter that runs at pipeline definition time, not execution time.

---

### REQ-LDM-04: Monoid Laws (Safe Aggregations)

**The Problem**: Not all aggregations are created equal. Some can be parallelized and re-aggregated, others can't.

**Safe (Monoidal) Aggregations**:
```sql
-- SUM: (a + b) + c = a + (b + c), identity = 0
SUM(amount)  -- ✓ Can partition, aggregate, re-aggregate

-- COUNT: Same
COUNT(*)  -- ✓

-- MIN/MAX: Same
MAX(date)  -- ✓
```

**Unsafe (Non-Monoidal) Aggregations**:
```sql
-- FIRST_VALUE without ordering: non-deterministic
FIRST_VALUE(status)  -- ✗ Different result on different partitions

-- MEDIAN: Not associative
MEDIAN(amount)  -- ✗ median(partition1) + median(partition2) ≠ median(all)

-- String concatenation with separator:
STRING_AGG(name, ',')  -- ✗ Order-dependent
```

**CDME Enforcement**:
- Only registered monoidal aggregations are allowed by default
- Non-monoidal aggregations require explicit `allow_non_associative=true` flag
- Approximate aggregations (HyperLogLog, t-Digest) are marked as such

**AWS Equivalent**: This is why Spark uses `Aggregator` with `reduce` semantics.

---

### REQ-LDM-04-A: Empty Aggregation Behavior

**The Problem**: What does `SUM()` return when there are no rows?

| Aggregation | Empty Result | Why |
|-------------|--------------|-----|
| SUM | 0 | Identity element |
| COUNT | 0 | Identity element |
| MIN/MAX | NULL or error? | No identity for "smallest of nothing" |
| AVG | NULL or error? | Division by zero |

**CDME Enforcement**: Every aggregation declares its identity element:
- `SUM` → 0
- `PRODUCT` → 1
- `MIN` → `+∞` or NULL (configurable)
- `STRING_AGG` → empty string

No surprises at runtime.

---

### REQ-LDM-05: Column-Level Access Control

**The Problem**: Lake Formation can restrict table access, but you want finer control. Some users can see `order_total`, but not `customer_ssn`.

**CDME Model**: Access control is on **relationships**, not just tables.

```yaml
# ldm/customer.yaml
entity: Customer
attributes:
  - name: customer_id
    access: PUBLIC
  - name: email
    access: [ADMIN, SUPPORT]
  - name: ssn
    access: [COMPLIANCE]

relationships:
  - name: orders
    target: Order
    access: [SALES, ANALYTICS]  # Only these roles can traverse
```

**Compiler Behavior**: If user `analyst@company.com` (role: ANALYTICS) tries to build a path `Customer.ssn`, the compiler rejects with "relationship not found" - they don't even know it exists.

**AWS Equivalent**: Lake Formation column-level + custom metadata for relationship ACLs.

---

### REQ-LDM-06: Grain Metadata (The Aggregation Hierarchy)

**The Problem**: You have tables at different aggregation levels:
- `transactions` (atomic, 1 row per event)
- `daily_summary` (1 row per customer per day)
- `monthly_rollup` (1 row per customer per month)

Mixing them in a query without explicit aggregation is a bug.

**CDME Grain Hierarchy**:
```yaml
grains:
  - name: ATOMIC
    level: 0
    description: Raw events, one row per occurrence
  - name: DAILY
    level: 1
    aggregates: ATOMIC
    dimensions: [customer_id, date]
  - name: MONTHLY
    level: 2
    aggregates: DAILY
    dimensions: [customer_id, year_month]
  - name: YEARLY
    level: 3
    aggregates: MONTHLY
```

**Compiler Behavior**:
```sql
-- INVALID: Mixing grains without aggregation
SELECT
  t.transaction_id,       -- ATOMIC
  d.daily_total           -- DAILY
FROM transactions t
JOIN daily_summary d ON t.customer_id = d.customer_id
-- ERROR: Cannot project ATOMIC and DAILY in same row without explicit aggregation
```

```sql
-- VALID: Explicit aggregation
SELECT
  d.customer_id,
  d.daily_total,
  SUM(t.amount) as atomic_sum  -- Aggregating ATOMIC to DAILY
FROM daily_summary d
JOIN transactions t
  ON t.customer_id = d.customer_id
  AND t.date = d.date
GROUP BY d.customer_id, d.daily_total
```

---

## Part 2: Physical Data Model (PDM) - Storage Abstraction

### REQ-PDM-01: Logical/Physical Separation

**The Problem**: Your business logic is tangled with storage paths. Moving from `s3://prod-bucket/orders/` to `s3://new-bucket/orders_v2/` breaks everything.

**CDME Solution**: Business logic references **logical names only**.

```yaml
# pdm/bindings.yaml
bindings:
  - entity: Order
    environments:
      dev:
        type: glue_table
        database: dev_db
        table: orders
      prod:
        type: glue_table
        database: prod_db
        table: orders
      test:
        type: s3_parquet
        path: s3://test-bucket/orders/
```

**Mapping Definition** (storage-agnostic):
```yaml
mapping:
  source: Order
  target: OrderEnriched
  transformations:
    - type: join
      with: Customer
      on: customer_id
```

**Benefit**: Same mapping works in dev (small Parquet files) and prod (partitioned Delta Lake).

---

### REQ-PDM-02: Event vs Snapshot Semantics

**The Problem**: Not all data is the same "shape":
- **Events**: Immutable occurrences (clicks, transactions, logs)
- **Snapshots**: State at a point in time (customer profile, inventory level)

Treating them the same causes bugs.

**CDME Source Declaration**:
```yaml
# pdm/sources/clickstream.yaml
source: Clickstream
generation_grain: EVENT
boundary: hourly  # Partition by hour
immutable: true   # Events never change

# pdm/sources/customer_profile.yaml
source: CustomerProfile
generation_grain: SNAPSHOT
boundary: daily   # Daily snapshot
supersedes: true  # Today's snapshot replaces yesterday's
```

**Compiler Behavior**:
- Can't do "as-of" queries on pure event streams
- Snapshot joins require temporal semantics declaration

---

### REQ-PDM-03: Epoch/Partition Boundaries

**The Problem**: When you run a daily job, what data goes in?

**CDME Epoch Definition**:
```yaml
job:
  name: daily_order_summary
  epoch:
    type: temporal
    field: order_date
    granularity: daily
    strategy: partition_filter  # Use S3 partition pruning
```

**AWS Equivalent**: Glue partition predicates, Athena partition pruning.

---

### REQ-PDM-04: Lookup Tables (Reference Data)

**Two types of lookups**:

1. **Data-backed**: Regular table (country codes, product catalog)
2. **Logic-backed**: Function (calculate age from birthdate)

```yaml
lookups:
  - name: CountryCode
    type: data_backed
    source: s3://reference-data/country_codes.parquet
    version: 2024-01-15  # Pinned version for reproducibility

  - name: AgeCalculator
    type: logic_backed
    function: |
      def calculate_age(birthdate, as_of_date):
        return (as_of_date - birthdate).years
    version: v1.2.0  # Function version
```

**Key Requirement**: Lookups MUST be versioned. Same input + same lookup version = same output.

---

### REQ-PDM-05: Temporal Binding (Table Routing by Date)

**The Problem**: You have `orders_2023`, `orders_2024` tables. Your logical model just says `Order`.

**CDME Temporal Binding**:
```yaml
entity: Order
temporal_binding:
  strategy: yearly_partition
  routing: |
    if epoch.year == 2024:
      return "orders_2024"
    elif epoch.year == 2023:
      return "orders_2023"
    else:
      raise EpochNotSupported
```

**AWS Equivalent**: Athena federation, Glue crawlers with pattern-based table creation.

---

## Part 3: Transformation Engine (TRV)

### REQ-TRV-01: Context Lifting (Handling 1:N Joins)

**The Problem**: When you traverse a 1:N relationship, you're no longer working with single rows.

```python
# Before: Working with Order (1 row)
order = Order.get(123)

# After: Traversing to line_items (N rows)
items = order.line_items  # Now we have a List[LineItem]

# The "context" has lifted from Scalar → List
```

**CDME Tracking**:
```
Path: Order.line_items.product.price
Context: Scalar → List → List (after 1:N)
         Must aggregate or intentionally work with lists
```

**Spark Equivalent**: This is what `explode()` does. CDME just makes it explicit and validated.

---

### REQ-TRV-02: Grain Safety (No Accidental Fan-Out)

**The #1 ETL Bug**: Joining at wrong grain, causing duplicate aggregation.

**CDME Prevention**:
```sql
-- Compiler rejects this
SELECT
  o.order_id,
  SUM(o.order_total),      -- ATOMIC grain (orders)
  SUM(d.daily_revenue)     -- DAILY grain (daily_summary)
FROM orders o
JOIN daily_summary d ON o.customer_id = d.customer_id
GROUP BY o.order_id

-- Error: Cannot aggregate attributes from incompatible grains (ATOMIC, DAILY)
-- without explicit grain alignment
```

**Valid Pattern**:
```sql
-- First aggregate orders to daily grain
WITH order_daily AS (
  SELECT customer_id, DATE(order_date) as date, SUM(order_total) as daily_orders
  FROM orders
  GROUP BY customer_id, DATE(order_date)
)
-- Then join at same grain
SELECT
  od.customer_id,
  od.daily_orders,
  ds.daily_revenue
FROM order_daily od
JOIN daily_summary ds
  ON od.customer_id = ds.customer_id
  AND od.date = ds.date
```

---

### REQ-TRV-03: Temporal Semantics (Cross-Epoch Joins)

**The Problem**: Joining "today's orders" with "yesterday's reference data" - is that valid?

**CDME Declaration**:
```yaml
mapping:
  source: Order  # Today's epoch
  lookups:
    - entity: ProductCatalog
      temporal_semantics: AS_OF  # Use catalog valid at order_date
    - entity: ExchangeRate
      temporal_semantics: LATEST  # Always use latest rate
    - entity: TaxRules
      temporal_semantics: EXACT   # Must match epoch exactly
```

**AWS Equivalent**: This is what Delta Lake Time Travel or Iceberg snapshots provide.

---

### REQ-TRV-04: Execution Telemetry

Every morphism execution captures:

```json
{
  "morphism_id": "order_enrichment",
  "input_rows": 1000000,
  "output_rows": 1000000,
  "dropped_rows": 0,
  "error_rows": 150,
  "duration_ms": 4532,
  "memory_peak_mb": 2048,
  "partitions_read": 24,
  "partitions_written": 24
}
```

**AWS Equivalent**: Glue job metrics, Spark UI stats, exported to CloudWatch/OpenLineage.

---

### REQ-TRV-05: Deterministic Reproducibility

**The Contract**: Same inputs + same mapping + same lookups = bit-identical outputs.

**What This Means**:
- No `current_timestamp()` in transformations (use job-level epoch timestamp)
- No `random()` (use deterministic hash if needed)
- All lookups are versioned
- Partition ordering doesn't affect output

**AWS Enforcement**:
- Glue job parameters include all dependency versions
- Output includes manifest of exact inputs used

---

### REQ-TRV-06: Cost Estimation (Prevent Runaway Jobs)

**The Problem**: A bad join blows up to 10B rows and bankrupts your Athena budget.

**CDME Cost Estimation**:
```yaml
job:
  name: customer_enrichment
  budget:
    max_output_rows: 100_000_000
    max_shuffle_size_gb: 500
    max_duration_minutes: 60
```

**Before Execution**:
```
Cost Estimate:
- Input: 10M orders, 1M customers
- Join: orders ⋈ customers (N:1, no fan-out)
- Expected output: 10M rows
- Estimated shuffle: 2GB
- Estimated duration: 5 minutes

✓ Within budget, proceeding...
```

**Over Budget**:
```
Cost Estimate:
- Input: 10M orders, 500M line_items
- Join: orders ⋈ line_items (1:N fan-out!)
- Expected output: 500M+ rows (exceeds max_output_rows: 100M)

✗ BLOCKED: Estimated output 500M exceeds budget 100M
  Suggestion: Add aggregation or filter before expansion
```

---

### REQ-SHF-01: Partition Compatibility (Sheaf Consistency)

**The Problem**: Joining two datasets with incompatible partitioning causes massive shuffles.

**CDME Validation**:
```yaml
# Source A: partitioned by (customer_id, date)
# Source B: partitioned by (product_id)
# Join on: customer_id

# Compiler warning:
# "Join requires shuffle on B (not partitioned by customer_id)"
# Consider co-partitioning sources or accepting shuffle cost
```

**AWS Equivalent**: This is what Spark's partition pruning optimizes, but CDME validates at definition time.

---

## Part 4: Type System (TYP)

### REQ-TYP-01: Rich Types (Not Just Primitives)

**Beyond SQL Types**:

| CDME Type | SQL Equivalent | Benefit |
|-----------|---------------|---------|
| `Int` | INTEGER | Basic |
| `Option[Int]` | INTEGER (nullable) | Explicit null handling |
| `Either[Error, Int]` | - | Failures are data, not exceptions |
| `Money(amount, currency)` | DECIMAL + VARCHAR | Semantic type |
| `Percent(value, 0..100)` | DECIMAL | Refined type with constraint |

**Type Declaration**:
```yaml
entity: Order
attributes:
  - name: total_amount
    type: Money
    currency_field: currency_code

  - name: discount_percent
    type: Percent
    constraint: value >= 0 AND value <= 100

  - name: customer_id
    type: Option[String]  # Explicitly nullable
```

---

### REQ-TYP-02: Refinement Types (Data Quality as Types)

**The Problem**: A column is "INTEGER" but business rules say it must be positive.

**CDME Refinement**:
```yaml
types:
  PositiveInteger:
    base: Int
    constraint: value > 0
    on_violation: route_to_error_domain

  ValidEmail:
    base: String
    pattern: "^[a-zA-Z0-9+_.-]+@[a-zA-Z0-9.-]+$"
    on_violation: route_to_error_domain

  FutureDate:
    base: Date
    constraint: value > CURRENT_DATE
```

**Usage**:
```yaml
entity: Order
attributes:
  - name: quantity
    type: PositiveInteger  # Negative values go to error domain
```

---

### REQ-TYP-03: Error Domain (Failures as Data)

**The Problem**: One bad record shouldn't crash the entire job.

**CDME Strategy**: Bad records are routed, not thrown.

```python
# Traditional ETL:
try:
    result = transform(row)
except:
    log.error("Failed!")
    raise  # Job dies

# CDME approach:
result = transform(row)  # Returns Either[Error, Row]
if result.is_error:
    error_domain.append(result.error)  # Continue processing
else:
    output.append(result.value)
```

**Error Domain Table**:
```sql
CREATE TABLE error_domain (
  job_id STRING,
  epoch_id STRING,
  source_entity STRING,
  source_key STRING,
  morphism_path STRING,
  error_type STRING,        -- 'REFINEMENT_VIOLATION', 'TYPE_ERROR', etc.
  error_detail JSON,
  offending_values JSON,
  created_at TIMESTAMP
);
```

**AWS Equivalent**: Dead Letter Queue pattern, but for data transformation.

---

### REQ-TYP-03-A: Batch Failure Threshold

**The Problem**: If 90% of records fail, it's probably a config error, not bad data.

**CDME Circuit Breaker**:
```yaml
job:
  error_handling:
    threshold:
      type: percentage
      value: 5  # Halt if > 5% of records fail
    on_breach:
      action: halt
      commit_successful: false  # Rollback everything
```

**Behavior**: First 10K rows, 7% fail → halt immediately, don't process 100M rows.

---

### REQ-TYP-05: No Implicit Casting

**The Problem**: Spark happily casts `"123abc"` to `123` (truncating), causing silent data loss.

**CDME Enforcement**:
```sql
-- REJECTED at compile time:
SELECT CAST(order_id AS INT) FROM orders  -- Implicit string→int

-- REQUIRED explicit morphism:
SELECT safe_cast_to_int(order_id) FROM orders
-- Returns Either[CastError, Int]
-- Failures routed to error domain
```

**AWS Equivalent**: Use Glue data quality rules + custom cast UDFs.

---

### REQ-TYP-06: Type Unification (Join Compatibility)

**The Problem**: Joining `orders.customer_id (VARCHAR)` with `customers.id (INT)` - Spark allows it, but it's a bug waiting to happen.

**CDME Validation**:
```
Join: orders.customer_id (String) ⋈ customers.customer_id (String)
✓ Types match

Join: orders.customer_id (String) ⋈ customers.id (Int)
✗ Type mismatch: String cannot join Int without explicit cast morphism
```

---

### REQ-TYP-07: Semantic Types (Prevent Category Errors)

**The Problem**: Adding `order_amount` (dollars) to `discount_rate` (percentage) is nonsense.

**CDME Semantic Types**:
```yaml
semantic_types:
  Money:
    base: Decimal(18,2)
    operations:
      add: Money + Money → Money
      multiply: Money * Number → Money
      invalid: Money + Percent  # Compile error

  Percent:
    base: Decimal(5,4)
    constraint: 0 <= value <= 1
    operations:
      apply: Percent * Money → Money  # Discount calculation
      add: Percent + Percent → Percent
      invalid: Percent + Money  # Compile error
```

**Compile-Time Check**:
```sql
SELECT order_amount + discount_rate
-- ERROR: Cannot add Money + Percent
-- Did you mean: order_amount * (1 - discount_rate)?
```

---

## Part 5: Lineage & Backward Traversal (INT, ADJ)

### REQ-INT-03: Full Lineage (Target → Source)

**The Promise**: For any output row, tell me exactly which source rows contributed.

**Lineage Record**:
```json
{
  "target_table": "customer_360",
  "target_key": "cust_12345",
  "target_epoch": "2024-01-15",
  "sources": [
    {
      "entity": "orders",
      "keys": ["ord_001", "ord_002", "ord_003"],
      "epoch": "2024-01-15"
    },
    {
      "entity": "customer_profile",
      "keys": ["cust_12345"],
      "epoch": "2024-01-14",
      "temporal_semantics": "AS_OF"
    }
  ],
  "morphism_path": "Order → CustomerEnriched → Customer360",
  "lookup_versions": {
    "CountryCode": "v2024.01",
    "ProductCatalog": "v3.2.1"
  }
}
```

**AWS Equivalent**: OpenLineage events → S3 → Athena for querying.

---

### REQ-ADJ-04: Reverse Lookups for Aggregations

**The Problem**: You have `customer_daily_summary` with aggregated revenue. Which orders contributed?

**Traditional Approach**: Re-run the aggregation query filtered by customer. Expensive.

**CDME Adjoint Approach**: Capture the mapping during forward execution.

```sql
-- Forward execution creates:
-- 1. customer_daily_summary (the output)
-- 2. customer_daily_summary_adjoint (the reverse mapping)

-- Adjoint table:
CREATE TABLE customer_daily_summary_adjoint (
  summary_key STRING,      -- PK of aggregated row
  source_keys ARRAY<STRING>, -- PKs of contributing orders
  morphism_id STRING,
  epoch_id STRING
);

-- Impact analysis query:
SELECT source_keys
FROM customer_daily_summary_adjoint
WHERE summary_key = 'cust_123_2024-01-15';
-- Returns: ['ord_001', 'ord_002', 'ord_003']
```

**Storage Strategies**:
| Strategy | Storage Overhead | Query Speed | Use Case |
|----------|-----------------|-------------|----------|
| Inline array | +20-50% | O(1) | Small groups |
| Separate table | +10-20% | O(log n) | Large groups |
| Roaring Bitmap | +5-10% | O(1) | High cardinality |

---

### REQ-ADJ-08: Data Reconciliation

**The Use Case**: Verify that your pipeline didn't lose any data.

```python
# Reconciliation check:
# backward(forward(input)) ⊇ input

# For aggregations:
# If I have 1000 orders → aggregate to 100 daily summaries
# Then expand daily summaries back to contributing orders
# I should get >= 1000 orders (maybe duplicates if order spans days)

recon_result = reconcile(
  input_dataset=orders,
  output_dataset=daily_summary,
  adjoint_table=daily_summary_adjoint
)

assert recon_result.missing_records == 0
assert recon_result.containment_valid == True
```

---

### REQ-ADJ-09: Impact Analysis

**The Use Case**: A customer complains about their January invoice. Which source records contributed?

```python
# Start from the target (invoice line)
invoice_line = InvoiceLine.get("inv_2024_001_line_5")

# Traverse backward through the pipeline
impact = analyze_impact(
  target_record=invoice_line,
  pipeline="order_to_invoice"
)

# Returns:
{
  "invoice_line": "inv_2024_001_line_5",
  "contributing_sources": {
    "orders": ["ord_123", "ord_456"],
    "payments": ["pay_789"],
    "adjustments": ["adj_001"]
  },
  "morphism_path": [
    "InvoiceLine ← InvoiceCalc ← OrderAgg ← Order",
    "InvoiceLine ← InvoiceCalc ← PaymentAgg ← Payment"
  ]
}
```

**Performance**: O(|target_subset|), not O(|full_dataset|).

---

## Part 6: AI Assurance (AI)

### REQ-AI-01: Hallucination Prevention

**The Problem**: LLM generates: "Join orders.customer_email with customers.email"
But `orders.customer_email` doesn't exist.

**CDME Validation**:
```python
# AI generates mapping
ai_mapping = llm.generate_mapping(intent="Enrich orders with customer info")

# CDME validates against LDM
validation = compiler.validate(ai_mapping, ldm=schema_registry)

# Result:
{
  "valid": false,
  "errors": [
    {
      "type": "MORPHISM_NOT_FOUND",
      "path": "orders.customer_email",
      "suggestion": "Did you mean orders.customer_id → customers.email?"
    }
  ]
}
```

**What CDME Catches**:
- Non-existent columns/relationships (hallucinated paths)
- Type mismatches (String joined to Int)
- Grain violations (atomic + daily mixed)
- Access control violations (column user can't see)

**What CDME Doesn't Catch**:
- Semantic correctness (is this the RIGHT business logic?)
- Human review still required for business validation

---

### REQ-AI-03: Dry Run Mode

**The Use Case**: Validate a mapping without executing it.

```bash
cdme validate --mapping order_enrichment.yaml --dry-run

Output:
┌──────────────────────────────────────────────────┐
│ CDME Dry Run Report                              │
├──────────────────────────────────────────────────┤
│ Topology Validation     ✓ All paths valid        │
│ Type Unification        ✓ All types compatible   │
│ Grain Safety            ✓ No grain violations    │
│ Access Control          ✓ All morphisms allowed  │
│ Cost Estimate           15M input → 15M output   │
│ Estimated Duration      ~8 minutes               │
│ Lineage Graph           Generated (see attached) │
└──────────────────────────────────────────────────┘
```

---

## Part 7: AWS Reference Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         CDME on AWS                                      │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐     │
│  │  Schema         │    │  CDME           │    │  Glue Job /     │     │
│  │  Registry       │───▶│  Compiler       │───▶│  EMR / Athena   │     │
│  │  (Glue Catalog) │    │  (Lambda/Step)  │    │  (Execution)    │     │
│  └─────────────────┘    └─────────────────┘    └────────┬────────┘     │
│         │                        │                       │              │
│         │                        ▼                       ▼              │
│  ┌──────▼──────────┐    ┌─────────────────┐    ┌─────────────────┐     │
│  │  LDM Definitions│    │  Mapping        │    │  S3 Data Lake   │     │
│  │  (S3/DynamoDB)  │    │  Artifacts      │    │  (Output)       │     │
│  └─────────────────┘    │  (S3)           │    └────────┬────────┘     │
│                         └─────────────────┘             │              │
│                                                         ▼              │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐     │
│  │  Lake Formation │    │  OpenLineage    │    │  Adjoint        │     │
│  │  (Access Ctrl)  │    │  (Lineage)      │    │  Metadata (S3)  │     │
│  └─────────────────┘    └─────────────────┘    └─────────────────┘     │
│                                                                          │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐     │
│  │  CloudWatch     │    │  Error Domain   │    │  SNS/EventBridge│     │
│  │  (Telemetry)    │    │  (S3/Athena)    │    │  (Alerts)       │     │
│  └─────────────────┘    └─────────────────┘    └─────────────────┘     │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Part 8: Requirement to Implementation Mapping

| Requirement | What It Means | AWS Implementation |
|-------------|---------------|-------------------|
| REQ-LDM-01 | Schema as graph | Glue Catalog + relationship metadata |
| REQ-LDM-02 | Cardinality (1:1, N:1, 1:N) | Glue table properties + CDME validation |
| REQ-LDM-03 | Path validation | Lambda compiler before Glue job |
| REQ-LDM-04 | Monoidal aggregations | Registered aggregators, reject non-associative |
| REQ-LDM-05 | Column-level ACL | Lake Formation + CDME path filtering |
| REQ-LDM-06 | Grain metadata | Glue table properties, validated at compile |
| REQ-PDM-01 | Logical/physical separation | Glue Catalog abstraction |
| REQ-PDM-02 | Event vs Snapshot | Source configuration metadata |
| REQ-PDM-03 | Epoch boundaries | Glue partition predicates |
| REQ-PDM-04 | Versioned lookups | S3 versioning + manifest |
| REQ-PDM-05 | Temporal binding | Athena table routing |
| REQ-TRV-01 | Context lifting (1:N) | Spark explode() + CDME tracking |
| REQ-TRV-02 | Grain safety | Compile-time validation |
| REQ-TRV-03 | Temporal semantics | Delta Lake time travel |
| REQ-TRV-04 | Telemetry | CloudWatch + OpenLineage |
| REQ-TRV-05 | Deterministic execution | Versioned dependencies, no random/timestamp |
| REQ-TRV-06 | Cost estimation | EXPLAIN output, budget enforcement |
| REQ-SHF-01 | Partition compatibility | Glue partition metadata check |
| REQ-INT-03 | Full lineage | OpenLineage to S3, Athena queryable |
| REQ-TYP-01 | Rich types | CDME type system, Glue schema |
| REQ-TYP-02 | Refinement types | Glue data quality + CDME validation |
| REQ-TYP-03 | Error domain | DLQ pattern, error table in S3 |
| REQ-TYP-05 | No implicit casting | CDME compile-time rejection |
| REQ-TYP-06 | Type unification | Join type compatibility check |
| REQ-AI-01 | Hallucination prevention | LDM validation of AI mappings |
| REQ-AI-03 | Dry run | Lambda validation endpoint |
| REQ-ADJ-04 | Aggregation reverse lookup | Adjoint tables in S3 |
| REQ-ADJ-08 | Reconciliation | Containment check job |
| REQ-ADJ-09 | Impact analysis | Backward traversal query |

---

## Part 9: Configuration Artifacts (Mapping + Transformation + Sources + Operations)

This section defines the **minimal artifact schemas** for a CDME job. These are the building blocks that data engineers author.

### 9.1 Mapping Definition Schema

A **mapping** is the core artifact - it declares what goes in, what comes out, and what transformations connect them.

```yaml
# mappings/order_enrichment.yaml
apiVersion: cdme/v1
kind: Mapping
metadata:
  name: order_enrichment
  version: 1.0.0
  description: Enrich orders with customer and product details
  owner: data-platform-team
  tags: [orders, customer-360, daily]

spec:
  # Source entities (input side)
  sources:
    - entity: Order
      alias: o
      filter: "order_status != 'CANCELLED'"

    - entity: Customer
      alias: c
      join:
        type: LEFT
        on: "o.customer_id = c.customer_id"
        cardinality: N:1  # Many orders per customer

    - entity: Product
      alias: p
      join:
        type: LEFT
        on: "o.product_id = p.product_id"
        cardinality: N:1

  # Target entity (output side)
  target:
    entity: OrderEnriched
    grain: ATOMIC  # Same grain as source
    grain_key: [order_id]

  # Transformations (what to compute)
  transformations:
    - name: order_id
      expr: "o.order_id"

    - name: customer_name
      expr: "c.full_name"

    - name: product_category
      expr: "p.category"

    - name: order_total_usd
      expr: "o.amount * lookup('ExchangeRate', o.currency, 'USD')"
      type: Money

    - name: is_high_value
      expr: "o.amount > 10000"
      type: Boolean

  # Lookups required
  lookups:
    - name: ExchangeRate
      version: AS_OF
      as_of_field: o.order_date

  # Validation rules
  validations:
    - name: positive_amount
      expr: "order_total_usd > 0"
      on_fail: route_to_error_domain
```

**Required Fields**:
| Field | Purpose | Example |
|-------|---------|---------|
| `metadata.name` | Unique identifier | `order_enrichment` |
| `spec.sources` | Input entities with join logic | `Order LEFT JOIN Customer` |
| `spec.target.entity` | Output entity name | `OrderEnriched` |
| `spec.target.grain` | Aggregation level | `ATOMIC`, `DAILY` |
| `spec.transformations` | Column expressions | `o.amount * rate` |

---

### 9.2 Transformation Types

Transformations declare **how** data flows from source to target.

```yaml
# transformation_types.yaml - Registry of allowed transformations
apiVersion: cdme/v1
kind: TransformationType
metadata:
  name: standard_transformations

spec:
  # Direct projection (no change)
  - type: PROJECTION
    signature: "A → A"
    description: Direct column copy
    example: "o.order_id"

  # Join enrichment (lookup)
  - type: JOIN_ENRICHMENT
    signature: "A → A ⊕ B"
    cardinality: [N:1, 1:1]
    description: Add columns from joined entity
    example: "LEFT JOIN customer ON o.customer_id = c.customer_id"

  # Aggregation (grain coarsening)
  - type: AGGREGATION
    signature: "List[A] → B"
    cardinality: N:1
    grain_change: FINER → COARSER
    requires_group_by: true
    allowed_functions: [SUM, COUNT, MIN, MAX, AVG, COLLECT_LIST]
    example: "SUM(o.amount) GROUP BY customer_id, date"

  # Explosion (grain refinement)
  - type: EXPLOSION
    signature: "A → List[B]"
    cardinality: 1:N
    grain_change: COARSER → FINER
    requires_context_lift: true
    example: "LATERAL VIEW explode(line_items)"

  # Computed column (expression)
  - type: EXPRESSION
    signature: "A → A'"
    description: Derived column from expression
    allowed_operations: [arithmetic, string, date, conditional]
    example: "o.quantity * o.unit_price AS total"

  # Lookup (reference data)
  - type: LOOKUP
    signature: "A × Ref → A'"
    description: Enrich with versioned reference data
    requires_version: true
    example: "lookup('CountryCode', o.country, '2024.01')"

  # Type cast (explicit)
  - type: SAFE_CAST
    signature: "A → Either[Error, B]"
    description: Type conversion with error handling
    routes_errors: true
    example: "safe_cast(o.quantity, INT)"
```

**Transformation in Mapping Context**:

```yaml
transformations:
  # Projection - direct copy
  - name: order_id
    type: PROJECTION
    expr: "o.order_id"

  # Expression - computed value
  - name: total_with_tax
    type: EXPRESSION
    expr: "o.subtotal * (1 + tax_rate)"
    output_type: Money

  # Aggregation - grain coarsening
  - name: daily_revenue
    type: AGGREGATION
    expr: "SUM(o.amount)"
    group_by: [customer_id, order_date]
    output_grain: DAILY

  # Lookup - reference data enrichment
  - name: country_name
    type: LOOKUP
    lookup_table: CountryCode
    lookup_key: o.country_code
    lookup_value: country_name
    lookup_version: "2024.01.15"

  # Safe cast - explicit type conversion
  - name: quantity_int
    type: SAFE_CAST
    expr: "o.quantity"
    from_type: String
    to_type: Int
    on_fail: route_to_error_domain
```

---

### 9.3 Source Configuration Schema

Sources declare **where** data comes from and **how** to partition/filter it.

```yaml
# sources/orders_source.yaml
apiVersion: cdme/v1
kind: Source
metadata:
  name: orders
  entity: Order  # Links to LDM entity

spec:
  # Physical binding (PDM layer)
  binding:
    type: glue_table
    database: production_db
    table: orders
    partition_columns: [order_date]

  # Or S3 direct:
  # binding:
  #   type: s3_parquet
  #   path: s3://data-lake/orders/
  #   partition_pattern: "year={yyyy}/month={MM}/day={dd}"

  # Generation characteristics
  generation:
    grain: EVENT              # Each row is an event occurrence
    immutable: true           # Events don't change after creation
    append_only: true         # No updates, only inserts

  # Temporal properties
  temporal:
    event_time_field: order_timestamp
    processing_time_field: ingestion_timestamp
    watermark: "5 minutes"    # Late data tolerance

  # Boundaries (what data to read)
  boundaries:
    epoch:
      type: partition_filter
      field: order_date
      strategy: exact         # Only this epoch's partition

    # Or time-based window:
    # epoch:
    #   type: time_window
    #   field: order_timestamp
    #   window: "24 hours"

  # Data quality expectations
  expectations:
    row_count_min: 1000       # Alert if below
    null_rate_max:
      customer_id: 0.01       # Max 1% nulls
      order_id: 0             # No nulls allowed
    freshness_max: "2 hours"  # Data should be recent
```

**Snapshot Source Example**:

```yaml
# sources/customer_profile_source.yaml
apiVersion: cdme/v1
kind: Source
metadata:
  name: customer_profile
  entity: Customer

spec:
  binding:
    type: glue_table
    database: production_db
    table: customer_profiles

  generation:
    grain: SNAPSHOT           # State at a point in time
    immutable: false          # Profiles change
    supersedes: true          # Today's snapshot replaces yesterday's

  temporal:
    snapshot_time_field: snapshot_date
    valid_from: effective_date
    valid_to: expiry_date     # SCD Type 2 support

  boundaries:
    epoch:
      type: latest_snapshot   # Use most recent complete snapshot
```

---

### 9.4 Semantic Labels (Operational Grain Identification)

Semantic labels provide **machine-readable metadata** that enables orchestration tools, catalogs, and governance systems to understand mappings without parsing transformation logic.

> **Note**: Job orchestration (scheduling, retries, notifications) is delegated to external tools (Airflow, Prefect, Step Functions). CDME defines *what* the mapping does; orchestrators define *when* and *how* to run it.

```yaml
# Semantic labels in mapping metadata
apiVersion: cdme/v1
kind: Mapping
metadata:
  name: order_enrichment
  version: 1.0.0

  # Semantic labels for operational classification
  labels:
    # Temporal grain - how often this mapping produces new data
    cdme.io/temporal-grain: daily
    cdme.io/epoch-field: order_date

    # Data classification
    cdme.io/data-domain: sales
    cdme.io/data-product: customer-360
    cdme.io/pii-level: contains-pii

    # Lineage behavior
    cdme.io/lineage-mode: key-derivable
    cdme.io/adjoint-enabled: "true"

    # Compliance/governance
    cdme.io/retention-policy: 7-years
    cdme.io/gdpr-relevant: "true"

    # Operational hints (for orchestrators)
    cdme.io/estimated-duration: medium     # quick|medium|long|very-long
    cdme.io/resource-profile: standard     # minimal|standard|memory-intensive|compute-intensive
    cdme.io/idempotent: "true"
    cdme.io/deterministic: "true"

    # Dependency hints
    cdme.io/upstream-freshness: "2h"       # Max staleness of inputs
    cdme.io/downstream-sla: "6h"           # Must complete by this time after epoch

    # Custom organizational labels
    acme.com/cost-center: analytics-team
    acme.com/data-steward: jane.smith
```

**Standard Label Taxonomy**:

| Label | Values | Purpose |
|-------|--------|---------|
| `cdme.io/temporal-grain` | `realtime`, `hourly`, `daily`, `weekly`, `monthly`, `adhoc` | Epoch granularity |
| `cdme.io/epoch-field` | Field name | Which field determines epoch membership |
| `cdme.io/data-domain` | Domain name | Business domain classification |
| `cdme.io/data-product` | Product name | Data product this mapping belongs to |
| `cdme.io/pii-level` | `none`, `contains-pii`, `contains-sensitive-pii` | Privacy classification |
| `cdme.io/lineage-mode` | `full`, `key-derivable`, `sampled`, `none` | Lineage capture strategy |
| `cdme.io/adjoint-enabled` | `true`, `false` | Whether reverse lookup is captured |
| `cdme.io/deterministic` | `true`, `false` | Same inputs → same outputs |
| `cdme.io/idempotent` | `true`, `false` | Safe to re-run |
| `cdme.io/estimated-duration` | `quick`, `medium`, `long`, `very-long` | Runtime estimate |
| `cdme.io/resource-profile` | `minimal`, `standard`, `memory-intensive`, `compute-intensive` | Resource requirements |

**Label Inheritance**:

Labels can be defined at multiple levels with override semantics:

```yaml
# ldm/domains/sales.yaml - Domain-level defaults
apiVersion: cdme/v1
kind: Domain
metadata:
  name: sales
  labels:
    cdme.io/data-domain: sales
    cdme.io/retention-policy: 7-years
    cdme.io/gdpr-relevant: "true"
    acme.com/cost-center: sales-analytics

---
# mappings/order_enrichment.yaml - Mapping overrides domain
metadata:
  name: order_enrichment
  domain: sales                    # Inherits sales domain labels
  labels:
    cdme.io/temporal-grain: daily  # Mapping-specific
    cdme.io/pii-level: contains-pii  # Override if needed
```

**Using Labels for Discovery**:

```bash
# Find all daily mappings in sales domain
cdme list mappings \
  --label cdme.io/temporal-grain=daily \
  --label cdme.io/data-domain=sales

# Find PII-containing mappings for compliance audit
cdme list mappings \
  --label cdme.io/pii-level=contains-pii

# Find all mappings for a data product
cdme list mappings \
  --label cdme.io/data-product=customer-360
```

**Orchestrator Integration Example** (Airflow):

```python
# Airflow DAG generator reads CDME labels
mapping = cdme.load_mapping("order_enrichment")

# Extract operational hints from labels
schedule = {
    "daily": "@daily",
    "hourly": "@hourly",
    "weekly": "@weekly"
}.get(mapping.labels.get("cdme.io/temporal-grain"), None)

resource_pool = {
    "minimal": "small_pool",
    "standard": "default_pool",
    "memory-intensive": "high_memory_pool",
    "compute-intensive": "high_cpu_pool"
}.get(mapping.labels.get("cdme.io/resource-profile"), "default_pool")

# Generate DAG from mapping + labels
dag = generate_dag(
    mapping=mapping,
    schedule_interval=schedule,
    pool=resource_pool,
    retries=3 if mapping.labels.get("cdme.io/idempotent") == "true" else 1
)
```

---

### 9.5 Complete Artifact Structure

A complete CDME deployment includes:

```
cdme/
├── ldm/                          # Logical Data Model
│   ├── entities/
│   │   ├── order.yaml            # Entity definitions
│   │   ├── customer.yaml
│   │   └── product.yaml
│   ├── relationships/
│   │   └── order_relationships.yaml
│   ├── grains/
│   │   └── hierarchy.yaml        # Grain levels
│   └── domains/
│       └── sales.yaml            # Domain-level label defaults
│
├── pdm/                          # Physical Data Model
│   ├── sources/
│   │   ├── orders_source.yaml    # Source bindings
│   │   └── customers_source.yaml
│   └── bindings/
│       └── environment_bindings.yaml
│
├── mappings/                     # Mapping definitions
│   ├── order_enrichment.yaml
│   └── customer_360.yaml
│
├── types/                        # Custom types
│   ├── semantic_types.yaml       # Money, Percent, etc.
│   └── refinement_types.yaml     # PositiveInteger, etc.
│
└── lookups/                      # Reference data config
    ├── exchange_rates.yaml
    └── country_codes.yaml
```

> **Out of Scope**: Job orchestration configuration (DAGs, schedules, alerts) lives in orchestrator-specific tooling (Airflow, Prefect, etc.), not in CDME artifacts. CDME provides labels that orchestrators can consume.

---

### 9.6 Immutable Run Hierarchy (ADR-009)

Every execution is bound to an **immutable snapshot** of configuration, code, and design. This enables complete reproducibility and audit trails.

**RunID Structure**:
```
RunID = run_{timestamp}_{hash}

Where hash = SHA256(config_hash + code_hash + design_hash)
```

**Hierarchy Levels**:

```
Level 0: RUN (Execution Instance)
    │    run_id, epoch, timestamp, executor
    ▼
Level 1: CONFIG SNAPSHOT
    │    mapping@version, sources, lookups, labels
    │    git_commit, config_hash
    ▼
Level 2: CODE SNAPSHOT
    │    transformations_hash, udf_versions
    │    engine_version, cdme_version
    ▼
Level 3: DESIGN SNAPSHOT
         adrs_in_effect, ldm_version
         type_system_version, requirements_trace
```

**Immutable Manifest** (stored per run):

```yaml
apiVersion: cdme/v1
kind: RunManifest
metadata:
  run_id: run_20240115_143022_a1b2c3

spec:
  execution:
    epoch_timestamp: "2024-01-15T00:00:00Z"
    triggered_by: airflow/dag_order_enrichment

  config:
    snapshot_id: cfg_sha256_abc123
    git_commit: 7a8b9c0d...
    mapping: order_enrichment@v1.2.3
    lookups:
      ExchangeRate: {version: "2024.01.15", hash: sha256:...}

  code:
    snapshot_id: code_sha256_def456
    cdme_version: "1.5.0"
    engine: spark-3.4.1

  design:
    snapshot_id: design_sha256_789xyz
    adrs: [ADR-001@v1, ADR-003@v2, ADR-009@v1]
    ldm_version: ldm_sha256_111222

  checksums:
    manifest: sha256:entire_manifest
    inputs: sha256:all_input_data
    outputs: sha256:all_output_data
```

**Key Guarantees**:
- **Reproducibility**: Same manifest → bit-identical outputs
- **Auditability**: Run → Config → Code → Design → Requirements chain
- **Comparison**: `cdme diff run_001 run_002` shows exactly what changed

See [ADR-009](adrs/ADR-009-immutable-run-hierarchy.md) for full specification.

---

### 9.7 Minimal Viable Mapping

For simple mappings, a minimal definition:

```yaml
# mappings/simple_filter.yaml
apiVersion: cdme/v1
kind: Mapping
metadata:
  name: active_orders
  version: 1.0.0

spec:
  sources:
    - entity: Order
      alias: o
      filter: "status = 'ACTIVE'"

  target:
    entity: ActiveOrder
    grain: ATOMIC
    grain_key: [order_id]

  transformations:
    - name: "*"              # Project all columns
      from: o
```

**Inference Rules**:
- If no `transformations` → project all source columns
- If no `grain_key` → infer from source primary key
- If no `join` → single source, no enrichment
- If no `lookups` → no reference data needed

---

## Part 10: Data Quality Guarantees for Data Engineers

This section maps common data engineering concerns to CDME requirements, showing how category-theoretic foundations provide formal guarantees for data quality issues that traditionally require ad-hoc validation.

### 10.1 Data Quality Concerns Matrix

| Data Quality Concern | Traditional Approach | CDME Guarantee | Requirements |
|---------------------|---------------------|----------------|--------------|
| **Missing data** | Null checks, COALESCE | Option types, refinement predicates | REQ-TYP-01, REQ-TYP-02 |
| **Duplicate records** | DISTINCT, dedup jobs | Grain keys, morphism cardinality | REQ-LDM-06, REQ-LDM-02 |
| **Schema drift** | Schema registry alerts | Immutable LDM topology | REQ-LDM-01, REQ-TRV-05-B |
| **Type mismatches** | Cast errors at runtime | Compile-time type unification | REQ-TYP-06, REQ-TYP-05 |
| **Referential integrity** | FK constraints, orphan checks | Morphism path validation | REQ-LDM-03, REQ-LDM-02 |
| **Data freshness** | Timestamp checks, SLAs | Epoch semantics, temporal binding | REQ-PDM-03, REQ-TRV-03 |
| **Completeness** | Row count validation | Coverage invariants, adjoint containment | REQ-COV-02, REQ-ADJ-08 |
| **Consistency** | Reconciliation jobs | Fidelity verification, certificate chain | REQ-COV-03, REQ-COV-08 |
| **Grain mixing** | Manual review, testing | Compile-time grain safety | REQ-TRV-02, REQ-LDM-06 |
| **Silent failures** | Log monitoring | Either monad, Error Domain | REQ-TYP-03, REQ-ERROR-01 |
| **Non-determinism** | Flaky test detection | Deterministic execution contract | REQ-TRV-05, REQ-INT-06 |
| **Data loss** | Reconciliation, audits | Adjoint round-trip containment | REQ-ADJ-08, REQ-ADJ-04 |

---

### 10.2 Missing Data & Nulls

**Why This Matters:**
- Unexpected NULLs breaking downstream logic
- COALESCE chains hiding data quality issues
- Ambiguity: "Is NULL missing or legitimately absent?"

**CDME Guarantees:**

```yaml
# Option types make nullability explicit
entity: Order
attributes:
  - name: customer_id
    type: String              # NOT NULL - absence is error
  - name: discount_code
    type: Option[String]      # Nullable - absence is valid
  - name: shipping_address
    type: Option[Address]     # Nested optional
```

| Scenario | Traditional | CDME |
|----------|-------------|------|
| Unexpected NULL in required field | Runtime NPE or silent corruption | REQ-TYP-02: Refinement violation → Error Domain |
| NULL propagation through joins | Silent NULL expansion | REQ-TYP-01: Option type propagation tracked |
| Ambiguous NULL semantics | Documentation (ignored) | REQ-TYP-01: `Option[T]` vs `T` is type-level |

**Requirements Providing Guarantee:**
- **REQ-TYP-01**: Extended type system with Option types
- **REQ-TYP-02**: Refinement types catch constraint violations
- **REQ-TYP-03**: Violations route to Error Domain, never silent

---

### 10.3 Duplicate Records

**Why This Matters:**
- Accidental row multiplication from bad joins
- Dedup logic that silently drops records
- Uncertainty: "Which record is authoritative?"

**CDME Guarantees:**

```yaml
# Grain keys define uniqueness
entity: DailySummary
grain: DAILY
grain_key: [customer_id, date]  # Unique constraint at this grain

# Cardinality prevents accidental fan-out
relationship:
  name: customer
  cardinality: N:1  # Many orders per customer - row count preserved
```

| Scenario | Traditional | CDME |
|----------|-------------|------|
| Join causes row multiplication | Runtime discovery, expensive reprocessing | REQ-LDM-02: 1:N flagged, context lifting required |
| Dedup drops wrong record | Logic bugs, data loss | REQ-LDM-04: Aggregations are monoidal, deterministic |
| Duplicate detection | Ad-hoc dedup jobs | REQ-LDM-06: Grain key uniqueness enforced |

**Requirements Providing Guarantee:**
- **REQ-LDM-02**: Cardinality types (1:1, N:1, 1:N) explicit
- **REQ-LDM-06**: Grain key defines uniqueness at each level
- **REQ-TRV-01**: Context lifting makes fan-out explicit

---

### 10.4 Schema Drift & Evolution

**Why This Matters:**
- Upstream schema changes breaking pipelines
- Column renames causing silent nulls
- Traceability: "What version of the schema was used?"

**CDME Guarantees:**

```yaml
# Schema is immutable topology - changes create new versions
ldm_version: ldm_sha256_abc123

# Every run binds to specific schema version
run_manifest:
  design:
    ldm_version: ldm_sha256_abc123  # Immutable reference
```

| Scenario | Traditional | CDME |
|----------|-------------|------|
| Column renamed upstream | Runtime failure or silent NULL | REQ-LDM-03: Path validation fails at compile time |
| Type changed upstream | Cast errors at runtime | REQ-TYP-06: Type unification fails at compile time |
| "What schema was used?" | Log archaeology | REQ-TRV-05-A: Run manifest binds to design snapshot |

**Requirements Providing Guarantee:**
- **REQ-LDM-01**: Schema as immutable topology
- **REQ-LDM-03**: Path validation against topology
- **REQ-TRV-05-A**: Immutable run hierarchy binds to schema version
- **REQ-TRV-05-B**: Artifact version binding prevents silent changes

---

### 10.5 Referential Integrity

**Why This Matters:**
- Orphan records from failed joins
- FK violations in denormalized data
- Validity: "Does this relationship actually exist?"

**CDME Guarantees:**

```yaml
# Relationships are first-class topology elements
relationship:
  name: customer
  source: Order
  target: Customer
  cardinality: N:1
  join_key: customer_id
  # This relationship EXISTS or path validation fails
```

| Scenario | Traditional | CDME |
|----------|-------------|------|
| Orphan records (no matching FK) | NULL from LEFT JOIN, or dropped from INNER | REQ-TYP-03: Either monad captures join failures |
| Non-existent relationship | Runtime error or wrong results | REQ-LDM-03: Path validation rejects at compile time |
| Circular references | Stack overflow or infinite loops | REQ-LDM-01: DAG structure enforced in topology |

**Requirements Providing Guarantee:**
- **REQ-LDM-01**: Relationships defined in topology
- **REQ-LDM-02**: Cardinality validated
- **REQ-LDM-03**: Path validation ensures relationships exist
- **REQ-TYP-03**: Join failures captured in Error Domain

---

### 10.6 Data Freshness & Latency

**Why This Matters:**
- Stale data in dashboards
- Processing data from wrong time window
- Currency: "Is this data from today or yesterday?"

**CDME Guarantees:**

```yaml
# Epoch semantics are explicit
source:
  entity: Order
  boundaries:
    epoch:
      type: partition_filter
      field: order_date
      strategy: exact  # Only this epoch's data

# Cross-epoch joins require declaration
mapping:
  lookups:
    - entity: ExchangeRate
      temporal_semantics: AS_OF  # Explicit: use rate valid at order_date
```

| Scenario | Traditional | CDME |
|----------|-------------|------|
| Processing wrong partition | Logic bugs, reprocessing | REQ-PDM-03: Epoch boundaries explicit |
| Joining stale reference data | Silent incorrectness | REQ-TRV-03: Cross-boundary requires temporal semantics |
| "When was this data from?" | Timestamp column inspection | REQ-TRV-05-A: Run manifest captures epoch |

**Requirements Providing Guarantee:**
- **REQ-PDM-03**: Boundary definition (how data is sliced)
- **REQ-TRV-03**: Temporal semantics for cross-boundary joins
- **REQ-INT-06**: Versioned lookups with temporal binding
- **REQ-SHF-01**: Sheaf consistency (same epoch context)

---

### 10.7 Completeness & Coverage

**Why This Matters:**
- Missing records in aggregations
- Incomplete data causing wrong totals
- Accountability: "Did we process everything?"

**CDME Guarantees:**

```yaml
# Fidelity invariants prove completeness
invariant:
  name: order_coverage
  type: COVERAGE
  expr: "count(output.orders) >= count(input.orders)"
  on_violation: BREACH

# Adjoint containment proves round-trip
reconciliation:
  check: "backward(forward(input)) ⊇ input"
  # Every input record is accounted for in output
```

| Scenario | Traditional | CDME |
|----------|-------------|------|
| Records dropped silently | Row count reconciliation | REQ-ADJ-08: Adjoint containment validation |
| Filter dropped too much | Manual inspection | REQ-ADJ-05: Filter adjoint captures filtered keys |
| Aggregation missing groups | Sum validation | REQ-COV-02: Coverage invariants |

**Requirements Providing Guarantee:**
- **REQ-ADJ-04**: Aggregation adjoints capture contributing keys
- **REQ-ADJ-05**: Filter adjoints capture filtered-out keys
- **REQ-ADJ-08**: Data reconciliation via adjoint round-trip
- **REQ-COV-02**: Fidelity invariants (Coverage type)

---

### 10.8 Consistency & Reconciliation

**Why This Matters:**
- Numbers don't match between systems
- Reconciliation breaks finding discrepancies
- Cross-domain consistency: "Why doesn't Finance match Risk?"

**CDME Guarantees:**

```yaml
# Cross-domain fidelity contracts
contract:
  name: finance_risk_fidelity
  invariants:
    - name: pnl_conservation
      type: CONSERVATION
      expr: "sum(finance.pnl) == sum(risk.pnl_input)"
      materiality_threshold: 0.01  # $0.01 tolerance
      on_breach: HALT
```

| Scenario | Traditional | CDME |
|----------|-------------|------|
| Cross-system mismatch | Monthly reconciliation nightmare | REQ-COV-03: Fidelity verification with certificates |
| "When did it break?" | Log archaeology | REQ-COV-08: Certificate chain shows exactly when |
| Tolerance creep | Gradually increasing thresholds | REQ-COV-02: Materiality thresholds auditable |

**Requirements Providing Guarantee:**
- **REQ-COV-01**: Cross-domain covariance contracts
- **REQ-COV-02**: Fidelity invariants with materiality thresholds
- **REQ-COV-03**: Fidelity verification produces certificates
- **REQ-COV-08**: Certificate chain proves continuous fidelity

---

### 10.9 Silent Failures & Error Handling

**Why This Matters:**
- Errors swallowed by try/catch
- Bad records silently dropped
- False confidence: "Did it actually succeed?"

**CDME Guarantees:**

```yaml
# Failures are data, not exceptions
result: Either[Error, Value]

# Error Domain captures all failures
error_domain:
  - source_key: "order_123"
    error_type: REFINEMENT_VIOLATION
    morphism_path: "Order.amount → PositiveDecimal"
    offending_value: -50.00
    # Nothing silently dropped - all failures queryable
```

| Scenario | Traditional | CDME |
|----------|-------------|------|
| Exception swallowed | Silent corruption | REQ-TYP-03: Either monad - no exceptions |
| Record dropped | Missing data mystery | REQ-ERROR-01: Error Domain captures all failures |
| Too many failures | Job "succeeds" with garbage | REQ-TYP-03-A: Batch threshold halts on systemic failure |

**Requirements Providing Guarantee:**
- **REQ-TYP-03**: Either monad - failures are data
- **REQ-TYP-03-A**: Batch failure threshold (circuit breaker)
- **REQ-ERROR-01**: Minimal error object content
- **REQ-TYP-04**: Idempotency of failure (same error every time)

---

### 10.10 Non-Determinism & Reproducibility

**Why This Matters:**
- Different results on re-run
- Flaky tests and pipelines
- Debugging impossibility: "Why did it work yesterday but not today?"

**CDME Guarantees:**

```yaml
# Forbidden non-deterministic operations
forbidden:
  - CURRENT_TIMESTAMP()  # Use epoch_timestamp
  - RANDOM()             # Use hash(key, seed)
  - UUID()               # Use UUID_V5(namespace, key)

# All dependencies versioned
run_manifest:
  lookups:
    ExchangeRate: {version: "2024.01.15", hash: sha256:abc}
  # Same inputs + same config = bit-identical outputs
```

| Scenario | Traditional | CDME |
|----------|-------------|------|
| Different results on re-run | Debugging nightmare | REQ-TRV-05: Deterministic execution contract |
| Lookup data changed | Silent incorrectness | REQ-INT-06: Versioned lookups |
| "What inputs were used?" | Log archaeology | REQ-TRV-05-A: Immutable run manifest |

**Requirements Providing Guarantee:**
- **REQ-TRV-05**: Deterministic reproducibility
- **REQ-INT-06**: Versioned lookups
- **REQ-INT-07**: Deterministic key generation
- **REQ-TRV-05-A**: Immutable run hierarchy

---

### 10.11 Data Loss Detection

**Why This Matters:**
- Records vanishing in pipeline
- Aggregation hiding missing data
- Auditability: "Where did those 1000 records go?"

**CDME Guarantees:**

```yaml
# Adjoint morphisms enable backward traversal
aggregation:
  name: daily_summary
  adjoint:
    type: PROJECTION
    backward: "returns set of contributing order_ids"
    storage: daily_summary_adjoint

# Reconciliation proves containment
backward(forward(orders)) ⊇ orders
# Every input order is accounted for in some output
```

| Scenario | Traditional | CDME |
|----------|-------------|------|
| Records lost in aggregation | Manual reconciliation | REQ-ADJ-04: Aggregation adjoints |
| Records lost in filter | Undetectable | REQ-ADJ-05: Filter adjoints (optional capture) |
| "Which inputs made this output?" | Expensive re-computation | REQ-ADJ-09: Impact analysis via backward traversal |

**Requirements Providing Guarantee:**
- **REQ-ADJ-04**: Aggregation backward captures contributing keys
- **REQ-ADJ-05**: Filter backward captures filtered keys
- **REQ-ADJ-08**: Data reconciliation via containment
- **REQ-ADJ-09**: Impact analysis (backward traversal)

---

### 10.12 Identified Gaps & Proposed Requirements

Analysis of common data quality concerns identified these potential gaps:

| Gap | Description | Proposed Requirement |
|-----|-------------|---------------------|
| **Late-arriving data** | Data arrives after epoch closes | REQ-PDM-06: Late arrival handling |
| **Data volume anomalies** | Unexpected row count changes | REQ-DQ-01: Volume threshold monitoring |
| **Value distribution drift** | Statistical properties change | REQ-DQ-02: Distribution monitoring |
| **Semantic validation** | Business rule validation beyond types | REQ-DQ-03: Custom validation rules |
| **Data profiling** | Automated discovery of data characteristics | REQ-DQ-04: Profiling integration |

#### Proposed: REQ-PDM-06: Late Arrival Handling

**Priority**: High
**Type**: Functional

**Description**: The system must handle late-arriving data (data that arrives after its epoch has closed) with explicit semantics.

**Acceptance Criteria**:
- Late arrival policy declared per source: REJECT, BACKFILL, QUARANTINE
- REJECT: Route to Error Domain with late arrival error
- BACKFILL: Trigger reprocessing of affected epochs
- QUARANTINE: Store for manual review
- Late arrival detection based on event_time vs processing_time
- Watermark configuration for acceptable lateness window
- Late arrivals logged with full context for audit

#### Proposed: REQ-DQ-01: Volume Threshold Monitoring

**Priority**: Medium
**Type**: Non-Functional (Observability)

**Description**: The system must detect anomalous data volumes that may indicate upstream issues.

**Acceptance Criteria**:
- Volume thresholds configurable per source: min_rows, max_rows, expected_range
- Thresholds can be absolute or relative to historical baseline
- Threshold breach triggers: WARN, HALT, or ALERT
- Empty input handling: explicit (error vs valid empty set)
- Volume metrics captured in telemetry (REQ-TRV-04)

#### Proposed: REQ-DQ-02: Distribution Monitoring

**Priority**: Low
**Type**: Non-Functional (Observability)

**Description**: The system should detect statistical distribution changes that may indicate data quality issues.

**Acceptance Criteria**:
- Distribution metrics: cardinality, null rate, value range, percentiles
- Baseline established from historical data
- Anomaly detection for significant deviations
- Distribution drift alerts (not enforcement - observability only)
- Integration with data catalog for profiling metadata

---

### 10.13 Summary: Category Theory → Data Quality

The categorical foundations provide these data quality guarantees:

| Category Concept | Data Quality Guarantee |
|-----------------|----------------------|
| **Types as Objects** | Schema correctness, null safety |
| **Morphisms as Arrows** | Transformation validity, referential integrity |
| **Composition** | Path correctness, no broken chains |
| **Functors** | Consistent mapping (LDM→PDM) |
| **Monoids** | Safe aggregation, parallelizable |
| **Either Monad** | Explicit error handling, no silent failures |
| **Option Monad** | Explicit null handling |
| **Kleisli Arrows** | Explicit fan-out, context lifting |
| **Adjoints** | Backward traversal, data reconciliation |
| **Sheaves** | Context consistency, epoch alignment |

**The key insight**: Traditional data quality requires runtime checks, monitoring, and reconciliation. CDME shifts most guarantees to **compile time** through the type system and topology validation. Runtime checks are reserved for **value-level** validation (refinement types, fidelity invariants).

---

## Part 11: Cross-Domain Fidelity Architecture

**Implements**: REQ-COV-01, REQ-COV-02, REQ-COV-03, REQ-COV-04, REQ-COV-05, REQ-COV-06, REQ-COV-07, REQ-COV-08

### 11.1 Overview: Why Cross-Domain Fidelity

Complex enterprises operate multiple data domains that must maintain consistent views of the same business reality:

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│    FINANCE      │     │      RISK       │     │   REGULATORY    │
│                 │     │                 │     │                 │
│  P&L, Positions │◄───►│  VaR, Exposure  │◄───►│  BCBS, FRTB     │
│  Trade Capture  │     │  Stress Testing │     │  Reporting      │
└─────────────────┘     └─────────────────┘     └─────────────────┘
        │                       │                       │
        └───────────────────────┼───────────────────────┘
                                │
                    ┌───────────▼───────────┐
                    │   FIDELITY CONTRACT   │
                    │                       │
                    │ "These domains MUST   │
                    │  agree on positions,  │
                    │  valuations, and      │
                    │  risk metrics"        │
                    └───────────────────────┘
```

**The Problem**: Without formal contracts, domains diverge silently:
- Finance books a trade, Risk doesn't see it for 3 days
- P&L says +$1M, Risk says +$950K - which is right?
- Regulatory report uses stale Risk data

**CDME Solution**: Formal covariance contracts with mathematical fidelity invariants and cryptographic verification.

---

### 11.2 Covariance Contract Schema (REQ-COV-01)

A covariance contract defines how entities in one domain relate to entities in another:

```yaml
# Covariance Contract Definition
contract:
  id: contract_finance_risk_positions
  version: "1.0.0"
  status: ACTIVE

  domains:
    source:
      name: finance
      entity: Position
      grain: ATOMIC
      grain_key: [position_id]
    target:
      name: risk
      entity: RiskPosition
      grain: ATOMIC
      grain_key: [position_id]

  # Cross-domain morphism
  mapping:
    type: COVARIANT  # Changes propagate source → target
    cardinality: "1:1"
    join_key: position_id

  # Grain alignment requirement
  grain_alignment:
    required: true
    source_grain: ATOMIC
    target_grain: ATOMIC
    # Cross-grain requires explicit aggregation morphism

  # Fidelity invariants (Section 11.3)
  invariants:
    - ref: position_conservation
    - ref: valuation_alignment
    - ref: exposure_containment

  # Enforcement mode
  enforcement:
    mode: STRICT  # STRICT | DEFERRED | ADVISORY
    on_violation: HALT
```

**Contract Registry**: Contracts are versioned artifacts in the Schema Registry, subject to the same immutability rules as LDM entities (REQ-TRV-05-B).

---

### 11.3 Fidelity Invariants (REQ-COV-02)

Fidelity invariants are mathematical assertions that must hold between domains:

```yaml
# Fidelity Invariant Definitions
invariants:

  # Conservation: Value preservation across domains
  - id: position_conservation
    type: CONSERVATION
    description: "Total notional must match between Finance and Risk"
    expression: |
      sum(finance.Position.notional) == sum(risk.RiskPosition.notional)
    grain: ATOMIC
    materiality_threshold:
      type: ABSOLUTE
      value: 0.01  # $0.01 tolerance
      currency: USD
    justification: "Rounding tolerance for currency conversion"

  # Coverage: Completeness guarantee
  - id: position_coverage
    type: COVERAGE
    description: "Risk must have all Finance positions"
    expression: |
      count(risk.RiskPosition) >= count(finance.Position)
    grain: ATOMIC
    materiality_threshold:
      type: PERCENTAGE
      value: 0.0  # Zero tolerance - all positions required

  # Alignment: Temporal consistency
  - id: valuation_alignment
    type: ALIGNMENT
    description: "Valuation dates must match"
    expression: |
      finance.Position.valuation_date == risk.RiskPosition.as_of_date
    grain: ATOMIC
    materiality_threshold: null  # Exact match required

  # Containment: Subset relationship
  - id: exposure_containment
    type: CONTAINMENT
    description: "Risk exposure keys must be subset of Finance"
    expression: |
      keys(risk.RiskPosition.exposure_id) ⊆ keys(finance.Position.position_id)
    grain: ATOMIC
```

**Invariant Types**:

| Type | Mathematical Form | Use Case |
|------|------------------|----------|
| CONSERVATION | `sum(A) == sum(B)` | Value preservation (P&L, notional) |
| COVERAGE | `count(B) >= count(A)` | Completeness (all records present) |
| ALIGNMENT | `A.field == B.field` | Temporal/value consistency |
| CONTAINMENT | `keys(A) ⊆ keys(B)` | Subset relationships |

**Materiality Thresholds**: Explicit, auditable tolerances for immaterial differences:
- ABSOLUTE: Fixed value ($0.01, 1 record)
- PERCENTAGE: Relative value (0.1% of total)
- NONE: Exact match required

---

### 11.4 Fidelity Verification Service (REQ-COV-03)

The verification service proves that invariants hold at a point in time:

```
┌─────────────────────────────────────────────────────────────────┐
│                  FIDELITY VERIFICATION SERVICE                   │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────┐   ┌──────────────┐   ┌────────────────────────┐  │
│  │ Contract │──►│  Invariant   │──►│  Fidelity Certificate  │  │
│  │ Registry │   │  Evaluator   │   │  (Cryptographic Proof) │  │
│  └──────────┘   └──────────────┘   └────────────────────────┘  │
│       │               │                        │                │
│       │               ▼                        ▼                │
│       │        ┌──────────────┐        ┌──────────────┐        │
│       │        │   Domain     │        │  Certificate │        │
│       └───────►│   Snapshots  │        │    Chain     │        │
│                │  (Hash-bound)│        │  (Immutable) │        │
│                └──────────────┘        └──────────────┘        │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

**Verification Process**:

```python
# Conceptual verification flow
def verify_fidelity(contract_id: str, epoch: str) -> FidelityCertificate:

    # 1. Load contract and invariants
    contract = contract_registry.get(contract_id)

    # 2. Snapshot both domains at epoch
    source_snapshot = snapshot_domain(contract.source, epoch)
    target_snapshot = snapshot_domain(contract.target, epoch)

    # 3. Evaluate each invariant
    results = []
    for invariant in contract.invariants:
        result = evaluate_invariant(
            invariant=invariant,
            source=source_snapshot,
            target=target_snapshot
        )
        results.append(InvariantResult(
            invariant_id=invariant.id,
            status=result.status,  # PASS | BREACH_IMMATERIAL | BREACH_MATERIAL | BREACH_CRITICAL
            expected=result.expected,
            actual=result.actual,
            difference=result.difference,
            within_threshold=result.within_threshold
        ))

    # 4. Generate cryptographic certificate
    certificate = FidelityCertificate(
        certificate_id=generate_certificate_id(),
        contract_id=contract_id,
        contract_version=contract.version,
        epoch=epoch,
        timestamp=utc_now(),
        source_snapshot_hash=source_snapshot.hash,
        target_snapshot_hash=target_snapshot.hash,
        invariant_results=results,
        overall_status=compute_overall_status(results),
        previous_certificate=get_previous_certificate(contract_id),
        signature=sign_certificate(...)
    )

    # 5. Store immutably
    certificate_store.store(certificate)

    return certificate
```

**Verification Modes**:
- **EXHAUSTIVE**: Check all records (O(n))
- **STATISTICAL**: Sample-based verification with confidence interval
- **INCREMENTAL**: Delta verification since last certificate

---

### 11.5 Contract Breach Detection (REQ-COV-04)

When invariants fail, breaches are classified and handled:

```yaml
# Breach Classification
breach_severity:
  IMMATERIAL:
    description: "Difference within materiality threshold"
    action: LOG_ONLY
    escalation: NONE

  MATERIAL:
    description: "Difference exceeds threshold but not structural"
    action: ALERT_AND_QUARANTINE
    escalation: DATA_STEWARD
    sla_hours: 24

  CRITICAL:
    description: "Structural breach - missing entities, broken relationships"
    action: HALT_DEPENDENT_PROCESSING
    escalation: DATA_OWNER
    sla_hours: 4
```

**Breach Record Structure**:

```yaml
breach:
  id: breach_20241215_001
  certificate_id: cert_abc123
  contract_id: contract_finance_risk_positions
  invariant_id: position_conservation

  severity: MATERIAL

  details:
    expected: 1000000.00
    actual: 999500.00
    difference: 500.00
    threshold: 0.01
    threshold_exceeded_by: 499.99

  contributing_records:
    # Via adjoint backward traversal (REQ-ADJ-09)
    source_keys: [pos_001, pos_002, pos_003]
    target_keys: [risk_001, risk_002]

  context:
    source_snapshot_hash: sha256:abc
    target_snapshot_hash: sha256:def
    epoch: "2024-12-15"

  response:
    action_taken: QUARANTINE
    quarantine_flag: FIDELITY_BREACH_MATERIAL
    notification_sent: true
    assigned_to: data_steward_team

  remediation:
    status: PENDING  # PENDING | IN_PROGRESS | RESOLVED | ACCEPTED
    resolution: null
    resolved_by: null
    resolved_at: null
```

---

### 11.6 Contract Enforcement (REQ-COV-07)

Enforcement prevents violations **before** they corrupt domain state:

```
┌─────────────────────────────────────────────────────────────────┐
│                    ENFORCEMENT PIPELINE                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Proposed    ┌──────────────┐   ┌──────────────┐   Committed    │
│  Change  ───►│  Pre-flight  │──►│ Transactional│──►  State      │
│              │  Validation  │   │   Commit     │                │
│              └──────────────┘   └──────────────┘                │
│                     │                  │                        │
│                     ▼                  ▼                        │
│              ┌──────────────┐   ┌──────────────┐                │
│              │   Reject if  │   │  Both domains│                │
│              │   invariants │   │  succeed or  │                │
│              │   would fail │   │  both fail   │                │
│              └──────────────┘   └──────────────┘                │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

**Enforcement Modes**:

| Mode | Behavior | Use Case |
|------|----------|----------|
| STRICT | Reject any operation that would violate invariants | Production, regulated |
| DEFERRED | Allow operation, require remediation within window | Migration periods |
| ADVISORY | Warn but allow (audit trail only) | Development, testing |

**Pre-flight Validation**:

```python
# Conceptual enforcement check
def enforce_contract(
    contract: CovarianceContract,
    proposed_changes: List[DomainChange]
) -> EnforcementResult:

    # Simulate the change
    simulated_source = apply_changes(current_source, proposed_changes)
    simulated_target = current_target  # Or also apply if bidirectional

    # Check invariants against simulated state
    violations = []
    for invariant in contract.invariants:
        result = evaluate_invariant(invariant, simulated_source, simulated_target)
        if result.status != PASS:
            violations.append(result)

    if violations and contract.enforcement.mode == STRICT:
        return EnforcementResult(
            allowed=False,
            reason="Invariant violations detected",
            violations=violations,
            remediation_suggestions=suggest_fixes(violations)
        )

    return EnforcementResult(allowed=True)
```

---

### 11.7 Fidelity Certificate Chain (REQ-COV-08)

Certificates form an immutable chain proving continuous fidelity:

```
┌─────────────┐   ┌─────────────┐   ┌─────────────┐   ┌─────────────┐
│  Cert N-2   │──►│  Cert N-1   │──►│   Cert N    │──►│  Cert N+1   │
│             │   │             │   │             │   │             │
│ hash: abc   │   │ hash: def   │   │ hash: ghi   │   │ hash: jkl   │
│ prev: ...   │   │ prev: abc   │   │ prev: def   │   │ prev: ghi   │
│ status: OK  │   │ status: OK  │   │ status: OK  │   │ status: ?   │
└─────────────┘   └─────────────┘   └─────────────┘   └─────────────┘
      │                 │                 │                 │
      ▼                 ▼                 ▼                 ▼
   Dec 12            Dec 13            Dec 14            Dec 15
```

**Chain Properties**:
- Each certificate references previous certificate hash
- Gaps are detectable (missing verification runs)
- Tamper-evident (hash chain)
- Supports branching (different grain levels, different contracts)

**Chain Query API**:

```python
# Query continuous fidelity proof
def prove_continuous_fidelity(
    contract_id: str,
    start_date: date,
    end_date: date
) -> ContinuousFidelityProof:

    certificates = certificate_store.get_range(
        contract_id=contract_id,
        start=start_date,
        end=end_date
    )

    return ContinuousFidelityProof(
        contract_id=contract_id,
        period=(start_date, end_date),
        certificate_count=len(certificates),
        all_passed=all(c.status == PASS for c in certificates),
        gaps=detect_gaps(certificates),
        breaches=extract_breaches(certificates),
        coverage_percentage=compute_coverage(certificates, start_date, end_date)
    )
```

---

### 11.8 Multi-Grain Fidelity (REQ-COV-06)

Fidelity must hold across grain boundaries:

```
Finance Domain                    Risk Domain

┌─────────────────┐              ┌─────────────────┐
│ ATOMIC (Trade)  │              │ ATOMIC (Risk)   │
│ trade_id        │─────────────►│ trade_id        │
│ notional        │   1:1        │ notional        │
└────────┬────────┘              └────────┬────────┘
         │ aggregation                    │ aggregation
         ▼                                ▼
┌─────────────────┐              ┌─────────────────┐
│ DAILY (Book)    │              │ DAILY (Desk)    │
│ book_id, date   │─────────────►│ desk_id, date   │
│ sum(notional)   │   N:1        │ sum(notional)   │
└────────┬────────┘              └────────┬────────┘
         │ aggregation                    │ aggregation
         ▼                                ▼
┌─────────────────┐              ┌─────────────────┐
│ MONTHLY (Firm)  │              │ MONTHLY (Firm)  │
│ firm_id, month  │─────────────►│ firm_id, month  │
│ sum(notional)   │   1:1        │ sum(notional)   │
└─────────────────┘              └─────────────────┘

INVARIANTS:
  - sum(finance.ATOMIC) == sum(finance.DAILY) == sum(finance.MONTHLY)
  - sum(risk.ATOMIC) == sum(risk.DAILY) == sum(risk.MONTHLY)
  - sum(finance.MONTHLY) == sum(risk.MONTHLY)  # Cross-domain at top grain
```

**Multi-Grain Invariant**:

```yaml
invariant:
  id: cross_grain_conservation
  type: CONSERVATION
  multi_grain: true

  levels:
    - grain: ATOMIC
      source_expr: sum(finance.Trade.notional)
      target_expr: sum(risk.RiskTrade.notional)

    - grain: DAILY
      source_expr: sum(finance.DailyBook.notional)
      target_expr: sum(risk.DailyDesk.notional)

    - grain: MONTHLY
      source_expr: sum(finance.MonthlyFirm.notional)
      target_expr: sum(risk.MonthlyFirm.notional)

  consistency_rule: |
    # Each level must match AND aggregations must be consistent
    all levels pass AND
    sum(ATOMIC) == sum(DAILY) == sum(MONTHLY)
```

---

### 11.9 AWS Reference Implementation

```
┌─────────────────────────────────────────────────────────────────┐
│                        AWS ARCHITECTURE                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────────────┐│
│  │   Contract   │   │  Invariant   │   │     Certificate      ││
│  │   Registry   │   │  Evaluator   │   │       Store          ││
│  │              │   │              │   │                      ││
│  │  DynamoDB    │   │  Step Func + │   │  S3 + DynamoDB       ││
│  │  + S3        │   │  Athena/Spark│   │  (Object Lock)       ││
│  └──────────────┘   └──────────────┘   └──────────────────────┘│
│         │                  │                     │              │
│         └──────────────────┼─────────────────────┘              │
│                            │                                    │
│                    ┌───────▼───────┐                           │
│                    │  EventBridge  │                           │
│                    │  (Scheduling) │                           │
│                    └───────────────┘                           │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

**Implementation Notes**:
- Contract Registry: DynamoDB for metadata, S3 for contract YAML
- Invariant Evaluator: Athena for SQL invariants, Spark for complex
- Certificate Store: S3 with Object Lock (WORM), DynamoDB for index
- Scheduling: EventBridge for periodic verification runs
- Integration: SNS for breach notifications, CloudWatch for metrics

---

### 11.10 Solution Design Extension Points

The following details are deferred to solution-specific designs:

| Aspect | Data Mapper Level | Solution Design Level |
|--------|------------------|----------------------|
| Contract schema | YAML structure defined | Concrete field mappings |
| Invariant types | 4 types defined | Domain-specific expressions |
| Verification service | API contract defined | Implementation code |
| Certificate format | Structure defined | Cryptographic algorithms |
| AWS implementation | Reference architecture | Terraform/CDK modules |
| UI/Dashboard | Not in scope | Solution-specific |

---

## Part 12: Data Quality Monitoring & Validation

**Implements**: REQ-PDM-06, REQ-DQ-01, REQ-DQ-02, REQ-DQ-03, REQ-DQ-04

### 12.1 Overview: Data Quality in CDME

CDME provides compile-time guarantees through the type system, but runtime data quality monitoring is essential for:

1. **Late-arriving data** - Records that arrive after epoch closure
2. **Volume anomalies** - Unexpected row counts indicating upstream issues
3. **Distribution drift** - Statistical changes in data characteristics
4. **Business rule validation** - Domain-specific checks beyond types
5. **Profiling** - Understanding data characteristics for baseline establishment

```
┌─────────────────────────────────────────────────────────────────┐
│                    DATA QUALITY LAYERS                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  COMPILE TIME (Type System)                                     │
│  ├── Schema validation (REQ-LDM-03)                             │
│  ├── Type unification (REQ-TYP-06)                              │
│  ├── Refinement types (REQ-TYP-02)                              │
│  └── Grain safety (REQ-TRV-02)                                  │
│                                                                  │
│  RUNTIME (Data Quality Monitor)                                 │
│  ├── Volume thresholds (REQ-DQ-01)                              │
│  ├── Distribution monitoring (REQ-DQ-02)                        │
│  ├── Custom validation rules (REQ-DQ-03)                        │
│  ├── Late arrival handling (REQ-PDM-06)                         │
│  └── Profiling integration (REQ-DQ-04)                          │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

### 12.2 Late Arrival Handling (REQ-PDM-06)

Late-arriving data requires explicit handling strategies:

```yaml
# Source configuration with late arrival handling
source:
  entity: Order
  physical:
    location: s3://data-lake/orders/
    format: parquet

  boundaries:
    epoch:
      type: partition_filter
      field: order_date

  late_arrival:
    # Detection: How to identify late records
    detection:
      event_time_field: order_timestamp
      processing_time_field: _processing_timestamp
      watermark: "2 hours"  # Records arriving >2h after epoch close are "late"

    # Strategy: What to do with late records
    strategy: ACCUMULATE  # REJECT | REPROCESS | ACCUMULATE | BACKFILL

    # Strategy-specific configuration
    accumulate_config:
      buffer_location: s3://data-lake/late-arrivals/orders/
      max_buffer_epochs: 3  # Accumulate for up to 3 epochs
      flush_to_next_epoch: true

    # Lineage marking
    lineage:
      mark_as_late: true
      late_arrival_flag: "_is_late_arrival"
      original_epoch_field: "_original_epoch"
```

**Late Arrival Strategies**:

| Strategy | Behavior | Use Case |
|----------|----------|----------|
| REJECT | Route to Error Domain with LATE_ARRIVAL code | Strict SLA, no tolerance |
| REPROCESS | Trigger reprocessing of affected epoch | High accuracy required |
| ACCUMULATE | Buffer for next epoch with lineage marking | Streaming with eventual consistency |
| BACKFILL | Separate pipeline with different SLAs | Historical corrections |

**Late Arrival Flow**:

```
┌────────────┐   ┌────────────────┐   ┌────────────────────────┐
│  Incoming  │──►│  Late Arrival  │──►│  Strategy Handler      │
│   Record   │   │   Detector     │   │                        │
└────────────┘   └────────────────┘   │  REJECT → Error Domain │
                        │             │  REPROCESS → Trigger   │
                        │             │  ACCUMULATE → Buffer   │
                        │             │  BACKFILL → Separate   │
                        │             └────────────────────────┘
                        │
                        ▼
                 ┌──────────────┐
                 │   Metrics    │
                 │ late_count   │
                 │ late_rate    │
                 │ avg_lateness │
                 └──────────────┘
```

---

### 12.3 Volume Threshold Monitoring (REQ-DQ-01)

Volume monitoring detects anomalous data volumes:

```yaml
# Volume threshold configuration
source:
  entity: Order

  volume_thresholds:
    enabled: true

    # Absolute thresholds
    absolute:
      min_records: 1000      # Fail if fewer than 1000 records
      max_records: 10000000  # Fail if more than 10M records

    # Relative thresholds (vs historical baseline)
    relative:
      baseline_window: "7d"  # 7-day rolling average
      min_percentage: 0.5    # Fail if <50% of baseline
      max_percentage: 2.0    # Fail if >200% of baseline

    # Zero tolerance
    zero_records: HALT  # WARN | HALT - empty input handling

    # Response to violations
    on_violation:
      below_min: HALT
      above_max: WARN

    # Metrics capture
    metrics:
      emit_to_telemetry: true
      include_baseline: true
      include_trend: true  # 7-day trend
```

**Volume Check Flow**:

```python
# Conceptual volume validation
def validate_volume(source: SourceConfig, epoch_data: DataFrame) -> VolumeResult:

    actual_count = epoch_data.count()

    # Check absolute thresholds
    if actual_count < source.volume_thresholds.absolute.min_records:
        return VolumeResult(
            status=VIOLATION,
            type=BELOW_MIN_ABSOLUTE,
            actual=actual_count,
            threshold=source.volume_thresholds.absolute.min_records,
            action=source.volume_thresholds.on_violation.below_min
        )

    # Check relative thresholds
    baseline = compute_baseline(
        source=source,
        window=source.volume_thresholds.relative.baseline_window
    )

    ratio = actual_count / baseline.average

    if ratio < source.volume_thresholds.relative.min_percentage:
        return VolumeResult(
            status=VIOLATION,
            type=BELOW_MIN_RELATIVE,
            actual=actual_count,
            baseline=baseline.average,
            ratio=ratio,
            action=source.volume_thresholds.on_violation.below_min
        )

    return VolumeResult(status=PASS, actual=actual_count, baseline=baseline)
```

---

### 12.4 Distribution Monitoring (REQ-DQ-02)

Distribution monitoring detects statistical drift in data:

```yaml
# Distribution monitoring configuration
source:
  entity: Order

  distribution_monitoring:
    enabled: true

    fields:
      - name: order_amount
        type: NUMERIC
        metrics:
          - null_rate
          - min_max
          - percentiles: [25, 50, 75, 95, 99]
          - mean_stddev
        thresholds:
          null_rate_max: 0.01      # Max 1% null
          null_rate_change: 0.005  # Max 0.5% change from baseline
          percentile_99_change: 0.20  # Max 20% change in P99

      - name: customer_id
        type: CATEGORICAL
        metrics:
          - null_rate
          - distinct_count
          - top_n: 10
        thresholds:
          null_rate_max: 0.0  # No nulls allowed
          cardinality_change: 0.10  # Max 10% change in distinct count

      - name: order_status
        type: CATEGORICAL
        metrics:
          - value_distribution
        thresholds:
          distribution_divergence:
            method: chi_squared
            p_value_threshold: 0.01

    baseline:
      window: "30d"
      min_samples: 7  # Need at least 7 days of data

    on_violation: WARN  # WARN | HALT | QUARANTINE
```

**Distribution Metrics**:

| Metric | Applicable Types | Description |
|--------|-----------------|-------------|
| null_rate | All | Percentage of null/missing values |
| distinct_count | Categorical | Cardinality of unique values |
| min_max | Numeric | Range boundaries |
| percentiles | Numeric | Distribution shape (P25, P50, P75, P95, P99) |
| mean_stddev | Numeric | Central tendency and spread |
| top_n | Categorical | Most frequent values |
| value_distribution | Categorical | Full frequency distribution |
| distribution_divergence | Categorical | Statistical test for distribution change |

---

### 12.5 Custom Validation Rules (REQ-DQ-03)

Custom validation rules express domain-specific constraints:

```yaml
# Custom validation rules
validation_rules:

  - id: rule_order_date_sequence
    description: "Order date must be before or equal to ship date"
    entity: Order
    expression: |
      order_date <= ship_date OR ship_date IS NULL
    severity: ERROR  # ERROR | WARNING
    on_failure: ERROR_DOMAIN
    error_code: INVALID_DATE_SEQUENCE
    error_message: "Ship date {ship_date} is before order date {order_date}"

  - id: rule_closed_order_has_close_date
    description: "Closed orders must have a close date"
    entity: Order
    expression: |
      status != 'CLOSED' OR close_date IS NOT NULL
    severity: ERROR
    on_failure: ERROR_DOMAIN
    error_code: MISSING_CLOSE_DATE

  - id: rule_amount_reasonable
    description: "Order amount should be reasonable"
    entity: Order
    expression: |
      order_amount > 0 AND order_amount < 10000000
    severity: WARNING
    on_failure: QUARANTINE
    error_code: SUSPICIOUS_AMOUNT

  - id: rule_valid_country
    description: "Country code must exist in reference data"
    entity: Order
    expression: |
      country_code IN (SELECT code FROM lookup.CountryCode)
    references:
      - lookup: CountryCode
        version: LATEST
    severity: ERROR
    on_failure: ERROR_DOMAIN
```

**Rule Execution Order**:

```
1. Type validation (compile-time, REQ-TYP-01)
2. Refinement type validation (runtime, REQ-TYP-02)
3. Custom validation rules (runtime, REQ-DQ-03) ← HERE
4. Transformation execution
5. Output validation
```

---

### 12.6 Profiling Integration (REQ-DQ-04)

Profiling provides data understanding and baseline establishment:

```yaml
# Profiling configuration
profiling:
  enabled: true
  mode: DRY_RUN  # DRY_RUN | CONTINUOUS | ON_DEMAND

  output:
    format: JSON  # JSON | OPENMETADATA | CUSTOM
    location: s3://data-lake/profiling/

  metrics:
    schema:
      infer_types: true
      compare_to_declared: true

    completeness:
      null_rates: true
      empty_string_rates: true

    uniqueness:
      unique_percentage: true
      duplicate_keys: true

    statistics:
      numeric:
        min_max: true
        mean_median: true
        stddev: true
        percentiles: [5, 25, 50, 75, 95]
        histogram_bins: 20
      string:
        min_max_length: true
        pattern_detection: true  # Email, phone, etc.

    referential:
      foreign_key_validity: true
      orphan_detection: true

  sampling:
    enabled: true
    sample_size: 100000
    method: RANDOM  # RANDOM | STRATIFIED | RESERVOIR
```

**Profiling Output Example**:

```json
{
  "entity": "Order",
  "epoch": "2024-12-15",
  "record_count": 1500000,
  "profiled_at": "2024-12-15T10:30:00Z",

  "fields": {
    "order_id": {
      "type_declared": "String",
      "type_inferred": "String",
      "null_rate": 0.0,
      "unique_rate": 1.0,
      "min_length": 10,
      "max_length": 10,
      "pattern": "ORD-[0-9]{6}"
    },
    "order_amount": {
      "type_declared": "Decimal(18,2)",
      "type_inferred": "Decimal",
      "null_rate": 0.001,
      "min": 0.01,
      "max": 999999.99,
      "mean": 250.50,
      "median": 125.00,
      "stddev": 450.25,
      "percentiles": {
        "p5": 10.00,
        "p25": 50.00,
        "p50": 125.00,
        "p75": 300.00,
        "p95": 1000.00
      }
    },
    "customer_id": {
      "type_declared": "String",
      "null_rate": 0.0,
      "distinct_count": 50000,
      "top_values": [
        {"value": "CUST-001", "count": 15000},
        {"value": "CUST-002", "count": 12000}
      ]
    }
  },

  "referential_integrity": {
    "customer_id → Customer.id": {
      "valid": 1499850,
      "orphan": 150,
      "orphan_rate": 0.0001
    }
  }
}
```

---

### 12.7 Data Quality Monitor Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                   DATA QUALITY MONITOR                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────────────┐│
│  │    Config    │   │   Baseline   │   │      Metrics         ││
│  │    Store     │   │    Store     │   │      Store           ││
│  │              │   │              │   │                      ││
│  │  (Thresholds,│   │ (Historical  │   │  (Results, Trends,   ││
│  │   Rules)     │   │  profiles)   │   │   Alerts)            ││
│  └──────────────┘   └──────────────┘   └──────────────────────┘│
│         │                  │                     │              │
│         └──────────────────┼─────────────────────┘              │
│                            │                                    │
│                    ┌───────▼───────┐                           │
│                    │   DQ Engine   │                           │
│                    │               │                           │
│                    │ Late Arrival  │                           │
│                    │ Volume Check  │                           │
│                    │ Distribution  │                           │
│                    │ Custom Rules  │                           │
│                    │ Profiling     │                           │
│                    └───────────────┘                           │
│                            │                                    │
│              ┌─────────────┼─────────────┐                     │
│              ▼             ▼             ▼                     │
│        ┌──────────┐  ┌──────────┐  ┌──────────┐               │
│        │  PASS    │  │ QUARANTINE│  │  ERROR   │               │
│        │ (proceed)│  │ (flagged) │  │  DOMAIN  │               │
│        └──────────┘  └──────────┘  └──────────┘               │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

### 12.8 Integration with Error Domain

Data quality violations integrate with the standard Error Domain (REQ-TYP-03):

```yaml
# Error Domain entries for DQ violations
error_domain:

  # Late arrival error
  - error_type: LATE_ARRIVAL
    source_key: "order_123"
    morphism_path: "source.Order"
    context:
      event_time: "2024-12-14T23:59:00Z"
      processing_time: "2024-12-15T04:30:00Z"
      epoch: "2024-12-14"
      lateness_hours: 4.5
      strategy_applied: REJECT

  # Volume threshold error
  - error_type: VOLUME_THRESHOLD_VIOLATION
    source_key: null  # Epoch-level error
    morphism_path: "source.Order"
    context:
      epoch: "2024-12-15"
      actual_count: 500
      threshold: 1000
      threshold_type: MIN_ABSOLUTE
      baseline_average: 1500

  # Distribution drift error
  - error_type: DISTRIBUTION_DRIFT
    source_key: null  # Field-level error
    morphism_path: "source.Order.order_amount"
    context:
      epoch: "2024-12-15"
      field: "order_amount"
      metric: "null_rate"
      expected: 0.001
      actual: 0.05
      threshold: 0.005

  # Custom rule violation
  - error_type: CUSTOM_RULE_VIOLATION
    source_key: "order_456"
    morphism_path: "source.Order"
    context:
      rule_id: "rule_order_date_sequence"
      rule_description: "Order date must be before ship date"
      values:
        order_date: "2024-12-15"
        ship_date: "2024-12-10"
```

---

### 12.9 AWS Reference Implementation

```
┌─────────────────────────────────────────────────────────────────┐
│                        AWS ARCHITECTURE                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────────────┐│
│  │    Config    │   │   Baseline   │   │      Metrics         ││
│  │  DynamoDB    │   │     S3 +     │   │    CloudWatch +      ││
│  │              │   │   Athena     │   │    OpenSearch        ││
│  └──────────────┘   └──────────────┘   └──────────────────────┘│
│         │                  │                     │              │
│         └──────────────────┼─────────────────────┘              │
│                            │                                    │
│                    ┌───────▼───────┐                           │
│                    │  Glue / EMR   │                           │
│                    │  (DQ Engine)  │                           │
│                    └───────────────┘                           │
│                            │                                    │
│              ┌─────────────┼─────────────┐                     │
│              ▼             ▼             ▼                     │
│        ┌──────────┐  ┌──────────┐  ┌──────────┐               │
│        │   SNS    │  │    S3    │  │    S3    │               │
│        │ (Alerts) │  │(Quarantine)│ │(Error DQ)│               │
│        └──────────┘  └──────────┘  └──────────┘               │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

**AWS Services Mapping**:
- Config Store: DynamoDB (thresholds, rules)
- Baseline Store: S3 + Athena (historical profiles, queryable)
- Metrics Store: CloudWatch Metrics + OpenSearch (visualization)
- DQ Engine: Glue ETL or EMR Spark (execution)
- Alerts: SNS → Slack/PagerDuty
- Quarantine: S3 bucket with lifecycle policies
- Error Domain: S3 (parquet) + Glue Catalog

---

### 12.10 Solution Design Extension Points

The following details are deferred to solution-specific designs:

| Aspect | Data Mapper Level | Solution Design Level |
|--------|------------------|----------------------|
| Late arrival strategy | Options defined | Strategy selection per source |
| Volume thresholds | Schema defined | Specific threshold values |
| Distribution metrics | Metric types defined | Field-specific configuration |
| Custom rules | Rule schema defined | Domain-specific rules |
| Profiling output | Format defined | Dashboard integration |
| AWS implementation | Reference architecture | Terraform/CDK modules |
| Alerting | Integration points | PagerDuty/Slack configuration |

---

## Appendix A: Glossary for Data Engineers

| CDME Term | Plain English |
|-----------|--------------|
| **Morphism** | Any transformation: SELECT, JOIN, UDF, aggregation |
| **Topology** | Your schema graph (tables + relationships) |
| **Grain** | Aggregation level (atomic row vs daily summary vs monthly rollup) |
| **Epoch** | Batch window (today's partition, this hour's data) |
| **Adjoint** | Reverse lookup (given output, find contributing inputs) |
| **Functor** | A consistent mapping (LDM entity → S3 path) |
| **Monoidal** | Aggregation that can be parallelized and combined (SUM, COUNT) |
| **Kleisli** | 1:N expansion (one order → many line items) |
| **Sheaf** | Data that belongs together (same epoch, same partition key) |
| **Either** | Result that's either Success or Error (not exception) |

---

## Appendix B: When to Use CDME

**CDME is foundational for:**
- Regulatory compliance (BCBS 239, FRTB, EU AI Act)
- AI/LLM-generated data mappings that need validation
- Automated mapping generation with assurance
- Complex multi-grain data models
- Full lineage and impact analysis
- Preventing silent data corruption

**This is not optional tooling** - it's the ontological foundation for trustworthy data pipelines. Every transformation, whether simple or complex, benefits from compile-time validation and lineage. The investment in proper topology pays dividends in automated assurance as pipelines scale.

---

**Document Status**: Draft
**Last Updated**: 2025-12-10
**Audience**: Senior Data Engineers, Data Architects
