# Customer Portal - 7-Stage AI SDLC Example

**Version**: 1.0.0
**Team**: Customer Experience Team
**Tech Lead**: maria@acme.com
**Product Owner**: john@acme.com

## Overview

This project demonstrates the **complete 7-stage AI SDLC methodology** with full requirement traceability from intent to runtime feedback.

### What This Example Shows

- ✅ **Complete 7-stage workflow**: Requirements → Design → Tasks → Code → System Test → UAT → Runtime Feedback
- ✅ **Requirement key traceability**: Every artifact tagged with requirement keys (REQ-F-*, REQ-NFR-*, etc.)
- ✅ **Bidirectional feedback**: Production issues flow back to requirements and generate new intents
- ✅ **AI agent orchestration**: Each stage has a configured AI agent with clear responsibilities
- ✅ **Key Principles integration**: Code stage follows TDD with all 7 principles
- ✅ **BDD testing**: System Test and UAT use Given/When/Then scenarios
- ✅ **Observability**: Runtime telemetry tagged with requirement keys

---

## Architecture

```
AI SDLC Pipeline for Customer Portal
┌─────────────┐
│   Intent    │  "Users need self-service portal"
│  Manager    │
└──────┬──────┘
       │
       ▼
┌─────────────────────┐  ◄──────────────┐
│ 1. Requirements     │                  │
│    Agent            │  Feedback Loop   │
│                     │  (All stages     │
│ Output:             │   feed back)     │
│ - REQ-F-AUTH-001    │                  │
│ - REQ-F-TICKET-001  │                  │
│ - REQ-NFR-PERF-001  │                  │
└──────┬──────────────┘                  │
       │                                  │
       ▼                                  │
┌─────────────────────┐                  │
│ 2. Design           │                  │
│    Agent            │                  │
│                     │                  │
│ Output:             │                  │
│ - Component diagrams│                  │
│ - API specs         │                  │
│ - Data models       │                  │
│ - Design-to-Req     │                  │
│   traceability      │                  │
└──────┬──────────────┘                  │
       │                                  │
       ▼                                  │
┌─────────────────────┐                  │
│ 3. Tasks            │  Jira:           │
│    Orchestrator     │  PORTAL-123      │
│                     │  → REQ-F-AUTH-001│
│ Output:             │                  │
│ - Jira epics/stories│                  │
│ - Work estimates    │                  │
│ - Dependencies      │                  │
└──────┬──────────────┘                  │
       │                                  │
       ▼                                  │
┌─────────────────────┐                  │
│ 4. Code Agent       │  TDD:            │
│    (Key Principles)   │  RED→GREEN       │
│                     │  →REFACTOR       │
│ Output:             │                  │
│ - auth.py           │                  │
│   # Implements:     │                  │
│   # REQ-F-AUTH-001  │                  │
│ - test_auth.py      │                  │
│ - 85% coverage      │                  │
└──────┬──────────────┘                  │
       │                                  │
       ▼                                  │
┌─────────────────────┐                  │
│ 5. System Test      │  BDD:            │
│    Agent (BDD)      │  Given/When/Then │
│                     │                  │
│ Output:             │                  │
│ - test_auth.feature │                  │
│   # Validates:      │                  │
│   # REQ-F-AUTH-001  │                  │
│ - 95% req coverage  │                  │
└──────┬──────────────┘                  │
       │                                  │
       ▼                                  │
┌─────────────────────┐                  │
│ 6. UAT Agent        │  Business        │
│    (BDD)            │  Validation      │
│                     │                  │
│ Output:             │                  │
│ - Manual UAT cases  │                  │
│ - Automated UAT     │                  │
│ - Business sign-off │                  │
└──────┬──────────────┘                  │
       │                                  │
       ▼                                  │
┌─────────────────────┐                  │
│ 7. Runtime          │  Observability   │
│    Feedback Agent   │  (Datadog)       │
│                     │                  │
│ Output:             │                  │
│ - ERROR:            │                  │
│   REQ-F-AUTH-001    │                  │
│   Auth timeout   ───┼──────────────────┘
│ - New intent:       │
│   "Fix auth timeout"│
└─────────────────────┘
```

