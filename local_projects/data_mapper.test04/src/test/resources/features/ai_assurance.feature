@AIAssurance
Feature: AI Assurance and Validation
  As a data architect
  I want AI-generated mapping definitions validated with the same rigour as human-authored ones
  So that no hallucinated or invalid mappings can enter the system

  # ---------------------------------------------------------------------------
  # REQ-F-AI-001: Topological Validity Check
  # ---------------------------------------------------------------------------

  # Validates: REQ-F-AI-001
  Scenario: AI-generated mapping passes identical validation as human-authored mapping
    Given a human-authored mapping "Trade -> Counterparty" that is valid
    And an AI-generated mapping "Trade -> Counterparty" with identical structure
    When both mappings are validated
    Then both pass validation with the same result

  # Validates: REQ-F-AI-001
  Scenario: AI-generated mapping referencing non-existent entity is rejected
    Given a data model with "Trade" and "Counterparty" entities
    When an AI generates a mapping from "Trade" to "SettlementAccount"
    And "SettlementAccount" does not exist in the data model
    Then the mapping is rejected with error "Entity 'SettlementAccount' not found in the data model"

  # Validates: REQ-F-AI-001
  Scenario: AI-generated mapping referencing non-existent relationship is rejected
    Given a data model with "Trade" and "Counterparty" entities
    And no "executes_at" relationship exists
    When an AI generates a mapping using "Trade.executes_at.Venue"
    Then the mapping is rejected with error "Relationship 'executes_at' not found on entity 'Trade'"

  # Validates: REQ-F-AI-001
  Scenario: AI-generated mapping with type violations is rejected
    Given an AI generates a mapping that assigns a "String" value to an "Int" attribute
    When the mapping is validated
    Then the mapping is rejected with error "Type mismatch: 'String' cannot be assigned to 'Int'"
    And the diagnostic message identifies the specific attribute and type conflict

  # Validates: REQ-F-AI-001
  Scenario: AI-generated mapping with granularity violations is rejected
    Given an AI generates a mapping that combines "Atomic" and "Daily" attributes without aggregation
    When the mapping is validated
    Then the mapping is rejected with a granularity violation error
    And the rejection is identical to what a human-authored mapping would receive

  # Validates: REQ-F-AI-001
  Scenario: Validation results include specific diagnostics per violation
    Given an AI generates a mapping with 3 separate violations
    When the mapping is validated
    Then the validation result contains 3 distinct diagnostic messages
    And each diagnostic identifies the specific rule that was violated

  # ---------------------------------------------------------------------------
  # REQ-F-AI-002: Triangulation of Assurance
  # ---------------------------------------------------------------------------

  # Validates: REQ-F-AI-002
  Scenario: Intent-to-logic mapping is traceable
    Given a business intent "Calculate counterparty exposure for all trades"
    And a mapping definition that implements this intent
    When I request the triangulation report
    Then the report links the intent to the specific transformation definitions

  # Validates: REQ-F-AI-002
  Scenario: Logic-to-proof mapping is traceable
    Given a transformation definition for "exposure calculation"
    And an execution trace from a pipeline run
    When I request the triangulation report
    Then the report links the transformation definition to the execution trace
    And includes the type unification report

  # Validates: REQ-F-AI-002
  Scenario: Proof-to-intent validation is automatable
    Given a completed pipeline run with lineage data
    When I run proof-to-intent validation
    Then the system checks whether the lineage graph covers the declared business intent
    And reports any intents that are not covered by the execution

  # Validates: REQ-F-AI-002
  Scenario: Triangulation report is producible per execution
    Given a completed pipeline run
    When I request a triangulation report
    Then the report is generated successfully
    And it contains intent coverage, transformation definitions, and execution proof

  # ---------------------------------------------------------------------------
  # REQ-F-AI-003: Dry Run Mode
  # ---------------------------------------------------------------------------

  # Validates: REQ-F-AI-003
  Scenario: Dry run performs full validation without writing results
    Given a pipeline configuration and input data
    When I execute the pipeline in dry-run mode
    Then full validation is performed
    And an execution plan is produced
    And an estimated cardinality is reported
    And no data is written to the output destination
    And no data is written to the error destination

  # Validates: REQ-F-AI-003
  Scenario: Dry run results indicate pass or fail with diagnostics
    Given a pipeline with a type violation in one transformation
    When I execute the pipeline in dry-run mode
    Then the dry run result indicates "FAIL"
    And the specific type violation is identified in the diagnostics

  # Validates: REQ-F-AI-003
  Scenario: Dry run uses the same validation logic as real execution
    Given a pipeline that would fail validation in real execution
    When I execute the pipeline in dry-run mode
    Then the dry run reports the same failures as real execution would
    And the diagnostic messages are identical

  # Validates: REQ-F-AI-003
  Scenario: Dry run of a valid pipeline reports success
    Given a fully valid pipeline configuration
    When I execute the pipeline in dry-run mode
    Then the dry run result indicates "PASS"
    And the execution plan shows the transformation sequence
    And the estimated cardinality is reported
    And no side effects have occurred
