# Tax Agent Requirements Specification

**Version:** 1.0
**Status:** Approved for TDD Implementation
**Context:** CDME Test Example - Type-Based Complexity & N:1 Aggregations
**Domain:** Tax Filing Management System

---

## 1. Executive Summary & Intent

This specification defines requirements for a **Tax Agent System** that demonstrates the CDME's capability to handle:

1. **Type-Based Polymorphism**: Different customer types (Personal, Business.Manufacture, Business.Financial) with distinct ledger schemas
2. **N:1 Aggregations**: Many atomic ledger entries → One consolidated tax form per customer per year
3. **Grain Transitions**: Atomic transaction grain → Yearly tax filing grain
4. **Adjoint Traceability**: Trace tax totals back to source ledger entries
5. **Accounting Invariants**: Prove all ledger entries are accounted for in the tax form
6. **Error Routing**: Invalid entries flow to error domain with full context

**Contrast with Airline Example:**
- **Airline**: Geographic/temporal complexity (multi-leg journeys, international currency)
- **Tax Agent**: Type-based complexity (customer types, different income/expense structures)

---

## 2. Domain Model

### 2.1. Customer Type Hierarchy

```
TaxCustomer (Abstract)
├── PersonalCustomer
│   ├── PersonalIncomeLedger
│   ├── PersonalDeductionLedger
│   └── PersonalCreditLedger
│
├── Business.ManufactureCustomer
│   ├── ManufactureRevenueLedger
│   ├── ManufactureCOGSLedger
│   └── ManufactureExpenseLedger
│
└── Business.FinancialCustomer
    ├── FinancialIncomeLedger
    ├── FinancialProvisionLedger
    └── FinancialCapitalLedger
```

### 2.2. Entity Definitions

#### 2.2.1. Customer Entities

**PersonalCustomer**
- **Grain**: Customer (customer_id)
- **Attributes**:
  - customer_id: String (PK)
  - customer_type: String = "PERSONAL"
  - first_name: String
  - last_name: String
  - ssn: String (nullable, encrypted)
  - filing_status: String (SINGLE, MARRIED_JOINT, MARRIED_SEPARATE, HEAD_OF_HOUSEHOLD)
  - dependents: Integer
  - state: String
  - country: String = "US"

**ManufactureCustomer**
- **Grain**: Customer (customer_id)
- **Attributes**:
  - customer_id: String (PK)
  - customer_type: String = "BUSINESS_MANUFACTURE"
  - business_name: String
  - ein: String (Employer Identification Number)
  - incorporation_date: Date
  - industry_code: String (NAICS)
  - state: String
  - employees: Integer

**FinancialCustomer**
- **Grain**: Customer (customer_id)
- **Attributes**:
  - customer_id: String (PK)
  - customer_type: String = "BUSINESS_FINANCIAL"
  - institution_name: String
  - ein: String
  - institution_type: String (BANK, CREDIT_UNION, INVESTMENT_FIRM)
  - regulatory_tier: String (TIER1, TIER2, TIER3)
  - state: String
  - country: String = "US"

#### 2.2.2. Ledger Entities (Atomic Grain)

**PersonalIncomeLedger**
- **Grain**: Atomic (entry_id)
- **Attributes**:
  - entry_id: String (PK)
  - customer_id: String (FK → PersonalCustomer)
  - tax_year: Integer
  - entry_date: Date
  - income_type: String (SALARY, INTEREST, DIVIDEND, CAPITAL_GAIN)
  - amount: Decimal
  - source: String (Employer/Institution name)
  - form_type: String (W2, 1099-INT, 1099-DIV, 1099-B)
  - status: String (PENDING, VALIDATED, REJECTED)

**PersonalDeductionLedger**
- **Grain**: Atomic (entry_id)
- **Attributes**:
  - entry_id: String (PK)
  - customer_id: String (FK → PersonalCustomer)
  - tax_year: Integer
  - entry_date: Date
  - deduction_type: String (STANDARD, MORTGAGE_INTEREST, CHARITY, STATE_TAX)
  - amount: Decimal
  - documentation: String (reference to supporting docs)
  - status: String (PENDING, VALIDATED, REJECTED)

**PersonalCreditLedger**
- **Grain**: Atomic (entry_id)
- **Attributes**:
  - entry_id: String (PK)
  - customer_id: String (FK → PersonalCustomer)
  - tax_year: Integer
  - entry_date: Date
  - credit_type: String (CHILD_TAX_CREDIT, EDUCATION_CREDIT, ENERGY_CREDIT)
  - amount: Decimal
  - qualifying_count: Integer (e.g., number of children)
  - status: String (PENDING, VALIDATED, REJECTED)

**ManufactureRevenueLedger**
- **Grain**: Atomic (entry_id)
- **Attributes**:
  - entry_id: String (PK)
  - customer_id: String (FK → ManufactureCustomer)
  - tax_year: Integer
  - entry_date: Date
  - revenue_type: String (PRODUCT_SALES, SERVICE_REVENUE, OTHER)
  - product_line: String
  - amount: Decimal
  - invoice_number: String
  - status: String (PENDING, VALIDATED, REJECTED)

