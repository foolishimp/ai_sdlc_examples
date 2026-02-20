# Airline System Test Case - Design Document

**Document Type**: System Test Design
**Version**: 1.0
**Date**: 2025-12-15
**Status**: Implemented
**Test File**: `../scala/cdme/AirlineSystemSpec.scala`
**Location**: `src/test/resources/docs/`

> **Note**: This is a domain-specific test case design document demonstrating
> CDME capabilities with a realistic airline booking scenario. It is NOT part
> of the core CDME design - see `docs/design/design_spark/` for core
> implementation design.

---

## 1. Overview

This document describes the design of a complex system test case that validates CDME's ability to handle a realistic enterprise scenario: **Multi-Leg International Airline Booking System mapped to an Accounting System**.

### 1.1 Business Context

Airlines operate international flights between airports in different countries. Customers book journeys that may span multiple flight segments (legs), with travel and return dates potentially months apart. The Accounting system requires:

- **Daily revenue summaries** (not individual segments)
- **Currency normalization** to USD
- **Route-level aggregation** by airline
- **Cabin class breakdowns** for premium revenue tracking

### 1.2 Key Challenges Validated

| Challenge | CDME Capability Tested |
|-----------|------------------------|
| Multi-leg journeys | 1:N relationships, aggregation |
| International flights | Multi-hop traversals (Airport → Country → Currency) |
| Daily rollup | Grain transition (Atomic → Daily) |
| Currency conversion | N:1 lookups with calculation |
| Long date spans | Temporal aggregation (months between outbound/return) |
| Codeshare flights | Complex filtering and dual-key aggregation |

---

## 2. Domain Model

### 2.1 Entity Relationship Diagram

```mermaid
erDiagram
    Country ||--o{ Airport : "has"
    Country ||--|| Currency : "uses"
    Country ||--o{ Customer : "resides_in"
    Country ||--o{ Airline : "home_base"

    Airport ||--o{ FlightSegment : "departure_from"
    Airport ||--o{ FlightSegment : "arrival_at"

    Airline ||--o{ FlightSegment : "operates"

    Customer ||--o{ Journey : "books"
    Customer }o--|| Currency : "prefers"

    Journey ||--|{ FlightSegment : "contains"
    Journey }o--|| Currency : "paid_in"

    FlightSegment }o..o| Airline : "codeshare_with"

    Country {
        string country_code PK
        string country_name
        string region
        string timezone
    }

    Currency {
        string currency_code PK
        string currency_name
        decimal usd_exchange_rate
        date effective_date
    }

    Airport {
        string airport_code PK
        string airport_name
        string city
        string country_code FK
        decimal latitude
        decimal longitude
    }

    Airline {
        string airline_code PK
        string airline_name
        string home_country_code FK
        string alliance
    }

    Customer {
        string customer_id PK
        string first_name
        string last_name
        string email
        string loyalty_tier
        string country_code FK
        string preferred_currency FK
    }

    Journey {
        string journey_id PK
        string customer_id FK
        date booking_date
        string journey_type
        decimal total_price_local
        string local_currency FK
        decimal total_price_usd
        string status
        date outbound_date
        date return_date
    }

    FlightSegment {
        string segment_id PK
        string journey_id FK
        string flight_number
        string airline_code FK
        string departure_airport FK
        string arrival_airport FK
        timestamp departure_datetime
        timestamp arrival_datetime
        date flight_date
        string cabin_class
        decimal segment_price_local
        decimal segment_price_usd
        int segment_sequence
        boolean is_codeshare
        string operating_airline
        int distance_km
        string status
    }
```

### 2.2 Class Diagram - Domain Entities