---

## Directory Structure

```
customer_portal/
├── README.md                          # This file
├── project.json                       # Project metadata
├── config/
│   └── config.yml                     # 7-stage AI SDLC configuration
│
├── docs/
│   ├── requirements/                  # Stage 1: Requirements artifacts
│   │   ├── user_stories/              # REQ-F-* functional requirements
│   │   ├── nfrs/                      # REQ-NFR-* non-functional requirements
│   │   ├── data_requirements/         # REQ-DATA-* data quality requirements
│   │   └── business_rules/            # REQ-BR-* business rules
│   │
│   ├── design/                        # Stage 2: Design artifacts
│   │   ├── components/                # Component diagrams
│   │   ├── data_models/               # Data models (ERDs)
│   │   ├── api/                       # OpenAPI specifications
│   │   └── adrs/                      # Architecture Decision Records
│   │
│   ├── tasks/                         # Stage 3: Work breakdown
│   │   ├── jira_export.json           # Jira tickets with requirement tags
│   │   ├── dependency_graph.mmd       # Task dependencies
│   │   └── capacity_plan.xlsx         # Sprint planning
│   │
│   ├── uat/                           # Stage 6: UAT artifacts
│   │   ├── manual_test_cases/         # Business-language test cases
│   │   └── sign_off.md                # Business sign-off document
│   │
│   └── traceability/
│       └── matrix.xlsx                # Full requirement traceability matrix
│
├── src/                               # Stage 4: Code (TDD)
│   └── customer_portal/
│       ├── auth/
│       │   └── auth_service.py        # Implements: REQ-F-AUTH-001
│       ├── tickets/
│       │   └── ticket_service.py      # Implements: REQ-F-TICKET-001
│       └── orders/
│           └── order_service.py       # Implements: REQ-F-ORDER-001
│
├── tests/                             # Stage 4 & 5: Tests
│   ├── unit/                          # Unit tests (pytest)
│   │   ├── test_auth.py               # Validates: REQ-F-AUTH-001
│   │   ├── test_tickets.py            # Validates: REQ-F-TICKET-001
│   │   └── test_orders.py             # Validates: REQ-F-ORDER-001
│   │
│   ├── integration/                   # Integration tests
│   │   └── test_api.py
│   │
│   ├── features/                      # Stage 5: BDD system tests
│   │   ├── auth.feature               # Given/When/Then for REQ-F-AUTH-001
│   │   ├── tickets.feature            # Given/When/Then for REQ-F-TICKET-001
│   │   └── orders.feature             # Given/When/Then for REQ-F-ORDER-001
│   │
│   ├── steps/                         # BDD step definitions
│   │   ├── auth_steps.py
│   │   ├── ticket_steps.py
│   │   └── order_steps.py
│   │
│   ├── uat/                           # Stage 6: Automated UAT tests
│   │   └── uat_scenarios.feature
│   │
│   └── data_quality/                  # Stage 6: Data validation
│       └── expectations/
│           └── customer_data.json     # Great Expectations tests
│
├── releases/                          # Stage 7: Release manifests
│   └── release_1.0.0_2025-11-14.yml   # With requirement key traceability
│
└── monitoring/                        # Stage 7: Runtime feedback
    ├── dashboards/
    │   ├── requirement_health.json    # Datadog dashboard
    │   └── feature_performance.json   # Grafana dashboard
    └── alerts/
        └── requirement_alerts.yml     # Alerts tagged with REQ keys
```

---

## The 7 Stages in Action

### Stage 1: Requirements (Section 4.0)

**Persona**: Product Owner (john@acme.com), Business Analyst (sarah@acme.com)

**Input**: Intent from business stakeholders
**Output**: Structured requirements with unique keys