**ManufactureCOGSLedger** (Cost of Goods Sold)
- **Grain**: Atomic (entry_id)
- **Attributes**:
  - entry_id: String (PK)
  - customer_id: String (FK → ManufactureCustomer)
  - tax_year: Integer
  - entry_date: Date
  - cogs_type: String (RAW_MATERIALS, LABOR, OVERHEAD, INVENTORY_ADJUSTMENT)
  - product_line: String
  - amount: Decimal
  - documentation: String
  - status: String (PENDING, VALIDATED, REJECTED)

**ManufactureExpenseLedger**
- **Grain**: Atomic (entry_id)
- **Attributes**:
  - entry_id: String (PK)
  - customer_id: String (FK → ManufactureCustomer)
  - tax_year: Integer
  - entry_date: Date
  - expense_type: String (DEPRECIATION, RENT, UTILITIES, MARKETING, ADMIN)
  - amount: Decimal
  - documentation: String
  - status: String (PENDING, VALIDATED, REJECTED)

**FinancialIncomeLedger**
- **Grain**: Atomic (entry_id)
- **Attributes**:
  - entry_id: String (PK)
  - customer_id: String (FK → FinancialCustomer)
  - tax_year: Integer
  - entry_date: Date
  - income_type: String (INTEREST_INCOME, TRADING_GAIN, FEE_INCOME)
  - asset_class: String (LOANS, SECURITIES, DERIVATIVES, ADVISORY)
  - amount: Decimal
  - counterparty: String
  - status: String (PENDING, VALIDATED, REJECTED)

**FinancialProvisionLedger**
- **Grain**: Atomic (entry_id)
- **Attributes**:
  - entry_id: String (PK)
  - customer_id: String (FK → FinancialCustomer)
  - tax_year: Integer
  - entry_date: Date
  - provision_type: String (LOAN_LOSS_PROVISION, TRADING_LOSS, CREDIT_VALUATION_ADJUSTMENT)
  - asset_class: String
  - amount: Decimal
  - risk_rating: String (AAA, AA, BBB, etc.)
  - status: String (PENDING, VALIDATED, REJECTED)

**FinancialCapitalLedger**
- **Grain**: Atomic (entry_id)
- **Attributes**:
  - entry_id: String (PK)
  - customer_id: String (FK → FinancialCustomer)
  - tax_year: Integer
  - entry_date: Date
  - capital_type: String (TIER1_CAPITAL, TIER2_CAPITAL, RISK_WEIGHTED_ASSETS)
  - amount: Decimal
  - regulatory_requirement: Decimal
  - status: String (PENDING, VALIDATED, REJECTED)

#### 2.2.3. Tax Form Entity (Yearly Grain)

**ConsolidatedTaxForm**
- **Grain**: Yearly (customer_id, tax_year)
- **Attributes**:
  - form_id: String (PK)
  - customer_id: String (FK → TaxCustomer)
  - customer_type: String (PERSONAL, BUSINESS_MANUFACTURE, BUSINESS_FINANCIAL)
  - tax_year: Integer
  - filing_date: Date
  - filing_status: String (DRAFT, SUBMITTED, ACCEPTED, AMENDED)

  **Common Sections (All Customer Types):**
  - total_income: Decimal
  - total_deductions: Decimal
  - taxable_income: Decimal
  - tax_before_credits: Decimal
  - total_credits: Decimal
  - total_tax: Decimal
  - refund_or_payment_due: Decimal

  **Personal-Specific:**
  - personal_salary_income: Decimal (nullable)
  - personal_interest_income: Decimal (nullable)
  - personal_dividend_income: Decimal (nullable)
  - personal_capital_gains: Decimal (nullable)
  - personal_standard_deduction: Decimal (nullable)
  - personal_itemized_deductions: Decimal (nullable)
  - personal_child_credits: Decimal (nullable)

  **Manufacture-Specific:**
  - manufacture_gross_revenue: Decimal (nullable)
  - manufacture_cogs: Decimal (nullable)
  - manufacture_gross_profit: Decimal (nullable)
  - manufacture_operating_expenses: Decimal (nullable)
  - manufacture_depreciation: Decimal (nullable)

  **Financial-Specific:**
  - financial_interest_income: Decimal (nullable)
  - financial_trading_income: Decimal (nullable)
  - financial_fee_income: Decimal (nullable)
  - financial_provisions: Decimal (nullable)
  - financial_net_income: Decimal (nullable)
  - financial_tier1_capital: Decimal (nullable)
  - financial_capital_ratio: Decimal (nullable)

---

## 3. Functional Requirements

### 3.1. Domain Model Requirements

**REQ-TAX-DOM-001: Customer Type Hierarchy**
- **Priority**: Critical
- **Description**: System must support three distinct customer types with type-specific ledger schemas
- **Acceptance Criteria**:
  1. PersonalCustomer, ManufactureCustomer, FinancialCustomer entities are defined
  2. Each customer type has a discriminator field (customer_type)
  3. Each customer type has 3 type-specific ledger entities
  4. Ledger entities have FK relationships to their customer type
