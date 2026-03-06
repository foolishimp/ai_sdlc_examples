# /gen-escalate - Escalation Queue Management

View and act on the escalation queue — signals that have exceeded bounded ambiguity and require human judgment. The queue is derived from events (not stored), consistent with the event-sourcing invariant.

<!-- Implements: REQ-SUPV-002 (Constraint Tolerances), REQ-LIFE-005 (Intent Events), REQ-LIFE-006 (Signal Source Classification) -->
<!-- Reference: AI_SDLC_ASSET_GRAPH_MODEL.md §4.6.2, ADR-014 (IntentEngine Binding), ADR-016 (Design Tolerances), ADR-017 (Functor Execution) -->

## Usage

```
/gen-escalate [--feature "REQ-F-*"] [--severity critical|high|medium|all] [--action]
```

| Option | Description |
|--------|-------------|
| (none) | Show current escalation queue |
| `--feature` | Filter to a specific feature |
| `--severity` | Filter by minimum severity (default: all) |
| `--action` | Interactive mode — process queue items one by one |

## Instructions

### Step 1: Build Escalation Queue from Events

The queue is a **projection** of events.jsonl — derived, not stored. Read the event log and collect all unresolved escalation signals:

#### 1a: Stuck Features

Read `iteration_completed` events. A feature is stuck when:
- Same non-zero `delta` for `stuck_threshold` or more consecutive iterations on the same edge
- Default `stuck_threshold`: 3 (configurable in `intentengine_config.yml`)

```python
# Pseudocode
for feature in active_features:
    for edge in unconverged_edges(feature):
        recent = last_n_iterations(events, feature, edge, n=stuck_threshold)
        if all_same_delta(recent) and delta > 0:
            queue.append(StuckEscalation(feature, edge, delta, iteration_count))
```

#### 1b: Tolerance Breaches

Read `interoceptive_signal` and `exteroceptive_signal` events that were triaged as `escalate` in `affect_triage` events:

```python
for signal in signals_with_escalation_decision("escalate"):
    if not has_subsequent_resolution(signal):
        queue.append(ToleranceEscalation(signal))
```

#### 1c: Unactioned Intents

Read `intent_raised` events that have no subsequent `spawn_created` or `spec_modified` referencing them:

```python
for intent in intent_raised_events:
    if not has_action(intent.intent_id):
        queue.append(UnactionedIntent(intent))
```

#### 1d: Pending Human Reviews

Read feature vectors for edges where `human_required: true` and human evaluator result is `pending`:

```python
for feature in active_features:
    for edge in feature.trajectory:
        if edge.human_required and edge.evaluator_results.human == "pending":
            queue.append(PendingReview(feature, edge))
```

#### 1e: Max Iteration Breaches

Read features where any edge has exceeded `max_iterations` without convergence:

```python
for feature in active_features:
    for edge in feature.trajectory:
        if edge.iteration > max_iterations and edge.status != "converged":
            queue.append(MaxIterationBreach(feature, edge))
```

### Step 2: Display Queue

```
═══ ESCALATION QUEUE ═══

Queue: {n} items ({critical} critical, {high} high, {medium} medium)

CRITICAL:
  [C1] STUCK  REQ-F-AUTH-001 on code↔unit_tests
       δ=3 unchanged for 4 iterations
       Failing: test_coverage_minimum, all_req_keys_have_tests
       Recommended: spawn discovery vector or request human review

  [C2] TOLERANCE  event_log_size: 12,400 lines (threshold: 10,000)
       Binding: ADR-013 (Multi-Agent Coordination)
       Recommended: implement log rotation

HIGH:
  [H1] INTENT  INT-OBS-042 "Missing test coverage for REQ-NFR-SEC-003"
       Signal source: gap (from /gen-gaps)
       Raised: 2026-02-22T15:00:00Z
       Recommended: spawn feature vector or add test

  [H2] REVIEW  REQ-F-API-001 on requirements→design
       Human review pending since 2026-02-21T10:00:00Z
       Recommended: run /gen-spec-review --feature "REQ-F-API-001"

MEDIUM:
  [M1] MAX_ITER  REQ-F-DB-001 on design→code (iteration 6/5)
       Exceeded max_iterations for standard profile
       Recommended: escalate to human or relax convergence criteria

Actions:
  1. Process item     — /gen-escalate --action
  2. Dismiss item     — provide reason (logged to events)
  3. Spawn vector     — /gen-spawn from escalation
  4. Review feature   — /gen-spec-review or /gen-review
  5. Acknowledge all  — log as TELEM signals, no action

═══════════════════════════
```