```mermaid
classDiagram
    class Country {
        +String country_code
        +String country_name
        +String region
        +String timezone
        +currency() Currency
    }

    class Currency {
        +String currency_code
        +String currency_name
        +BigDecimal usd_exchange_rate
        +Date effective_date
        +convertToUSD(amount: BigDecimal) BigDecimal
    }

    class Airport {
        +String airport_code
        +String airport_name
        +String city
        +String country_code
        +BigDecimal latitude
        +BigDecimal longitude
        +country() Country
    }

    class Airline {
        +String airline_code
        +String airline_name
        +String home_country_code
        +String alliance
        +homeCountry() Country
    }

    class Customer {
        +String customer_id
        +String first_name
        +String last_name
        +String email
        +String loyalty_tier
        +String country_code
        +String preferred_currency
        +country() Country
        +currency() Currency
    }

    class Journey {
        +String journey_id
        +String customer_id
        +Date booking_date
        +JourneyType journey_type
        +BigDecimal total_price_local
        +String local_currency
        +BigDecimal total_price_usd
        +JourneyStatus status
        +Date outbound_date
        +Date return_date
        +customer() Customer
        +currency() Currency
        +segments() List~FlightSegment~
        +isRoundTrip() Boolean
        +durationDays() Int
    }

    class FlightSegment {
        +String segment_id
        +String journey_id
        +String flight_number
        +String airline_code
        +String departure_airport
        +String arrival_airport
        +Timestamp departure_datetime
        +Timestamp arrival_datetime
        +Date flight_date
        +CabinClass cabin_class
        +BigDecimal segment_price_local
        +BigDecimal segment_price_usd
        +Int segment_sequence
        +Boolean is_codeshare
        +String operating_airline
        +Int distance_km
        +SegmentStatus status
        +journey() Journey
        +airline() Airline
        +departure() Airport
        +arrival() Airport
        +route() String
    }

    class DailyRevenueSummary {
        +String summary_id
        +Date summary_date
        +String airline_code
        +String route
        +String departure_country
        +String arrival_country
        +Long segment_count
        +Long passenger_count
        +BigDecimal total_revenue_usd
        +BigDecimal avg_segment_price_usd
        +Long total_distance_km
        +Long economy_count
        +Long business_count
        +Long first_count
    }

    class MonthlyCustomerSummary {
        +String summary_id
        +String summary_month
        +String customer_id
        +Long journey_count
        +Long segment_count
        +BigDecimal total_spend_usd
        +Long total_distance_km
        +Long countries_visited
        +Long airlines_used
    }

    Country "1" --> "1" Currency : uses
    Airport "N" --> "1" Country : located_in
    Airline "N" --> "1" Country : based_in
    Customer "N" --> "1" Country : resides_in
    Customer "N" --> "1" Currency : prefers
    Journey "N" --> "1" Customer : booked_by
    Journey "N" --> "1" Currency : paid_in
    Journey "1" --> "N" FlightSegment : contains
    FlightSegment "N" --> "1" Airline : operated_by
    FlightSegment "N" --> "1" Airport : departs_from
    FlightSegment "N" --> "1" Airport : arrives_at
```

### 2.3 Enumeration Types

```mermaid
classDiagram
    class JourneyType {
        <<enumeration>>
        ONE_WAY
        ROUND_TRIP
        MULTI_CITY
    }

    class JourneyStatus {
        <<enumeration>>
        CONFIRMED
        CANCELLED
        COMPLETED
    }

    class SegmentStatus {
        <<enumeration>>
        SCHEDULED
        FLOWN
        CANCELLED
    }

    class CabinClass {
        <<enumeration>>
        ECONOMY
        BUSINESS
        FIRST
    }

    class GrainLevel {
        <<enumeration>>
        ATOMIC
        DAILY
        MONTHLY
        CUSTOMER
        SUMMARY
        +level() Int
    }
```

---

## 3. Grain Hierarchy

### 3.1 Grain Level Diagram

```mermaid
graph TB
    subgraph "Fine Grain (Atomic)"
        FS[FlightSegment<br/>grain_key: segment_id<br/>level: 0]
        J[Journey<br/>grain_key: journey_id<br/>level: 0]
    end

    subgraph "Medium Grain"
        D[Daily<br/>grain_key: date + airline + route<br/>level: 10]
        C[Customer<br/>grain_key: customer_id<br/>level: 15]
    end

    subgraph "Coarse Grain"
        M[Monthly<br/>grain_key: month + customer_id<br/>level: 20]
        S[Summary<br/>grain_key: varies<br/>level: 30]
    end

    FS -->|"Aggregate by date/airline/route"| D
    FS -->|"Aggregate by journey_id"| J
    J -->|"Aggregate by customer"| C
    J -->|"Aggregate by month"| M
    D -->|"Aggregate by month"| M
    C -->|"Aggregate to summary"| S
    M -->|"Aggregate to summary"| S

    style FS fill:#e1f5fe
    style J fill:#e1f5fe
    style D fill:#fff3e0
    style C fill:#fff3e0
    style M fill:#f3e5f5
    style S fill:#f3e5f5
```

