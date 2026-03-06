# /gen-iterate - Invoke the Universal Iteration Function

Run one iteration of `iterate(Asset, Context[], Evaluators)` on a specific graph edge.

<!-- Implements: REQ-ITER-001, REQ-ITER-002 -->

## Usage

```
/gen-iterate --edge "{source}→{target}" --feature "REQ-F-{DOMAIN}-{SEQ}" [--mode {interactive|engine|auto}] [--profile {name}]
```

| Option | Description |
|--------|-------------|
| `--edge` | The graph transition to traverse (e.g., "design→code", "code↔unit_tests") |
| `--feature` | The feature vector (REQ-F-*) being worked on |
| `--mode` | Execution mode: `interactive` (LLM agent, default), `engine` (F_D deterministic CLI), `auto` (engine for deterministic edges, interactive otherwise) |
| `--profile` | Projection profile to use (full, standard, poc, spike, hotfix, minimal). Overrides feature vector's profile if set. |

**Deterministic edges** (engine mode applies): `code↔unit_tests`, `design→test_cases`, `design→uat_tests`

## Instructions

This command is the primary workflow action. It invokes the iterate agent on a specific edge of the asset graph.

### Step 0: Select Execution Mode

Determine the execution mode from `--mode` (default: `interactive`):

**`engine` mode** — delegate entirely to the F_D engine CLI:
1. Locate the primary asset for the feature+edge (from the feature vector trajectory, or ask the user)
2. Run via Bash:
   ```bash
   PYTHONPATH={plugin_root}/code python -m genesis run-edge \
     --edge "{edge}" \
     --feature "{feature}" \
     --asset "{asset_path}" \
     --deterministic-only \
     --max-iterations 10 \
     --fd-timeout 120
   ```
3. Parse the JSON output — it contains `delta`, `converged`, `total_iterations`, `iterations[].checks`
4. Format as the **Iteration Report** (Step 5) and emit no additional events (engine handles all emission)
5. If `converged: true` → update feature vector trajectory, proceed to Step 4 (convergence handling)
6. If `spawn_requested` → present spawn to user as in Step 6

**`auto` mode** — route by edge type:
- Edge is `code↔unit_tests`, `design→test_cases`, or `design→uat_tests` → use `engine` mode
- All other edges → use `interactive` mode

**`interactive` mode** — proceed to Step 1 (LLM agent path, current default behaviour)

### Step 1: Validate Edge

1. Read `.ai-workspace/graph/graph_topology.yml`
2. Verify the requested edge exists in the `transitions` section
3. If not found, list available transitions and ask the user to choose

### Step 2: Load Context and Build Effective Checklist

1. Load the edge parameterisation from `.ai-workspace/graph/edges/{edge_config}`
2. Load project constraints — resolve tenant context (try `.ai-workspace/{impl}/context/project_constraints.yml` first, fall back to `.ai-workspace/context/project_constraints.yml`)
3. Load the feature vector from `.ai-workspace/features/active/{feature}.yml`
   - If the file does not exist, create it from `.ai-workspace/features/feature_vector_template.yml`, populate the feature ID, title, and intent fields, and save to `.ai-workspace/features/active/{feature}.yml`
4. **Load projection profile** (if `--profile` is set or feature vector has `profile` field):
   - Load from `.ai-workspace/profiles/{profile}.yml` or fall back to `v2/config/profiles/{profile}.yml`
   - **Validate edge**: if edge is in `graph.skip`, stop and report "Edge {edge} is skipped in profile {profile}"
   - **Apply evaluator overrides**: profile evaluators compose on top of edge defaults
   - **Apply convergence rules**: profile strictness, human_required_on_all_edges
   - **Apply context density**: profile determines which context elements are required/optional
5. **Load encoding table** (functor encoding from profile + feature overrides):
   - Load `encoding` section from the profile (strategy, mode, valence, functional_units)
   - Apply feature vector `functor.overrides` on top (feature-specific unit→category mappings)
   - The encoding table maps each functional unit (evaluate, construct, classify, route, propose, sense, emit, decide) to a category (F_D, F_P, F_H)
   - Record the active encoding in the feature vector's `functor` section
6. Build the **effective checklist** (composition algorithm from the iterate agent):
   - Start with edge checklist (from edge config)
   - Resolve `$variable` references from project_constraints.yml
   - Apply profile evaluator overrides (if profile loaded)
   - Apply `threshold_overrides` from feature.constraints
   - Append `acceptance_criteria` from feature.constraints
   - Append `additional_checks` from feature.constraints
7. Load remaining Context[] from the resolved tenant context directory (ADRs, models, policy)
   - Respect profile context density: `minimal` loads only project_constraints, `full` loads everything
8. Record the current context hash for spec reproducibility
9. **Check time-box** (if feature vector has `time_box.enabled: true`):
   - Calculate remaining time from `time_box.started` + `time_box.duration`
   - If expired, trigger fold-back convergence (see Step 4)

