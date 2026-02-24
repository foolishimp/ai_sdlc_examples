#!/bin/bash
# ============================================================================
# on-iterate-start.sh — UserPromptSubmit hook for /gen-iterate
#
# Fires when user submits a prompt containing "gen-iterate".
# Records edge context so the Stop hook can verify protocol completion.
#
# Implements: REQ-TOOL-006 (Methodology Hooks), Protocol enforcement (Layer 1 — Engine)
# Processing regime: REFLEX (§4.3) — fires unconditionally at prompt
# boundary, no judgment required.
# ============================================================================

set -euo pipefail

INPUT=$(cat)
CWD=$(echo "$INPUT" | jq -r '.cwd // empty')

if [ -z "$CWD" ]; then
  exit 0
fi

WORKSPACE="$CWD/.ai-workspace"

# Only activate if .ai-workspace exists (project is initialised)
if [ ! -d "$WORKSPACE" ]; then
  exit 0
fi

# -----------------------------------------------------------------------
# Build valid asset type list from graph topology (topology-aware)
# Checks workspace copy first (project override), then plugin source.
# -----------------------------------------------------------------------
TOPOLOGY=""
if [ -f "$WORKSPACE/graph/graph_topology.yml" ]; then
  TOPOLOGY="$WORKSPACE/graph/graph_topology.yml"
elif [ -n "${CLAUDE_PLUGIN_ROOT:-}" ] && [ -f "${CLAUDE_PLUGIN_ROOT}/config/graph_topology.yml" ]; then
  TOPOLOGY="${CLAUDE_PLUGIN_ROOT}/config/graph_topology.yml"
fi

if [ -n "$TOPOLOGY" ]; then
  # Extract asset type keys: lines matching "^  <key>:" under asset_types section
  # Keys may contain letters, digits, hyphens, underscores (e.g. design2, design-v2)
  ASSET_TYPES=$(awk '/^asset_types:/{found=1; next} found && /^[^ ]/{exit} found && /^  [a-zA-Z][a-zA-Z0-9_-]*:/{gsub(/^  |:.*/, ""); print}' "$TOPOLOGY" 2>/dev/null | paste -sd'|' - || true)
fi

# Fallback: if topology parse failed or file not found, use known defaults
if [ -z "${ASSET_TYPES:-}" ]; then
  ASSET_TYPES="intent|requirements|design|module_decomp|basis_projections|code|unit_tests|uat_tests|test_cases|cicd|running_system|telemetry"
fi

# Extract edge from the prompt (best-effort parse)
# Matches patterns like: --edge "intent→requirements" or --edge "design→code"
PROMPT=$(echo "$INPUT" | jq -r '.prompt // empty')
EDGE=$(echo "$PROMPT" | grep -oE "(${ASSET_TYPES})[→↔](${ASSET_TYPES})" 2>/dev/null | head -1 || true)

if [ -z "$EDGE" ]; then
  # Could not parse edge — don't set context, allow through
  exit 0
fi

# Record edge traversal state
mkdir -p "$WORKSPACE/events"
echo "$EDGE" > "$WORKSPACE/.edge_in_progress"
date +%s > "$WORKSPACE/.edge_start_time"

# Snapshot current events.jsonl line count for delta check
if [ -f "$WORKSPACE/events/events.jsonl" ]; then
  wc -l < "$WORKSPACE/events/events.jsonl" | tr -d ' ' > "$WORKSPACE/.edge_events_baseline"
else
  echo "0" > "$WORKSPACE/.edge_events_baseline"
fi

# Inject context for Claude — stdout goes to the agent
cat <<EOF
Edge traversal started: $EDGE
Protocol requirements (enforced by Stop hook):
  1. Emit event to .ai-workspace/events/events.jsonl (every iteration)
  2. Update feature vector in .ai-workspace/features/active/
  3. Regenerate .ai-workspace/STATUS.md
  4. Show iteration report to user
  5. Record source_findings (backward) and process_gaps (inward) in event
EOF

exit 0
