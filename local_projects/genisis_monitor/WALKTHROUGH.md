# Genesis Monitor — End-to-End Walkthrough

**Scenario**: Build a Todo List Dashboard from scratch using the AI SDLC methodology,
with Genesis Monitor observing every step in real-time.

**Time to complete**: ~30–60 minutes (excluding LLM iteration time)

---

## What You Will See

By the end of this walkthrough, Genesis Monitor will be showing a live project with:

- Intent → Requirements → Design → Code ↔ Tests converging in real-time
- Human review gates surfaced as pending events
- The deterministic engine evaluating code correctness
- Every iteration recorded in the event log and visible in the timeline scrubber

---

## Prerequisites

- Genesis Monitor installed (`pip install -e ".[dev]"` from `imp_python_fastapi/`)
- Claude Code installed (`claude` CLI available)
- Genesis plugin installed in Claude Code (`/gen-status` available in a Claude session)
- Python 3.12+

---

## Step 1: Create the Project Directory

```bash
mkdir -p ~/projects/todo-dashboard
cd ~/projects/todo-dashboard
```

Nothing else yet. No files, no `.ai-workspace/`. This is truly from scratch.

---

## Step 2: Start Genesis Monitor on Your Projects Root

In a separate terminal, start the monitor pointing at the parent directory so it
discovers the new project as soon as it initialises:

```bash
python -m genesis_monitor --watch-dir ~/projects
```

Open `http://localhost:8000` in your browser. You will see the index page with
whatever projects are in `~/projects`. The `todo-dashboard` directory is **not
listed yet** — it has no `.ai-workspace/` so the scanner ignores it.

Leave this browser tab open. It will update automatically.

---

## Step 3: Open Claude Code in the Project

```bash
cd ~/projects/todo-dashboard
claude
```

Claude Code opens. The Genesis Bootloader is not installed yet — you are starting
clean.

---

## Step 4: Initialise the Project

Type:

```
/gen-start
```

`/gen-start` detects state `UNINITIALISED` (no `.ai-workspace/` directory) and
runs the 5-question progressive init flow. Answer the prompts:

```
Project name: todo-dashboard                (auto-detected from directory — confirm)
Project kind: application
Language:     Python                        (or your preference)
Test runner:  pytest                        (auto-detected or choose)
Intent:       A real-time web dashboard for managing a personal todo list,
              with due dates, priority levels, and completion tracking.
```

What happens behind the scenes:

1. `.ai-workspace/` directory tree is created
2. `specification/INTENT.md` is written with your intent statement
3. `graph_topology.yml` is copied into `.ai-workspace/graph/`
4. `project_constraints.yml` scaffold is written (mandatory dims left blank for now)
5. A `project_initialized` event is appended to `events.jsonl`

**What you see in Genesis Monitor**: The `todo-dashboard` project appears in the
project tree within a few seconds. It shows **no status** (grey badge) because
no edges have been worked yet. Click it.

The project detail page shows an empty event feed and a blank asset graph.

---

## Step 5: Create Your First Feature Vector

`/gen-start` continues and detects state `NO_FEATURES`. It prompts you to spawn
your first feature:

```
Intent captured. Let's create your first feature vector.
Feature title: Todo Item CRUD
```

This runs `/gen-spawn --type feature`, which creates:

```
.ai-workspace/features/active/REQ-F-TODO-001.yml
```

A `spawn_created` event is written to `events.jsonl`.

**What you see in Genesis Monitor**: The Feature Vectors card shows
`REQ-F-TODO-001 — Todo Item CRUD` with status `not started`.

You can spawn more features now if you like:

```
/gen-spawn --type feature
Feature title: Real-Time Updates via SSE
```

Creates `REQ-F-REALTIME-001.yml`. For this walkthrough we will focus on
`REQ-F-TODO-001` first.

---

## Step 6: Iterate — Intent → Requirements

```
/gen-start
```

State is now `IN_PROGRESS`. The command selects `REQ-F-TODO-001` on the
`intent→requirements` edge and delegates to `/gen-iterate`.

The **iterate agent** runs:

1. Loads `INTENT.md` as the source asset
2. Loads the `intent_requirements.yml` edge config (evaluator checklist)
3. Constructs a draft `REQUIREMENTS.md` candidate
4. Runs evaluators against the draft:
   - **F_D (deterministic)**: checks structure, REQ key format, section completeness
   - **F_P (agent)**: checks coverage of intent, checks for ambiguity
   - **F_H (human)**: marks a review request — **pauses here**

An `edge_started` event is written, followed by an `iteration_completed` event.

You see output like:

```
Edge: intent→requirements (iteration 1)

Deterministic checks: 5/5 pass
Agent checks:         8/9 pass (1 finding: "due date format unspecified")
Human check:          PENDING

δ = 1 (human review required)
```

**What you see in Genesis Monitor**:
- Asset Graph: `intent` → `requirements` edge is amber (in progress)
- Edge Status: `intent→requirements` — In Progress, Iteration 1
- Recent Events: `edge_started`, `iteration_completed` with δ=1

---

## Step 7: Human Review — Requirements

The iterate agent has produced a requirements draft and is waiting for your
approval. Run:

```
/gen-review --feature REQ-F-TODO-001
```

The agent presents the current requirements candidate:

```
REVIEW REQUEST
==============
Feature:   REQ-F-TODO-001 — "Todo Item CRUD"
Edge:      intent → requirements
Iteration: 1

CURRENT CANDIDATE:
  ## Requirements
  REQ-F-TODO-001-AC1: The system MUST allow creating todo items with title and
                      optional description.
  REQ-F-TODO-001-AC2: The system MUST support marking items complete/incomplete.
  REQ-F-TODO-001-AC3: The system MUST support due dates (date only, no time).
  ...

FINDINGS:
  - "due date format unspecified" (agent) — suggest ISO 8601 date string

Your decision [approve / reject / refine]:
```

You respond:

```
refine: Add a priority field (low/medium/high/critical). Due date format is
        ISO 8601 date string (YYYY-MM-DD). Also add a requirement for
        bulk-complete (select multiple, mark done).
```

A `review_completed` event is written with decision `refined`.

`/gen-start` runs another iteration incorporating your feedback. The agent
updates the requirements draft. The new iteration runs all evaluators again.
This time human review passes (you approve):

```
Your decision: approve
```

`edge_converged` event written. Requirements edge is done.

**What you see in Genesis Monitor**:
- Asset Graph: `intent` → `requirements` node turns green
- Edge Status: `intent→requirements` — Converged, 2 iterations
- Timeline (Gantt): a completed bar appears for this edge
- Consciousness Loop: the `refined` decision appears as a causal chain event

---

## Step 8: Iterate — Requirements → Design

```
/gen-start
```

Selects the next unconverged edge: `requirements→design`. This is where
**constraint dimensions** are prompted for the first time (progressive disclosure
— they were not asked at init to avoid overwhelming you).

```
Mandatory constraint dimensions needed for design:

ecosystem_compatibility:
  Language: Python 3.12
  Framework: FastAPI + HTMX  (or your choice)
  Runtime: uvicorn

deployment_target:
  Platform: local development (single process)

security_model:
  Auth: none (personal tool, no auth needed)

build_system:
  Tool: pip + pytest
  CI: none initially
```

These are written to `project_constraints.yml`. Now the design iterate begins.

The agent generates a design document covering:
- Architecture (server-rendered HTML vs. API + SPA)
- Data model (SQLite or in-memory)
- Mermaid diagram of the asset graph
- ADR decisions for key choices

Human review is required at this edge too. You review and approve (or refine
and re-iterate). Same flow as requirements.

When converged:

**What you see in Genesis Monitor**:
- Asset Graph: `requirements` → `design` turns green
- Constraint Dimensions card: all mandatory dims shown as resolved
- Timeline: Gantt now has two completed bars

---

## Step 9: Iterate — Code ↔ Unit Tests (TDD co-evolution)

```
/gen-start
```

Now on the `code↔unit_tests` edge — the TDD co-evolution phase.

The iterate agent generates:
1. Application code (`app.py`, `models.py`, `routes.py`, etc.)
2. Test suite (`tests/test_*.py`) co-evolving with the code

This edge is where the **deterministic engine** becomes the primary evaluator.
The agent finishes its construction pass and writes the `edge_started` event.
Then it hands off to the engine for evaluation:

```
Deterministic evaluation running...
  (calls: python -m genesis evaluate --edge "code↔unit_tests" --feature REQ-F-TODO-001)
```

---

## Step 10: The Deterministic Engine Runs

The engine (`python -m genesis evaluate`) does the following — all without LLM calls:

```bash
python -m genesis evaluate \
  --edge "code↔unit_tests" \
  --feature "REQ-F-TODO-001" \
  --deterministic-only
```

The engine runs these checks in sequence:

| Check | Tool | Pass criterion |
|-------|------|---------------|
| Tests pass | `pytest tests/ -q` | exit code 0 |
| Test coverage | `pytest --cov --cov-report=term-missing` | ≥ 80% |
| Lint | `ruff check src/` | exit code 0 |
| Type hints present | `mypy src/` or grep | all public functions annotated |
| REQ keys tagged | grep `# Implements: REQ-` | ≥1 tag per source file |

Each check emits a structured event to `events.jsonl` as it completes. This is
**Level 4 reliability** — the events are written by deterministic code, not
instructed by the LLM, so they are guaranteed to be accurate.

A typical first run output looks like:

```json
{
  "event_type": "iteration_completed",
  "edge": "code↔unit_tests",
  "feature": "REQ-F-TODO-001",
  "iteration": 1,
  "delta": 3,
  "evaluators": {
    "passed": 2,
    "failed": 3,
    "details": [
      {"name": "tests_pass",          "result": "pass"},
      {"name": "test_coverage",       "result": "fail", "actual": "62%", "threshold": "80%"},
      {"name": "lint",                "result": "fail", "findings": ["E501 line too long (3)"]},
      {"name": "req_keys_tagged",     "result": "fail", "files_missing": ["routes.py"]},
      {"name": "type_hints",          "result": "pass"}
    ]
  }
}
```

δ = 3 (three failing checks).

**What you see in Genesis Monitor** (updates within seconds of each event):
- Edge Status: `code↔unit_tests` — In Progress, Iteration 1, δ=3
- Recent Events: each check result streaming in
- Convergence card: red indicators for the three failing checks
- Test Traceability: `routes.py` shown as missing `# Implements:` tag

---

## Step 11: The Engine Found Issues — Human Interaction

The engine halts with δ > 0. Back in the Claude Code session, the iterate agent
reads the engine results and presents them to you:

```
Engine evaluation complete — δ = 3

Failing checks:
  1. test_coverage (62% < 80%)
     Missing coverage on: TodoItem.priority_label(), routes/bulk_complete
  2. lint — 3 E501 violations in models.py
  3. req_keys_tagged — routes.py has no # Implements: REQ- tag

These need to be fixed before the edge can converge.

Options:
  a) Auto-fix and re-evaluate  (/gen-start --auto)
  b) Fix manually and re-evaluate  (edit files, then /gen-start)
  c) Spawn a discovery vector for coverage gap  (/gen-spawn --type spike)
```

These findings appear as **pending human interaction events** in the Genesis
Monitor's Protocol Compliance card — the engine recorded what was missing, and
a human response is needed to resolve it.

You respond to the agent:

```
Auto-fix the lint. For coverage: add tests for priority_label and bulk_complete.
Add the REQ tag to routes.py manually.
```

The agent:
1. Runs `ruff check --fix` (lint auto-fix)
2. Generates additional tests for the two uncovered methods
3. Adds `# Implements: REQ-F-TODO-001` to `routes.py`
4. Writes an `iteration_completed` event with the changes made

A `review_completed` event records your decision.

---

## Step 12: Re-trigger the Engine

Now re-run the engine:

```
/gen-start
```

Or directly:

```bash
python -m genesis evaluate \
  --edge "code↔unit_tests" \
  --feature "REQ-F-TODO-001" \
  --deterministic-only
```

The engine runs again. This time:

```json
{
  "event_type": "iteration_completed",
  "edge": "code↔unit_tests",
  "feature": "REQ-F-TODO-001",
  "iteration": 2,
  "delta": 0,
  "evaluators": {
    "passed": 5,
    "failed": 0,
    "details": [
      {"name": "tests_pass",      "result": "pass"},
      {"name": "test_coverage",   "result": "pass", "actual": "84%"},
      {"name": "lint",            "result": "pass"},
      {"name": "req_keys_tagged", "result": "pass"},
      {"name": "type_hints",      "result": "pass"}
    ]
  },
  "status": "converged"
}
```