### 3.2 Grain Transition Rules

| From Grain | To Grain | Allowed? | Requirement |
|------------|----------|----------|-------------|
| Atomic (0) | Atomic (0) | ✅ Yes | None |
| Atomic (0) | Daily (10) | ✅ Yes | Aggregation required |
| Atomic (0) | Monthly (20) | ✅ Yes | Aggregation required |
| Daily (10) | Atomic (0) | ❌ No | Cannot refine grain |
| Daily (10) | Monthly (20) | ✅ Yes | Aggregation required |
| Monthly (20) | Daily (10) | ❌ No | Cannot refine grain |

---

## 4. Data Flow Architecture

### 4.1 Pipeline Overview

```mermaid
flowchart LR
    subgraph Sources["Source Systems"]
        OPS[Operations DB<br/>FlightSegments]
        BOOK[Booking System<br/>Journeys]
        REF[Reference Data<br/>Airports, Airlines, etc.]
    end

    subgraph CDME["CDME Processing"]
        direction TB
        CONFIG[CONFIG<br/>Load YAML]
        INIT[INIT<br/>Registry + Session]
        COMPILE[COMPILE<br/>Validate + Plan]
        EXEC[EXECUTE<br/>Spark Jobs]

        CONFIG --> INIT
        INIT --> COMPILE
        COMPILE --> EXEC
    end

    subgraph Targets["Target Systems"]
        ACC[Accounting<br/>DailyRevenueSummary]
        ANA[Analytics<br/>MonthlyCustomerSummary]
        DLQ[Error DLQ<br/>Failed Records]
    end

    OPS --> CDME
    BOOK --> CDME
    REF --> CDME

    CDME --> ACC
    CDME --> ANA
    CDME --> DLQ
```

### 4.2 Daily Revenue Summary Pipeline

```mermaid
flowchart TB
    subgraph Input["Input: FlightSegment (Atomic)"]
        FS[FlightSegment Records<br/>N rows per day]
    end

    subgraph Morphisms["Morphism Chain"]
        F1[FILTER: status = 'FLOWN']
        F2[FILTER: cabin_class IN premium]
        F3[FILTER: distance_km > 5000]
        AGG[AGGREGATE:<br/>GROUP BY date, airline, route]
    end

    subgraph Projections["Projections"]
        P1[summary_date = flight_date]
        P2[airline_code = airline_code]
        P3[route = CONCAT departure-arrival]
        P4[segment_count = COUNT segment_id]
        P5[total_revenue_usd = SUM segment_price_usd]
        P6[avg_segment_price = AVG segment_price_usd]
    end

    subgraph Output["Output: DailyRevenueSummary (Daily)"]
        DRS[Daily Revenue Summary<br/>M rows groupKey=date,airline,route]
    end

    FS --> F1
    F1 --> F2
    F2 --> F3
    F3 --> AGG
    AGG --> P1
    AGG --> P2
    AGG --> P3
    AGG --> P4
    AGG --> P5
    AGG --> P6
    P1 & P2 & P3 & P4 & P5 & P6 --> DRS

    style FS fill:#e3f2fd
    style DRS fill:#e8f5e9
```

---

## 5. Sequence Diagrams

### 5.1 Mapping Compilation Sequence