**Example Requirement**:
```yaml
id: REQ-F-AUTH-001
type: functional
title: User Login with Email/Password
priority: high
status: approved

description: |
  As a customer, I want to log in to the portal using my email and password
  so that I can access my account information securely.

acceptance_criteria:
  - User can enter email and password
  - System validates credentials against user database
  - Successful login redirects to dashboard
  - Failed login shows error message
  - Account locks after 5 failed attempts

personas: [Customer]
traced_to_intent: INT-001 "Customer self-service portal"
```

**Traceability**: REQ-F-AUTH-001 flows through all stages

---

### Stage 2: Design (Section 5.0)

**Persona**: Solution Architect (alice@acme.com)

**Input**: REQ-F-AUTH-001
**Output**: Technical design with traceability

**Example Design**:
```yaml
component: AuthenticationService
implements_requirements:
  - REQ-F-AUTH-001
  - REQ-NFR-PERF-001 (response time < 200ms)

architecture:
  pattern: JWT-based authentication
  endpoints:
    - POST /api/auth/login
    - POST /api/auth/logout
    - POST /api/auth/refresh

  database:
    table: users
    fields: [email, password_hash, failed_attempts, locked_until]

  security:
    - Password hashing: bcrypt
    - Rate limiting: 5 requests/min
    - Account lockout: 15 minutes after 5 failures
```

**Traceability**: Design artifacts link back to REQ-F-AUTH-001

---

### Stage 3: Tasks (Section 6.0)

**Persona**: Tech Lead (maria@acme.com), Scrum Master

**Input**: Design artifacts
**Output**: Jira tickets with requirement tags

**Example Jira Ticket**:
```yaml
ticket: PORTAL-123
type: User Story
title: Implement JWT-based authentication

requirements:
  - REQ-F-AUTH-001
  - REQ-NFR-PERF-001

tasks:
  - PORTAL-124: Create AuthenticationService class
  - PORTAL-125: Implement password hashing with bcrypt
  - PORTAL-126: Add rate limiting middleware
  - PORTAL-127: Write unit tests (REQ-F-AUTH-001)
  - PORTAL-128: Write BDD tests (REQ-F-AUTH-001)

estimate: 8 story points
sprint: Sprint 5
```

**Agent Orchestration**: Tasks Stage Orchestrator assigns PORTAL-124 to Code Agent

---

### Stage 4: Code (Section 7.0) - TDD

**Persona**: Developer (Customer Experience Dev Team)

**Input**: PORTAL-124 work unit
**Output**: Production code + tests with requirement tags

**Example Code**:
```python
# src/customer_portal/auth/auth_service.py
# Implements: REQ-F-AUTH-001

class AuthenticationService:
    """
    JWT-based authentication service.

    Implements:
        - REQ-F-AUTH-001: User login with email/password
        - REQ-NFR-PERF-001: Response time < 200ms
    """

    def login(self, email: str, password: str) -> AuthToken:
        """Authenticate user and return JWT token."""
        # Implementation following TDD cycle:
        # 1. RED: test_login_with_valid_credentials (failed)
        # 2. GREEN: Minimal implementation (passed)
        # 3. REFACTOR: Improve code quality (passed)
        ...
```

**TDD Cycle**:
1. **RED**: Write `test_auth.py::test_login_with_valid_credentials` → FAILS
2. **GREEN**: Implement minimal `login()` method → PASSES
3. **REFACTOR**: Improve code quality, add error handling → PASSES
4. **COMMIT**: `git commit -m "feat: implement user login\n\nImplements: REQ-F-AUTH-001"`

---

### Stage 5: System Test (Section 8.0) - BDD

**Persona**: QA Engineer (qa-team@acme.com)

**Input**: Deployed code
**Output**: BDD scenarios validating requirements