### Step 3: Invoke Iterate Agent

Pass to the `gen-iterate` agent:
- The current asset (from the feature vector's trajectory)
- The loaded Context[]
- The edge parameterisation (evaluators, convergence criteria, guidance)

The agent performs three directions of gap detection:
- **Backward** (source analysis): Identifies ambiguities, gaps, and underspecification in the source asset
- **Forward** (output evaluation): Runs the effective checklist against the generated output
- **Inward** (process evaluation): Identifies gaps in the evaluators, context, and guidance themselves

### Step 4: Process Results

1. Update the feature vector tracking file:
   - Increment iteration count
   - Record evaluator results
   - Record context hash
   - Update status (iterating | converged | blocked | time_box_expired)

2. If human evaluator required:
   - Present the candidate for review
   - Record approval/rejection/feedback

3. **Extended convergence check** (for discovery, spike, PoC, hotfix vectors):
   - If `vector_type` is not `feature`:
     - Ask human: "Has the question been answered / risk been assessed?"
     - If yes: converge with `convergence_type: question_answered`
   - If `time_box.enabled` and time expired:
     - Converge with `convergence_type: time_box_expired`
     - Package partial results as fold-back payload

4. If converged (any convergence type):
   - Report convergence and convergence_type
   - Update feature vector status
   - If child vector: trigger fold-back to parent (see `/gen-spawn` fold-back process)
   - **Update task tracking** (see Step 4a)
   - Show next available transitions

4a. **Emit Events** (MANDATORY — every iteration must emit events):

   **On first iteration for this feature+edge**: emit an `edge_started` event:
   ```json
   {"event_type": "edge_started", "timestamp": "{ISO 8601}", "project": "{project name}", "feature": "{REQ-F-*}", "edge": "{source}→{target}", "data": {"iteration": 1}}
   ```

   **On every iteration**: emit an `iteration_completed` event:
   ```json
   {
     "event_type": "iteration_completed",
     "timestamp": "{ISO 8601}",
     "project": "{project name}",
     "feature": "{REQ-F-*}",
     "edge": "{source}→{target}",
     "iteration": {n},
     "status": "{iterating|converged|blocked|time_box_expired|spawn_requested}",
     "convergence_type": "{standard|question_answered|time_box_expired|}",
     "evaluators": {
       "passed": {n},
       "failed": {n},
       "skipped": {n},
       "total": {n},
       "details": [{"name": "{check}", "type": "{agent|deterministic|human}", "result": "{pass|fail|skip}", "required": true}]
     },
     "asset": "{path to output artifact}",
     "context_hash": "{sha256:...}",
     "profile": "{profile name}",
     "vector_type": "{feature|discovery|spike|poc|hotfix}",
     "encoding": {
       "strategy": "{profile encoding strategy}",
       "mode": "{headless|interactive|autopilot}",
       "valence": "{high|medium|low}",
       "active_units": {"evaluate": "F_D", "construct": "F_P", "...": "..."}
     },
     "delta": {count of failing required checks},
     "next_edge": "{next available transition, if converged}"
   }
   ```

   **On evaluator failure** (REQ-SUPV-003): for each evaluator check that fails or is skipped, emit an `evaluator_detail` event:
   ```json
   {"event_type": "evaluator_detail", "timestamp": "{ISO 8601}", "project": "{project name}", "data": {"feature": "{REQ-F-*}", "edge": "{source}→{target}", "iteration": {n}, "check_name": "{check}", "check_type": "F_D|F_P|F_H", "result": "fail|skip", "expected": "{what the check requires}", "observed": "{what was found}", "consecutive_failures": {count of consecutive iterations this check has failed on this edge}, "evaluator_config": "edge_params/{file}.yml"}}
   ```
   These events enable the LLM evaluator to detect patterns: "check X has failed 4 consecutive iterations" signals the requirement or evaluator needs revision, not just another iteration. Only emitted for non-passing checks — passing checks are covered by the `iteration_completed` summary.

   **On encoding escalation**: if a functional unit's encoding changes during iteration (e.g., deterministic check fails and escalates to human), emit an `encoding_escalated` event:
   ```json
   {"event_type": "encoding_escalated", "timestamp": "{ISO 8601}", "project": "{project name}", "data": {"feature": "{REQ-F-*}", "edge": "{source}→{target}", "iteration": {n}, "functional_unit": "{unit}", "from_category": "{F_D|F_P|F_H}", "to_category": "{F_D|F_P|F_H}", "trigger": "{reason}"}}
   ```
   Also record the escalation in the feature vector trajectory's `escalations` array.

   **On convergence**: also emit an `edge_converged` event:
   ```json
   {"event_type": "edge_converged", "timestamp": "{ISO 8601}", "project": "{project name}", "feature": "{REQ-F-*}", "edge": "{source}→{target}", "data": {"iteration": {n}, "evaluators": "{pass}/{total}", "convergence_type": "standard|question_answered|time_box_expired"}}
   ```

   - The event log is **append-only** and **immutable**. Events are never modified or deleted.
   - Create `.ai-workspace/events/` directory on first event if it doesn't exist.
   - The `project` field comes from the tenant context `project_constraints.yml` → `project.name`.
   - See the iterate agent's **Event Type Reference** for the full event schema catalogue.

4b. **Update Derived Views** (projections of the event stream):

   After emitting the event, update all derived views:

   1. **Feature vector** (`.ai-workspace/features/active/{feature}.yml`):
      - Update trajectory status, iteration count, evaluator results
      - Record `started_at` on first iteration, `converged_at` on convergence
      - This is a **state projection** — the latest state derived from events

   2. **Task log** (`.ai-workspace/tasks/active/ACTIVE_TASKS.md`):
      - On convergence only, append a phase completion entry:
        ```markdown
        ### {feature}: {source}→{target} CONVERGED
        **Date**: {timestamp}
        **Iterations**: {n}
        **Evaluators**: {pass_count}/{total} checks passed
        **Asset**: {path to output artifact}
        **Next edge**: {next available transition from graph_topology}
        ```
      - This is a **filtered projection** — convergence events rendered as markdown

   3. **STATUS.md** (`.ai-workspace/STATUS.md`):
      - Regenerate following the `/gen-status --gantt` spec
      - Includes Gantt chart, phase summary, process telemetry, and self-reflection
      - This is a **computed projection** — analytics derived from the full event stream
      - The self-reflection section closes the telemetry loop: `Telemetry / Observer → feedback → new Intent`

   All three views can be reconstructed from `events.jsonl` alone. The event log is the source of truth.

5. If not converged:
   - Report delta (what's still needed)
   - **Check for stuck delta**: if the same check has failed for > 3 consecutive iterations,
     emit an `intent_raised` event with `signal_source: "test_failure"` — the delta is
     stuck and likely requires action beyond this edge's scope
   - **Check for refactoring signals**: if the iterate agent's process gap analysis
     identifies structural issues beyond the current feature scope, emit an
     `intent_raised` event with `signal_source: "refactoring"`
   - **Check for source escalation**: if backward gap detection found `escalate_upstream`
     dispositions, emit an `intent_raised` event with `signal_source: "source_finding"`
   - Present any generated intents to the human for decision
   - If `--auto`: re-invoke iterate (unless stuck delta detected — pause for human)
   - If not auto: wait for user to re-invoke

6. **Spawn detection**:
   - If the iterate agent reports a spawn opportunity:
     - Present the recommended child vector type and reason to the user
     - If approved: suggest `/gen-spawn --type {type} --parent {feature} --reason "{reason}"`
     - If declined: continue iteration

### Step 5: Show Iteration Report

```
═══ ITERATION REPORT ═══
Edge:       {source} → {target}
Feature:    {REQ-F-*}
Iteration:  {n}

CHECKLIST RESULTS: {pass_count} of {total_required} required checks pass
┌──────────────────────────┬───────────────┬────────┬──────────┐
│ Check                    │ Type          │ Result │ Required │
├──────────────────────────┼───────────────┼────────┼──────────┤
│ {check_name}             │ {type}        │ {result}│ {yes/no}│
└──────────────────────────┴───────────────┴────────┴──────────┘

FAILURES: {specific failure details with remediation}
SKIPPED:  {unresolved $variables or optional checks}
DELTA:    {count of failing required checks}
STATUS:   {CONVERGED | CONVERGED_QUESTION_ANSWERED | TIME_BOX_EXPIRED | ITERATING | BLOCKED | SPAWN_REQUESTED}
VECTOR:   {feature | discovery | spike | poc | hotfix}
PROFILE:  {profile name, if configured}
ENCODING: {strategy}/{mode}/{valence} — {override_count} overrides, {η_count} η
TIME_BOX: {remaining duration, if configured}
NEXT:     {suggested action}

TASK LOG: {feature}: {source}→{target} {STATUS} at {timestamp} ({n} iterations)
═══════════════════════════
```

On convergence, the task log entry is also appended to `.ai-workspace/tasks/active/ACTIVE_TASKS.md`
and `/gen-status --gantt` will include this phase in the Gantt chart.

## Examples

```bash
# Generate requirements from intent
/gen-iterate --edge "intent→requirements" --feature "REQ-F-AUTH-001"

# Generate design from requirements
/gen-iterate --edge "requirements→design" --feature "REQ-F-AUTH-001"

# TDD co-evolution (code + tests)
/gen-iterate --edge "code↔unit_tests" --feature "REQ-F-AUTH-001"

# Auto-iterate code generation until tests pass
/gen-iterate --edge "code↔unit_tests" --feature "REQ-F-AUTH-001" --auto
```
