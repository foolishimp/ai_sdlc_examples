Feature: Adjoint Reconciliation

  Scenario: Reverse impact analysis using Adjoints
    Given a source entity "Trades"
    And a forward morphism to "Cashflows"
    When I traverse forward with trade "T1"
    Then I should get cashflow "C1"
    And traversing backward from "C1" must contain "T1"
