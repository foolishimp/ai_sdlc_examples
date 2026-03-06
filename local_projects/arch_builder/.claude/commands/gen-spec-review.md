# /gen-spec-review - Formal Spec-Boundary Review

Structured review protocol for spec-boundary edges where the asset crosses from one specification level to another (intent→requirements, requirements→design). Extends `/gen-review` with gradient checking — does the transformation preserve intent while binding to the target level's constraints?

<!-- Implements: REQ-LIFE-009 (Spec Review as Gradient Check), REQ-EVAL-003 (Human Accountability), REQ-UX-006 (Human Gate Awareness) -->
<!-- Reference: AI_SDLC_ASSET_GRAPH_MODEL.md §0, §4.1, ADR-008, ADR-014, ADR-017 -->

## Usage

```
/gen-spec-review --feature "REQ-F-*" --edge "{source}→{target}" [--diff] [--checklist-only]
```

| Option | Description |
|--------|-------------|
| `--feature` | The feature vector under review |
| `--edge` | The spec-boundary edge being reviewed (intent→requirements or requirements→design) |
| `--diff` | Show delta between source and target assets side-by-side |
| `--checklist-only` | Print the review checklist without running it (for pre-review preparation) |

## Applicability

This command applies at **spec-boundary edges** — transitions where the asset type changes specification level:

| Edge | Source level | Target level | What gradient means |
|------|-------------|-------------|-------------------|
| `intent→requirements` | Business motivation | Functional/non-functional requirements | Intent preserved? All motivations captured as REQ keys? |
| `requirements→design` | What (tech-agnostic) | How (tech-bound) | Requirements preserved? Each REQ key has a design binding? |
| `design→code` | Architecture (ADRs) | Implementation | Design decisions preserved? ADR constraints respected? |

For non-spec-boundary edges (code↔unit_tests, uat_tests), use `/gen-review` instead.

## Instructions

### Step 1: Load Review Context

1. Read the feature vector from `.ai-workspace/features/active/{feature}.yml`
2. Read the **source asset** (the specification-level artifact being transformed)
3. Read the **target asset** (the construction-level artifact being reviewed)
4. Read the edge configuration from `config/edge_params/{edge}.yml`
5. Read the IntentEngine config from `config/intentengine_config.yml` for escalation thresholds

### Step 2: Gradient Check — Three Dimensions

Present the review as a structured gradient check. The "gradient" is the transformation fidelity from source to target:

#### 2a: Completeness Gradient

```
COMPLETENESS CHECK — Does the target cover everything in the source?
═══════════════════════════════════════════════════════════════════

Source asset:  {path}
Target asset:  {path}

REQ Key Mapping:
  REQ-F-AUTH-001  ✓ requirements → design binding (ADR-005)
  REQ-F-AUTH-002  ✗ requirements defined, no design binding
  REQ-NFR-SEC-001 ✓ requirements → design binding (ADR-005 §3)

Coverage: {n}/{m} REQ keys have target bindings ({pct}%)
```

For each REQ key in the source:
- Check if the target asset references it (via `Implements: REQ-*` tags)
- Check if acceptance criteria are addressed
- Flag any source REQ keys with no target binding

#### 2b: Fidelity Gradient

```
FIDELITY CHECK — Does the target faithfully represent the source?
════════════════════════════════════════════════════════════════

For each source requirement, assess:
  1. Is the intent preserved? (not just letter, but spirit)
  2. Are any requirements weakened or narrowed in translation?
  3. Are new constraints introduced that weren't in the source?

Fidelity assessment:
  REQ-F-AUTH-001  ✓ faithful (intent preserved, no narrowing)
  REQ-F-AUTH-002  ~ partial (error handling requirement softened)
  REQ-NFR-SEC-001 ✓ faithful (security constraints maintained)
```

This is the **agent evaluator** (F_P) component — it requires interpretation, not just mechanical checking.

#### 2c: Boundary Gradient

