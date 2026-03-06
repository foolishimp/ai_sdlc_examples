# /gen-start - State-Driven Routing Entry Point

State-machine controller that detects project state and delegates to the appropriate command. Two verbs replace the 9-command learning curve: **Start** ("Go.") and **Status** ("Where am I?").

<!-- Implements: REQ-UX-001, REQ-UX-002, REQ-UX-004, REQ-UX-005, REQ-TOOL-003 (Workflow Commands), REQ-INTENT-001 (Intent Capture — Step 3), REQ-INTENT-002 (Intent as Spec — Step 3) -->
<!-- Reference: AI_SDLC_ASSET_GRAPH_MODEL.md v2.9.0 §7.5 Event Sourcing, ADR-012 -->

## Usage

```
/gen-start [--feature "REQ-F-*"] [--edge "source→target"] [--auto] [--profile "standard"]
```

| Option | Description |
|--------|-------------|
| (none) | Detect state, select feature/edge, run one iteration |
| `--feature` | Override automatic feature selection |
| `--edge` | Override automatic edge determination |
| `--auto` | Loop: iterate → check convergence → next edge → repeat (pauses at human gates) |
| `--profile` | Override default profile for new features |

## Instructions

### Step 0: State Detection Algorithm

Detect the current project state from the workspace filesystem and event log. **State is derived, never stored** (§7.5 Event Sourcing).

Check in this order (first match wins):

| # | State | Detection | Action |
|---|-------|-----------|--------|
| 1 | `UNINITIALISED` | No `.ai-workspace/` directory | → Progressive Init (Step 1) |
| 2 | `NEEDS_CONSTRAINTS` | `.ai-workspace/` exists AND `project_constraints.yml` has mandatory dimensions with empty values AND at least one feature is at or past `requirements→design` edge | → Constraint Prompting (Step 2) |
| 3 | `NEEDS_INTENT` | No intent file in `specification/INTENT.md` OR intent file is empty/placeholder | → Intent Authoring (Step 3) |
| 4 | `NO_FEATURES` | Intent exists but `features/active/` is empty | → Feature Creation (Step 4) |
| 5 | `STUCK` | Any feature with same δ (failing check count) for 3+ consecutive iterations (read from `events.jsonl`) | → Stuck Recovery (Step 7) |
| 6 | `ALL_BLOCKED` | All active features blocked (spawn dependency unresolved OR human review pending with no actionable edge) | → Blocked Recovery (Step 8) |
| 7 | `IN_PROGRESS` | Active features with unconverged edges | → Feature/Edge Selection (Step 5) |
| 8 | `ALL_CONVERGED` | All active features fully converged (all edges in profile satisfied) | → Release/Gaps (Step 6) |

Display the detected state to the user:

```
State: IN_PROGRESS
  3 features active, 1 converged, 0 blocked
  Next: REQ-F-AUTH-001 on code↔unit_tests (iteration 4)
```

### Step 1: Progressive Init (UNINITIALISED)

Run a 5-question flow, then delegate to `/gen-init`:

1. **Project name**: Auto-detect from directory name, `package.json`, `pyproject.toml`, `build.sbt`, or `Cargo.toml`. Ask to confirm.
2. **Project kind**: Ask — application / library / service / data-pipeline
3. **Language**: Auto-detect from existing files (`.py` → Python, `.ts`/`.js` → TypeScript, `.scala` → Scala, `.rs` → Rust, `.go` → Go, `.java` → Java). Ask to confirm.
4. **Test runner**: Auto-detect from config files (`pytest.ini`/`pyproject.toml[tool.pytest]` → pytest, `jest.config.*` → jest, `build.sbt` → sbt test). Ask to confirm.
5. **Intent description**: Ask — "In one sentence, what are you building?"

Map project kind to default profile:
- application → `standard`
- library → `full`
- service → `standard`
- data-pipeline → `standard`

Delegate to `/gen-init` with detected values. Write intent description to `specification/INTENT.md`.

**Constraint dimensions are NOT prompted here** — they are deferred until the `requirements→design` edge (REQ-UX-002 Progressive Disclosure).

### Step 2: Deferred Constraint Prompting (NEEDS_CONSTRAINTS)

When a feature reaches the `requirements→design` edge and mandatory constraint dimensions are unresolved:

Prompt for each unresolved mandatory dimension:
- **ecosystem_compatibility**: language, version, runtime, frameworks (pre-populate from init detection)
- **deployment_target**: platform, cloud_provider, environment_tiers
- **security_model**: authentication, authorisation, data_protection
- **build_system**: tool, module_structure, ci_integration

Mention advisory dimensions but do not require them:
> "Advisory dimensions (data_governance, performance_envelope, observability, error_handling) can be filled in now or deferred. Press Enter to skip."

Write resolved values to `.ai-workspace/{impl}/context/project_constraints.yml`.

Then proceed to Step 5 (Feature/Edge Selection) to continue the iteration.

### Step 3: Intent Authoring (NEEDS_INTENT)

Prompt the user:
> "Describe what you want to build (the intent). This will become the root of your feature vectors."

Write the response to `specification/INTENT.md` using the standard intent template. Emit `project_initialized` event if not already emitted.

Then re-run state detection (will transition to NO_FEATURES).

### Step 4: Feature Creation (NO_FEATURES)

Inform the user:
> "Intent captured. Let's create your first feature vector."

Delegate to `/gen-spawn --type feature`. If user provides `--profile`, use it; otherwise use the default profile from init.

Then re-run state detection (will transition to IN_PROGRESS).

### Step 5: Feature/Edge Selection (IN_PROGRESS)

#### 5a: Feature Selection Algorithm