δ = 0. **The edge has converged.**

An `edge_converged` event is immediately written.

**What you see in Genesis Monitor**:
- Asset Graph: `code↔unit_tests` node turns **green**
- Edge Status: `code↔unit_tests` — Converged, 2 iterations
- Convergence card: all checks green
- Test Traceability: 100% of REQ keys traced
- Timeline: third bar completes
- Event feed: `edge_converged` at the top

---

## Step 13: Check Status

```
/gen-status
```

Output:

```
State: IN_PROGRESS (1 active, 0 converged)

You Are Here:
  REQ-F-TODO-001  intent ✓ → req ✓ → design ✓ → code ✓ → tests ✓ → uat ○

Project Rollup:
  Edges converged: 5/6 (83%)
  Features: 0 fully converged, 1 in-progress

Next: REQ-F-TODO-001 on uat_tests edge
```

One edge left: UAT tests.

```
/gen-start
```

Runs the UAT test iteration. When those converge:

```
State: ALL_CONVERGED

All features converged!

Recommended actions:
  1. /gen-gaps    — Check for traceability gaps before release
  2. /gen-release — Create a versioned release
```

**What you see in Genesis Monitor**:
- Asset Graph: all nodes green
- Edge Status: all edges converged
- Convergence card: 6/6 green
- Feature Vectors: `REQ-F-TODO-001` shown as fully converged
- Timeline: complete Gantt from intent to UAT

---

## Using the Temporal Scrubber to Review History

Now that the build is complete, use the scrubber to replay the journey:

1. Drag the **right handle** left to the very beginning of the timeline.
   The dashboard reverts to the initial state — blank graph, no events.

2. Slide it forward slowly. Watch the edges converge one by one as you move
   through time.

3. Stop at any point where δ was non-zero and see exactly which checks were
   failing at that moment.

4. Move back to **Now** (right handle at far right) to return to live state.

The scrubber works because all state is derived from the event log. Every
`edge_started`, `iteration_completed`, and `edge_converged` event is a
timestamped fact that the temporal projection engine replays.

---

## Summary — What Happened

| Phase | Who did it | Events emitted |
|-------|-----------|---------------|
| Init | `/gen-start` CLI | `project_initialized` |
| Feature creation | `/gen-spawn` CLI | `spawn_created` |
| intent→requirements | Agent (LLM) + human review | `edge_started`, `iteration_completed` ×2, `review_completed`, `edge_converged` |
| requirements→design | Agent (LLM) + human review | `edge_started`, `iteration_completed` ×N, `review_completed`, `edge_converged` |
| code↔unit_tests | Agent (LLM constructs) + **deterministic engine evaluates** | `edge_started`, `iteration_completed` ×2, `edge_converged` |
| UAT | Agent (LLM) | `edge_started`, `iteration_completed` ×N, `edge_converged` |

The pattern is always the same:

```
/gen-start  →  agent constructs candidate
            →  engine evaluates (deterministic checks)
            →  human reviews if required
            →  /gen-start again if delta > 0
            →  repeat until delta == 0
            →  edge_converged
```

Genesis Monitor watches every step and shows you where you are, where you've been,
and what's failing — in real-time, without any manual instrumentation.

---

## Typical Commands Reference

| Situation | Command |
|-----------|---------|
| Start / continue from current state | `/gen-start` |
| Auto-loop until human gate | `/gen-start --auto` |
| Check where you are | `/gen-status` |
| See Gantt timeline | `/gen-status --gantt` |
| Human review a pending gate | `/gen-review --feature REQ-F-*` |
| Run the deterministic engine manually | `python -m genesis evaluate --edge "code↔unit_tests" --feature REQ-F-* --deterministic-only` |
| Create a new feature | `/gen-spawn --type feature` |
| Create a spike for investigation | `/gen-spawn --type spike` |
| Check for traceability gaps | `/gen-gaps` |
| Create a versioned release | `/gen-release` |
| Workspace health check | `/gen-status --health` |
