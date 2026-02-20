@ErrorHandling
Feature: Error Handling and Failure Reporting
  As a data architect
  I want all failures to be captured as structured data objects
  So that no data is silently lost and errors are fully traceable

  Background:
    Given a pipeline with a configured error destination
    And an input dataset of 1000 trade records

  # ---------------------------------------------------------------------------
  # REQ-F-ERR-001: Failures as Data
  # ---------------------------------------------------------------------------

  # Validates: REQ-F-ERR-001
  Scenario: Failed records are routed to the error destination as data objects
    Given 5 records fail a type validation during processing
    When the pipeline executes
    Then 995 records appear in the success output
    And 5 structured error objects appear in the error destination
    And no records are silently dropped

  # Validates: REQ-F-ERR-001
  Scenario: Individual record failures do not halt the entire batch
    Given 10 records fail a refinement type predicate
    And the batch failure threshold is set to 5%
    When the pipeline executes
    Then processing continues for the remaining 990 records
    And all 10 failed records are routed to the error destination

  # Validates: REQ-F-ERR-001
  Scenario: Every transformation step produces either a success value or an error
    Given a transformation step applied to 100 records
    When 3 records fail the transformation
    Then 3 error objects are produced for the failed records
    And 97 success values are produced for the passing records
    And 3 + 97 = 100 (no records unaccounted for)

  # ---------------------------------------------------------------------------
  # REQ-F-ERR-002: Error Object Structure
  # ---------------------------------------------------------------------------

  # Validates: REQ-F-ERR-002
  Scenario: Error objects contain complete diagnostic information
    Given a record with "notional" value "-100" that violates the "PositiveDecimal" constraint
    When the pipeline processes this record
    Then the error object contains:
      | field              | value                                     |
      | constraint_type    | refinement_predicate                      |
      | offending_values   | notional: -100                            |
      | source_entity      | Trade                                     |
      | source_epoch       | 2025-03-15                                |
      | morphism_path      | Trade.notional -> PositiveDecimal check   |
      | timestamp          | (a valid timestamp)                       |

  # Validates: REQ-F-ERR-002
  Scenario: Error objects are structured, not free-text
    Given a record that fails processing
    When the error object is created
    Then every field in the error object has a defined schema
    And the error object is machine-readable (not a plain text message)
    And the error object can be serialised to the error destination

  # Validates: REQ-F-ERR-002
  Scenario: Error objects include the full path where failure occurred
    Given a transformation chain "Trade -> Counterparty -> Region"
    And a failure occurs at the "Counterparty -> Region" step
    When the error object is created
    Then the morphism path in the error object shows "Trade -> Counterparty -> Region"
    And the failure point is identified as "Counterparty -> Region"

  # ---------------------------------------------------------------------------
  # REQ-F-ERR-003: Idempotent Error Handling
  # ---------------------------------------------------------------------------

  # Validates: REQ-F-ERR-003
  Scenario: Re-processing the same failing records produces identical errors
    Given a set of 10 records that fail a type constraint
    When I process these records once and collect the error output
    And I process the same records a second time with the same configuration
    Then both error outputs are bitwise identical

  # Validates: REQ-F-ERR-003
  Scenario: Error ordering is deterministic for the same input ordering
    Given an input dataset with records in a specific order
    When I process the dataset twice
    Then the errors appear in the same order in both runs

  # Validates: REQ-F-ERR-003
  Scenario: No non-deterministic side effects in error routing
    Given a failing record processed twice with the same configuration
    When I compare the two error objects
    Then the only difference may be the processing timestamp
    And all diagnostic fields (constraint_type, offending_values, source_entity, morphism_path) are identical

  # ---------------------------------------------------------------------------
  # REQ-F-ERR-004: Error Sink Routing
  # ---------------------------------------------------------------------------

  # Validates: REQ-F-ERR-004
  Scenario: All error objects are written to the declared error destination
    Given 15 records fail during processing
    When the pipeline completes
    Then exactly 15 error objects appear in the configured error destination
    And no error objects are lost or written to an alternative location

  # Validates: REQ-F-ERR-004
  Scenario Outline: Error destination supports multiple storage types
    Given an error destination configured as "<storage_type>"
    When errors are routed during processing
    Then errors are written to the "<storage_type>" destination

    Examples:
      | storage_type    |
      | file system     |
      | database table  |
      | message queue   |

  # Validates: REQ-F-ERR-004
  Scenario: Missing error destination declaration is a validation error
    When I define a pipeline without declaring an error destination
    Then the pipeline definition is rejected with error "Error destination must be declared in the execution configuration"
    And no execution occurs

  # Validates: REQ-F-ERR-004
  Scenario: Error destination is declared in the execution configuration
    Given an execution configuration with error destination "errors_db.trade_errors"
    When the pipeline is compiled
    Then the error destination is recorded in the execution artifact
    And all subsequent errors are routed to "errors_db.trade_errors"
