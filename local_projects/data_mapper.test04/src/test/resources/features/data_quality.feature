@DataQuality
Feature: Data Quality Controls
  As a data architect
  I want configurable batch thresholds, circuit breakers, and schema validation
  So that structural errors are caught early and data quality issues are managed

  # ---------------------------------------------------------------------------
  # REQ-DATA-QUAL-001: Batch Failure Threshold
  # ---------------------------------------------------------------------------

  # Validates: REQ-DATA-QUAL-001
  Scenario: Batch failure threshold as percentage halts processing when exceeded
    Given a pipeline processing 10,000 records
    And a batch failure threshold of 2% (200 records)
    When 250 records fail during processing
    Then the pipeline halts with status "FAILED"
    And the error destination contains all failures encountered before the halt

  # Validates: REQ-DATA-QUAL-001
  Scenario: Batch failure threshold as absolute count halts processing when exceeded
    Given a pipeline processing 10,000 records
    And a batch failure threshold of 100 records (absolute)
    When 120 records fail during processing
    Then the pipeline halts with status "FAILED"

  # Validates: REQ-DATA-QUAL-001
  Scenario: Processing continues when failure count is below threshold
    Given a pipeline processing 10,000 records
    And a batch failure threshold of 5%
    When 100 records fail during processing (1%)
    Then the pipeline continues processing
    And 9,900 records are in the success output
    And 100 error objects are in the error destination

  # Validates: REQ-DATA-QUAL-001
  Scenario: Batch failure threshold is configurable per job
    Given a pipeline with configurable threshold
    When I set the threshold to 1% for a critical job
    And I set the threshold to 10% for a tolerant job
    Then each job enforces its own threshold independently

  # Validates: REQ-DATA-QUAL-001
  Scenario: Commit or rollback of successful records is configurable on threshold breach
    Given a pipeline that has processed 5,000 records successfully before threshold breach
    When the batch failure threshold is exceeded
    And the configuration specifies "commit" for successful records
    Then the 5,000 successful records are committed to the output
    Given the configuration specifies "rollback" for successful records
    Then no records are committed to the output

  # ---------------------------------------------------------------------------
  # REQ-DATA-QUAL-002: Probabilistic Circuit Breaker
  # ---------------------------------------------------------------------------

  # Validates: REQ-DATA-QUAL-002
  Scenario: Early-stage sampling detects structural errors
    Given a pipeline configured with circuit breaker sample size of 10,000 records
    And a structural error threshold of 5%
    When the first 10,000 records are sampled
    And 800 records (8%) fail with the same type of error
    Then the circuit breaker trips with error "Structural error detected: 8% failure rate exceeds 5% threshold"
    And processing is halted immediately

  # Validates: REQ-DATA-QUAL-002
  Scenario: Circuit breaker distinguishes structural errors from data quality errors
    Given a pipeline configured with a circuit breaker
    When the sample shows 2% failure rate (below the structural threshold)
    Then the circuit breaker allows processing to continue
    And the failures are treated as data quality issues (routed to error destination)

  # Validates: REQ-DATA-QUAL-002
  Scenario: Structural errors do not flood the error destination
    Given a pipeline with a structural misconfiguration
    When the circuit breaker detects 8% failure rate in the sample
    Then processing halts after the sample phase
    And the error destination contains only the sample failures (not millions of identical errors)

  # Validates: REQ-DATA-QUAL-002
  Scenario: Circuit breaker results are recorded in the accounting ledger
    Given a pipeline where the circuit breaker trips
    When the run completes with "FAILED" status
    Then the accounting ledger records the circuit breaker result
    And the ledger shows the sampled failure rate and threshold that was exceeded

  # Validates: REQ-DATA-QUAL-002
  Scenario: Circuit breaker sample size is configurable
    Given a pipeline configured with circuit breaker sample size of 5,000 records
    When the circuit breaker phase runs
    Then exactly 5,000 records are sampled for the structural check

  # ---------------------------------------------------------------------------
  # REQ-DATA-QUAL-003: Input Schema Validation
  # ---------------------------------------------------------------------------

  # Validates: REQ-DATA-QUAL-003
  Scenario: Schema validation runs before any data processing
    Given a physical source bound to a logical entity
    When the pipeline starts
    Then the physical source schema is validated against the expected logical entity schema
    And this validation occurs before any data records are processed

  # Validates: REQ-DATA-QUAL-003
  Scenario: Missing columns in the physical source halt execution
    Given a logical entity "Trade" expects columns "trade_id", "notional", "trade_date"
    And the physical source is missing column "notional"
    When schema validation runs
    Then the pipeline halts with error "Missing column 'notional' in physical source for entity 'Trade'"
    And no data processing occurs

  # Validates: REQ-DATA-QUAL-003
  Scenario: Type mismatch between physical and logical schema halts execution
    Given a logical entity "Trade" expects "notional" as "Decimal"
    And the physical source has "notional" as "String"
    When schema validation runs
    Then the pipeline halts with error "Type mismatch for column 'notional': expected 'Decimal', found 'String'"

  # Validates: REQ-DATA-QUAL-003
  Scenario: Extra columns in the physical source trigger a warning but not a halt
    Given a logical entity "Trade" expects columns "trade_id", "notional"
    And the physical source has columns "trade_id", "notional", "internal_flag"
    When schema validation runs
    Then a warning is logged: "Schema drift detected: extra column 'internal_flag' in physical source for entity 'Trade'"
    And the pipeline continues processing

  # Validates: REQ-DATA-QUAL-003
  Scenario: Schema validation failure produces a structured error
    Given a physical source with multiple schema issues
    When schema validation runs
    Then each issue is reported as a structured error
    And the report includes: column name, expected type, actual type, and issue classification