```
BOUNDARY CHECK — Does the target stay within its specification level?
═══════════════════════════════════════════════════════════════════

For intent→requirements:
  - No technology choices in requirements (that's design's job)
  - No implementation details (that's code's job)
  - Requirements are testable and measurable

For requirements→design:
  - Technology choices are explicit (ADRs)
  - Constraint dimensions are resolved (ecosystem, deployment, security, build)
  - Design doesn't redefine requirements (preserves upstream)

For design→code:
  - ADR decisions respected (no silent overrides)
  - Architecture patterns followed
  - Code doesn't redesign (implements, doesn't invent)

Boundary violations found: {n}
  {list any violations}
```

### Step 3: Present Review Summary

```
═══ SPEC-BOUNDARY REVIEW ═══

Feature:    {REQ-F-*} — "{title}"
Edge:       {source} → {target}
Iteration:  {n}

GRADIENT RESULTS:
  Completeness:  {n}/{m} REQ keys covered ({pct}%)     {✓|✗}
  Fidelity:      {n}/{m} faithfully represented ({pct}%) {✓|~|✗}
  Boundary:      {n} violations found                    {✓|✗}

{If --diff: show side-by-side source vs target for each REQ key}

AGENT EVALUATOR FINDINGS:
  {List any gaps, weaknesses, or boundary violations}

PREVIOUS ITERATIONS:
  Iteration {n-1}: {summary of previous feedback}
  Iteration {n-2}: {summary}

HUMAN DECISION REQUIRED:
  1. Approve — gradient acceptable, promote to next edge
  2. Reject  — gradient too steep, rework needed (provide feedback)
  3. Refine  — specific changes needed (capture as iteration guidance)
  4. Escalate — source asset needs revision (escalate upstream)

═══════════════════════════
```

### Step 4: Collect and Record Decision

Collect the human's decision. Record in the feature vector:

```yaml
# In feature vector trajectory for the reviewed edge
evaluator_results:
  agent: pass        # or fail with details
  deterministic: pass
  human:
    decision: approved|rejected|refined|escalated
    feedback: "{text}"
    gradient_scores:
      completeness: 0.95
      fidelity: 0.90
      boundary: 1.0
    timestamp: "{ISO 8601}"
```

### Step 5: Handle Escalation Upstream

If the human chooses **Escalate** (source asset needs revision):

1. Emit a `source_finding` in the iteration event:
   ```json
   {"description": "{what's wrong with the source}", "classification": "SOURCE_GAP", "disposition": "escalate_upstream"}
   ```

2. Emit an `intent_raised` event if the finding warrants a new work item:
   ```json
   {"event_type": "intent_raised", "timestamp": "{ISO 8601}", "project": "{project}", "data": {"intent_id": "INT-REV-{SEQ}", "trigger": "spec_review escalation on {edge}", "delta": "{what source is missing}", "signal_source": "source_finding", "vector_type": "feature", "affected_req_keys": ["REQ-*"], "severity": "high"}}
   ```

3. Mark the current edge as **blocked** in the feature vector until the upstream edge is re-iterated.

### Step 6: Emit Review Event

Append `review_completed` event to `.ai-workspace/events/events.jsonl`:

```json
{
  "event_type": "review_completed",
  "timestamp": "{ISO 8601}",
  "project": "{project name}",
  "data": {
    "feature": "REQ-F-*",
    "edge": "{source}→{target}",
    "iteration": {n},
    "review_type": "spec_boundary",
    "decision": "approved|rejected|refined|escalated",
    "feedback": "{human feedback}",
    "gradient": {
      "completeness": {score},
      "fidelity": {score},
      "boundary": {violations}
    },
    "all_evaluators_pass": true|false,
    "escalation": null|{"upstream_edge": "{edge}", "intent_id": "INT-REV-*"}
  }
}
```

If approved and all evaluators pass, also emit `edge_converged`.

## Relationship to /gen-review

`/gen-review` is the general-purpose human gate — it works on any edge.
`/gen-spec-review` is the **specialised** review for spec-boundary edges — it adds the gradient check (completeness, fidelity, boundary) that is meaningless on non-spec edges.

The iterate agent should delegate to `/gen-spec-review` when:
- The edge is `intent→requirements`, `requirements→design`, or `design→code`
- The edge config has `human_required: true`
- The human evaluator is triggered

For all other edges, delegate to `/gen-review`.