- **Test Scenarios**:
  - TC-DOM-001: Register PersonalCustomer with PersonalIncomeLedger
  - TC-DOM-002: Register ManufactureCustomer with ManufactureCOGSLedger
  - TC-DOM-003: Register FinancialCustomer with FinancialProvisionLedger
  - TC-DOM-004: Validate FK constraints prevent ledger orphans

**REQ-TAX-DOM-002: Ledger Entry Validation**
- **Priority**: High
- **Description**: All ledger entries must have valid status and non-negative amounts where applicable
- **Acceptance Criteria**:
  1. Status field must be one of: PENDING, VALIDATED, REJECTED
  2. Amount fields for income/revenue must be >= 0
  3. Amount fields for deductions/expenses must be >= 0
  4. Tax year must be a valid 4-digit year (2000-2099)
  5. Entry date must be within the tax year
- **Test Scenarios**:
  - TC-DOM-005: Reject negative income amount → route to error domain
  - TC-DOM-006: Reject invalid status "UNKNOWN" → route to error domain
  - TC-DOM-007: Accept valid entry with status=VALIDATED
  - TC-DOM-008: Reject entry_date outside tax_year → route to error domain

**REQ-TAX-DOM-003: Tax Form Schema Polymorphism**
- **Priority**: Critical
- **Description**: ConsolidatedTaxForm must accommodate all customer types with type-specific sections
- **Acceptance Criteria**:
  1. Common sections (total_income, total_tax) are non-nullable
  2. Type-specific sections are nullable
  3. PersonalCustomer forms populate personal_* fields only
  4. ManufactureCustomer forms populate manufacture_* fields only
  5. FinancialCustomer forms populate financial_* fields only
  6. customer_type discriminator determines which sections are populated
- **Test Scenarios**:
  - TC-DOM-009: Personal form has personal_salary_income populated, manufacture_* null
  - TC-DOM-010: Manufacture form has manufacture_gross_revenue populated, financial_* null
  - TC-DOM-011: Financial form has financial_interest_income populated, personal_* null

---

### 3.2. Grain Transition Requirements

**REQ-TAX-GRN-001: Atomic to Yearly Aggregation**
- **Priority**: Critical
- **Description**: Transform atomic ledger entries (daily/transaction grain) to yearly tax forms
- **Acceptance Criteria**:
  1. Source grain: Atomic (entry_id)
  2. Target grain: Yearly (customer_id, tax_year)
  3. Aggregation is mandatory (grain transition without aggregation must fail)
  4. All entries for a customer-year are aggregated into a single form
- **Test Scenarios**:
  - TC-GRN-001: 100 PersonalIncomeLedger entries → 1 ConsolidatedTaxForm (year=2024)
  - TC-GRN-002: Attempt direct copy without aggregation → GrainSafetyError
  - TC-GRN-003: Multiple customers, same year → N forms (1 per customer)

**REQ-TAX-GRN-002: Multi-Source Aggregation**
- **Priority**: High
- **Description**: Aggregate multiple ledger types into a single tax form
- **Acceptance Criteria**:
  1. PersonalCustomer: Aggregate PersonalIncomeLedger + PersonalDeductionLedger + PersonalCreditLedger
  2. ManufactureCustomer: Aggregate ManufactureRevenueLedger + ManufactureCOGSLedger + ManufactureExpenseLedger
  3. FinancialCustomer: Aggregate FinancialIncomeLedger + FinancialProvisionLedger + FinancialCapitalLedger
  4. Each ledger type populates distinct tax form sections
- **Test Scenarios**:
  - TC-GRN-004: Personal: Sum income entries → total_income field
  - TC-GRN-005: Manufacture: Sum revenue - COGS - expenses → taxable_income
  - TC-GRN-006: Financial: Sum income - provisions → net_income

**REQ-TAX-GRN-003: Grain Key Alignment**
- **Priority**: Critical
- **Description**: Aggregation keys must align with target grain dimensions
- **Acceptance Criteria**:
  1. Group by: customer_id, tax_year
  2. These dimensions match ConsolidatedTaxForm grain exactly
  3. Any attempt to aggregate by misaligned keys (e.g., entry_date) must fail
- **Test Scenarios**:
  - TC-GRN-007: Aggregate by (customer_id, tax_year) → Success
  - TC-GRN-008: Aggregate by (customer_id, entry_date) → GrainSafetyError

---

### 3.3. Aggregation Requirements

**REQ-TAX-AGG-001: Monoidal Aggregation Functions**
- **Priority**: Critical
- **Description**: All aggregations must be monoidal (associative, identity element)
- **Acceptance Criteria**:
  1. SUM: Associative, identity=0
  2. COUNT: Associative, identity=0
  3. MIN/MAX: Associative, identity=+∞/-∞
  4. AVG: Not directly monoidal (use SUM + COUNT instead)
  5. System rejects non-monoidal aggregations (e.g., MEDIAN without sketch)
