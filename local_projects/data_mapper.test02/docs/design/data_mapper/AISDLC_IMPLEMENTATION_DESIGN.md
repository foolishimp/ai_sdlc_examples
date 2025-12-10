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
| [ADR-009](adrs/ADR-009-immutable-run-hierarchy.md) | Immutable Run Hierarchy | REQ-TRV-05, REQ-INT-03, REQ-AI-02 |

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