```mermaid
sequenceDiagram
    autonumber
    participant User as Data Engineer
    participant Compiler as Compiler
    participant Registry as SchemaRegistry
    participant Validator as GrainValidator
    participant Plan as ExecutionPlan

    User->>Compiler: compile(mapping, context)

    rect rgb(230, 245, 255)
        Note over Compiler,Registry: Entity Validation
        Compiler->>Registry: getEntity("FlightSegment")
        Registry-->>Compiler: Entity(FlightSegment, Atomic)
        Compiler->>Registry: getEntity("DailyRevenueSummary")
        Registry-->>Compiler: Entity(DailyRevenueSummary, Daily)
    end

    rect rgb(255, 243, 224)
        Note over Compiler,Registry: Path Validation
        loop For each projection
            Compiler->>Registry: validatePath(sourcePath)
            Registry-->>Compiler: PathValidationResult
        end
    end

    rect rgb(243, 229, 245)
        Note over Compiler,Validator: Grain Safety Check
        Compiler->>Validator: validateTransition(Atomic, Daily, hasAgg=true)
        Validator-->>Compiler: Right(()) - allowed with aggregation
    end

    rect rgb(232, 245, 233)
        Note over Compiler,Plan: Plan Generation
        Compiler->>Plan: new ExecutionPlan(morphisms, projections)
        Plan-->>Compiler: ExecutionPlan
    end

    Compiler-->>User: Right(ExecutionPlan)
```

### 5.2 Execution Sequence with Error Handling

```mermaid
sequenceDiagram
    autonumber
    participant Driver as Spark Driver
    participant Exec as Executor
    participant ErrDomain as ErrorDomain
    participant DLQ as Dead Letter Queue

    Driver->>Exec: execute(plan)

    rect rgb(230, 245, 255)
        Note over Exec: Load Source Data
        Exec->>Exec: loadSourceData("FlightSegment")
        Exec-->>Exec: DataFrame (N rows)
    end

    rect rgb(255, 243, 224)
        Note over Exec,ErrDomain: Apply Morphisms
        loop For each morphism
            Exec->>Exec: applyMorphism(df, morphism)
            alt Validation Error
                Exec->>ErrDomain: routeError(ErrorObject)
                ErrDomain->>ErrDomain: accumulator.add(error)
            end
        end
    end

    rect rgb(243, 229, 245)
        Note over Exec,ErrDomain: Threshold Check
        Exec->>ErrDomain: checkThreshold(totalRows)
        alt Error Rate < 5%
            ErrDomain-->>Exec: Continue
        else Error Rate >= 5%
            ErrDomain-->>Exec: Halt(reason, count)
            ErrDomain->>DLQ: flushToDLQ()
            Exec-->>Driver: Left(ThresholdExceeded)
        end
    end

    rect rgb(232, 245, 233)
        Note over Exec: Apply Projections & Write
        Exec->>Exec: applyProjections(df)
        Exec->>Exec: writeOutput(resultDf)
    end

    Exec-->>Driver: Right(ExecutionResult)
```

### 5.3 Multi-Hop Path Validation Sequence

```mermaid
sequenceDiagram
    autonumber
    participant Compiler
    participant Registry as SchemaRegistry
    participant E1 as FlightSegment Entity
    participant E2 as Airport Entity
    participant E3 as Country Entity

    Note over Compiler,E3: Path: FlightSegment.departure.country.country_name

    Compiler->>Registry: validatePath("FlightSegment.departure.country.country_name")

    Registry->>E1: getEntity("FlightSegment")
    E1-->>Registry: Entity with relationships

    Registry->>E1: getRelationship("departure")
    E1-->>Registry: Relationship(target=Airport, N:1)

    Registry->>E2: getEntity("Airport")
    E2-->>Registry: Entity with relationships

    Registry->>E2: getRelationship("country")
    E2-->>Registry: Relationship(target=Country, N:1)

    Registry->>E3: getEntity("Country")
    E3-->>Registry: Entity with attributes

    Registry->>E3: getAttribute("country_name")
    E3-->>Registry: Attribute(type=String)

    Registry-->>Compiler: PathValidationResult(valid=true, finalType=String)
```

---

## 6. State Diagrams

### 6.1 Journey Lifecycle State Machine

