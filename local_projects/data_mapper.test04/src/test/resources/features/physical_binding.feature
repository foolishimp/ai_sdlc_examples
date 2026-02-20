@PDM
Feature: Physical Data Binding
  As a data architect
  I want to bind logical entities to physical storage independently of business logic
  So that I can change storage without rewriting transformation rules

  # ---------------------------------------------------------------------------
  # REQ-F-PDM-001: Functorial Mapping
  # ---------------------------------------------------------------------------

  Background:
    Given a logical data model with "Trade" and "Counterparty" entities
    And a relationship from "Trade" to "Counterparty"

  # Validates: REQ-F-PDM-001
  Scenario: Logical model is independent of physical storage
    Given "Trade" is bound to physical table "trades_db.trade_events"
    And "Counterparty" is bound to physical table "ref_db.counterparties"
    When I inspect the "Trade to Counterparty" relationship
    Then the relationship references only logical entity names
    And no physical table names appear in the relationship definition

  # Validates: REQ-F-PDM-001
  Scenario: Re-pointing a physical source does not change business logic
    Given "Trade" is bound to physical table "trades_db.trade_events"
    And a transformation rule "calculate_exposure" is defined using "Trade" and "Counterparty"
    When I re-bind "Trade" to a new physical table "trades_v2.trade_events"
    Then only the physical binding configuration changes
    And the transformation rule "calculate_exposure" remains unchanged
    And no business logic is modified

  # Validates: REQ-F-PDM-001
  Scenario: Business logic referencing physical tables is rejected
    When I define a transformation rule that directly references "trades_db.trade_events"
    Then the definition is rejected with error "Business logic must reference logical entities, not physical tables"

  # Validates: REQ-F-PDM-001
  Scenario: Physical binding preserves relationship composition
    Given "Trade" is bound to physical source "A"
    And "Counterparty" is bound to physical source "B"
    And "Region" is bound to physical source "C"
    When I bind the composed path "Trade -> Counterparty -> Region"
    Then the physical execution of "Trade -> Counterparty -> Region" equals the composition of individual physical bindings

  # ---------------------------------------------------------------------------
  # REQ-F-PDM-002: Generation Grain Semantics
  # ---------------------------------------------------------------------------

  # Validates: REQ-F-PDM-002
  Scenario Outline: Physical source declares a generation grain
    Given a physical source "<source>" for entity "<entity>"
    When I declare its generation grain as "<grain>"
    Then the binding is accepted with generation grain "<grain>"

    Examples:
      | source              | entity       | grain    |
      | trade_events        | Trade        | Event    |
      | counterparty_snap   | Counterparty | Snapshot |

  # Validates: REQ-F-PDM-002
  Scenario: Physical source without generation grain is rejected
    Given a physical source "trade_events" for entity "Trade"
    When I bind it without specifying a generation grain
    Then the binding is rejected with error "Generation grain is required for physical source 'trade_events'"

  # Validates: REQ-F-PDM-002
  Scenario: Generation grain determines how processing windows are applied
    Given a physical source "trade_events" with generation grain "Event"
    Then processing windows are applied as temporal slices over the event stream
    Given a physical source "counterparty_snap" with generation grain "Snapshot"
    Then processing windows are applied as point-in-time snapshots

  # ---------------------------------------------------------------------------
  # REQ-F-PDM-003: Epoch Boundary Definition
  # ---------------------------------------------------------------------------

  # Validates: REQ-F-PDM-003
  Scenario: Event source defines temporal window boundaries
    Given a physical source "trade_events" with generation grain "Event"
    When I define its processing window boundary as "Daily"
    Then the boundary is accepted
    And records are partitioned into daily windows

  # Validates: REQ-F-PDM-003
  Scenario: Snapshot source defines version boundaries
    Given a physical source "counterparty_snap" with generation grain "Snapshot"
    When I define its processing window boundary by "snapshot_timestamp"
    Then the boundary is accepted
    And each snapshot version is a distinct processing window

  # Validates: REQ-F-PDM-003
  Scenario: Physical source without processing window boundary is rejected
    Given a physical source "trade_events" with generation grain "Event"
    When I bind it without defining a processing window boundary
    Then the binding is rejected with error "Processing window boundary is required for source 'trade_events'"

  # Validates: REQ-F-PDM-003
  Scenario: Processing window resolution is deterministic
    Given a physical source with daily processing windows
    When I request data for processing window "2025-03-15"
    Then the same set of records is returned every time for that window identifier

  # ---------------------------------------------------------------------------
  # REQ-F-PDM-004: Lookup Binding
  # ---------------------------------------------------------------------------

  # Validates: REQ-F-PDM-004
  Scenario: Data-backed lookup is bound to a physical table
    Given a "CurrencyRate" lookup entity
    When I bind it as a data-backed lookup to table "ref_db.fx_rates"
    Then the lookup binding is accepted
    And the lookup resolves values from the physical table

  # Validates: REQ-F-PDM-004
  Scenario: Logic-backed lookup is bound to a computed function
    Given a "BusinessDayCalendar" lookup entity
    When I bind it as a logic-backed lookup to a computation function
    Then the lookup binding is accepted
    And the lookup resolves values via the computation function

  # Validates: REQ-F-PDM-004
  Scenario: Both lookup types are transparent to business logic
    Given a data-backed lookup "CurrencyRate" and a logic-backed lookup "BusinessDayCalendar"
    When business logic references both lookups
    Then the business logic does not distinguish between data-backed and logic-backed
    And both lookups are used identically in transformation rules

  # ---------------------------------------------------------------------------
  # REQ-F-PDM-005: Temporal Binding
  # ---------------------------------------------------------------------------

  # Validates: REQ-F-PDM-005
  Scenario: Entity maps to different physical sources across time
    Given a logical entity "Trade"
    When I bind "Trade" to "legacy_trades" for processing windows before "2024-01-01"
    And I bind "Trade" to "new_trades" for processing windows from "2024-01-01" onward
    Then the temporal binding is accepted
    And the correct physical source is selected based on the processing window

  # Validates: REQ-F-PDM-005
  Scenario: Overlapping temporal ranges for the same entity are rejected
    Given a logical entity "Trade"
    When I bind "Trade" to "source_a" for processing windows "2023-01-01" to "2024-06-30"
    And I bind "Trade" to "source_b" for processing windows "2024-01-01" to "2024-12-31"
    Then the binding is rejected with error "Overlapping temporal ranges for entity 'Trade'"

  # Validates: REQ-F-PDM-005
  Scenario: Temporal binding resolution is deterministic
    Given a temporal binding for "Trade" across two physical sources
    When I resolve the physical source for processing window "2024-06-15"
    Then the same physical source is returned every time for that window
