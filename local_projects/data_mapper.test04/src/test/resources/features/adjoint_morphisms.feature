@Adjoint
Feature: Reverse Mappings and Backward Traceability
  As a data architect
  I want every forward transformation to have a reverse mapping
  So that I can trace outputs back to inputs for reconciliation and audit

  # ---------------------------------------------------------------------------
  # REQ-F-ADJ-001: Adjoint Interface
  # ---------------------------------------------------------------------------

  # Validates: REQ-F-ADJ-001
  Scenario: Every transformation provides both forward and backward mappings
    Given a transformation "tradeToCounterparty" from "Trade" to "Counterparty"
    Then it has a forward mapping from "Trade" to "Counterparty"
    And it has a backward mapping from "Counterparty" to "Trade"

  # Validates: REQ-F-ADJ-001
  Scenario: Backward mapping preserves containment
    Given a forward mapping that transforms trade "T1" to counterparty "C1"
    When I apply the backward mapping to "C1"
    Then the result contains at least the original trade "T1"

  # Validates: REQ-F-ADJ-001
  Scenario: Transformation without backward mapping is rejected
    When I define a transformation from "Trade" to "Counterparty" with a forward mapping only
    Then the definition is rejected with error "Backward mapping is required for transformation 'tradeToCounterparty'"

  # Validates: REQ-F-ADJ-001
  Scenario: Backward mapping strategy is determined by cardinality type
    Given a "1:1" transformation, the backward mapping uses "exact inverse"
    And a "N:1" transformation, the backward mapping uses "preimage lookup"
    And a "1:N" transformation, the backward mapping uses "parent collection"

  # ---------------------------------------------------------------------------
  # REQ-F-ADJ-002: Isomorphic (1:1) Reverse Mapping
  # ---------------------------------------------------------------------------

  # Validates: REQ-F-ADJ-002
  Scenario: One-to-one transformation has exact round-trip
    Given a "1:1" transformation "currencyCodeToName" that maps "USD" to "US Dollar"
    When I apply the forward mapping to "USD" and then the backward mapping
    Then the result is exactly "USD"
    And no information is lost in the round-trip

  # Validates: REQ-F-ADJ-002
  Scenario: Self-reversing transformation is a special case
    Given a "1:1" transformation "toggleFlag" where forward and backward are the same operation
    When I apply the forward mapping to "Active"
    Then the result is "Inactive"
    When I apply the backward mapping to "Inactive"
    Then the result is "Active"

  # ---------------------------------------------------------------------------
  # REQ-F-ADJ-003: Preimage (N:1) Reverse Mapping
  # ---------------------------------------------------------------------------

  # Validates: REQ-F-ADJ-003
  Scenario: Many-to-one backward mapping returns all inputs that produce the same output
    Given a "N:1" transformation "tradeToCounterparty" where:
      | trade | counterparty |
      | T1    | C1           |
      | T2    | C1           |
      | T3    | C2           |
    When I apply the backward mapping to "C1"
    Then the result contains both "T1" and "T2"
    And the result does not contain "T3"

  # Validates: REQ-F-ADJ-003
  Scenario: Preimage lookup uses metadata captured during forward execution
    Given a "N:1" transformation has executed in the forward direction
    When I request the backward mapping
    Then the system uses the reverse-mapping metadata captured during the forward run
    And no re-execution of the forward pipeline is required

  # Validates: REQ-F-ADJ-003
  Scenario: Containment holds for many-to-one reverse mapping
    Given trade "T1" maps to counterparty "C1" via a "N:1" transformation
    When I apply forward then backward to "T1"
    Then the backward result contains at least "T1"
    And the backward result may also contain other trades that map to "C1"

  # ---------------------------------------------------------------------------
  # REQ-F-ADJ-004: Kleisli (1:N) Reverse Mapping
  # ---------------------------------------------------------------------------

  # Validates: REQ-F-ADJ-004
  Scenario: One-to-many backward mapping collects the parent record
    Given a trade "T1" that generates cashflows "CF1", "CF2", "CF3"
    When I apply the backward mapping to cashflows "CF1", "CF2", "CF3"
    Then the result is the parent trade "T1"

  # Validates: REQ-F-ADJ-004
  Scenario: Parent-child mapping is captured during forward execution
    Given a "1:N" transformation from "Trade" to "Cashflow"
    When the forward transformation executes
    Then the parent-child mapping (which trade produced which cashflows) is recorded
    And the mapping is available for backward traceability

  # Validates: REQ-F-ADJ-004
  Scenario: Round-trip is exact when parent tracking is maintained
    Given a trade "T1" that generates cashflows "CF1", "CF2"
    When I apply the forward mapping to "T1" and then the backward mapping
    Then the result is exactly "T1"

  # ---------------------------------------------------------------------------
  # REQ-F-ADJ-005: Aggregation Reverse Mapping
  # ---------------------------------------------------------------------------

  # Validates: REQ-F-ADJ-005
  Scenario: Aggregation backward mapping returns all contributing source records
    Given trades "T1" (100), "T2" (200), "T3" (300) aggregated by counterparty "C1"
    And the aggregation produces a sum of 600 for "C1"
    When I apply the backward mapping to the aggregated value for "C1"
    Then the result contains trades "T1", "T2", and "T3"

  # Validates: REQ-F-ADJ-005
  Scenario: Reverse-mapping table maps output groups to contributing inputs
    Given an aggregation that groups trades by counterparty
    When the aggregation executes
    Then a reverse-mapping table is produced
    And the table maps each counterparty group key to the set of contributing trade record keys

  # Validates: REQ-F-ADJ-005
  Scenario: Containment holds for aggregation reverse mapping
    Given trades "T1", "T2" aggregated into group "G1"
    When I apply the forward aggregation and then the backward mapping to "G1"
    Then the backward result contains at least "T1" and "T2"

  # ---------------------------------------------------------------------------
  # REQ-F-ADJ-006: Filter Reverse Mapping
  # ---------------------------------------------------------------------------

  # Validates: REQ-F-ADJ-006
  Scenario: Filter backward mapping returns both passed and excluded records
    Given 100 trades filtered by "notional > 1000"
    And 70 trades pass the filter and 30 are excluded
    When I apply the backward mapping to the filtered output
    Then the result contains all 70 passed trades tagged as "passed"
    And the result contains all 30 excluded trades tagged as "filtered"

  # Validates: REQ-F-ADJ-006
  Scenario: Excluded record keys are captured during forward execution
    Given a filter transformation is applied during forward execution
    When 30 records are excluded by the filter
    Then the keys of all 30 excluded records are captured and stored
    And the filter predicate metadata is recorded alongside the excluded keys

  # Validates: REQ-F-ADJ-006
  Scenario: Containment holds for filter reverse mapping
    Given 100 input records processed by a filter
    When I apply the forward filter and then the backward mapping
    Then the backward result contains all 100 original records
    And each record is tagged as either "passed" or "filtered"

  # ---------------------------------------------------------------------------
  # REQ-F-ADJ-007: Contravariant Reverse Mapping Composition
  # ---------------------------------------------------------------------------

  # Validates: REQ-F-ADJ-007
  Scenario: Composed reverse mappings apply in reverse order
    Given transformation "f" from "Trade" to "Counterparty"
    And transformation "g" from "Counterparty" to "Region"
    And the composed forward path is "g after f" (Trade -> Counterparty -> Region)
    When I apply the backward mapping of the composed path
    Then the backward applies "f-backward" after "g-backward"
    That is, the backward goes Region -> Counterparty -> Trade (reverse order)

  # Validates: REQ-F-ADJ-007
  Scenario: Contravariant composition is validated at definition time
    Given transformations "f" and "g" with their respective backward mappings
    When I compose "g after f"
    Then the system validates that the backward composition "f-backward after g-backward" is type-compatible
    And the validation occurs before any data is processed

  # Validates: REQ-F-ADJ-007
  Scenario: Identity reverse mapping is itself
    Given the identity transformation on "Trade"
    When I apply the backward mapping of the identity
    Then the result is the identity transformation
    And the round-trip produces the original trade unchanged

  # ---------------------------------------------------------------------------
  # REQ-F-ADJ-008: Backward Metadata Capture
  # ---------------------------------------------------------------------------

  # Validates: REQ-F-ADJ-008
  Scenario: Reverse-mapping metadata is captured during forward execution
    Given an aggregation transformation that groups trades by counterparty
    When the forward execution completes
    Then reverse-mapping tables are populated with group-key-to-input-key mappings
    And the metadata is stored persistently (survives process restart)

  # Validates: REQ-F-ADJ-008
  Scenario: Filter metadata is captured during forward execution
    Given a filter transformation that excludes trades below a threshold
    When the forward execution completes
    Then excluded record keys are stored persistently
    And the filter predicate description is stored alongside

  # Validates: REQ-F-ADJ-008
  Scenario: Parent-child metadata is captured during one-to-many forward execution
    Given a "1:N" transformation from "Trade" to "Cashflow"
    When the forward execution completes
    Then parent-child mappings (trade-to-cashflow) are stored persistently
    And the mappings are stored within the same processing window context as the forward run

  # Validates: REQ-F-ADJ-008
  Scenario: Backward metadata is stored within the same processing window context
    Given a forward execution for processing window "2025-03-15"
    When the backward metadata is captured
    Then the metadata is tagged with processing window "2025-03-15"
    And the metadata is accessible only within the "2025-03-15" context