```mermaid
stateDiagram-v2
    [*] --> PENDING: Customer initiates booking

    PENDING --> CONFIRMED: Payment successful
    PENDING --> CANCELLED: Payment failed / User cancelled

    CONFIRMED --> COMPLETED: All segments flown
    CONFIRMED --> CANCELLED: User cancellation
    CONFIRMED --> PARTIAL: Some segments cancelled

    PARTIAL --> COMPLETED: Remaining segments flown
    PARTIAL --> CANCELLED: Full refund requested

    COMPLETED --> [*]
    CANCELLED --> [*]

    note right of PENDING
        Awaiting payment
        No segments activated
    end note

    note right of CONFIRMED
        Payment received
        Segments scheduled
    end note

    note right of COMPLETED
        All segments FLOWN
        Revenue recognized
    end note
```

### 6.2 Flight Segment State Machine

```mermaid
stateDiagram-v2
    [*] --> SCHEDULED: Booking confirmed

    SCHEDULED --> FLOWN: Flight departed & arrived
    SCHEDULED --> CANCELLED: Flight cancelled
    SCHEDULED --> RESCHEDULED: Schedule change

    RESCHEDULED --> SCHEDULED: New time confirmed
    RESCHEDULED --> CANCELLED: Cannot reschedule

    FLOWN --> [*]: Revenue recognized
    CANCELLED --> [*]: Refund processed

    note right of SCHEDULED
        Future flight
        No revenue yet
    end note

    note right of FLOWN
        Flight completed
        Include in daily summary
    end note

    note right of CANCELLED
        Exclude from revenue
        Route to DLQ if unexpected
    end note
```

### 6.3 CDME Pipeline State Machine

```mermaid
stateDiagram-v2
    [*] --> CONFIG_LOADING

    CONFIG_LOADING --> CONFIG_LOADED: YAML parsed successfully
    CONFIG_LOADING --> CONFIG_ERROR: Invalid YAML

    CONFIG_LOADED --> INITIALIZING

    INITIALIZING --> INITIALIZED: Registry loaded
    INITIALIZING --> INIT_ERROR: Registry build failed

    INITIALIZED --> COMPILING

    COMPILING --> COMPILED: Plan generated
    COMPILING --> COMPILE_ERROR: Validation failed

    COMPILED --> EXECUTING

    EXECUTING --> CHECKING_THRESHOLD: Batch processed

    CHECKING_THRESHOLD --> EXECUTING: Under threshold
    CHECKING_THRESHOLD --> THRESHOLD_EXCEEDED: Over threshold

    EXECUTING --> COMPLETED: All batches done

    COMPLETED --> [*]
    CONFIG_ERROR --> [*]
    INIT_ERROR --> [*]
    COMPILE_ERROR --> [*]
    THRESHOLD_EXCEEDED --> [*]

    note right of COMPILING
        Path validation
        Type checking
        Grain safety
    end note

    note right of CHECKING_THRESHOLD
        Error rate check
        Circuit breaker
    end note
```

### 6.4 Error Domain State Machine

```mermaid
stateDiagram-v2
    [*] --> COLLECTING

    COLLECTING --> COLLECTING: routeError() - under limits
    COLLECTING --> BUFFER_FULL: Buffer limit reached
    COLLECTING --> THRESHOLD_CHECK: checkThreshold() called

    BUFFER_FULL --> BUFFER_FULL: routeError() - count only
    BUFFER_FULL --> THRESHOLD_CHECK: checkThreshold() called

    THRESHOLD_CHECK --> CONTINUE: Rate < threshold
    THRESHOLD_CHECK --> HALT_ABSOLUTE: Count > absolute limit
    THRESHOLD_CHECK --> HALT_PERCENTAGE: Rate > percentage limit

    CONTINUE --> COLLECTING: Resume processing

    HALT_ABSOLUTE --> FLUSHING_DLQ
    HALT_PERCENTAGE --> FLUSHING_DLQ

    FLUSHING_DLQ --> HALTED: DLQ write complete

    HALTED --> RESET: reset() called
    RESET --> COLLECTING: New batch

    HALTED --> [*]

    note right of BUFFER_FULL
        Still counting errors
        No new details captured
    end note

    note right of HALT_PERCENTAGE
        5% threshold exceeded
        Circuit breaker triggered
    end note
```

