# /gen-checkpoint - Save Session Snapshot

Create an immutable checkpoint of the current workspace state for recovery.

<!-- Implements: REQ-TOOL-008 (Context Snapshot), REQ-INTENT-004 (Spec Reproducibility) -->

## Usage

```
/gen-checkpoint [--message "description"]
```

| Option | Description |
|--------|-------------|
| `--message` | Description of what this checkpoint captures |

## Instructions

### Step 1: Compute Context Hash

1. Resolve tenant context directory (try `.ai-workspace/{impl}/context/` first, fall back to `.ai-workspace/context/`)
2. Enumerate all files in the resolved context directory (excluding `context_manifest.yml`)
3. Sort by relative path (lexicographic, UTF-8)
4. For each file: read bytes, normalise to UTF-8 NFC, compute SHA-256
5. Serialise sorted entry list as deterministic YAML
6. Compute aggregate SHA-256 of the serialised list
7. Update `context_manifest.yml` in the resolved context directory

### Step 2: Create Snapshot

Create a snapshot file at `.ai-workspace/snapshots/snapshot-{timestamp}.yml`:

```yaml
timestamp: "{ISO-8601}"
message: "{user message or auto-generated}"
context_hash: "sha256:{aggregate hash}"

feature_states:
  - feature: "REQ-F-AUTH-001"
    status: "in_progress"
    current_edge: "code↔unit_tests"
    iteration: 3

  - feature: "REQ-F-DB-001"
    status: "converged"

tasks:
  in_progress: 2
  pending: 3
  blocked: 0

git_ref: "{current git commit hash}"
```

### Step 3: Emit Event

Append a `checkpoint_created` event to `.ai-workspace/events/events.jsonl`:

```json
{"event_type": "checkpoint_created", "timestamp": "{ISO 8601}", "project": "{project name from project_constraints.yml}", "data": {"context_hash": "sha256:{aggregate hash}", "feature_count": {count of active features}, "git_ref": "{current git commit hash}", "message": "{user message or auto-generated}", "snapshot_path": ".ai-workspace/snapshots/snapshot-{timestamp}.yml"}}
```

### Step 4: Update Task Tracking

Update `.ai-workspace/tasks/active/ACTIVE_TASKS.md` with current task status.

### Step 5: Report

```
Checkpoint saved
================
Timestamp:    {timestamp}
Context hash: sha256:{hash}
Features:     {active}/{total}
Git ref:      {commit hash}
Snapshot:     .ai-workspace/snapshots/snapshot-{timestamp}.yml
```
