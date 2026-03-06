# /gen-trace - Show Feature Vector Trajectory

Display the full trajectory of a REQ key through the asset graph.

<!-- Implements: REQ-FEAT-002 (Bidirectional Navigation) -->

## Usage

```
/gen-trace "REQ-{TYPE}-{DOMAIN}-{SEQ}" [--direction forward|backward|both]
```

| Option | Description |
|--------|-------------|
| `REQ-*` | The requirement key to trace |
| `--direction` | Trace direction (default: both) |

## Instructions

### Forward Trace (Intent → Runtime)

Starting from the REQ key, trace forward through the graph:

```
REQ-F-AUTH-001: "User authentication"
=== FORWARD TRACE ===

Intent:       INT-042 "Fix auth timeout"
                ↓
Requirements: REQ-F-AUTH-001 (converged, human approved)
                ↓
Design:       imp_portal/design/auth_design.md
              Component: AuthenticationService
                ↓
Code:         src/auth/service.py
              # Implements: REQ-F-AUTH-001
                ↓
Tests:        tests/test_auth.py
              # Validates: REQ-F-AUTH-001
              Coverage: 92%
                ↓
UAT:          tests/uat/auth.feature
              Scenario: Successful login (PASSED)
                ↓
Runtime:      Telemetry tag: REQ-F-AUTH-001
              Status: healthy, latency p99 = 180ms
```

### Backward Trace (Runtime → Intent)

Starting from a runtime observation, trace backward:

```
Alert: "REQ-F-AUTH-001 timeout rate > 5%"
=== BACKWARD TRACE ===

Telemetry:    REQ-F-AUTH-001 timeout alert
                ↑
Running:      /api/v1/auth/login endpoint
                ↑
Code:         src/auth/service.py:42
              # Implements: REQ-F-AUTH-001
                ↑
Design:       AuthenticationService component
                ↑
Requirements: REQ-F-AUTH-001 "User login with email/password"
                ↑
Intent:       INT-042 "Fix auth timeout"
```

### Step 1: Find All Assets

Search for the REQ key in:
- Feature vector files (`.ai-workspace/features/`)
- Code files (`# Implements: REQ-*` or `# Validates: REQ-*`)
- Design documents
- Test files
- Commit messages

### Step 2: Build Trajectory

Assemble the trajectory showing each graph node the REQ key touches.

### Step 3: Identify Gaps

Report any missing links:
```
GAPS:
  - No UAT test found for REQ-F-AUTH-001
  - No telemetry tagging for REQ-F-AUTH-001
```