- **Test Scenarios**:
  - TC-AGG-001: SUM(amount) over empty set → 0
  - TC-AGG-002: COUNT(entry_id) over 1000 entries → 1000
  - TC-AGG-003: Attempt AVG without SUM+COUNT → Error or decompose to SUM/COUNT

**REQ-TAX-AGG-002: Type-Based Routing to Aggregation**
- **Priority**: Critical
- **Description**: Route ledger entries to correct tax form section based on customer_type
- **Acceptance Criteria**:
  1. PersonalIncomeLedger (SALARY) → personal_salary_income
  2. ManufactureRevenueLedger (PRODUCT_SALES) → manufacture_gross_revenue
  3. FinancialIncomeLedger (INTEREST_INCOME) → financial_interest_income
  4. Invalid customer_type → route to error domain
- **Test Scenarios**:
  - TC-AGG-004: Filter PersonalIncomeLedger where income_type=SALARY, sum → personal_salary_income
  - TC-AGG-005: Filter ManufactureCOGSLedger, sum → manufacture_cogs
  - TC-AGG-006: Ledger with customer_type=INVALID → ErrorDomain

**REQ-TAX-AGG-003: Progressive Tax Bracket Calculation**
- **Priority**: High
- **Description**: Calculate tax using progressive brackets (simplified US federal structure)
- **Acceptance Criteria**:
  1. Tax brackets (2024 SINGLE):
     - $0 - $11,000: 10%
     - $11,001 - $44,725: 12%
     - $44,726 - $95,375: 22%
     - $95,376 - $182,100: 24%
     - $182,101+: 32%
  2. Tax = Sum of (bracket_income × bracket_rate)
  3. Calculation is deterministic and reproducible
  4. Filing status affects brackets (SINGLE, MARRIED_JOINT, etc.)
- **Test Scenarios**:
  - TC-AGG-007: Taxable income $50,000 SINGLE → Tax = $6,617.50
  - TC-AGG-008: Taxable income $200,000 SINGLE → Tax = $47,836.50
  - TC-AGG-009: Same income, MARRIED_JOINT → Different tax (different brackets)

**REQ-TAX-AGG-004: Business Net Income Calculation**
- **Priority**: High
- **Description**: Calculate business net income using standard accounting formula
- **Acceptance Criteria**:
  1. Manufacture: Gross Profit = Gross Revenue - COGS
  2. Manufacture: Net Income = Gross Profit - Operating Expenses
  3. Financial: Net Income = Total Income - Provisions
  4. All intermediate values are captured in tax form
- **Test Scenarios**:
  - TC-AGG-010: Revenue=$1M, COGS=$600K, Expenses=$200K → Net Income=$200K
  - TC-AGG-011: Financial Income=$500K, Provisions=$50K → Net Income=$450K

---

### 3.4. Adjoint & Traceability Requirements

**REQ-TAX-ADJ-001: Reverse Join Metadata Capture**
- **Priority**: Critical
- **Description**: Capture all source entry_ids that contributed to each aggregated tax form field
- **Acceptance Criteria**:
  1. For each tax form row (customer_id, tax_year), capture:
     - personal_salary_income → [entry_id_1, entry_id_2, ...]
     - manufacture_cogs → [entry_id_X, entry_id_Y, ...]
     - total_income → [all contributing entry_ids]
  2. Metadata stored in ReverseJoinMetadata structure
  3. Metadata location persisted in accounting ledger
- **Test Scenarios**:
  - TC-ADJ-001: 50 PersonalIncomeLedger entries (SALARY) → tax form captures all 50 entry_ids
  - TC-ADJ-002: Query tax form for personal_salary_income → retrieve original 50 entries
  - TC-ADJ-003: Verify bidirectional traversal: entry → form → entry

**REQ-TAX-ADJ-002: Filter Tracking**
- **Priority**: High
- **Description**: Track ledger entries filtered out (status=REJECTED)
- **Acceptance Criteria**:
  1. Filter condition: status IN ('VALIDATED')
  2. Entries with status=REJECTED or PENDING captured in FilteredKeysMetadata
  3. Filtered metadata includes filter condition identifier
  4. Metadata persisted to storage (not ephemeral)
- **Test Scenarios**:
  - TC-ADJ-004: 100 entries, 10 REJECTED → 10 entry_ids in FilteredKeysMetadata
  - TC-ADJ-005: Query FilteredKeysMetadata → retrieve rejected entry_ids and reason

**REQ-TAX-ADJ-003: Error Key Capture**
- **Priority**: Critical
- **Description**: All error records must capture source_key from input
- **Acceptance Criteria**:
  1. Error records include entry_id as source_key
  2. Error records include morphism_path (e.g., "filter_validated", "validate_amount")
  3. Error records include error_type (VALIDATION_ERROR, TYPE_ERROR, etc.)
  4. Error records include expected vs actual values
- **Test Scenarios**:
  - TC-ADJ-006: Negative amount → ErrorDomain with source_key=entry_id
  - TC-ADJ-007: Query error dataset → retrieve full error context

