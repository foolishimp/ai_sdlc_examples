@BusinessRules
Feature: Business Rules Enforcement
  As a data architect
  I want critical business rules enforced as structural constraints
  So that violations are impossible by construction

  # ---------------------------------------------------------------------------
  # REQ-BR-LDM-001: Grain Mixing Prohibition
  # ---------------------------------------------------------------------------

  # Validates: REQ-BR-LDM-001
  Scenario: Combining attributes from different granularity levels without aggregation is forbidden
    Given a "Trade" entity at "Atomic" granularity
    And a "MonthlySummary" entity at "Monthly" granularity
    When I try to project "Trade.notional" and "MonthlySummary.total_pnl" into the same output record
    Then the projection is rejected with error "Granularity mixing forbidden: 'Atomic' (Trade.notional) and 'Monthly' (MonthlySummary.total_pnl) cannot be combined without explicit aggregation"

  # Validates: REQ-BR-LDM-001
  Scenario: Error identifies the conflicting granularity levels and attributes
    When I try to combine "Atomic" and "Daily" data in a single record
    Then the error message identifies both the granularity levels ("Atomic", "Daily") and the specific attributes involved

  # Validates: REQ-BR-LDM-001
  Scenario: Grain mixing prohibition is enforced at definition time
    When I try to define a path that mixes granularity levels
    Then the rejection occurs at definition time (before any data is processed)
    And no execution plan is generated

  # ---------------------------------------------------------------------------
  # REQ-BR-LDM-002: Lookup Immutability within Processing Window
  # ---------------------------------------------------------------------------

  # Validates: REQ-BR-LDM-002
  Scenario: Same lookup key returns the same value within a processing window
    Given a "CurrencyRate" lookup pinned to processing window "2025-03-15"
    And key "USD/EUR" maps to "0.92"
    When the pipeline resolves "USD/EUR" multiple times during the same run
    Then every resolution returns "0.92"

  # Validates: REQ-BR-LDM-002
  Scenario: Lookup mutation detected within a processing window halts execution
    Given a "CurrencyRate" lookup for processing window "2025-03-15"
    When the system detects that key "USD/EUR" changed from "0.92" to "0.93" during execution
    Then execution is halted with error "Lookup mutation detected: 'CurrencyRate' key 'USD/EUR' changed within processing window '2025-03-15'"

  # Validates: REQ-BR-LDM-002
  Scenario: Lookup version is recorded in lineage for reproducibility
    Given a pipeline that uses "CurrencyRate" lookup at version "v3"
    When the pipeline completes
    Then the lineage metadata records "CurrencyRate" version "v3"
    And this version can be used to reproduce the exact same results

  # ---------------------------------------------------------------------------
  # REQ-BR-TYP-001: Explicit Conversion Mandate
  # ---------------------------------------------------------------------------

  # Validates: REQ-BR-TYP-001
  Scenario: Every type conversion is an explicit named transformation
    Given a need to convert "Int" to "String"
    Then a named conversion transformation "IntToString" must be defined
    And the conversion is visible in the data model as a declared transformation

  # Validates: REQ-BR-TYP-001
  Scenario: No implicit type conversions exist in the system
    When I audit the type system for implicit conversions
    Then zero implicit conversions are found
    And every type change pathway is a declared, named transformation

  # Validates: REQ-BR-TYP-001
  Scenario: Hidden type coercions are structurally impossible
    Given a "Decimal" value and an "Int" target attribute
    When the system processes the assignment
    Then no silent rounding, truncation, or coercion occurs
    And the assignment is rejected unless an explicit conversion is applied

  # ---------------------------------------------------------------------------
  # REQ-BR-ERR-001: No Silent Data Loss
  # ---------------------------------------------------------------------------

  # Validates: REQ-BR-ERR-001
  Scenario: Every input record appears in exactly one output partition
    Given a pipeline processing 10,000 records
    When the pipeline completes
    Then each of the 10,000 input records appears in exactly one of:
      | partition  |
      | processed  |
      | filtered   |
      | errored    |
    And no record is in zero partitions
    And no record is in multiple partitions

  # Validates: REQ-BR-ERR-001
  Scenario: Accounting violation triggers FAILED status
    Given a pipeline where the accounting invariant is violated
    When the completion check runs
    Then the run is marked as "FAILED"
    And the discrepancy details are reported

  # Validates: REQ-BR-ERR-001
  Scenario: Silent drops are structurally impossible when accounting holds
    Given the accounting invariant is enforced
    Then there is no code path that can discard a record without routing it to an output partition
    And any violation is detected automatically before the run is marked complete

  # ---------------------------------------------------------------------------
  # REQ-BR-AI-001: No AI Fast-Path
  # ---------------------------------------------------------------------------

  # Validates: REQ-BR-AI-001
  Scenario: Validation code path is identical for AI and human authors
    Given an AI-generated mapping and a human-authored mapping with identical structure
    When both are validated
    Then the exact same validation code path is executed for both
    And both produce identical validation results

  # Validates: REQ-BR-AI-001
  Scenario: No configuration flag bypasses validation for AI-generated mappings
    When I search for any configuration option to skip or relax validation for AI-generated content
    Then no such option exists in the system
    And there is no mechanism to treat AI-generated mappings differently from human-authored ones

  # Validates: REQ-BR-AI-001
  Scenario: Invalid AI-generated mapping is rejected the same way as an invalid human mapping
    Given an invalid mapping (referencing a non-existent entity)
    When the mapping is submitted as AI-generated
    Then it is rejected with the same error message as if it were human-authored
    When the same mapping is submitted as human-authored
    Then it is rejected with the identical error message

  # ---------------------------------------------------------------------------
  # REQ-BR-ADJ-001: Adjoint Execution Context
  # ---------------------------------------------------------------------------

  # Validates: REQ-BR-ADJ-001
  Scenario: Backward traversal uses the same processing window as the forward run
    Given a forward pipeline run for processing window "2025-03-15"
    And backward traceability metadata was captured during the forward run
    When I execute a backward traversal
    Then the backward traversal uses processing window "2025-03-15" context
    And the results are consistent with the forward run

  # Validates: REQ-BR-ADJ-001
  Scenario: Cross-window backward query without temporal semantics is rejected
    Given backward traceability metadata from processing window "2025-03-15"
    When I request a backward traversal using processing window "2025-03-16" without temporal semantics
    Then the request is rejected with error "Cross-window backward query requires declared temporal semantics"

  # Validates: REQ-BR-ADJ-001
  Scenario: Backward traceability metadata is tagged with its processing window of origin
    Given a forward pipeline run for processing window "2025-03-15"
    When the backward metadata is persisted
    Then the metadata carries the tag "epoch: 2025-03-15"
    And it can only be used for backward queries in the "2025-03-15" context (unless temporal semantics are declared)