---

## 7. Component Architecture

### 7.1 CDME Component Diagram

```mermaid
flowchart TB
    subgraph Config["Configuration Layer"]
        YAML[YAML Files]
        CL[ConfigLoader]
        CM[ConfigModel]
    end

    subgraph Registry["Registry Layer"]
        SR[SchemaRegistry]
        ER[EntityRegistry]
        BR[BindingRegistry]
    end

    subgraph Compiler["Compiler Layer"]
        C[Compiler]
        PV[PathValidator]
        GV[GrainValidator]
        PG[PlanGenerator]
    end

    subgraph Executor["Executor Layer"]
        E[Executor]
        TE[TypedExecutor]
        MM[MorphismManager]
        PM[ProjectionManager]
    end

    subgraph Morphisms["Morphism Types"]
        FM[FilterMorphism]
        AM[AggregateMorphism]
        TM[TraverseMorphism]
        WM[WindowMorphism]
    end

    subgraph ErrorHandling["Error Handling"]
        ED[ErrorDomain]
        EC[ErrorConfig]
        EO[ErrorObject]
        DLQ[DLQ Writer]
    end

    subgraph Adjoint["Adjoint System"]
        AW[AdjointWrapper]
        AC[AdjointClassification]
        AM2[AdjointMetadata]
    end

    YAML --> CL
    CL --> CM
    CM --> SR
    SR --> ER
    SR --> BR

    SR --> C
    C --> PV
    C --> GV
    C --> PG

    PG --> E
    E --> TE
    E --> MM
    E --> PM

    MM --> FM
    MM --> AM
    MM --> TM
    MM --> WM

    E --> ED
    ED --> EC
    ED --> EO
    ED --> DLQ

    MM --> AW
    AW --> AC
    AW --> AM2
```

### 7.2 Algebra Integration

```mermaid
classDiagram
    class Aggregator~A, B~ {
        <<trait>>
        +empty: B
        +combine(b1: B, b2: B): B
        +extract(a: A): B
    }

    class Monoid~A~ {
        <<trait>>
        +empty: A
        +combine(a1: A, a2: A): A
    }

    class AggregatorFactory {
        +create~A~(aggregationType: String): Aggregator~A, A~
        +isSupported(aggregationType: String): Boolean
        +supportedTypes: List~String~
    }

    class SumAggregator {
        +empty: BigDecimal = 0
        +combine(b1, b2): BigDecimal
    }

    class CountAggregator {
        +empty: Long = 0
        +combine(b1, b2): Long
    }

    class AvgAggregator {
        +empty: Avg = Avg(0, 0)
        +combine(b1, b2): Avg
        +finalize(): BigDecimal
    }

    class MinAggregator~A~ {
        +empty: Option~A~ = None
        +combine(b1, b2): Option~A~
    }

    class MaxAggregator~A~ {
        +empty: Option~A~ = None
        +combine(b1, b2): Option~A~
    }

    Aggregator <|.. SumAggregator
    Aggregator <|.. CountAggregator
    Aggregator <|.. AvgAggregator
    Aggregator <|.. MinAggregator
    Aggregator <|.. MaxAggregator

    Monoid <|-- Aggregator
    AggregatorFactory ..> Aggregator : creates
```

---

## 8. Mapping Configurations

### 8.1 Daily Revenue Summary Mapping

```yaml
mapping:
  name: daily_revenue_summary
  description: "Aggregate flight segments to daily airline route revenue"

  source:
    entity: FlightSegment
    epoch: "${epoch}"

  target:
    entity: DailyRevenueSummary
    grain:
      level: Daily
      key: [summary_date, airline_code, route]

  morphisms:
    - name: filter_flown
      type: FILTER
      predicate: "status = 'FLOWN'"

    - name: aggregate_daily
      type: AGGREGATE

  projections:
    - name: summary_date
      source: flight_date

    - name: airline_code
      source: airline_code

    - name: route
      source: "CONCAT(departure_airport, '-', arrival_airport)"

    - name: segment_count
      source: segment_id
      aggregation: COUNT

    - name: total_revenue_usd
      source: segment_price_usd
      aggregation: SUM

    - name: avg_segment_price_usd
      source: segment_price_usd
      aggregation: AVG

    - name: total_distance_km
      source: distance_km
      aggregation: SUM

  validations:
    - field: total_revenue_usd
      validationType: RANGE
      expression: "0:"
      errorMessage: "Revenue must be non-negative"
```

