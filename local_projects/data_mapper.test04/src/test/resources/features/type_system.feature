@TypeSystem
Feature: Type System
  As a data architect
  I want a strict type system that prevents implicit data coercion
  So that type safety is enforced across all data transformations

  # ---------------------------------------------------------------------------
  # REQ-F-TYP-001: Extended Type System
  # ---------------------------------------------------------------------------

  # Validates: REQ-F-TYP-001
  Scenario Outline: Primitive types are supported
    When I declare an attribute with type "<type>"
    Then the attribute type is accepted

    Examples:
      | type      |
      | String    |
      | Int       |
      | Long      |
      | Decimal   |
      | Date      |
      | Timestamp |
      | Boolean   |

  # Validates: REQ-F-TYP-001
  Scenario: Sum types (tagged unions) are supported
    When I declare an attribute with type "TradeStatus" as a union of "Active" or "Terminated" or "Pending"
    Then the attribute type is accepted as a tagged union
    And each value must be exactly one of "Active", "Terminated", or "Pending"

  # Validates: REQ-F-TYP-001
  Scenario: Product types (records) are supported
    When I declare an attribute with type "Address" as a record of "street: String", "city: String", "postcode: String"
    Then the attribute type is accepted as a record type
    And a value of type "Address" contains all three fields

  # Validates: REQ-F-TYP-001
  Scenario: Nested type composition is supported
    When I declare an attribute with type "Optional list of (String, Decimal) pairs"
    Then the attribute type is accepted as a nested composition
    And the nesting depth is not limited

  # ---------------------------------------------------------------------------
  # REQ-F-TYP-002: Refinement Types
  # ---------------------------------------------------------------------------

  # Validates: REQ-F-TYP-002
  Scenario: Refinement type constrains a base type with a predicate
    When I declare a type "PositiveDecimal" as "Decimal where value > 0"
    Then the type is accepted as a refinement of "Decimal"

  # Validates: REQ-F-TYP-002
  Scenario: Refinement type violation produces a structured error
    Given a "PositiveDecimal" type defined as "Decimal where value > 0"
    When a record contains value "-5.00" for a "PositiveDecimal" attribute
    Then a structured error is produced with constraint "value > 0" and offending value "-5.00"
    And the error is routed to the error destination

  # Validates: REQ-F-TYP-002
  Scenario: Refinement types compose with other type constructors
    When I declare a type "List of PositiveDecimal"
    Then the type is accepted
    And each element in the list must satisfy the "value > 0" predicate

  # ---------------------------------------------------------------------------
  # REQ-F-TYP-003: No Implicit Casting
  # ---------------------------------------------------------------------------

  # Validates: REQ-F-TYP-003
  Scenario: Implicit type conversion does not exist
    Given an attribute "trade_id" of type "Int"
    And an attribute "trade_ref" of type "String"
    When I try to assign "trade_id" to "trade_ref" without an explicit conversion
    Then the assignment is rejected with error "Type mismatch: 'Int' cannot be implicitly converted to 'String'"

  # Validates: REQ-F-TYP-003
  Scenario: Explicit type conversion via a named transformation is accepted
    Given an attribute "trade_id" of type "Int"
    And a named conversion "IntToString" from "Int" to "String"
    When I apply "IntToString" to convert "trade_id" to a String value
    Then the conversion is accepted
    And the conversion is traceable in the lineage

  # Validates: REQ-F-TYP-003
  Scenario: No silent value coercion occurs between types
    Given a "Decimal" value "3.14159"
    And a target attribute of type "Int"
    When I try to assign the Decimal to the Int attribute
    Then the assignment is rejected
    And no silent truncation or rounding occurs

  # ---------------------------------------------------------------------------
  # REQ-F-TYP-004: Type Unification Rules
  # ---------------------------------------------------------------------------

  # Validates: REQ-F-TYP-004
  Scenario: Exact type match allows chaining of transformations
    Given a transformation "f" that outputs type "Counterparty"
    And a transformation "g" that accepts type "Counterparty" as input
    When I chain "g after f"
    Then the chain is accepted because the output type of "f" matches the input type of "g"

  # Validates: REQ-F-TYP-004
  Scenario: Declared subtype relationship allows chaining
    Given a type "CorporateCounterparty" declared as a subtype of "Counterparty"
    And a transformation "f" that outputs type "CorporateCounterparty"
    And a transformation "g" that accepts type "Counterparty" as input
    When I chain "g after f"
    Then the chain is accepted because "CorporateCounterparty" is a declared subtype of "Counterparty"

  # Validates: REQ-F-TYP-004
  Scenario: Incompatible types reject chaining
    Given a transformation "f" that outputs type "Trade"
    And a transformation "g" that accepts type "Instrument" as input
    When I try to chain "g after f"
    Then the chain is rejected with error "Type mismatch: 'Trade' is not compatible with 'Instrument'"

  # Validates: REQ-F-TYP-004
  Scenario: Subtype relationships are declared, not inferred
    Given types "Trade" and "EquityTrade"
    And no subtype relationship has been declared between them
    When I try to use "EquityTrade" where "Trade" is expected
    Then the usage is rejected because no subtype relationship is declared
    And the system does not infer a relationship based on structural similarity

  # ---------------------------------------------------------------------------
  # REQ-F-TYP-005: Semantic Type Distinctions
  # ---------------------------------------------------------------------------

  # Validates: REQ-F-TYP-005
  Scenario: Semantic types distinguish domain concepts over the same base type
    Given a semantic type "Money" over base type "Decimal"
    And a semantic type "Percent" over base type "Decimal"
    Then "Money" and "Percent" are treated as distinct types
    And operations between them are not automatically allowed

  # Validates: REQ-F-TYP-005
  Scenario: Arithmetic between incompatible semantic types is rejected
    Given an attribute "notional" of semantic type "Money"
    And an attribute "trade_date" of semantic type "Date"
    When I try to add "notional" and "trade_date"
    Then the operation is rejected with error "Incompatible semantic types: 'Money' and 'Date'"

  # Validates: REQ-F-TYP-005
  Scenario: Conversion between semantic types requires an explicit transformation
    Given a "Money" value in USD
    And a target attribute of semantic type "Percent"
    When I try to assign the Money value to the Percent attribute
    Then the assignment is rejected
    When I apply an explicit "MoneyToPercent" conversion transformation
    Then the assignment is accepted

  # Validates: REQ-F-TYP-005
  Scenario: Semantic types participate in type unification
    Given a transformation "f" that outputs type "Money"
    And a transformation "g" that accepts type "Money" as input
    When I chain "g after f"
    Then the chain is accepted
    Given a transformation "h" that accepts type "Percent" as input
    When I try to chain "h after f"
    Then the chain is rejected because "Money" is not compatible with "Percent"
