#!/bin/bash
# ============================================================================
# on-stop-check-protocol.sh — Stop hook for iterate protocol enforcement
#
# Fires when Claude attempts to stop responding. If an edge traversal is
# in progress, verifies that all mandatory side effects were completed.
# Blocks if incomplete; allows on second attempt (stop_hook_active=true).
#
# This is the DETERMINISTIC EVALUATOR of the iterate protocol itself.
# The methodology has three evaluator types for artifacts (Human, Agent,
# Deterministic). This hook adds protocol enforcement — a deterministic
# check that iterate()'s mandatory side effects were completed.
#
# Processing regime: REFLEX (§4.3) — fires unconditionally at every stop
# boundary. This is the autonomic nervous system verifying that the
# mandatory reflexes (event emission, feature vector update, STATUS
# regeneration) actually fired during the edge traversal.
#
# Implements: REQ-TOOL-006 (Methodology Hooks), REQ-LIFE-008 (Protocol Enforcement Hooks), Protocol enforcement (Layer 1 — Engine)
# ============================================================================

set -euo pipefail

INPUT=$(cat)

# -----------------------------------------------------------------------
# EXIT CONDITION: prevent infinite regression
# On the second Stop attempt, stop_hook_active=true. Allow through.
# This is the circuit breaker — ensures the agent can always eventually stop.
# -----------------------------------------------------------------------
STOP_ACTIVE=$(echo "$INPUT" | jq -r '.stop_hook_active // "false"')
if [ "$STOP_ACTIVE" = "true" ]; then
  # Clean up state files and allow stop
  CWD=$(echo "$INPUT" | jq -r '.cwd // empty')
  if [ -n "$CWD" ]; then
    rm -f "$CWD/.ai-workspace/.edge_in_progress" \
          "$CWD/.ai-workspace/.edge_start_time" \
          "$CWD/.ai-workspace/.edge_events_baseline"
  fi
  exit 0
fi

# -----------------------------------------------------------------------
# Check if we're in an edge traversal
# -----------------------------------------------------------------------
CWD=$(echo "$INPUT" | jq -r '.cwd // empty')
if [ -z "$CWD" ]; then
  exit 0
fi

WORKSPACE="$CWD/.ai-workspace"
EDGE_FILE="$WORKSPACE/.edge_in_progress"

# Not in an edge traversal — allow stop
if [ ! -f "$EDGE_FILE" ]; then
  exit 0
fi

EDGE=$(cat "$EDGE_FILE")
START_TIME=$(cat "$WORKSPACE/.edge_start_time" 2>/dev/null || echo "0")
BASELINE=$(cat "$WORKSPACE/.edge_events_baseline" 2>/dev/null || echo "0")

MISSING=""

# -----------------------------------------------------------------------
# Check 1: events.jsonl was appended (at least 1 new event)
# -----------------------------------------------------------------------
EVENTS_FILE="$WORKSPACE/events/events.jsonl"
if [ -f "$EVENTS_FILE" ]; then
  CURRENT_LINES=$(wc -l < "$EVENTS_FILE" | tr -d ' ')
  if [ "$CURRENT_LINES" -le "$BASELINE" ]; then
    MISSING="$MISSING- events.jsonl: no new event emitted (was $BASELINE lines, still $BASELINE)\n"
  fi
else
  MISSING="$MISSING- events.jsonl: file does not exist\n"
fi