### 8.2 Premium International Analysis Mapping

```yaml
mapping:
  name: premium_international_revenue
  description: "Analyze premium cabin international flights"

  source:
    entity: FlightSegment
    epoch: "${epoch}"

  target:
    entity: DailyRevenueSummary
    grain:
      level: Daily
      key: [summary_date, airline_code]

  morphisms:
    # Chain of 4 business rule filters
    - name: filter_flown
      type: FILTER
      predicate: "status = 'FLOWN'"

    - name: filter_premium
      type: FILTER
      predicate: "cabin_class IN ('BUSINESS', 'FIRST')"

    - name: filter_longhaul
      type: FILTER
      predicate: "distance_km > 5000"

    - name: filter_high_value
      type: FILTER
      predicate: "segment_price_usd > 1000"

    - name: aggregate_daily
      type: AGGREGATE

  projections:
    - name: summary_date
      source: flight_date

    - name: airline_code
      source: airline_code

    - name: premium_segment_count
      source: segment_id
      aggregation: COUNT

    - name: premium_revenue_usd
      source: segment_price_usd
      aggregation: SUM

    - name: avg_premium_price
      source: segment_price_usd
      aggregation: AVG

    - name: total_premium_distance
      source: distance_km
      aggregation: SUM
```

### 8.3 Monthly Customer Summary Mapping

```yaml
mapping:
  name: monthly_customer_travel
  description: "Monthly customer travel pattern summary"

  source:
    entity: Journey
    epoch: "${epoch}"

  target:
    entity: MonthlyCustomerSummary
    grain:
      level: Monthly
      key: [summary_month, customer_id]

  morphisms:
    - name: filter_completed
      type: FILTER
      predicate: "status = 'COMPLETED'"

    - name: aggregate_monthly
      type: AGGREGATE

  projections:
    - name: summary_month
      source: "DATE_FORMAT(booking_date, 'yyyy-MM')"

    - name: customer_id
      source: customer_id

    - name: journey_count
      source: journey_id
      aggregation: COUNT

    - name: total_spend_usd
      source: total_price_usd
      aggregation: SUM

    - name: avg_journey_value
      source: total_price_usd
      aggregation: AVG
```

---

## 9. Test Scenarios Matrix

### 9.1 Feature Coverage

| Feature ID | Feature Name | Scenarios | Key Validations |
|------------|--------------|-----------|-----------------|
| SYS-001 | Complex Schema Registration | 3 | 9 entities, multi-hop paths |
| SYS-002 | Grain Transition | 3 | Atomic→Daily with aggregation |
| SYS-003 | Multi-Leg Journey | 2 | 1:N relationships, round-trips |
| SYS-004 | Currency Conversion | 2 | N:1 lookups, USD normalization |
| SYS-005 | Codeshare Analysis | 1 | Boolean filter, dual-key |
| SYS-006 | Monthly Customer | 2 | Atomic→Monthly aggregation |
| SYS-007 | Complex Filters | 1 | 4-filter chain + aggregate |
| SYS-008 | Window Functions | 2 | Running totals, rankings |
| SYS-009 | Error Detection | 2 | Invalid paths, missing entities |
| SYS-010 | Adjoint Integration | 2 | Reverse-join metadata |
| SYS-011 | Error Domain | 2 | Threshold checking |

### 9.2 Requirements Traceability

