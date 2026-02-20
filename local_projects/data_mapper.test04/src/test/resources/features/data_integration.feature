@Integration
Feature: Data Integration, Derived Attributes, and Lineage
  As a data architect
  I want to define derived attributes, complex business logic, and external calculators
  So that all computed values are traceable, type-safe, and composable

  # ---------------------------------------------------------------------------
  # REQ-F-INT-001: Isomorphic Synthesis (Derived Attributes)
  # ---------------------------------------------------------------------------

  # Validates: REQ-F-INT-001
  Scenario: Derived attribute is defined via a pure function over existing attributes
    Given a "Trade" entity with attributes "notional" (Decimal) and "fx_rate" (Decimal)
    When I define a derived attribute "local_value" as "notional * fx_rate"
    Then the derived attribute "local_value" is accepted with type "Decimal"
    And it participates in the type system like any other attribute

  # Validates: REQ-F-INT-001
  Scenario: Derived attribute composes with other transformations
    Given a derived attribute "local_value" on "Trade"
    And a transformation from "Trade" to "CounterpartyExposure" that uses "local_value"
    When I chain the derivation with the transformation
    Then the composition is type-checked and accepted

  # Validates: REQ-F-INT-001
  Scenario: Derived attribute has a backward mapping
    Given a derived attribute "local_value" as "notional * fx_rate"
    When I request the backward mapping of "local_value"
    Then the system traces "local_value" back to its source attributes "notional" and "fx_rate"

  # ---------------------------------------------------------------------------
  # REQ-F-INT-002: Complex Business Logic
  # ---------------------------------------------------------------------------

  # Validates: REQ-F-INT-002
  Scenario: Conditional expression is supported as a transformation
    Given a "Trade" entity with attribute "trade_type"
    When I define a derived attribute "fee_rate" using:
      """
      If trade_type is "OTC" then 0.005
      Else if trade_type is "Exchange" then 0.001
      Else 0.003
      """
    Then the conditional transformation is accepted

  # Validates: REQ-F-INT-002
  Scenario: Fallback chain (coalesce) is supported
    Given a "Trade" entity with attributes "preferred_rate", "market_rate", and "default_rate"
    When I define a derived attribute "effective_rate" as:
      """
      Use the first non-null value from: preferred_rate, market_rate, default_rate
      """
    Then the fallback chain is accepted
    And the first available value is used

  # Validates: REQ-F-INT-002
  Scenario: Multiple transformations compose into a single derivation
    Given a "Trade" entity with attributes "notional", "fx_rate", and "fee_rate"
    When I define a derived attribute "net_value" as "notional * fx_rate - notional * fee_rate"
    Then the multi-step composition is accepted
    And all intermediate transformations are type-checked

  # Validates: REQ-F-INT-002
  Scenario: Record type can be constructed from multiple input attributes
    Given attributes "trade_id" (Int), "counterparty_name" (String), "amount" (Decimal)
    When I construct a record type "TradeReport" from these three attributes
    Then the record type is accepted
    And "TradeReport" has fields "trade_id", "counterparty_name", and "amount"

  # ---------------------------------------------------------------------------
  # REQ-F-INT-003: Multi-Grain Formulation
  # ---------------------------------------------------------------------------

  # Validates: REQ-F-INT-003
  Scenario: Formula referencing finer-grained attribute with explicit aggregation is accepted
    Given a "DailySummary" entity at "Daily" granularity
    And a "Trade" entity at "Atomic" granularity with attribute "notional"
    When I define a formula on "DailySummary" that references "sum(Trade.notional)"
    Then the formula is accepted because the finer-grained attribute is explicitly aggregated

  # Validates: REQ-F-INT-003
  Scenario: Formula referencing finer-grained attribute without aggregation is rejected
    Given a "DailySummary" entity at "Daily" granularity
    And a "Trade" entity at "Atomic" granularity with attribute "notional"
    When I define a formula on "DailySummary" that directly references "Trade.notional"
    Then the formula is rejected with error "Direct reference to finer-grained attribute 'Trade.notional' requires explicit aggregation"

  # Validates: REQ-F-INT-003
  Scenario: Aggregation scope aligns to the coarser entity's granularity
    Given a "MonthlySummary" entity at "Monthly" granularity
    And a "Trade" entity at "Atomic" granularity
    When I aggregate "Trade.notional" for use in a "MonthlySummary" formula
    Then the aggregation scope aligns to "Monthly" boundaries
    And all atomic trades within each month are included in the aggregation

  # ---------------------------------------------------------------------------
  # REQ-F-INT-004: Versioned Lookups
  # ---------------------------------------------------------------------------

  # Validates: REQ-F-INT-004
  Scenario Outline: Lookup usage declares version semantics
    Given a "CurrencyRate" lookup entity
    When I reference the lookup with version semantic "<semantic>"
    Then the lookup usage is accepted

    Examples:
      | semantic              |
      | explicit version v3   |
      | as-of 2025-03-15      |
      | deterministic alias   |

  # Validates: REQ-F-INT-004
  Scenario: Unversioned lookup is rejected
    Given a "CurrencyRate" lookup entity
    When I reference the lookup without specifying version semantics
    Then the usage is rejected with error "Version semantics required for lookup 'CurrencyRate'"

  # Validates: REQ-F-INT-004
  Scenario: Same lookup key returns the same value within a single execution
    Given a "CurrencyRate" lookup pinned to version "v3"
    And key "USD/EUR" maps to "0.92" in version "v3"
    When the pipeline resolves "USD/EUR" at the start and again at the end of execution
    Then both resolutions return "0.92"

  # Validates: REQ-F-INT-004
  Scenario: Lookup version is captured in lineage metadata
    Given a pipeline that uses "CurrencyRate" lookup at version "v3"
    When the pipeline executes
    Then the lineage metadata records that "CurrencyRate" version "v3" was used

  # ---------------------------------------------------------------------------
  # REQ-F-INT-005: Full Lineage Traceability
  # ---------------------------------------------------------------------------

  # Validates: REQ-F-INT-005
  Scenario: Every target value maps back to its source entity and transformation path
    Given a target attribute "exposure" derived from "Trade.notional * CurrencyRate.rate"
    When I query the lineage for a specific "exposure" value
    Then the lineage shows:
      | field                    | value                                        |
      | source_entity            | Trade, CurrencyRate                          |
      | source_epoch             | 2025-03-15                                   |
      | morphism_path            | Trade.notional -> multiply -> CurrencyRate   |

  # Validates: REQ-F-INT-005
  Scenario: Lineage is queryable per target record
    Given a completed pipeline run with 1000 output records
    When I query lineage for output record with key "R42"
    Then the system returns the complete source-to-target path for "R42"

  # Validates: REQ-F-INT-005
  Scenario: Lineage survives aggregation via backward traceability metadata
    Given trades aggregated by counterparty
    When I query lineage for aggregated output group "C1"
    Then the lineage includes the backward traceability showing all contributing trades

  # Validates: REQ-F-INT-005
  Scenario: Lineage is stored in a structured, machine-readable format
    When a pipeline run completes
    Then the lineage data is stored in a structured format (not plain text)
    And the lineage data is queryable by record key, entity, and transformation path

  # ---------------------------------------------------------------------------
  # REQ-F-INT-006: Identity Synthesis (Surrogate Keys)
  # ---------------------------------------------------------------------------

  # Validates: REQ-F-INT-006
  Scenario: Surrogate key generation is deterministic
    Given input values "trade_id=123, trade_date=2025-03-15, counterparty=C1"
    When I generate a surrogate key from these values
    And I generate a surrogate key again from the same values
    Then both keys are bitwise identical

  # Validates: REQ-F-INT-006
  Scenario: Different input values produce different surrogate keys
    Given input values "trade_id=123" and "trade_id=456"
    When I generate surrogate keys for each
    Then the two keys are different

  # Validates: REQ-F-INT-006
  Scenario: Surrogate key generation is a standard typed transformation
    Given a surrogate key generator declared with input type "(Int, Date, String)" and output type "SurrogateKey"
    Then it participates in the type system
    And it composes with other transformations

  # ---------------------------------------------------------------------------
  # REQ-F-INT-007: External Computational Transformations
  # ---------------------------------------------------------------------------

  # Validates: REQ-F-INT-007
  Scenario: External calculator is registered with typed inputs and outputs
    Given an external cashflow calculator
    When I register it with input type "Trade" and output type "List[Cashflow]"
    And I declare its version as "v2.1"
    And I assert it is deterministic
    Then the registration is accepted as a standard transformation

  # Validates: REQ-F-INT-007
  Scenario: External calculator type compatibility is checked at definition time
    Given an external calculator registered with input type "Trade"
    When I try to connect it to a path that outputs type "Instrument"
    Then the connection is rejected with error "Type mismatch: 'Instrument' is not compatible with 'Trade'"

  # Validates: REQ-F-INT-007
  Scenario: External calculator version is captured in lineage
    Given an external calculator "CashflowEngine" at version "v2.1"
    When the pipeline executes using this calculator
    Then the lineage metadata records "CashflowEngine v2.1" as part of the transformation chain

  # Validates: REQ-F-INT-007
  Scenario: External calculator must provide a backward mapping
    Given an external calculator
    When I register it without a backward mapping
    Then the registration is rejected with error "Backward mapping is required"
    When I register it with an opaque backward mapping and declared containment bounds
    Then the registration is accepted
