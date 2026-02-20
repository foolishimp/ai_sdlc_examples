@Traversal
Feature: Data Traversal and Path Execution
  As a data architect
  I want data traversal paths to be validated and executed with safety guarantees
  So that transformations are correct, grain-safe, and deterministic

  Background:
    Given a data model with the following entities and granularity levels:
      | entity         | granularity |
      | Trade          | Atomic      |
      | Counterparty   | Atomic      |
      | DailySummary   | Daily       |
      | Cashflow       | Atomic      |
    And a "N:1" relationship from "Trade" to "Counterparty"
    And a "1:N" relationship from "Trade" to "Cashflow"

  # ---------------------------------------------------------------------------
  # REQ-F-TRV-001: Kleisli Context Lifting
  # ---------------------------------------------------------------------------

  # Validates: REQ-F-TRV-001
  Scenario: Traversing a one-to-many relationship produces a list of results
    Given a trade that generates 3 cashflows
    When I traverse the path "Trade -> Cashflow"
    Then the result is a list of 3 cashflow records
    And the execution context has been lifted from single-record to list

  # Validates: REQ-F-TRV-001
  Scenario: Scalar operations are applied to each record in a list context
    Given a trade that generates 3 cashflows
    And each cashflow has a "payment_amount" attribute
    When I traverse "Trade -> Cashflow" and then apply "payment_amount" lookup
    Then "payment_amount" is extracted from each of the 3 cashflows individually

  # Validates: REQ-F-TRV-001
  Scenario: Nested one-to-many traversals flatten correctly
    Given a trade that generates 3 cashflows
    And each cashflow has 2 settlement instructions
    When I traverse "Trade -> Cashflow -> SettlementInstruction"
    Then the result is a flat list of 6 settlement instructions
    And the result is not a nested list of lists

  # Validates: REQ-F-TRV-001
  Scenario: Context type is tracked through the entire traversal path
    Given a path "Trade -> Counterparty" with cardinality "N:1"
    Then the execution context remains "single-record"
    Given a path "Trade -> Cashflow" with cardinality "1:N"
    Then the execution context becomes "list"
    Given a path "Trade -> Cashflow -> SettlementInstruction" with cardinalities "1:N, 1:N"
    Then the execution context remains "list" (flattened, not nested)

  # ---------------------------------------------------------------------------
  # REQ-F-TRV-002: Grain Safety Enforcement
  # ---------------------------------------------------------------------------

  # Validates: REQ-F-TRV-002
  Scenario: Combining attributes from incompatible granularity levels is rejected
    Given a "Trade" entity at "Atomic" granularity
    And a "DailySummary" entity at "Daily" granularity
    When I try to project "Trade.notional" and "DailySummary.total_volume" into the same output record
    Then the path is rejected with error "Granularity violation: cannot combine 'Atomic' and 'Daily' without explicit aggregation"
    And the rejection occurs at definition time

  # Validates: REQ-F-TRV-002
  Scenario: Expressions mixing incompatible granularity levels are rejected
    When I define a calculated field "Trade.notional * DailySummary.average_price"
    Then the definition is rejected with error "Granularity violation: expression mixes 'Atomic' and 'Daily'"
    And the rejection occurs at definition time

  # Validates: REQ-F-TRV-002
  Scenario: Joining entities at different granularity levels without aggregation is rejected
    When I define a join between "Trade" at "Atomic" and "DailySummary" at "Daily"
    And no aggregation is specified
    Then the join is rejected with error "Granularity violation: join between 'Atomic' and 'Daily' requires explicit aggregation"

  # Validates: REQ-F-TRV-002
  Scenario: Combining attributes with explicit aggregation is accepted
    Given a "Trade" entity at "Atomic" granularity
    And a "DailySummary" entity at "Daily" granularity
    When I aggregate "Trade.notional" using "sum" to "Daily" granularity
    And I combine the aggregated value with "DailySummary.total_volume"
    Then the path is accepted because both values are at "Daily" granularity

  # ---------------------------------------------------------------------------
  # REQ-F-TRV-003: Context Consistency Enforcement
  # ---------------------------------------------------------------------------

  # Validates: REQ-F-TRV-003
  Scenario: Joining entities from the same processing window is accepted
    Given "Trade" data from processing window "2025-03-15"
    And "Counterparty" data from processing window "2025-03-15"
    When I join "Trade" with "Counterparty"
    Then the join is accepted because both share the same processing window

  # Validates: REQ-F-TRV-003
  Scenario: Joining entities from different processing windows without temporal semantics is rejected
    Given "Trade" data from processing window "2025-03-15"
    And "Counterparty" data from processing window "2025-03-14"
    When I join "Trade" with "Counterparty" without declaring temporal semantics
    Then the join is rejected with error "Processing window mismatch: 'Trade' at '2025-03-15' and 'Counterparty' at '2025-03-14' require declared temporal semantics"

  # Validates: REQ-F-TRV-003
  Scenario Outline: Cross-window joins with declared temporal semantics are accepted
    Given "Trade" data from processing window "2025-03-15"
    And "Counterparty" data from processing window "2025-03-14"
    When I join "Trade" with "Counterparty" using temporal semantic "<semantic>"
    Then the join is accepted with temporal semantic "<semantic>"

    Examples:
      | semantic |
      | As-Of    |
      | Latest   |
      | Exact    |

  # ---------------------------------------------------------------------------
  # REQ-F-TRV-004: Boundary Alignment Detection
  # ---------------------------------------------------------------------------

  # Validates: REQ-F-TRV-004
  Scenario: Cross-boundary traversal is detected automatically
    Given "Trade" data is partitioned by daily processing windows
    And "ReferenceRate" data is partitioned by monthly processing windows
    When I define a path from "Trade" to "ReferenceRate"
    Then the system detects a processing window boundary crossing
    And the system requires a declared temporal semantic for the crossing

  # Validates: REQ-F-TRV-004
  Scenario: Undeclared cross-boundary traversal is rejected
    Given a path that crosses a processing window boundary
    When no temporal semantic is declared for the crossing
    Then the path is rejected with error "Processing window boundary crossing detected: temporal semantic required"
    And the rejection identifies the exact point where the boundary is crossed

  # Validates: REQ-F-TRV-004
  Scenario: Boundary alignment is validated at definition time
    Given a path that crosses a processing window boundary with declared temporal semantics
    When the path is validated
    Then validation occurs before any data is processed
    And the declared temporal semantic is recorded in the path metadata

  # ---------------------------------------------------------------------------
  # REQ-F-TRV-005: Operational Telemetry
  # ---------------------------------------------------------------------------

  # Validates: REQ-F-TRV-005
  Scenario: Row counts are captured at each transformation step
    Given a pipeline that applies 3 transformation steps
    When the pipeline executes
    Then the telemetry log records the row count after each transformation step
    And the telemetry does not alter the transformation results

  # Validates: REQ-F-TRV-005
  Scenario: Quality metrics are accumulated during execution
    Given a pipeline that processes 1000 trade records
    When the pipeline executes
    Then the telemetry captures null rates per attribute
    And the telemetry captures type violation counts
    And the telemetry captures latency per transformation step

  # Validates: REQ-F-TRV-005
  Scenario: Telemetry is a side-channel that does not affect results
    Given a pipeline producing output "X" without telemetry
    When I enable telemetry and re-run the same pipeline
    Then the output is still "X" (bitwise identical)
    And telemetry data is available separately

  # ---------------------------------------------------------------------------
  # REQ-F-TRV-006: Deterministic Reproducibility
  # ---------------------------------------------------------------------------

  # Validates: REQ-F-TRV-006
  Scenario: Identical inputs with identical configuration produce identical outputs
    Given a pipeline with fixed input data and configuration
    When I run the pipeline twice
    Then both runs produce bitwise identical outputs

  # Validates: REQ-F-TRV-006
  Scenario: Non-deterministic operations without seeding are rejected
    When I define a transformation that calls "random()" without a seed
    Then the definition is rejected with error "Non-deterministic operation 'random()' requires a seed"

  # Validates: REQ-F-TRV-006
  Scenario: Lookup versions are pinned per execution context
    Given a "CurrencyRate" lookup at version "v3"
    And the pipeline is configured to use processing window "2025-03-15"
    When the pipeline executes
    Then all lookup resolutions use version "v3" throughout the entire run
    And the lookup version is recorded in the execution metadata

  # ---------------------------------------------------------------------------
  # REQ-F-TRV-007: Cardinality Cost Estimation
  # ---------------------------------------------------------------------------

  # Validates: REQ-F-TRV-007
  Scenario: Cost estimation runs before execution
    Given a pipeline with a "1:N" traversal that may expand records
    And a cardinality budget of 1,000,000 output rows
    When I compile the pipeline
    Then a cardinality estimate is produced before any data is processed
    And the estimate identifies potential expansion points

  # Validates: REQ-F-TRV-007
  Scenario: Pipeline exceeding cardinality budget is rejected
    Given a pipeline estimated to produce 5,000,000 output rows
    And a cardinality budget of 1,000,000 output rows
    When I compile the pipeline
    Then the pipeline is rejected with error "Cardinality budget exceeded"
    And the rejection report identifies the transformation step causing the explosion

  # Validates: REQ-F-TRV-007
  Scenario: Cardinality budget is configurable per execution
    Given a pipeline estimated to produce 5,000,000 output rows
    When I set the cardinality budget to 10,000,000 output rows
    Then the pipeline is accepted
    When I set the cardinality budget to 1,000,000 output rows
    Then the pipeline is rejected