| Requirement | Test Coverage | Feature |
|-------------|---------------|---------|
| REQ-LDM-01 | Entity definitions | SYS-001 |
| REQ-LDM-02 | Cardinality types | SYS-001, SYS-003 |
| REQ-LDM-06 | Grain metadata | SYS-002, SYS-006 |
| REQ-TRV-01 | Context lifting | SYS-004 |
| REQ-TRV-02 | Grain safety | SYS-002 |
| REQ-ADJ-04 | Backward for aggregations | SYS-010 |
| REQ-ADJ-05 | Backward for filters | SYS-010 |
| REQ-TYP-03 | Error domain | SYS-011 |
| REQ-TYP-03-A | Batch threshold | SYS-011 |

---

## 10. Performance Considerations

### 10.1 Expected Data Volumes

| Entity | Daily Volume | Monthly Volume | Key Characteristics |
|--------|--------------|----------------|---------------------|
| FlightSegment | 500K | 15M | High cardinality, partitioned by date |
| Journey | 100K | 3M | 1:N with segments (avg 5 segments) |
| DailyRevenueSummary | 10K | 300K | Aggregated (50x reduction) |
| MonthlyCustomerSummary | 50K | 50K | Monthly snapshot |

### 10.2 Aggregation Strategy Selection

```mermaid
flowchart TD
    START[Aggregation Required] --> CHECK_TYPE{Full or GroupBy?}

    CHECK_TYPE -->|Full| FULL[Single Reducer<br/>Trivial]
    CHECK_TYPE -->|GroupBy| CHECK_SKEW{Known Skew?}

    CHECK_SKEW -->|No| CHECK_BUCKET{Source Bucketed?}
    CHECK_SKEW -->|Yes| SALTING[Salted Aggregation<br/>2-stage shuffle]

    CHECK_BUCKET -->|Yes| BUCKETED[No Shuffle<br/>Local aggregation]
    CHECK_BUCKET -->|No| CHECK_DIM{Small Dimension?}

    CHECK_DIM -->|Yes| BROADCAST[Broadcast Join<br/>+ Local agg]
    CHECK_DIM -->|No| PARTIAL[Partial Aggregation<br/>Map-side combine]

    style FULL fill:#c8e6c9
    style BUCKETED fill:#c8e6c9
    style BROADCAST fill:#fff9c4
    style PARTIAL fill:#fff9c4
    style SALTING fill:#ffccbc
```

---

## 11. Appendix

### 11.1 Sample Data

**FlightSegment Example:**
```json
{
  "segment_id": "SEG-20241215-QF1-001",
  "journey_id": "JRN-20241201-C123",
  "flight_number": "QF1",
  "airline_code": "QF",
  "departure_airport": "SYD",
  "arrival_airport": "LHR",
  "departure_datetime": "2024-12-15T17:00:00Z",
  "arrival_datetime": "2024-12-16T05:30:00Z",
  "flight_date": "2024-12-15",
  "cabin_class": "BUSINESS",
  "segment_price_local": 8500.00,
  "segment_price_usd": 5525.00,
  "segment_sequence": 1,
  "is_codeshare": false,
  "distance_km": 17016,
  "status": "FLOWN"
}
```

**DailyRevenueSummary Example:**
```json
{
  "summary_id": "DRS-20241215-QF-SYDLHR",
  "summary_date": "2024-12-15",
  "airline_code": "QF",
  "route": "SYD-LHR",
  "departure_country": "AU",
  "arrival_country": "GB",
  "segment_count": 450,
  "passenger_count": 450,
  "total_revenue_usd": 1856250.00,
  "avg_segment_price_usd": 4125.00,
  "total_distance_km": 7657200,
  "economy_count": 350,
  "business_count": 80,
  "first_count": 20
}
```

### 11.2 Related Documents

- [SPARK_IMPLEMENTATION_DESIGN.md](./SPARK_IMPLEMENTATION_DESIGN.md) - Core implementation design
- [SPARK_SOLUTION_DESIGN.md](./SPARK_SOLUTION_DESIGN.md) - Solution architecture
- [ADR-005-adjoint-metadata.md](./adrs/ADR-005-adjoint-metadata.md) - Adjoint storage strategy
- [ADR-006-scala-type-system.md](./adrs/ADR-006-scala-type-system.md) - Type safety design

---

**Document Status**: Complete
**Last Updated**: 2025-12-15
**Author**: CDME Development Team