If `--feature` is provided, use it. Otherwise, select automatically:

**Priority tiers** (first match wins within each tier):

1. **Time-boxed spawns approaching expiry**: Features where `time_box.enabled: true` AND remaining time < 25% of budget. Sorted by urgency (least time remaining first).
2. **Closest-to-complete**: Features with the fewest unconverged edges remaining. Reduces WIP.
3. **Feature priority**: From feature vector `priority` field (critical > high > medium > low).
4. **Most recently touched**: From last event timestamp for the feature in `events.jsonl`.

Display selection reasoning:
```
Feature: REQ-F-AUTH-001 "User authentication"
  Selected: closest-to-complete (1 edge remaining)
```

#### 5b: Edge Determination Algorithm

If `--edge` is provided, use it. Otherwise, determine automatically:

1. Read the active profile's graph configuration (which edges are included/skipped)
2. Read the feature's trajectory from its `.yml` file
3. Walk edges in topological order (from `graph_topology.yml` transitions)
4. Skip edges already converged
5. Skip edges not included in the active profile
6. Return the first unconverged, non-skipped edge

For co-evolution edges (`code↔unit_tests`), present both sides as a single unit.

Display determination:
```
Edge: code↔unit_tests (TDD co-evolution)
  Skipped: intent→requirements (converged), requirements→design (converged)
  Profile: standard (includes this edge)
```

#### 5c: Delegate to Iterate

Delegate to `/gen-iterate --edge "{source}→{target}" --feature "{feature_id}"`.

### Step 6: Release/Gaps (ALL_CONVERGED)

All features are converged. Suggest next actions:

```
All features converged! 🎉

Recommended actions:
  1. /gen-gaps           — Check for traceability gaps before release
  2. /gen-release        — Create a versioned release
  3. /gen-spawn --type feature  — Start a new feature
```

If `--auto` is set, delegate to `/gen-gaps` first, then `/gen-release`.

### Step 7: Stuck Recovery (STUCK)

Surface stuck features:

```
Stuck features detected:
  REQ-F-AUTH-001 on code↔unit_tests — δ=3 unchanged for 4 iterations
    Failing checks: test_coverage_minimum, all_req_keys_have_tests

Recommended actions:
  1. Spawn a discovery vector to investigate the root cause
  2. Request human review (/gen-review)
  3. Override and force-iterate (/gen-iterate --force)
```

If `--auto` is set, pause and ask the user to choose.

### Step 8: Blocked Recovery (ALL_BLOCKED)

Surface blocked features with reasons:

```
All features blocked:
  REQ-F-AUTH-001 — blocked by spawn dependency (REQ-F-DB-001 not converged)
  REQ-F-API-001  — human review pending on requirements edge

Recommended actions:
  1. Work on the blocking feature: /gen-start --feature "REQ-F-DB-001"
  2. Complete pending reviews: /gen-review --feature "REQ-F-API-001"
```

### Step 9: Auto-Mode Loop

When `--auto` is provided, loop:

```
while state == IN_PROGRESS:
    feature = select_feature()
    edge = determine_edge(feature)

    # Pause at human gates
    if edge.human_required:
        print("Human gate: {edge} requires human review. Pausing auto-mode.")
        delegate to /gen-review
        break

    # Pause at spawn decisions
    if iterate_result.recommends_spawn:
        print("Spawn recommended: {reason}. Pausing auto-mode.")
        break

    # Pause if stuck
    if iterate_result.delta_unchanged and iteration_count >= 3:
        print("Stuck: δ unchanged for {n} iterations. Pausing auto-mode.")
        break

    # Pause if time-box expired
    if feature.time_box_expired:
        print("Time-box expired for {feature}. Pausing auto-mode.")
        break

    delegate to /gen-iterate --edge "{edge}" --feature "{feature}"
    re-detect state
```

### Step 10: Recovery and Self-Healing (REQ-UX-005)

On every invocation, before state detection, run a quick health check:

| Issue | Detection | Recovery |
|-------|-----------|----------|
| Corrupted event log | Malformed JSON lines in `events.jsonl` | Offer: "Event log has N corrupted lines. Truncate at last valid line?" |
| Missing feature vectors | Event log references feature IDs with no `.yml` file | Offer: "Feature {id} referenced in events but no vector file. Regenerate from events?" |
| Orphaned spawns | Child vectors with `parent.feature` pointing to non-existent parent | Offer: "Spawn {id} has no parent. Link to feature or archive?" |
| Stuck features | δ unchanged 3+ iterations | Detected in state machine (Step 7) |
| Unresolved constraints | Mandatory dimensions empty when feature is at design edge | Detected in state machine (Step 2) |

Recovery is always non-destructive — never silently delete user data. Always ask before modifying.

## Output Format

Per-state output examples:

**UNINITIALISED**:
```
Project not initialized. Starting progressive setup...
  Project: my-app (detected from directory)
  Language: Python 3.12 (detected from .py files)
  Test runner: pytest (detected from pyproject.toml)
  Profile: standard (default for application)
```

**IN_PROGRESS**:
```
State: IN_PROGRESS (3 active, 1 converged)
Feature: REQ-F-AUTH-001 "User authentication" (closest-to-complete)
Edge: code↔unit_tests (iteration 4, δ=2)
Delegating to /gen-iterate...
```

**ALL_CONVERGED**:
```
State: ALL_CONVERGED
All 4 features converged across 20 edges.
Run /gen-gaps to check traceability, then /gen-release.
```

## Event Emission

Start does **not** emit its own events. It delegates to underlying commands which emit their own events. This avoids double-counting in the event log.

The state detection algorithm is a **pure read** — it observes workspace state without modifying it.
