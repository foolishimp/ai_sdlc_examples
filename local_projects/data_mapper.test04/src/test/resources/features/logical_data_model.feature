@LDM
Feature: Data Model Structure
  As a data architect
  I want to define a logical data model with entities, relationships, and rules
  So that data transformations are structurally correct by construction

  # ---------------------------------------------------------------------------
  # REQ-F-LDM-001: Schema as Category
  # ---------------------------------------------------------------------------

  # Validates: REQ-F-LDM-001
  Scenario: Data model contains entities and directed relationships
    Given a data model has been defined with "Trade" and "Counterparty" entities
    And a "belongs_to" relationship links "Trade" to "Counterparty"
    When I inspect the data model structure
    Then "Trade" appears as an entity in the data model
    And "Counterparty" appears as an entity in the data model
    And a directed relationship "belongs_to" exists from "Trade" to "Counterparty"

  # Validates: REQ-F-LDM-001
  Scenario: Every entity has an identity relationship
    Given a data model has been defined with a "Trade" entity
    When I look up the identity relationship for "Trade"
    Then the identity mapping returns the original trade data unchanged

  # Validates: REQ-F-LDM-001
  Scenario: Chaining relationships satisfies associativity
    Given a data model with entities "Trade", "Counterparty", and "Region"
    And a relationship "f" from "Trade" to "Counterparty"
    And a relationship "g" from "Counterparty" to "Region"
    And a relationship "h" from "Region" to "RegulatoryBody"
    When I chain "h after (g after f)" and "(h after g) after f"
    Then both chained paths produce identical results for any input trade

  # Validates: REQ-F-LDM-001
  Scenario: Identity relationship composes transparently with other relationships
    Given a data model with a "Trade" entity and a relationship "f" from "Trade" to "Counterparty"
    When I chain the identity of "Trade" with "f"
    Then the result is equivalent to applying "f" alone
    When I chain "f" with the identity of "Counterparty"
    Then the result is equivalent to applying "f" alone

  # Validates: REQ-F-LDM-001
  Scenario: Data model supports multiple relationships between the same entity pair
    Given a data model with "Trade" and "Counterparty" entities
    When I define a "primary_counterparty" relationship from "Trade" to "Counterparty"
    And I define a "clearing_counterparty" relationship from "Trade" to "Counterparty"
    Then both relationships coexist in the data model
    And each relationship can be referenced independently

  # Validates: REQ-F-LDM-001
  Scenario Outline: Invalid entity reference is rejected
    Given a data model with "Trade" and "Counterparty" entities
    When I try to define a relationship from "<source>" to "<target>"
    Then the definition is rejected with error "Entity '<missing>' not found"
    And no data processing occurs

    Examples:
      | source | target    | missing   |
      | Trade  | Position  | Position  |
      | Order  | Trade     | Order     |

  # ---------------------------------------------------------------------------
  # REQ-F-LDM-002: Morphism Cardinality Types
  # ---------------------------------------------------------------------------

  # Validates: REQ-F-LDM-002
  Scenario Outline: Every relationship declares exactly one cardinality type
    Given a data model with "Trade" and "Counterparty" entities
    When I define a relationship with cardinality "<cardinality>"
    Then the relationship is accepted with cardinality "<cardinality>"

    Examples:
      | cardinality |
      | 1:1         |
      | N:1         |
      | 1:N         |

  # Validates: REQ-F-LDM-002
  Scenario: Relationship without cardinality type is rejected
    Given a data model with "Trade" and "Counterparty" entities
    When I define a relationship from "Trade" to "Counterparty" without specifying a cardinality type
    Then the definition is rejected with error "Cardinality type is required"

  # Validates: REQ-F-LDM-002
  Scenario: Cardinality type is enforced during path validation
    Given a "1:1" relationship "f" from "Trade" to "TradeDetail"
    And a "1:N" relationship "g" from "TradeDetail" to "Cashflow"
    When I chain "g after f"
    Then the composed path has cardinality context "1:N"

  # ---------------------------------------------------------------------------
  # REQ-F-LDM-003: Path Validation via Composition
  # ---------------------------------------------------------------------------

  # Validates: REQ-F-LDM-003
  Scenario: Valid traversal path is accepted at definition time
    Given a data model with "Trade", "Counterparty", and "Region" entities
    And a relationship from "Trade" to "Counterparty"
    And a relationship from "Counterparty" to "Region"
    When I define a path "Trade.counterparty.region.name"
    Then the path is validated successfully
    And no execution has occurred

  # Validates: REQ-F-LDM-003
  Scenario: Missing relationship in a path is rejected at definition time
    Given a data model with "Trade" and "Counterparty" entities
    And no relationship from "Trade" to "Region" exists
    When I define a path "Trade.region.name"
    Then the path is rejected with error "Relationship 'region' not found on entity 'Trade'"
    And the rejection occurs before any data is processed

  # Validates: REQ-F-LDM-003
  Scenario: Type mismatch between chained relationships is rejected
    Given a relationship "f" from "Trade" to "Counterparty"
    And a relationship "g" from "Instrument" to "Market"
    When I try to chain "g after f"
    Then the chain is rejected with error "Type mismatch: 'Counterparty' is not compatible with 'Instrument'"

  # ---------------------------------------------------------------------------
  # REQ-F-LDM-004: Grain Metadata
  # ---------------------------------------------------------------------------

  # Validates: REQ-F-LDM-004
  Scenario: Every entity declares a granularity level
    Given I define a "Trade" entity with granularity "Atomic"
    And I define a "DailySummary" entity with granularity "Daily"
    Then the "Trade" entity has granularity "Atomic"
    And the "DailySummary" entity has granularity "Daily"

  # Validates: REQ-F-LDM-004
  Scenario: Entity without granularity level is rejected
    When I define a "Trade" entity without specifying a granularity level
    Then the definition is rejected with error "Granularity level is required for entity 'Trade'"

  # Validates: REQ-F-LDM-004
  Scenario: Granularity levels form a hierarchy from fine to coarse
    Given granularity levels "Atomic", "Daily", "Monthly", "Yearly"
    Then "Atomic" is finer than "Daily"
    And "Daily" is finer than "Monthly"
    And "Monthly" is finer than "Yearly"
    And "Yearly" is not finer than "Atomic"

  # Validates: REQ-F-LDM-004
  Scenario: Granularity levels are configurable per data model
    Given a data model configured with granularity levels "Tick", "Intraday", "EndOfDay"
    When I define a "Quote" entity with granularity "Tick"
    Then the definition is accepted
    When I define a "Quote" entity with granularity "Daily"
    Then the definition is rejected with error "Granularity level 'Daily' is not defined in this data model"

  # ---------------------------------------------------------------------------
  # REQ-F-LDM-005: Attribute Type Declarations
  # ---------------------------------------------------------------------------

  # Validates: REQ-F-LDM-005
  Scenario: Every attribute on an entity is typed
    Given a "Trade" entity with attributes:
      | name       | type      |
      | trade_id   | Int       |
      | notional   | Decimal   |
      | trade_date | Date      |
    Then all attributes have explicit type declarations

  # Validates: REQ-F-LDM-005
  Scenario: Attribute without a type is rejected
    When I define a "Trade" entity with an attribute "notional" that has no type
    Then the definition is rejected with error "Type declaration required for attribute 'notional' on entity 'Trade'"

  # Validates: REQ-F-LDM-005
  Scenario: Attribute types are immutable once published
    Given a published data model version "1.0" where "Trade.notional" is "Decimal"
    When I try to change "Trade.notional" to "String" in version "1.0"
    Then the change is rejected with error "Cannot modify attribute type in a published version"
    And a new version must be created to change attribute types

  # ---------------------------------------------------------------------------
  # REQ-F-LDM-006: Monoidal Aggregation
  # ---------------------------------------------------------------------------

  # Validates: REQ-F-LDM-006
  Scenario: Valid aggregation declares an associative operation and identity value
    Given a "sum" aggregation over "Decimal" values
    Then the aggregation declares an associative operation "addition"
    And the aggregation declares an identity value of "0"

  # Validates: REQ-F-LDM-006
  Scenario: Non-associative aggregation is rejected
    When I define an aggregation using "median" over "Decimal" values
    Then the definition is rejected with error "Aggregation 'median' is not associative"

  # Validates: REQ-F-LDM-006
  Scenario: Aggregation of empty input returns the identity value
    Given a "sum" aggregation with identity value "0"
    When I aggregate an empty set of records
    Then the result is "0"

  # Validates: REQ-F-LDM-006
  Scenario Outline: Built-in aggregations are available
    Given the built-in "<aggregation>" aggregation
    Then it has an associative operation and an identity element

    Examples:
      | aggregation   |
      | sum           |
      | count         |
      | min           |
      | max           |
      | concatenation |

  # ---------------------------------------------------------------------------
  # REQ-F-LDM-007: Multi-Level Aggregation
  # ---------------------------------------------------------------------------

  # Validates: REQ-F-LDM-007
  Scenario: Multi-level aggregation from fine to coarse granularity is accepted
    Given a "Trade" entity at "Atomic" granularity
    And a "DailySummary" entity at "Daily" granularity
    And a "MonthlySummary" entity at "Monthly" granularity
    When I define aggregation from "Atomic" to "Daily" using "sum"
    And I define aggregation from "Daily" to "Monthly" using "sum"
    Then both aggregation levels are accepted
    And each level satisfies the associativity laws independently

  # Validates: REQ-F-LDM-007
  Scenario: Aggregation from coarse to fine granularity is rejected
    Given a "MonthlySummary" entity at "Monthly" granularity
    And a "DailySummary" entity at "Daily" granularity
    When I define aggregation from "Monthly" to "Daily"
    Then the definition is rejected with error "Cannot aggregate from coarser 'Monthly' to finer 'Daily' granularity"

  # ---------------------------------------------------------------------------
  # REQ-F-LDM-008: Schema Versioning
  # ---------------------------------------------------------------------------

  # Validates: REQ-F-LDM-008
  Scenario: Data model changes produce a new version
    Given a data model at version "v1"
    When I add a new entity "Settlement" to the data model
    Then a new version "v2" is created
    And the original version "v1" remains unchanged and retrievable

  # Validates: REQ-F-LDM-008
  Scenario: Prior data model versions are retrievable for audit
    Given data model versions "v1", "v2", and "v3" exist
    When I request version "v1" for lineage reconstruction
    Then the complete data model as of version "v1" is returned
    And it reflects the state of the model at the time "v1" was created

  # Validates: REQ-F-LDM-008
  Scenario: Version identifiers are monotonically increasing
    Given a data model at version "v1"
    When I create versions "v2" and then "v3"
    Then "v2" is newer than "v1"
    And "v3" is newer than "v2"
    And no two versions share the same identifier
