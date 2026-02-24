#!/bin/bash
# ============================================================================
# on-artifact-written.sh — PostToolUse hook for artifact write observation
#
# Fires after every Write|Edit tool call. Detects artifact writes and emits
# lightweight events for real-time progress tracking and audit.
#
# This is the invariant-observation end of the observability sliding scale
# (§7.7.5). It never blocks, never runs evaluators, and fails silently.
#
# Implements: REQ-SENSE-006, REQ-TOOL-006
# Processing regime: REFLEX (§4.3) — fires unconditionally, observation-only
# Reference: ADR-CG-006 (Artifact Write Observation)
# ============================================================================

# Never block — trap all errors and exit 0
trap 'exit 0' ERR EXIT

INPUT=$(cat)

# Extract fields from PostToolUse hook input
TOOL_NAME=$(echo "$INPUT" | jq -r '.tool_name // empty' 2>/dev/null)
FILE_PATH=$(echo "$INPUT" | jq -r '.tool_input.file_path // empty' 2>/dev/null)
CWD=$(echo "$INPUT" | jq -r '.cwd // empty' 2>/dev/null)

# Bail if missing required data
if [ -z "$TOOL_NAME" ] || [ -z "$FILE_PATH" ] || [ -z "$CWD" ]; then
  exit 0
fi

WORKSPACE="$CWD/.ai-workspace"

# Only activate if .ai-workspace exists (project is initialised)
if [ ! -d "$WORKSPACE" ]; then
  exit 0
fi

# -----------------------------------------------------------------------
# Exclusion: non-artifact paths
# -----------------------------------------------------------------------

# Make path relative to CWD for pattern matching
REL_PATH="${FILE_PATH#$CWD/}"

# Skip workspace internals, git, node_modules
case "$REL_PATH" in
  .ai-workspace/*|.git/*|node_modules/*|.claude/*) exit 0 ;;
esac

# Skip infrastructure files
case "$(basename "$REL_PATH")" in
  CLAUDE.md|.gitignore|.gitattributes|pyproject.toml|setup.py|setup.cfg|\
  package.json|package-lock.json|tsconfig.json|Makefile|Dockerfile|\
  docker-compose.yml|.env|.env.*|requirements.txt|Cargo.toml|go.mod|\
  README.md|LICENSE|*.lock) exit 0 ;;
esac

# -----------------------------------------------------------------------
# Multi-tenant: strip imp_<name>/ prefix for asset type mapping
# -----------------------------------------------------------------------
MAPPED_PATH=$(echo "$REL_PATH" | sed -E 's|^imp_[^/]+/||')

# -----------------------------------------------------------------------
# Asset type mapping from directory structure
# -----------------------------------------------------------------------
ASSET_TYPE=""
case "$MAPPED_PATH" in
  specification/*|spec/*)
    ASSET_TYPE="requirements"
    ;;
  design/adrs/*|design/*)
    ASSET_TYPE="design"
    ;;
  tests/e2e/*|tests/uat/*)
    ASSET_TYPE="uat_tests"
    ;;
  tests/*test*|tests/*)
    ASSET_TYPE="unit_tests"
    ;;
  code/*|src/*|lib/*)
    ASSET_TYPE="code"
    ;;
esac

# If no asset type matched, skip (not a methodology-managed artifact)
if [ -z "$ASSET_TYPE" ]; then
  exit 0
fi

# -----------------------------------------------------------------------
# Emit artifact_modified event
# -----------------------------------------------------------------------
EVENTS_FILE="$WORKSPACE/events/events.jsonl"

# Ensure events directory exists
mkdir -p "$(dirname "$EVENTS_FILE")"

TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%S.000Z")
PROJECT=$(basename "$CWD")

EVENT=$(jq -nc \
  --arg ts "$TIMESTAMP" \
  --arg proj "$PROJECT" \
  --arg fp "$REL_PATH" \
  --arg at "$ASSET_TYPE" \
  --arg tool "$TOOL_NAME" \
  '{
    event_type: "artifact_modified",
    timestamp: $ts,
    project: $proj,
    file_path: $fp,
    asset_type: $at,
    tool: $tool
  }')

echo "$EVENT" >> "$EVENTS_FILE"

# -----------------------------------------------------------------------
# First-write detection: emit edge_started on first write to new asset type
# -----------------------------------------------------------------------
SESSION_FILE="$WORKSPACE/.session_assets_seen"

# Check if we've seen this asset type in this session
if [ ! -f "$SESSION_FILE" ] || ! grep -q "^${ASSET_TYPE}$" "$SESSION_FILE" 2>/dev/null; then
  # Record this asset type as seen
  echo "$ASSET_TYPE" >> "$SESSION_FILE"

  # Infer edge from asset type using graph topology if available
  EDGE=""
  TOPO_FILE="$WORKSPACE/graph/graph_topology.yml"
  if [ -f "$TOPO_FILE" ]; then
    # Find a transition targeting this asset type
    EDGE=$(awk -v target="$ASSET_TYPE" '
      /^  - source:/ { src = $3 }
      /target:/ { if ($2 == target) print src "→" target }
    ' "$TOPO_FILE" | head -1)
  fi

  # Emit edge_started event
  EDGE_EVENT=$(jq -nc \
    --arg ts "$TIMESTAMP" \
    --arg proj "$PROJECT" \
    --arg edge "${EDGE:-unknown→$ASSET_TYPE}" \
    --arg trigger "artifact_write_detected" \
    --arg fp "$REL_PATH" \
    '{
      event_type: "edge_started",
      timestamp: $ts,
      project: $proj,
      edge: $edge,
      trigger: $trigger,
      first_file: $fp
    }')

  echo "$EDGE_EVENT" >> "$EVENTS_FILE"
fi

exit 0