**REQ-TAX-ADJ-004: Backward Traversal Proof**
- **Priority**: Critical
- **Description**: Any tax form field value must be traceable to source ledger entries
- **Acceptance Criteria**:
  1. Given (customer_id, tax_year, field_name) → retrieve all source entry_ids
  2. Traversal does not require recomputation (metadata is sufficient)
  3. Traversal is deterministic (same query → same results)
- **Test Scenarios**:
  - TC-ADJ-008: Query total_income for customer=C123, year=2024 → retrieve 200 entry_ids
  - TC-ADJ-009: Verify sum of retrieved entry amounts equals tax form total_income

---

### 3.5. Error Handling Requirements

**REQ-TAX-ERR-001: Validation Error Routing**
- **Priority**: Critical
- **Description**: Route invalid ledger entries to error domain without halting pipeline
- **Acceptance Criteria**:
  1. Negative amounts → ErrorDomain (error_type=AMOUNT_VALIDATION)
  2. Invalid status values → ErrorDomain (error_type=ENUM_VALIDATION)
  3. Entry date outside tax year → ErrorDomain (error_type=DATE_VALIDATION)
  4. Missing required fields → ErrorDomain (error_type=REQUIRED_FIELD)
  5. Valid entries continue to aggregation
- **Test Scenarios**:
  - TC-ERR-001: 1000 entries, 50 invalid → 50 in ErrorDomain, 950 aggregated
  - TC-ERR-002: Error record contains entry_id, error_type, expected, actual
  - TC-ERR-003: Valid entries produce correct tax form totals

**REQ-TAX-ERR-002: Error Threshold Circuit Breaker**
- **Priority**: High
- **Description**: Halt processing if error rate exceeds threshold
- **Acceptance Criteria**:
  1. Default threshold: 5% of total records
  2. Configurable absolute threshold: 10,000 errors
  3. If threshold exceeded: halt execution, emit OpenLineage FAIL event
  4. Threshold check occurs after early sampling (first 10k rows)
- **Test Scenarios**:
  - TC-ERR-004: 2% error rate (1000 errors / 50k rows) → continue processing
  - TC-ERR-005: 10% error rate (5000 errors / 50k rows) → halt processing
  - TC-ERR-006: Error threshold breach emits FAIL event with discrepancy details

**REQ-TAX-ERR-003: Error Object Structure**
- **Priority**: High
- **Description**: Error objects must contain minimum required metadata
- **Acceptance Criteria**:
  1. source_key: entry_id from source ledger
  2. morphism_path: Which transformation failed (e.g., "validate_amount")
  3. error_type: Category of failure (VALIDATION_ERROR, TYPE_ERROR, etc.)
  4. expected: Expected value or constraint (e.g., "> 0")
  5. actual: Actual value that failed (e.g., "-100")
  6. context: Map with tax_year, customer_id, ledger_type
- **Test Scenarios**:
  - TC-ERR-007: Negative amount error has expected="> 0", actual="-100"
  - TC-ERR-008: Context includes tax_year=2024, customer_id=C123

---

### 3.6. Accounting Invariant Requirements

**REQ-TAX-ACC-001: Accounting Invariant**
- **Priority**: Critical
- **Description**: Every input ledger entry must be accounted for in exactly one output partition
- **Acceptance Criteria**:
  1. Equation: |input_entries| = |aggregated_entries| + |filtered_entries| + |error_entries|
  2. Mutual exclusivity: No entry in multiple partitions
  3. Completeness: No entry unaccounted
  4. Verification runs before marking run as COMPLETE
- **Test Scenarios**:
  - TC-ACC-001: 1000 input entries = 950 aggregated + 30 filtered + 20 errors
  - TC-ACC-002: Verification passes → run status=COMPLETE
  - TC-ACC-003: Verification fails (missing entries) → run status=FAILED

**REQ-TAX-ACC-002: Accounting Ledger**
- **Priority**: Critical
- **Description**: Each run produces a ledger.json proving accounting invariant
- **Acceptance Criteria**:
  1. Ledger contains:
     - Input count and source key field (entry_id)
     - Partition breakdown (aggregated, filtered, error)
     - Adjoint metadata locations
     - Verification status (balanced/unbalanced)
     - Discrepancy details if unbalanced
  2. Ledger format: JSON, machine-readable, human-auditable
  3. Ledger written atomically at run completion
- **Test Scenarios**:
  - TC-ACC-004: ledger.json contains input_count=1000, aggregated_count=950
  - TC-ACC-005: ledger.json has verification_status="BALANCED"
  - TC-ACC-006: Unbalanced run → ledger.json has discrepancy_details (missing 5 keys)

**REQ-TAX-ACC-003: Adjoint Key Capture**
- **Priority**: Critical
- **Description**: All morphisms capture source keys in adjoint metadata
- **Acceptance Criteria**:
  1. Aggregations: All contributing entry_ids in ReverseJoinMetadata
  2. Filters: Excluded entry_ids in FilteredKeysMetadata
  3. Errors: All error records have source_key=entry_id
  4. Metadata persisted to storage (Delta tables)
  5. Metadata locations registered in accounting ledger