### Step 3: Interactive Processing (--action)

When `--action` is provided, process queue items one by one:

For each item, present context and ask the human to choose:

```
Processing [C1] STUCK: REQ-F-AUTH-001 on code↔unit_tests
─────────────────────────────────────────────────────────

Context:
  Feature: REQ-F-AUTH-001 "User authentication"
  Edge: code↔unit_tests (TDD co-evolution)
  Iterations: 4 (stuck_threshold: 3)
  Delta: 3 (test_coverage_minimum, all_req_keys_have_tests, no_secrets_in_code)
  Last iteration: 2026-02-22T14:00:00Z

Recent iteration history:
  Iter 1: δ=5 (5 failing checks)
  Iter 2: δ=3 (2 fixed, 3 remaining)
  Iter 3: δ=3 (same 3 failing)
  Iter 4: δ=3 (same 3 failing) ← STUCK

Choose action:
  1. Spawn discovery vector — investigate root cause
  2. Force iterate — try once more with additional context
  3. Relax convergence — mark failing checks as advisory
  4. Escalate to human review — /gen-spec-review
  5. Dismiss — acknowledge and move on (log reason)
```

Record the decision in events.jsonl:

```json
{
  "event_type": "escalation_resolved",
  "timestamp": "{ISO 8601}",
  "project": "{project}",
  "data": {
    "escalation_type": "stuck|tolerance|intent|review|max_iteration",
    "feature": "REQ-F-*",
    "edge": "{source}→{target}",
    "action": "spawn|force_iterate|relax|review|dismiss",
    "reason": "{human's explanation}",
    "intent_id": "{if spawning, the new intent ID}"
  }
}
```

### Step 4: Queue Health Summary

After processing (or when displaying), show queue health:

```
Queue Health:
  Oldest unresolved: 3 days (INT-OBS-042)
  Average resolution time: 1.2 days
  Resolution pattern: 60% spawn, 25% dismiss, 15% force_iterate
  Queue trend: ↓ decreasing (5 items last week, 3 this week)
```

This is methodology self-observation — the queue's own telemetry feeds back into the IntentEngine.

### Step 5: Emit Summary Event

After any queue interaction, emit a summary event:

```json
{
  "event_type": "escalation_queue_reviewed",
  "timestamp": "{ISO 8601}",
  "project": "{project}",
  "data": {
    "queue_size": {n},
    "items_processed": {n},
    "actions_taken": {"spawn": 0, "force_iterate": 0, "relax": 0, "review": 0, "dismiss": 0},
    "oldest_unresolved_days": {n},
    "severity_distribution": {"critical": 0, "high": 0, "medium": 0}
  }
}
```

## Escalation Types Reference

| Type | Source | Detection | Default Severity |
|------|--------|-----------|-----------------|
| `stuck` | iteration_completed events | Same δ for stuck_threshold iterations | critical |
| `tolerance` | interoceptive/exteroceptive signals | Tolerance breached, triaged as escalate | varies (from affect_triage) |
| `intent` | intent_raised events | No subsequent spawn/spec_modified | high |
| `review` | Feature vector state | human evaluator pending | high |
| `max_iteration` | iteration_completed events | iteration > max_iterations | medium |

## Integration with /gen-start

The `/gen-start` state machine checks for escalations as part of state detection:
- **STUCK** state: at least one stuck feature → delegates to this command
- **ALL_BLOCKED** state: all features blocked → delegates to this command
- **IN_PROGRESS** state: if queue is non-empty, mention count in status line

The escalation queue is the human-facing view of the IntentEngine's `escalate` output type.
