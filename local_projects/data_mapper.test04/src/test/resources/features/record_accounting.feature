@Accounting
Feature: Record Accounting and Completeness Guarantees
  As a data architect
  I want every input record accounted for in exactly one output partition
  So that no data is silently lost and completeness is provable

  Background:
    Given a pipeline processing 10,000 trade records
    And a configured error destination and output destination

  # ---------------------------------------------------------------------------
  # REQ-F-ACC-001: Accounting Invariant
  # ---------------------------------------------------------------------------

  # Validates: REQ-F-ACC-001
  Scenario: Every input record appears in exactly one output partition
    When the pipeline completes processing
    Then the count of processed records plus filtered records plus errored records equals 10,000
    And no record appears in more than one partition
    And no record is unaccounted for

  # Validates: REQ-F-ACC-001
  Scenario Outline: Accounting invariant holds for different failure distributions
    Given <processed> records are processed successfully
    And <filtered> records are intentionally filtered out
    And <errored> records fail with errors
    When the pipeline completes
    Then the accounting equation holds: <processed> + <filtered> + <errored> = 10000

    Examples:
      | processed | filtered | errored |
      | 10000     | 0        | 0       |
      | 9500      | 300      | 200     |
      | 0         | 0        | 10000   |
      | 8000      | 2000     | 0       |

  # Validates: REQ-F-ACC-001
  Scenario: Verification runs automatically before a run is marked complete
    When the pipeline finishes processing all records
    Then the accounting invariant is verified automatically
    And the verification occurs before the run status is set to "COMPLETE"

  # Validates: REQ-F-ACC-001
  Scenario: Mutual exclusivity -- no record in multiple partitions
    Given a trade "T1" that is processed successfully
    Then "T1" does not appear in the filtered partition
    And "T1" does not appear in the errored partition

  # ---------------------------------------------------------------------------
  # REQ-F-ACC-002: Accounting Ledger
  # ---------------------------------------------------------------------------

  # Validates: REQ-F-ACC-002
  Scenario: Each pipeline run produces a structured accounting ledger
    When the pipeline completes
    Then an accounting ledger is produced containing:
      | field                      | description                          |
      | input_record_count         | total input records (10,000)         |
      | source_key_field           | identifier for the input key         |
      | processed_count            | records in success output            |
      | filtered_count             | records intentionally excluded       |
      | errored_count              | records routed to error destination  |
      | verification_status        | balanced or unbalanced               |

  # Validates: REQ-F-ACC-002
  Scenario: Accounting ledger references backward traceability metadata
    When the pipeline completes
    Then the accounting ledger contains references to:
      | metadata_type            | description                                       |
      | reverse_mapping_tables   | locations of group-key-to-input-key mappings       |
      | filtered_key_stores      | locations of excluded record key lists              |

  # Validates: REQ-F-ACC-002
  Scenario: Accounting ledger is written atomically at run completion
    When the pipeline completes (whether success or failure)
    Then the accounting ledger is written as a single atomic operation
    And a partially written ledger is never visible to consumers

  # Validates: REQ-F-ACC-002
  Scenario: Accounting ledger records verification status
    Given a pipeline run where the accounting invariant holds
    When the ledger is produced
    Then the verification_status is "balanced"
    Given a pipeline run where the accounting invariant is violated
    When the ledger is produced
    Then the verification_status is "unbalanced"

  # ---------------------------------------------------------------------------
  # REQ-F-ACC-003: Run Completion Gate
  # ---------------------------------------------------------------------------

  # Validates: REQ-F-ACC-003
  Scenario: Run is marked COMPLETE only when accounting is balanced
    Given a pipeline run where all 10,000 records are accounted for
    When the accounting verification passes
    Then the run status is set to "COMPLETE"
    And an observability event of type "COMPLETE" is emitted with a ledger reference

  # Validates: REQ-F-ACC-003
  Scenario: Run is marked FAILED when accounting is unbalanced
    Given a pipeline run where 50 records are unaccounted for
    When the accounting verification fails
    Then the run status is set to "FAILED"
    And an observability event of type "FAIL" is emitted with discrepancy details
    And the ledger is persisted showing the imbalance

  # Validates: REQ-F-ACC-003
  Scenario: Accounting ledger is persisted regardless of pass or fail
    Given a pipeline run that fails accounting verification
    When the run completes
    Then the accounting ledger is still written and persisted
    And the ledger shows the exact discrepancy

  # Validates: REQ-F-ACC-003
  Scenario: No manual override of the completion gate is permitted
    Given a pipeline run where 100 records are unaccounted for
    When the accounting verification fails
    Then there is no mechanism to force the run to "COMPLETE" status
    And the run remains in "FAILED" status until the discrepancy is resolved

  # ---------------------------------------------------------------------------
  # REQ-F-ACC-004: Backward Traversal Proof
  # ---------------------------------------------------------------------------

  # Validates: REQ-F-ACC-004
  Scenario: Any output record is traceable to its source records
    Given a completed pipeline run with aggregated output
    When I query the backward traceability for output group "G1"
    Then the system returns the set of source trade keys that contributed to "G1"
    And no re-execution of the pipeline is required

  # Validates: REQ-F-ACC-004
  Scenario: Filtered records are traceable as intentional exclusions
    Given a completed pipeline run where trade "T5" was filtered out
    When I query the backward traceability for trade "T5"
    Then the system confirms "T5" was intentionally excluded
    And the filter criteria that excluded "T5" are returned

  # Validates: REQ-F-ACC-004
  Scenario: Errored records are traceable to their failure reason
    Given a completed pipeline run where trade "T9" failed processing
    When I query the backward traceability for trade "T9"
    Then the system returns the original record for "T9"
    And the system returns the failure reason (error object)

  # Validates: REQ-F-ACC-004
  Scenario: Backward traceability is deterministic
    Given a completed pipeline run
    When I query backward traceability for the same output key twice
    Then both queries return identical results
    And the results do not change over time for the same run

  # ---------------------------------------------------------------------------
  # REQ-F-ACC-005: Morphism-Level RBAC
  # ---------------------------------------------------------------------------

  # Validates: REQ-F-ACC-005
  Scenario: User without permission cannot see restricted transformations
    Given a transformation "sensitiveMapping" restricted to role "risk_analyst"
    And a user with role "general_analyst"
    When the user views the available transformations
    Then "sensitiveMapping" does not appear in their view of the data model

  # Validates: REQ-F-ACC-005
  Scenario: Path referencing a denied transformation is rejected
    Given a transformation "sensitiveMapping" restricted to role "risk_analyst"
    And a user with role "general_analyst"
    When the user tries to define a path that includes "sensitiveMapping"
    Then the path is rejected with error "Access denied: transformation 'sensitiveMapping' is not available"
    And the rejection occurs at definition time

  # Validates: REQ-F-ACC-005
  Scenario: User with correct role can access restricted transformations
    Given a transformation "sensitiveMapping" restricted to role "risk_analyst"
    And a user with role "risk_analyst"
    When the user views the available transformations
    Then "sensitiveMapping" appears in their view of the data model
    And the user can include "sensitiveMapping" in traversal paths