- **Test Scenarios**:
  - TC-ACC-007: Aggregation metadata contains 950 entry_ids
  - TC-ACC-008: Filter metadata contains 30 rejected entry_ids
  - TC-ACC-009: Error dataset contains 20 records with source_key

**REQ-TAX-ACC-004: Run Completion Gate**
- **Priority**: Critical
- **Description**: Run cannot be marked COMPLETE unless accounting invariant passes
- **Acceptance Criteria**:
  1. Verification runs automatically before setting status=COMPLETE
  2. Pass: Emit OpenLineage COMPLETE event with ledger reference
  3. Fail: Set status=FAILED, emit OpenLineage FAIL event
  4. Partial outputs may be committed (controlled by JobConfig)
  5. Accounting ledger persisted regardless of pass/fail
- **Test Scenarios**:
  - TC-ACC-010: Balanced run → status=COMPLETE, OpenLineage COMPLETE event
  - TC-ACC-011: Unbalanced run → status=FAILED, OpenLineage FAIL event
  - TC-ACC-012: ledger.json persisted even on failure

---

## 4. Key Use Cases

### 4.1. Personal Tax Filing

**Use Case**: Individual taxpayer with salary, investment income, and deductions

**Input Data**:
- PersonalCustomer: customer_id=P001, filing_status=SINGLE, dependents=0
- PersonalIncomeLedger:
  - 12 entries: income_type=SALARY (monthly paychecks)
  - 4 entries: income_type=INTEREST (quarterly interest)
  - 2 entries: income_type=DIVIDEND (semi-annual dividends)
- PersonalDeductionLedger:
  - 1 entry: deduction_type=STANDARD ($14,600 for 2024 SINGLE)
- PersonalCreditLedger:
  - 0 entries (no dependents, no qualifying credits)

**Expected Output**:
- ConsolidatedTaxForm:
  - customer_id=P001, tax_year=2024
  - total_income = SUM(all income entries) = $95,000
  - total_deductions = $14,600 (standard deduction)
  - taxable_income = $95,000 - $14,600 = $80,400
  - tax_before_credits = $13,617.50 (progressive brackets)
  - total_credits = $0
  - total_tax = $13,617.50
  - personal_salary_income = $85,000
  - personal_interest_income = $8,000
  - personal_dividend_income = $2,000
  - manufacture_* = null (not applicable)
  - financial_* = null (not applicable)

**Adjoint Metadata**:
- ReverseJoinMetadata for total_income → [18 entry_ids]
- ReverseJoinMetadata for personal_salary_income → [12 entry_ids]

---

### 4.2. Manufacturing Business Tax Filing

**Use Case**: Manufacturing company with product sales, COGS, and operating expenses

**Input Data**:
- ManufactureCustomer: customer_id=M001, industry_code=3361 (Motor Vehicle Manufacturing)
- ManufactureRevenueLedger:
  - 365 entries: revenue_type=PRODUCT_SALES (daily sales)
- ManufactureCOGSLedger:
  - 200 entries: cogs_type=RAW_MATERIALS
  - 100 entries: cogs_type=LABOR
  - 50 entries: cogs_type=OVERHEAD
- ManufactureExpenseLedger:
  - 50 entries: expense_type=DEPRECIATION
  - 30 entries: expense_type=MARKETING
  - 20 entries: expense_type=ADMIN

**Expected Output**:
- ConsolidatedTaxForm:
  - customer_id=M001, tax_year=2024
  - total_income = SUM(revenue) = $5,000,000
  - total_deductions = SUM(COGS) + SUM(expenses) = $3,500,000
  - taxable_income = $5,000,000 - $3,500,000 = $1,500,000
  - tax_before_credits = $315,000 (21% corporate rate)
  - manufacture_gross_revenue = $5,000,000
  - manufacture_cogs = $3,000,000
  - manufacture_gross_profit = $2,000,000
  - manufacture_operating_expenses = $500,000
  - personal_* = null
  - financial_* = null

**Adjoint Metadata**:
- ReverseJoinMetadata for manufacture_gross_revenue → [365 entry_ids]
- ReverseJoinMetadata for manufacture_cogs → [350 entry_ids]

---

### 4.3. Financial Institution Tax Filing

**Use Case**: Bank with interest income, trading gains, and loan loss provisions

**Input Data**:
- FinancialCustomer: customer_id=F001, institution_type=BANK, regulatory_tier=TIER1
- FinancialIncomeLedger:
  - 500 entries: income_type=INTEREST_INCOME (loan interest)
  - 200 entries: income_type=TRADING_GAIN (securities trading)
  - 100 entries: income_type=FEE_INCOME (service charges)
- FinancialProvisionLedger:
  - 50 entries: provision_type=LOAN_LOSS_PROVISION
- FinancialCapitalLedger:
  - 4 entries: capital_type=TIER1_CAPITAL (quarterly reports)

