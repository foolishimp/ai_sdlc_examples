# Genesis Monitor — UAT Use Cases

**Version**: 1.0.0
**Date**: 2026-03-04
**Derived From**: REQUIREMENTS.md v3.1.0
**Method**: BDD (Given/When/Then), business language only
**Executable tests**: `imp_python_fastapi/tests/test_uat_todo_lifecycle.py`

---

## Purpose

This document specifies functional use cases for Genesis Monitor as an observer of
the AI SDLC methodology lifecycle. Every scenario is:

1. **Written in business language** — Given/When/Then, no platform-specific syntax
2. **Traced to REQ keys** — every scenario carries `Validates: REQ-F-*` tags
3. **Event-verifiable** — outcomes verifiable via `events.jsonl` and HTTP responses
4. **Fixture-based** — scenarios reference one of the lifecycle fixtures below

---

## Scenario: Full Lifecycle — Todo Dashboard

The canonical end-to-end scenario. A developer builds a personal todo dashboard
from scratch using the AI SDLC methodology. Genesis Monitor observes every step.

This scenario is the primary e2e use case — the other UCs below cover individual
observable slices of this same lifecycle.

### Lifecycle Fixture

All UC-GM-* use cases reference a shared event timeline representing the todo
dashboard project lifecycle. Events are timestamped to allow temporal scrubbing
tests.

```
T+00:00  project_initialized       (todo-dashboard created)
T+00:05  spawn_created             (REQ-F-TODO-001 "Todo Item CRUD")
T+00:10  edge_started              (intent→requirements)
T+00:30  iteration_completed       (iter 1, δ=1 — human review pending)
T+00:45  review_completed          (decision: refined — add priority field)
T+01:00  iteration_completed       (iter 2, δ=0 — requirements converged)
T+01:00  edge_converged            (intent→requirements)
T+01:05  edge_started              (requirements→design)
T+01:30  iteration_completed       (iter 1, δ=1 — human review pending)
T+01:45  review_completed          (decision: approved)
T+01:50  iteration_completed       (iter 2, δ=0 — design converged)
T+01:50  edge_converged            (requirements→design)
T+01:55  edge_started              (code↔unit_tests)
T+02:15  iteration_completed       (iter 1, δ=3 — tests 62%, lint, missing REQ tag)
T+02:30  review_completed          (decision: refined — fix coverage and lint)
T+02:45  iteration_completed       (iter 2, δ=0 — all checks pass)
T+02:45  edge_converged            (code↔unit_tests)
```

---

## UC-GM-01: Empty Project Not Listed

`Validates: REQ-F-DISC-001, REQ-F-DISC-002`

```gherkin
Given a directory "todo-dashboard" with no .ai-workspace/ subdirectory
And Genesis Monitor is watching the parent directory

When I open the index page

Then "todo-dashboard" does not appear in the project tree
```

---

## UC-GM-02: Project Appears After Initialization

`Validates: REQ-F-DISC-001, REQ-F-DISC-002, REQ-F-DASH-001, REQ-F-DASH-006`

```gherkin
Given a "todo-dashboard" project that has been initialized
  (project_initialized event emitted, .ai-workspace/ created)
And no feature vectors have been spawned yet

When I open the index page

Then "todo-dashboard" appears in the project tree
And it shows a "no status" badge (grey)
And clicking the project navigates to the project detail page
And the detail page shows the project path
And no edges are shown in the Edge Status card (none started)
```

---

## UC-GM-03: Feature Vector Appears After Spawn

`Validates: REQ-F-PARSE-002, REQ-F-DASH-002`

```gherkin
Given the lifecycle fixture up to T+00:05 (spawn_created for REQ-F-TODO-001)

When I view the project detail page

Then the Feature Vectors card shows REQ-F-TODO-001 "Todo Item CRUD"
And its status is "not started" (no edges have begun)
And the Asset Graph shows the intent node
```

---

## UC-GM-04: In-Progress Edge with Pending Human Review

`Validates: REQ-F-NAV-001, REQ-F-NAV-003, REQ-F-DASH-002, REQ-F-DASH-003`

