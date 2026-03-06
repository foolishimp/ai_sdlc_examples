# /gen-spawn - Spawn a Child Vector

Spawn a child vector (discovery, spike, PoC, or hotfix) from a parent feature vector. The child runs independently with its own projection profile and convergence criteria. On convergence (or time-box expiry), results fold back to the parent's Context[].

<!-- Implements: REQ-FEAT-003 (Cross-Feature Dependencies) -->

## Usage

```
/gen-spawn --type {discovery|spike|poc|hotfix} --parent "REQ-F-{DOMAIN}-{SEQ}" --reason "{why}" [--duration "{time}"]
```

| Option | Description |
|--------|-------------|
| `--type` | The child vector type (determines projection profile) |
| `--parent` | The parent feature vector (REQ-F-*) that spawned this child |
| `--reason` | Why this child is being spawned (knowledge gap, risk, feasibility question, incident) |
| `--duration` | Time-box duration override (default from profile: spike=1 week, poc=3 weeks, hotfix=4 hours, discovery=configurable) |

## Instructions

### Step 1: Validate Parent

1. Read the parent feature vector from `.ai-workspace/features/active/{parent}.yml`
2. Verify the parent exists and is in `in_progress` or `iterating` status
3. Note which edge triggered the spawn (from the last iteration report)

### Step 2: Select Projection Profile

Map the vector type to its default projection profile:

| Vector Type | Default Profile | Default Duration |
|-------------|----------------|-----------------|
| discovery | minimal | configurable |
| spike | spike | 1 week |
| poc | poc | 3 weeks |
| hotfix | hotfix | 4 hours |

Load the profile from `.ai-workspace/profiles/{profile}.yml` or fall back to `v2/config/profiles/{profile}.yml`.

### Step 3: Create Child Feature Vector

1. Generate child feature ID: `REQ-F-{TYPE}-{SEQ}` (e.g., `REQ-F-SPIKE-001`)
   - Type prefix matches vector type: SPIKE, DISC, POC, HOTFIX
   - Sequence number: next available for that type
2. Create from `.ai-workspace/features/feature_vector_template.yml`
3. Populate:
   - `feature`: generated ID
   - `title`: derived from `--reason`
   - `intent`: same as parent intent (or new INT-* if this is an independent investigation)
   - `vector_type`: from `--type`
   - `profile`: from step 2
   - `parent.feature`: from `--parent`
   - `parent.edge`: edge where spawn was triggered
   - `parent.reason`: from `--reason`
   - `time_box.enabled`: true
   - `time_box.duration`: from `--duration` or profile default
   - `time_box.started`: current timestamp
   - `time_box.check_in`: from profile
   - `time_box.on_expiry`: from profile
4. Save to `.ai-workspace/features/active/{child_id}.yml`

### Step 4: Update Parent

1. Add child to parent's `children` list:
   ```yaml
   children:
     - feature: "REQ-F-SPIKE-001"
       vector_type: spike
       status: pending
       fold_back_status: pending
       fold_back_payload: ""
   ```
2. If the parent should block on the child's result:
   - Set parent edge status to `blocked`
   - Add `blocked_by: REQ-F-SPIKE-001` to the edge

### Step 5: Emit Event

Append a `spawn_created` event to `.ai-workspace/events/events.jsonl`:

```json
{"event_type": "spawn_created", "timestamp": "{ISO 8601}", "project": "{project name}", "data": {"parent": "{parent feature ID}", "child": "{child feature ID}", "vector_type": "{discovery|spike|poc|hotfix}", "reason": "{reason}", "time_box": "{duration}", "profile": "{profile name}", "triggered_at_edge": "{edge where spawn was triggered}"}}
```

### Step 6: Report

```
═══ SPAWN REPORT ═══
Parent:     {parent_id}
Child:      {child_id}
Type:       {vector_type}
Profile:    {profile_name}
Reason:     {reason}

Time Box:   {duration} starting now
Check-in:   {cadence}
On Expiry:  {action}

Graph Edges (from profile):
  {list of included edges}

Evaluators (from profile):
  {evaluator configuration}

NEXT: Run `/gen-iterate --edge "{first_edge}" --feature "{child_id}"` to begin
═══════════════════════════
```

## Fold-Back (when child converges or expires)

When a child vector converges or its time box expires:

1. Package the child's outputs:
   - For `discovery`: findings document, answer to the question
   - For `spike`: risk assessment, recommendation, experiment results
   - For `poc`: feasibility verdict, prototype (if promotable), lessons learned
   - For `hotfix`: the fix, regression test, rollback plan

2. Update parent's `children` entry:
   ```yaml
   children:
     - feature: "REQ-F-SPIKE-001"
       vector_type: spike
       status: converged            # or time_box_expired
       fold_back_status: folded_back
       fold_back_payload: ".ai-workspace/features/fold-back/REQ-F-SPIKE-001.md"
   ```

3. Write fold-back payload to `.ai-workspace/features/fold-back/{child_id}.md`:
   - Summary of findings
   - Recommendation (proceed / pivot / abandon)
   - Relevant artifacts to carry forward
   - References to child's trajectory

4. Add fold-back to parent's Context[]:
   - The child's results now constrain subsequent parent iterations
   - Unblock the parent edge if it was blocked

5. **Emit fold-back event** to `.ai-workspace/events/events.jsonl`:
   ```json
   {"event_type": "spawn_folded_back", "timestamp": "{ISO 8601}", "project": "{project name}", "data": {"parent": "{parent ID}", "child": "{child ID}", "fold_back_status": "converged|time_box_expired", "payload_path": ".ai-workspace/features/fold-back/{child_id}.md"}}
   ```

6. If hotfix: spawn remediation feature vector (permanent fix for the root cause)

## Examples

```bash
# Spawn a spike to assess if WebSocket will work
/gen-spawn --type spike --parent "REQ-F-REALTIME-001" --reason "Can WebSocket handle 10k concurrent connections?"

# Spawn a discovery to answer a domain question
/gen-spawn --type discovery --parent "REQ-F-BILLING-001" --reason "How does the tax calculation work for EU VAT?"

# Spawn a PoC to validate an approach
/gen-spawn --type poc --parent "REQ-F-SEARCH-001" --reason "Is Elasticsearch viable for our query patterns?" --duration "2 weeks"

# Spawn a hotfix for a production incident
/gen-spawn --type hotfix --parent "REQ-F-AUTH-001" --reason "Login timeout rate exceeded 5% SLA"
```