**Expected Output**:
- ConsolidatedTaxForm:
  - customer_id=F001, tax_year=2024
  - total_income = SUM(income) = $800,000
  - total_deductions = SUM(provisions) = $50,000
  - taxable_income = $800,000 - $50,000 = $750,000
  - tax_before_credits = $157,500 (21% corporate rate)
  - financial_interest_income = $500,000
  - financial_trading_income = $200,000
  - financial_fee_income = $100,000
  - financial_provisions = $50,000
  - financial_net_income = $750,000
  - financial_tier1_capital = $5,000,000 (average of quarterly reports)
  - personal_* = null
  - manufacture_* = null

**Adjoint Metadata**:
- ReverseJoinMetadata for financial_interest_income → [500 entry_ids]
- ReverseJoinMetadata for financial_provisions → [50 entry_ids]

---

## 5. Non-Functional Requirements

**REQ-TAX-NFR-001: Performance**
- Process 10M ledger entries → 100K tax forms in < 5 minutes (Spark cluster)
- Adjoint metadata capture adds < 10% overhead to runtime

**REQ-TAX-NFR-002: Data Quality**
- 100% of ledger entries accounted for (accounting invariant)
- Zero silent data loss
- All errors captured with full context

**REQ-TAX-NFR-003: Auditability**
- Full backward traversal: tax form field → source ledger entries
- Deterministic reproducibility: same inputs → same outputs
- Accounting ledger proves completeness

**REQ-TAX-NFR-004: Extensibility**
- Add new customer types without modifying core engine
- Add new ledger types without breaking existing pipelines
- Support future tax law changes via configuration

---

## 6. Test Data Scenarios

### 6.1. Happy Path - Personal Customer

**Customer**: P001, SINGLE, 0 dependents
**Ledger Entries**: 20 entries (12 salary, 4 interest, 2 dividend, 1 standard deduction, 1 child credit)
**Expected Outcome**: Tax form with $95,000 income, $13,617.50 tax

### 6.2. Happy Path - Manufacturing Customer

**Customer**: M001, Motor Vehicle Mfg
**Ledger Entries**: 815 entries (365 revenue, 350 COGS, 100 expenses)
**Expected Outcome**: Tax form with $5M revenue, $315,000 tax

### 6.3. Error Handling - Negative Amounts

**Customer**: P002
**Ledger Entries**: 100 entries, 5 with negative amounts
**Expected Outcome**: 5 entries routed to ErrorDomain, 95 aggregated, accounting invariant holds

### 6.4. Error Handling - Invalid Status

**Customer**: M002
**Ledger Entries**: 500 entries, 20 with status=PENDING
**Expected Outcome**: 20 entries filtered (FilteredKeysMetadata), 480 aggregated

### 6.5. Multi-Customer, Multi-Year

**Customers**: P001, P002, M001 (3 customers)
**Years**: 2023, 2024 (2 years)
**Ledger Entries**: 10,000 entries across customers/years
**Expected Outcome**: 6 tax forms (3 customers × 2 years), accounting invariant for each

---

## 7. Traceability Matrix

| Requirement ID | CDME Core Req | Design Component | Test Scenario |
|---------------|---------------|------------------|---------------|
| REQ-TAX-DOM-001 | REQ-LDM-01 | SchemaRegistry | TC-DOM-001 |
| REQ-TAX-DOM-002 | REQ-TYP-02 | RefinementTypes | TC-DOM-005 |
| REQ-TAX-DOM-003 | REQ-LDM-06 | PolymorphicSchema | TC-DOM-009 |
| REQ-TAX-GRN-001 | REQ-TRV-02 | GrainValidator | TC-GRN-001 |
| REQ-TAX-GRN-002 | REQ-INT-05 | MultiSourceAgg | TC-GRN-004 |
| REQ-TAX-AGG-001 | REQ-LDM-04 | MonoidAggregator | TC-AGG-001 |
| REQ-TAX-AGG-002 | REQ-INT-04 | TypeBasedRouter | TC-AGG-004 |
| REQ-TAX-AGG-003 | REQ-INT-01 | TaxBracketCalc | TC-AGG-007 |
| REQ-TAX-ADJ-001 | REQ-INT-03 | ReverseJoinMeta | TC-ADJ-001 |
| REQ-TAX-ADJ-002 | REQ-ACC-03 | FilteredKeysMeta | TC-ADJ-004 |
| REQ-TAX-ADJ-003 | REQ-ERROR-01 | ErrorDomain | TC-ADJ-006 |
| REQ-TAX-ERR-001 | REQ-TYP-03 | EitherMonad | TC-ERR-001 |
| REQ-TAX-ERR-002 | REQ-TYP-03-A | CircuitBreaker | TC-ERR-004 |
| REQ-TAX-ACC-001 | REQ-ACC-01 | AccountingLedger | TC-ACC-001 |
| REQ-TAX-ACC-002 | REQ-ACC-02 | LedgerWriter | TC-ACC-004 |
| REQ-TAX-ACC-003 | REQ-ACC-03 | AdjointWrapper | TC-ACC-007 |
| REQ-TAX-ACC-004 | REQ-ACC-04 | RunCompletionGate | TC-ACC-010 |

---

## 8. Success Criteria