```gherkin
Given the lifecycle fixture at T+00:35
  (edge_started and iteration_completed with δ=1 for intent→requirements)

When I view the project detail page

Then the Edge Status card shows "intent→requirements" as "in_progress"
And the iteration count is 1
And the Convergence card shows 1 failing check (human review pending)
And the Recent Events feed shows "iteration_completed" with delta=1
And the Asset Graph shows the requirements node in amber
```

---

## UC-GM-05: Human Review Recorded

`Validates: REQ-F-NAV-003, REQ-F-PROTO-001`

```gherkin
Given the lifecycle fixture at T+00:50
  (review_completed with decision "refined" has been written)

When I view the project detail page

Then the Recent Events feed shows "review_completed" with decision "refined"
And the Protocol Compliance card shows the review event linked to iteration 1
And the Convergence card still shows the edge as in_progress
  (review recorded but delta not yet zero — second iteration pending)
```

---

## UC-GM-06: Requirements Edge Converges

`Validates: REQ-F-NAV-003, REQ-F-DASH-002, REQ-F-DASH-003`

```gherkin
Given the lifecycle fixture at T+01:02
  (edge_converged written for intent→requirements after δ=0 in iteration 2)

When I view the project detail page

Then the Edge Status card shows "intent→requirements" as "converged"
And the iteration count is 2
And the Asset Graph shows the requirements node in green
And the Timeline card shows a completed Gantt bar for this edge
```

---

## UC-GM-07: Deterministic Engine Failure Visible

`Validates: REQ-F-NAV-003, REQ-F-DASH-003, REQ-F-PROTO-001`

This is the core observability scenario for the deterministic engine.

```gherkin
Given the lifecycle fixture at T+02:20
  (iteration_completed with δ=3 on code↔unit_tests:
   test_coverage=62% (< 80%), lint=3 violations, req_keys_tagged=FAIL)

When I view the project detail page

Then the Edge Status card shows "code↔unit_tests" as "in_progress" with δ=3
And the Convergence card shows 3 failing checks:
  - test_coverage: 62% (threshold: 80%)
  - lint: 3 violations
  - req_keys_tagged: FAIL
And the passing checks are shown in green (tests_pass, type_hints)
And the Protocol Compliance card flags this iteration
  as needing human resolution before the next run
And the Recent Events feed shows "iteration_completed" with delta=3
```

---

## UC-GM-08: Engine Converges After Human Fix

`Validates: REQ-F-NAV-003, REQ-F-DASH-002, REQ-F-DASH-003`

```gherkin
Given the lifecycle fixture at T+02:47
  (iteration_completed with δ=0 followed by edge_converged for code↔unit_tests)

When I view the project detail page

Then the Edge Status card shows "code↔unit_tests" as "converged"
And the Convergence card shows all 5 checks passing (green)
And the Asset Graph shows the code and unit_tests nodes in green
And the Test Traceability card shows full REQ key coverage
And the Timeline shows a completed Gantt bar for code↔unit_tests
```

---

## UC-GM-09: Full Lifecycle — All Edges Green

`Validates: REQ-F-DASH-001, REQ-F-DASH-002, REQ-F-DASH-006`

```gherkin
Given the complete lifecycle fixture (all edges converged)

When I view the project detail page

Then the Asset Graph shows all nodes in green
And the Edge Status card shows all 3 edges as "converged"
And the Convergence card shows 100% pass rate
And the Feature Vectors card shows REQ-F-TODO-001 as "converged"
And the index page project tree shows a green convergence badge
```

---

## UC-GM-10: Temporal Scrubber — Historical State Reconstruction

`Validates: REQ-F-NAV-001, REQ-F-NAV-003, REQ-F-NAV-007`

The scrubber is the core time-travel feature. This UC verifies that the monitor
correctly reconstructs state at any past timestamp.

```gherkin
Given the complete lifecycle fixture

When I query the project detail page with ?t= set to T+00:35
  (after the first failing iteration on intent→requirements)

Then the Edge Status shows "intent→requirements" as "in_progress" with δ=1
And the Convergence card shows 1 failing check
And no other edges are shown (they hadn't started yet at T+00:35)
And the Asset Graph shows only intent and requirements nodes active

When I query with ?t= set to T+01:02
  (just after requirements converged, before design started)

Then "intent→requirements" shows as "converged"
And "requirements→design" shows as "not started"
And the code edge is not visible

When I query with no ?t= parameter (live state)

Then all 3 edges show as "converged"
```

