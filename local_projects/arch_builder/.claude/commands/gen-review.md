# /gen-review - Human Evaluator Review Point

Present the current asset candidate for human review and approval.

<!-- Implements: REQ-EVAL-001 (Human Evaluator), REQ-LIFE-009 (Spec Review as Gradient Check), REQ-UX-006 (Human Gate Awareness) -->

## Usage

```
/gen-review --feature "REQ-F-*" [--edge "{source}→{target}"]
```

| Option | Description |
|--------|-------------|
| `--feature` | The feature vector to review |
| `--edge` | Specific edge to review (defaults to current active edge) |

## Instructions

### Step 1: Load Feature State

Read the feature vector file from `.ai-workspace/features/active/{feature}.yml`.
Identify the current edge and asset candidate.

### Step 2: Present for Review

Display the current asset candidate with context:

```
REVIEW REQUEST
==============
Feature:    {REQ-F-*} — "{title}"
Edge:       {source} → {target}
Iteration:  {n}

CURRENT CANDIDATE:
{Display the asset — code, design doc, requirements, etc.}

EVALUATOR RESULTS SO FAR:
  Agent:          {results}
  Deterministic:  {results}
  Human:          PENDING (this review)

CONTEXT:
  Requirements addressed: {list REQ-* keys}
  Context hash: {hash}
```

### Step 3: Collect Human Decision

Ask the user:
- **Approve**: Asset is acceptable, proceed to promotion
- **Reject**: Asset needs rework, provide feedback
- **Refine**: Specific changes needed (capture as iteration guidance)

### Step 4: Record Decision

Update the feature vector file with the human evaluator result:
- Decision (approved/rejected/refined)
- Feedback text
- Timestamp

If approved and all other evaluators pass: mark as converged.
If rejected: provide feedback for next iteration.

### Step 5: Emit Event

Append a `review_completed` event to `.ai-workspace/events/events.jsonl`:

```json
{"event_type": "review_completed", "timestamp": "{ISO 8601}", "project": "{project name from project_constraints.yml}", "data": {"feature": "REQ-F-*", "edge": "{source}→{target}", "iteration": {n}, "decision": "approved|rejected|refined", "feedback": "{human feedback text or empty}", "all_evaluators_pass": true|false}}
```

If the decision is `approved` and all evaluators pass (triggering convergence), also emit an `edge_converged` event as specified in the iterate agent's Event Type Reference.