The Tax Agent System implementation is considered **COMPLETE** when:

1. **Domain Model**: All 13 entities registered (3 customers, 9 ledgers, 1 tax form)
2. **Grain Transitions**: Atomic ledger → Yearly tax form with mandatory aggregation
3. **Type Routing**: PersonalIncomeLedger entries correctly route to personal_* tax form fields
4. **Aggregations**: Progressive tax bracket calculation produces correct tax amounts
5. **Adjoint Capture**: Every tax form field traceable to source ledger entries
6. **Error Handling**: Invalid entries routed to ErrorDomain without halting pipeline
7. **Accounting Invariant**: 100% of input entries accounted (aggregated + filtered + errors)
8. **Ledger Proof**: ledger.json generated with verification status and metadata locations
9. **All Test Scenarios Pass**: 50+ test scenarios from TC-DOM-001 to TC-ACC-012
10. **Performance**: Process 1M ledger entries in < 30 seconds (local Spark)

---

## 9. Implementation Notes

### 9.1. Key Differences from Airline Example

| Aspect | Airline Example | Tax Agent Example |
|--------|----------------|-------------------|
| **Complexity Type** | Geographic/Temporal | Type-Based/Polymorphic |
| **Grain Transition** | FlightSegment → DailyRevenue | Ledger Entry → YearlyTaxForm |
| **Cardinality** | N:1 (segments → daily summary) | N:1 (many entries → 1 form) |
| **Key Challenge** | Multi-leg journeys, currency | Customer type polymorphism |
| **Aggregation** | SUM revenue by route/airline | SUM income by customer/year |
| **Type Routing** | Cabin class, codeshare | Customer type, ledger type |
| **Domain Rules** | Flight scheduling, routes | Tax brackets, accounting |

### 9.2. Progressive Tax Bracket Implementation

The progressive tax bracket calculation is a **Computational Morphism** (REQ-INT-01):

```scala
def calculateTax(taxableIncome: BigDecimal, filingStatus: String): BigDecimal = {
  val brackets = getBrackets(filingStatus) // [(upperBound, rate)]

  brackets.foldLeft((BigDecimal(0), BigDecimal(0))) { case ((totalTax, prevBound), (upperBound, rate)) =>
    val bracketIncome = (taxableIncome - prevBound).max(0).min(upperBound - prevBound)
    val bracketTax = bracketIncome * rate
    (totalTax + bracketTax, upperBound)
  }._1
}
```

This is **deterministic** (same income → same tax), **pure** (no side effects), and **total** (defined for all valid inputs).

### 9.3. Multi-Source Aggregation Pattern

For PersonalCustomer, three ledger types aggregate into one form:

```yaml
mapping:
  name: personal_tax_form
  sources:
    - PersonalIncomeLedger (filter: status=VALIDATED)
    - PersonalDeductionLedger (filter: status=VALIDATED)
    - PersonalCreditLedger (filter: status=VALIDATED)
  target: ConsolidatedTaxForm
  grain: Yearly (customer_id, tax_year)
  projections:
    - total_income: SUM(amount) FROM PersonalIncomeLedger
    - total_deductions: SUM(amount) FROM PersonalDeductionLedger
    - total_credits: SUM(amount) FROM PersonalCreditLedger
    - taxable_income: total_income - total_deductions
    - tax_before_credits: calculateTax(taxable_income, filing_status)
    - total_tax: tax_before_credits - total_credits
```

---

## 10. Appendix: Tax Bracket Reference

### 10.1. 2024 Federal Tax Brackets (Simplified)

**Filing Status: SINGLE**

| Taxable Income Range | Marginal Rate | Tax Calculation |
|---------------------|---------------|-----------------|
| $0 - $11,000 | 10% | 10% of income |
| $11,001 - $44,725 | 12% | $1,100 + 12% of excess over $11,000 |
| $44,726 - $95,375 | 22% | $5,147 + 22% of excess over $44,725 |
| $95,376 - $182,100 | 24% | $16,290 + 24% of excess over $95,375 |
| $182,101+ | 32% | $37,104 + 32% of excess over $182,100 |

**Filing Status: MARRIED_JOINT**

| Taxable Income Range | Marginal Rate |
|---------------------|---------------|
| $0 - $22,000 | 10% |
| $22,001 - $89,450 | 12% |
| $89,451 - $190,750 | 22% |
| $190,751 - $364,200 | 24% |
| $364,201+ | 32% |

### 10.2. Corporate Tax Rate (Simplified)

- **Flat Rate**: 21% (Tax Cuts and Jobs Act of 2017)
- **No Brackets**: All corporate taxable income taxed at 21%

---

**Document Version**: 1.0
**Last Updated**: 2025-12-16
**Author**: Requirements Agent (AI SDLC Method)
**Status**: Ready for TDD Implementation

**Next Steps**:
1. Design Agent: Create TaxAgentSystemSpec.scala test structure
2. Code Agent: Implement entities, mappings, and test scenarios
3. System Test Agent: Validate all TC-* test scenarios pass
4. Runtime Agent: Monitor accounting invariant in production
