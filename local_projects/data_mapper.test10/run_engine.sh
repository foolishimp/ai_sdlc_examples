#!/usr/bin/env bash
# Run the genisis engine on test10 (CDME Scala/Spark)
# Can be run from inside Claude Code (strips CLAUDECODE env var) or a separate terminal.
#
# Usage:
#   cd /Users/jim/src/apps/ai_sdlc_examples/local_projects/data_mapper.test10
#   ./run_engine.sh [edge] [mode]
#
# Examples:
#   ./run_engine.sh                                    # construct intent→requirements
#   ./run_engine.sh "intent→requirements"              # same, explicit
#   ./run_engine.sh "requirements→design"              # next edge
#   ./run_engine.sh "intent→requirements" evaluate     # evaluate-only (no construct)

set -euo pipefail

WORKSPACE="/Users/jim/src/apps/ai_sdlc_examples/local_projects/data_mapper.test10"
ENGINE_CODE="/Users/jim/src/apps/ai_sdlc_method/imp_claude/code"
FEATURE="REQ-F-CDME-CORE"
MODEL="sonnet"
TIMEOUT=300

# Strip Claude Code env vars to allow nested claude -p calls
unset CLAUDECODE 2>/dev/null || true
unset CLAUDE_CODE_SSE_PORT 2>/dev/null || true
unset CLAUDE_CODE_ENTRYPOINT 2>/dev/null || true

EDGE="${1:-intent→requirements}"
MODE="${2:-construct}"

# Map edges to asset files and output paths
case "$EDGE" in
    "intent→requirements")
        ASSET="specification/INTENT.md"
        OUTPUT="imp_claude/design/REQUIREMENTS.md"
        ;;
    "requirements→design")
        ASSET="imp_claude/design/REQUIREMENTS.md"
        OUTPUT="imp_claude/design/DESIGN.md"
        ;;
    "design→code")
        ASSET="imp_claude/design/DESIGN.md"
        OUTPUT="imp_claude/code/src/main/scala/cdme/core/placeholder.scala"
        ;;
    "code↔unit_tests")
        ASSET="imp_claude/code/src/main/scala/cdme/core/placeholder.scala"
        OUTPUT="imp_claude/code/src/test/scala/cdme/core/placeholder_test.scala"
        ;;
    *)
        echo "Unknown edge: $EDGE"
        echo "Known edges: intent→requirements, requirements→design, design→code, code↔unit_tests"
        exit 1
        ;;
esac

cd "$WORKSPACE"
export PYTHONPATH="$ENGINE_CODE"

echo "═══════════════════════════════════════════"
echo "GENISIS ENGINE — test10 CDME (Scala/Spark)"
echo "═══════════════════════════════════════════"
echo "Edge:     $EDGE"
echo "Feature:  $FEATURE"
echo "Asset:    $ASSET"
echo "Output:   $OUTPUT"
echo "Mode:     $MODE"
echo "Model:    $MODEL"
echo "═══════════════════════════════════════════"
echo ""

if [ "$MODE" = "construct" ]; then
    echo "Running: construct (F_P builds + F_D gates)"
    python -m genisis construct \
        --edge "$EDGE" \
        --feature "$FEATURE" \
        --asset "$ASSET" \
        --output "$OUTPUT" \
        --model "$MODEL" \
        --timeout "$TIMEOUT" \
        --workspace "$WORKSPACE"
elif [ "$MODE" = "run-edge" ]; then
    echo "Running: run-edge (loop until converge/spawn/budget)"
    python -m genisis run-edge \
        --edge "$EDGE" \
        --feature "$FEATURE" \
        --asset "$ASSET" \
        --output "$OUTPUT" \
        --construct \
        --model "$MODEL" \
        --timeout "$TIMEOUT" \
        --max-iterations 5 \
        --workspace "$WORKSPACE"
elif [ "$MODE" = "evaluate" ]; then
    echo "Running: evaluate-only (no construct)"
    python -m genisis evaluate \
        --edge "$EDGE" \
        --feature "$FEATURE" \
        --asset "$ASSET" \
        --model "$MODEL" \
        --timeout "$TIMEOUT" \
        --workspace "$WORKSPACE"
else
    echo "Unknown mode: $MODE (use: construct, run-edge, evaluate)"
    exit 1
fi

echo ""
echo "═══════════════════════════════════════════"
echo "Done. Check events: cat .ai-workspace/events/events.jsonl | python -m json.tool"
echo "═══════════════════════════════════════════"