# -----------------------------------------------------------------------
# Check 2: Feature vector updated (any .yml in features/active/ modified
#           after edge start time)
# -----------------------------------------------------------------------
FEATURE_UPDATED=false
if [ -d "$WORKSPACE/features/active" ]; then
  for f in "$WORKSPACE/features/active"/*.yml; do
    if [ -f "$f" ]; then
      FILE_MTIME=$(stat -f%m "$f" 2>/dev/null || stat -c%Y "$f" 2>/dev/null || echo "0")
      if [ "$FILE_MTIME" -ge "$START_TIME" ]; then
        FEATURE_UPDATED=true
        break
      fi
    fi
  done
fi
if [ "$FEATURE_UPDATED" = false ]; then
  # Also check features/ root level (some projects put vectors there directly)
  for f in "$WORKSPACE/features"/*.yml; do
    if [ -f "$f" ] && [ "$(basename "$f")" != "feature_vector_template.yml" ] && [ "$(basename "$f")" != "feature_index.yml" ]; then
      FILE_MTIME=$(stat -f%m "$f" 2>/dev/null || stat -c%Y "$f" 2>/dev/null || echo "0")
      if [ "$FILE_MTIME" -ge "$START_TIME" ]; then
        FEATURE_UPDATED=true
        break
      fi
    fi
  done
fi
if [ "$FEATURE_UPDATED" = false ]; then
  MISSING="$MISSING- Feature vector: not updated since edge started\n"
fi

# -----------------------------------------------------------------------
# Check 3: STATUS.md regenerated
# -----------------------------------------------------------------------
STATUS_FILE="$WORKSPACE/STATUS.md"
if [ -f "$STATUS_FILE" ]; then
  STATUS_MTIME=$(stat -f%m "$STATUS_FILE" 2>/dev/null || stat -c%Y "$STATUS_FILE" 2>/dev/null || echo "0")
  if [ "$STATUS_MTIME" -lt "$START_TIME" ]; then
    MISSING="$MISSING- STATUS.md: not regenerated since edge started\n"
  fi
else
  MISSING="$MISSING- STATUS.md: file does not exist\n"
fi

# -----------------------------------------------------------------------
# Check 4: Latest edge_iteration_completed event has source_findings and process_gaps
# (Filter by event type to avoid false blocks from spec_modified or other event types)
# -----------------------------------------------------------------------
if [ -f "$EVENTS_FILE" ]; then
  LAST_EDGE_EVENT=$(grep '"iteration_completed"' "$EVENTS_FILE" 2>/dev/null || true)
  if [ -n "$LAST_EDGE_EVENT" ]; then
    LAST_EDGE_EVENT=$(echo "$LAST_EDGE_EVENT" | tail -1)
  else
    # No iteration_completed event — check the latest event as fallback
    LAST_EDGE_EVENT=$(tail -1 "$EVENTS_FILE" 2>/dev/null || true)
  fi
  HAS_FINDINGS=$(echo "$LAST_EDGE_EVENT" | jq 'has("source_findings")' 2>/dev/null || echo "false")
  HAS_GAPS=$(echo "$LAST_EDGE_EVENT" | jq 'has("process_gaps")' 2>/dev/null || echo "false")
  if [ "$HAS_FINDINGS" != "true" ]; then
    MISSING="$MISSING- source_findings: not present in latest edge event (backward gap detection missing)\n"
  fi
  if [ "$HAS_GAPS" != "true" ]; then
    MISSING="$MISSING- process_gaps: not present in latest edge event (inward gap detection missing)\n"
  fi
fi

# -----------------------------------------------------------------------
# Decision
# -----------------------------------------------------------------------
if [ -n "$MISSING" ]; then
  # Block — protocol incomplete
  REASON=$(printf "Edge traversal '$EDGE' protocol incomplete.\n\nMissing mandatory side effects:\n$MISSING\nComplete these before finishing. The iterate protocol requires:\n1. Emit event to events.jsonl (with source_findings + process_gaps)\n2. Update the feature vector trajectory\n3. Regenerate STATUS.md\n\nIf you cannot complete these, explain why. The hook will allow stop on the next attempt.")

  jq -n --arg reason "$REASON" '{"decision":"block","reason":$reason}'
  exit 0
else
  # All checks pass — clean up and allow stop
  rm -f "$EDGE_FILE" \
        "$WORKSPACE/.edge_start_time" \
        "$WORKSPACE/.edge_events_baseline"
  exit 0
fi