**Example BDD Test**:
```gherkin
# tests/features/auth.feature
# Validates: REQ-F-AUTH-001

Feature: User Authentication
  As a customer
  I want to log in to the portal
  So that I can access my account

  Scenario: Successful login with valid credentials
    Given I am on the login page
    And I have a registered account with email "user@example.com"
    When I enter my email "user@example.com"
    And I enter my password "SecurePass123"
    And I click the "Login" button
    Then I should be redirected to the dashboard
    And I should see my account information

  Scenario: Failed login with invalid password
    Given I am on the login page
    When I enter email "user@example.com"
    And I enter an incorrect password
    Then I should see an error message "Invalid credentials"
    And I should remain on the login page

  Scenario: Account lockout after 5 failed attempts
    Given I am on the login page
    And I have failed login 4 times
    When I fail login a 5th time
    Then my account should be locked
    And I should see "Account locked. Try again in 15 minutes"
```

**Coverage**: REQ-F-AUTH-001 covered by 3 BDD scenarios

---

### Stage 6: UAT (Section 9.0)

**Persona**: Business SME (john@acme.com), UAT Lead

**Input**: System test passed
**Output**: Business sign-off

**Example UAT Test Case**:
```markdown
# UAT-001: User Login (Manual Test)
## Validates: REQ-F-AUTH-001

### Test Case (Business Language)
**Given** I am a customer with account user@example.com
**When** I go to the portal and log in with my credentials
**Then** I should see my dashboard with my orders and tickets

### Test Execution
- **Tester**: john@acme.com (Product Owner)
- **Date**: 2025-11-14
- **Result**: ✅ PASS
- **Sign-off**: Approved for production

### Notes
- Login was fast (< 1 second)
- Dashboard loaded correctly with all my data
- No issues found
```

---

### Stage 7: Runtime Feedback (Section 10.0)

**Persona**: SRE (sre-team@acme.com), DevOps

**Input**: Production deployment
**Output**: Telemetry and feedback loop

**Example Release Manifest**:
```yaml
# releases/release_1.0.0_2025-11-14.yml
release:
  version: 1.0.0
  date: 2025-11-14

  requirements_deployed:
    - REQ-F-AUTH-001: "User login with email/password"
      components:
        - src/customer_portal/auth/auth_service.py
        - tests/unit/test_auth.py
        - tests/features/auth.feature
      jira_tickets: [PORTAL-123, PORTAL-124, PORTAL-125]
```

**Runtime Monitoring**:
```yaml
# Datadog Alert
alert: AUTH_TIMEOUT_SPIKE
timestamp: 2025-11-15T10:23:00Z
requirement: REQ-F-AUTH-001
metric: auth.login.duration
threshold: 200ms
actual: 1500ms
severity: high

action: Generate new intent
new_intent: INT-042 "Fix authentication timeout issue"
assigned_to: Requirements Agent
```

**Feedback Loop Closes**: Alert creates new intent → Requirements stage → Full cycle restarts

---

## How to Use This Example

### 1. Load the Configuration

```bash
# From your AI development environment (Claude Code, etc.)
cd /path/to/ai_sdlc_method/examples/local_projects/customer_portal

# AI assistant loads config.yml
# - Reads ai_sdlc.methodology_plugin
# - Loads 7-stage configurations
# - Understands requirement traceability
```

### 2. Start from Intent

```yaml
# Example intent
intent:
  id: INT-001
  description: "Users need a self-service portal to manage accounts"
  stakeholder: "Customer Experience Team"
  priority: high
```

### 3. Requirements Stage

```bash
# AI Requirements Agent processes intent
# Generates: REQ-F-AUTH-001, REQ-F-TICKET-001, REQ-F-ORDER-001
# Output: docs/requirements/user_stories/
```

### 4. Design Stage

```bash
# AI Design Agent reads requirements
# Generates: Component diagrams, API specs, data models
# Output: docs/design/
# Traceability: All design artifacts link to REQ-* keys
```

### 5. Tasks Stage

```bash
# AI Tasks Orchestrator creates work breakdown
# Generates Jira tickets: PORTAL-123 → REQ-F-AUTH-001
# Assigns work units to Code Agents
```

### 6. Code Stage (TDD)

