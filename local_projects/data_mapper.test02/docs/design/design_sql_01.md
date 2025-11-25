This is a critical step. To get buy-in from traditional Data Architects, we must map the mathematical rigor (CT) into standard Enterprise Architecture terminology (ER modeling, ETL patterns, and Logical vs. Physical separation).

Here is the **same architecture**, but translated into the language of **Data Architecture, SQL, and Entity-Relationship modeling**.

-----

# Architecture: Logical-Physical Mapping Engine

This document outlines the architecture for a metadata-driven Data Mapper. It enforces a strict separation between the **Logical Data Model (LDM)** (the business view) and the **Physical Data Model (PDM)** (the underlying database implementation).

## Key Concept Dictionary

Before reviewing the diagrams, please note the following terminology mappings used to standardize the architecture:

| Category Theory Term | Data Architect Term |
| :--- | :--- |
| **Category / Topology** | **Logical Domain / Bounded Context** |
| **Object** | **Entity / Business Object** |
| **Morphism (Arrow)** | **Relationship / Functional Dependency** |
| **Functor** | **Physical Binding (Mapping Config)** |
| **Kleisli Arrow (1:N)** | **One-to-Many Relationship (Granularity Shift)** |
| **Sheaf / Context** | **Rowset / Execution Context** |
| **Synthesis / Fold** | **Derived Attribute / Aggregation** |

-----

## 1\. The Meta-Model (Class Structure)

This diagram defines how we configure the system. We have a **Logical Layer** (Entities and Relationships) that is abstract. We have a **Physical Layer** (Tables and Columns). The **Binding Engine** links them together so business rules don't break when table names change.

```mermaid
classDiagram
    %% The Logical Layer (Business View)
    class LogicalDomain {
        +String domainName
        +Set~Entity~ entities
    }

    class LogicalEntity {
        +String name (e.g., "Trade")
        +String namespace
    }

    class Relationship {
        +LogicalEntity source
        +LogicalEntity target
        +Cardinality type (1:1, 1:N, N:1)
        +Direction navigationalDirection
    }

    %% The Physical Layer (Implementation)
    class PhysicalDatabase {
        +String connectionString
        +String dbType (SQL, CSV, NoSQL)
    }

    class PhysicalTable {
        +String tableName
        +String schema
    }

    %% The Glue (The "Functor")
    class PhysicalBinding {
        %% Maps Logical to Physical
        +Map~LogicalEntity, PhysicalTable~ tableMapping
        +Map~Relationship, JoinLogic~ joinMapping
        +executeBinding()
    }

    class IntegrationRequest {
        %% Source Domain -> Target Domain
        +LogicalDomain sourceContext
        +LogicalDomain targetContext
        +List~DerivationRule~ mappings
    }

    %% Relationships
    LogicalDomain *-- LogicalEntity
    LogicalDomain *-- Relationship
    
    PhysicalBinding --> LogicalDomain : Implements
    PhysicalBinding --> PhysicalDatabase : Connects To
    
    IntegrationRequest --> LogicalDomain : Reads From
    IntegrationRequest --> LogicalDomain : Writes To
```

-----

## 2\. Handling Granularity & Integration

This diagram visualizes a specific use case: **Mapping Trading Data to a Regulatory Report**.

It highlights the **Granularity Problem** (The "Deficit").

  * **Yellow:** The Logical definition (what the business sees).
  * **Blue:** The Physical tables (where data lives).
  * **Bold Path:** How the engine handles the "Fan-out" when moving from a single Trade to multiple Legs (1:N).

<!-- end list -->

```mermaid
graph TD
    subgraph "Source Domain: Trading System"
        T[Entity: Trade]
        L[Entity: Leg]
        C[Entity: Counterparty]
        
        T -- "1:N Relationship<br/>(Granularity Increases)" --> L
        T -- "N:1 Relationship<br/>(Lookup)" --> C
    end

    subgraph "Physical Layer (Database)"
        SQL_T[Table: trades_v1]
        SQL_L[Table: trade_legs]
        SQL_C[Table: cpt_ref]
    end

    subgraph "Target Domain: Regulatory Reporting"
        R[Entity: LCR_Report]
        B[Entity: Bucket]
    end

    %% The Bindings
    T -.->|Binding Config| SQL_T
    L -.->|Binding Config| SQL_L
    C -.->|Binding Config| SQL_C

    %% The Data Flow
    SQL_T -->|SELECT| T
    SQL_L -->|SELECT| L
    
    %% The Integration Logic
    L == "Derivation Logic<br/>(Calc Notional * Haircut)" ==> B
    B -- "Aggregation (SUM/GROUP BY)" --> R
```

-----

## 3\. Execution Sequence (The Path Resolver)

This shows what happens when a user or system requests a specific data point, such as `Trade.legs.notional`.

The engine acts as a **Query Compiler**. It resolves the logical path into physical queries, automatically handling the loop required when data expands from one row (Trade) to many rows (Legs).

```mermaid
sequenceDiagram
    autonumber
    participant User
    participant Engine as Query Engine
    participant LDM as Logical Model
    participant Binding as Physical Binding
    participant Rowset as Working Rowset

    Note over User, Rowset: User Request: "Get Trade.legs.notional"

    User->>Engine: Resolve Path("Trade.legs.notional")
    
    %% Step 1: Root Entity
    Engine->>LDM: Lookup Entity("Trade")
    LDM-->>Engine: Valid Entity
    Engine->>Binding: Query Table("Trade")
    Binding-->>Rowset: Returns Single Row [ID: 101]
    
    %% Step 2: Traversal (Handling the 1:N)
    Engine->>LDM: Lookup Relationship("Trade.legs")
    LDM-->>Engine: Type is 1-to-Many (Fan-out)
    
    Note right of Engine: CONTEXT SWITCH<br/>Engine switches from Single Row mode to Multi-Row mode
    
    Engine->>Binding: Query Related("legs", TradeID=101)
    Binding-->>Rowset: Returns List [Leg_A, Leg_B, Leg_C]
    
    %% Step 3: Transformation
    Engine->>LDM: Get Attribute Definition("notional")
    
    loop For Each Leg in Rowset
        Engine->>Rowset: Calculate Notional Value
    end
    
    Rowset-->>Engine: Return List [100k, 50k, 25k]
    
    Engine->>User: Return Final Dataset
```