---

## UC-GM-11: Event Density Heatmap Reflects Activity Distribution

`Validates: REQ-F-NAV-007`

```gherkin
Given the complete lifecycle fixture with events spread across ~3 hours

When the project detail page loads

Then the heatmap density array has 100 buckets
And buckets containing iteration events have higher density than empty buckets
And the highest-density bucket corresponds to the time window with the most events
And the density values are normalised to [0.0, 1.0]
```

---

## UC-GM-12: Design Tenant Isolation

`Validates: REQ-F-MTEN-001, REQ-F-MTEN-002, REQ-F-MTEN-003`

```gherkin
Given a project with events from two design implementations:
  - "imp_claude" emitted the requirements and design events
  - "imp_gemini" emitted separate requirements events for the same feature

When I view the project detail page without ?design=

Then all events are shown (merged view)
And the design tenant selector shows two tabs:
  "All tenants (N)", "imp_claude (M)", "imp_gemini (K)"

When I click the "imp_claude" tab

Then only imp_claude events are used for all projections
And the URL becomes /project/{id}?design=imp_claude
And the Edge Status reflects only imp_claude's iteration history

When I move the temporal scrubber while on the imp_claude tab

Then the URL becomes ?design=imp_claude&t={timestamp}
And all fragment reloads include both parameters
```

---

## UC-GM-13: Read-Only Contract

`Validates: REQ-NFR-001`

```gherkin
Given any lifecycle fixture

When Genesis Monitor serves any page or fragment route
And when the filesystem watcher detects any workspace change

Then no file in the .ai-workspace/ directory is written, modified, or deleted
And no file outside the server's template directory is written
```

---

## UC-GM-14: Live SSE Update — Edge Converges While Watching

`Validates: REQ-F-STREAM-001, REQ-F-STREAM-002, REQ-F-DASH-006`

```gherkin
Given a project where "code↔unit_tests" is in_progress (δ=3)
And I have the project detail page open in a browser with an active SSE connection

When the deterministic engine writes a new iteration_completed event with δ=0
And then writes an edge_converged event

Then within 1 second the Edge Status card updates to show "converged"
And the Asset Graph node turns green without a full page reload
And the Recent Events feed shows the new events at the top
And I did not press refresh
```

---

## Coverage Matrix

| UC | REQ Keys Validated | Fixture Timestamp |
|----|-------------------|-------------------|
| UC-GM-01 | REQ-F-DISC-001, REQ-F-DISC-002 | Before init |
| UC-GM-02 | REQ-F-DISC-001/002, REQ-F-DASH-001/006 | T+00:00 |
| UC-GM-03 | REQ-F-PARSE-002, REQ-F-DASH-002 | T+00:05 |
| UC-GM-04 | REQ-F-NAV-001/003, REQ-F-DASH-002/003 | T+00:35 |
| UC-GM-05 | REQ-F-NAV-003, REQ-F-PROTO-001 | T+00:50 |
| UC-GM-06 | REQ-F-NAV-003, REQ-F-DASH-002/003 | T+01:02 |
| UC-GM-07 | REQ-F-NAV-003, REQ-F-DASH-003, REQ-F-PROTO-001 | T+02:20 |
| UC-GM-08 | REQ-F-NAV-003, REQ-F-DASH-002/003 | T+02:47 |
| UC-GM-09 | REQ-F-DASH-001/002/006 | T+02:47 (final) |
| UC-GM-10 | REQ-F-NAV-001/003/007 | Multiple timestamps |
| UC-GM-11 | REQ-F-NAV-007 | Final state |
| UC-GM-12 | REQ-F-MTEN-001/002/003 | Multi-tenant fixture |
| UC-GM-13 | REQ-NFR-001 | Any fixture |
| UC-GM-14 | REQ-F-STREAM-001/002, REQ-F-DASH-006 | Live (SSE) |