```bash
# AI Code Agent executes TDD cycle
# RED: Write failing test
pytest tests/unit/test_auth.py  # FAILS

# GREEN: Implement minimal code
pytest tests/unit/test_auth.py  # PASSES

# REFACTOR: Improve quality
pytest tests/unit/test_auth.py  # STILL PASSES

# COMMIT
git commit -m "feat: implement user login\n\nImplements: REQ-F-AUTH-001"
```

### 7. System Test Stage (BDD)

```bash
# AI QA Agent generates BDD scenarios
# Output: tests/features/auth.feature

# Run BDD tests
pytest tests/features/

# Coverage analysis: REQ-F-AUTH-001 covered ✅
```

### 8. UAT Stage

```bash
# Business SME executes manual UAT
# Automated UAT tests also run
# Sign-off obtained: docs/uat/sign_off.md
```

### 9. Runtime Feedback

```bash
# Deploy to production with release manifest
# Runtime monitoring captures metrics tagged with REQ keys
# Alerts flow back to Intent Manager
# New intents created from production issues
```

---

## Requirement Traceability Example

```
Flow for REQ-F-AUTH-001 (User Login):

Intent: INT-001 "Customer self-service portal"
  ↓
Requirements: REQ-F-AUTH-001 "User login with email/password"
  ↓
Design: AuthenticationService → REQ-F-AUTH-001
        POST /api/auth/login → REQ-F-AUTH-001
  ↓
Tasks: PORTAL-123 → REQ-F-AUTH-001
       PORTAL-124 → REQ-F-AUTH-001 (Subtask)
  ↓
Code: src/customer_portal/auth/auth_service.py
      # Implements: REQ-F-AUTH-001
  ↓
Tests: tests/unit/test_auth.py
       # Validates: REQ-F-AUTH-001
       tests/features/auth.feature
       # Validates: REQ-F-AUTH-001
  ↓
UAT: UAT-001 → REQ-F-AUTH-001
     Business sign-off: ✅ Approved
  ↓
Runtime: release_1.0.0_2025-11-14.yml
         REQ-F-AUTH-001 deployed to production
         Datadog metrics: auth.login.* tagged with REQ-F-AUTH-001
  ↓
Feedback: ERROR: REQ-F-AUTH-001 - Auth timeout
          New Intent: INT-042 "Fix auth timeout"
  ↓
Requirements: REQ-F-AUTH-002 "Optimize authentication performance"
  [Cycle repeats...]
```

---

## Key Benefits

✅ **Complete Traceability**: Every line of code traces back to a business requirement
✅ **Feedback Loops**: Production issues automatically create new requirements
✅ **AI Orchestration**: Each stage has a specialized AI agent
✅ **Quality Gates**: Every stage has clear pass/fail criteria
✅ **Key Principles**: Code stage follows TDD with all 7 principles
✅ **BDD Testing**: Business-readable test scenarios
✅ **Runtime Observability**: Production metrics linked to requirements

---

## References

- **AI SDLC Methodology Plugin**: `../../../claude-code/claude-code/plugins/aisdlc-methodology/`
- **AI SDLC Method**: `../../../docs/ai_sdlc_method.md`
- **Key Principles Principles**: `../../../claude-code/claude-code/plugins/aisdlc-methodology/docs/principles/KEY_PRINCIPLES.md`
- **TDD Workflow**: `../../../claude-code/claude-code/plugins/aisdlc-methodology/docs/processes/TDD_WORKFLOW.md`

---

## Questions?

**Q: Why 7 stages instead of just "write code"?**
A: Traceability and feedback. When a production issue occurs, you can trace it back to the original requirement and understand the full impact.

**Q: Do I need to use all 7 stages?**
A: Yes for complete traceability. But you can simplify for low-risk projects.

**Q: How does the feedback loop work?**
A: Runtime alerts tagged with requirement keys automatically create new intents, which flow into the Requirements stage, restarting the cycle.

**Q: Is this overkill for small projects?**
A: This example shows the complete methodology. Adapt it to your needs. Small projects can use fewer quality gates but should maintain traceability.

---

🔥 **Excellence or nothing** 🔥
