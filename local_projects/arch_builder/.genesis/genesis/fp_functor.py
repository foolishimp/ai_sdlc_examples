# Implements: REQ-ROBUST-001 (Actor Isolation), REQ-ROBUST-002 (Supervisor Pattern for F_P Calls), REQ-ITER-001 (Universal Iteration)
"""ADR-024 F_P functor — invoke actor via MCP with constrained Intent.

If MCP unavailable → return StepResult(converged=False, delta=-1, skipped=True).
No subprocess fallback. No claude -p. Ever.

The actor:
  1. Receives the Intent (edge, feature, failures, constraints, budget_usd)
  2. Runs iterate() autonomously at grain="iteration"
  3. Uses full tool access to read files, modify code, run tests, verify output
  4. Self-evaluates against edge criteria
  5. Returns StepResult via fold-back to the engine

The engine's role after actor invocation: run F_D checks only.
"""

from __future__ import annotations

import json
import time
from pathlib import Path

from .contracts import Intent, SpawnRecord, StepAudit, StepResult, VersionedArtifact
from .functor import mcp_available


class FpFunctor:
    """F_P functor implementation — MCP actor invocation."""

    def invoke(self, intent: Intent, state: Path) -> StepResult:
        """Invoke the F_P actor via MCP.

        If MCP is unavailable, returns a skipped StepResult immediately.
        The engine continues with F_D results only — this is not a degraded
        mode, it is the F_D-only evaluation path (ADR-019).
        """
        if not mcp_available():
            return StepResult(
                run_id=intent.run_id,
                converged=False,
                delta=-1,
                workspace=state,
                audit=StepAudit(
                    functor_type="F_P",
                    transport="none",
                    skipped=True,
                ),
            )

        prompt = _build_actor_prompt(intent, state)
        t0 = time.monotonic()

        try:
            raw = _mcp_invoke(prompt, state, intent)
            duration_ms = int((time.monotonic() - t0) * 1000)
            return _parse_actor_result(intent, raw, duration_ms, state)
        except Exception as exc:  # noqa: BLE001
            # Result not yet available (actor pending or fold-back missing).
            # skipped=True: orchestrator treats this as "no result" not a failure.
            # delta=-1 is the sentinel for "no measurement taken".
            duration_ms = int((time.monotonic() - t0) * 1000)
            return StepResult(
                run_id=intent.run_id,
                converged=False,
                delta=-1,
                duration_ms=duration_ms,
                workspace=state,
                audit=StepAudit(
                    functor_type="F_P",
                    transport="mcp",
                    skipped=True,
                    stall_killed=False,
                ),
            )


# ── Internal helpers ──────────────────────────────────────────────────────────


def _build_actor_prompt(intent: Intent, workspace: Path) -> str:
    """Build the mandate delivered to the actor."""
    failures_section = ""
    if intent.failures:
        failures_section = "\n\n## Prior F_D Failures\n" + "\n".join(
            f"- {f}" for f in intent.failures
        )

    constraints_section = ""
    if intent.constraints:
        constraints_section = "\n\n## Constraints\n" + "\n".join(
            f"- {k}: {v}" for k, v in intent.constraints.items()
        )

    return f"""# Actor Mandate

**Edge**: {intent.edge}
**Feature**: {intent.feature}
**Grain**: {intent.grain}
**Run ID**: {intent.run_id}
**Workspace**: {workspace}
**Budget**: ${intent.budget_usd:.2f}
**Max Depth**: {intent.max_depth}{failures_section}{constraints_section}

## Your Task

You are a recursive actor. Iterate on the above edge until the F_D checklist
passes. Use your full tool access — read files, write code, run tests.

When done, emit a JSON result to stdout:
```json
{{
  "converged": true|false,
  "delta": <int>,
  "cost_usd": <float>,
  "artifacts": [{{"path": "<str>", "content_hash": "<sha256>", "previous_hash": "<sha256>"}}],
  "spawns": []
}}
```
"""


def _mcp_invoke(prompt: str, workspace: Path, intent: Intent) -> dict:
    """Invoke the actor via MCP claude_code tool.

    At MVP this is a stub — the actual MCP tool call is issued by the
    calling Claude session's gen-iterate skill, not by Python code.
    The engine records intent and waits for the result to be injected
    via the fold-back mechanism (StepResult written to workspace state).

    When the full MCP client library is available, this will issue:
        mcp_client.call_tool("claude_code", {
            "prompt": prompt,
            "model": "claude-sonnet-4-6",
            "max_budget_usd": intent.budget_usd,
        })
    """
    # MVP stub: check if a fold-back result exists in workspace state
    result_path = workspace / ".ai-workspace" / "agents" / f"fp_result_{intent.run_id}.json"
    if result_path.exists():
        return json.loads(result_path.read_text())

    # No result yet — raise so caller returns a skipped StepResult
    raise RuntimeError(f"MCP actor result not yet available (run_id={intent.run_id})")


def _parse_actor_result(
    intent: Intent, raw: dict, duration_ms: int, workspace: Path
) -> StepResult:
    """Parse the actor's fold-back result into a StepResult."""
    artifacts = [
        VersionedArtifact(
            path=a["path"],
            content_hash=a.get("content_hash", ""),
            previous_hash=a.get("previous_hash", ""),
        )
        for a in raw.get("artifacts", [])
    ]
    spawns = [
        SpawnRecord(
            child_run_id=s.get("child_run_id", ""),
            feature=s.get("feature", intent.feature),
            edge=s.get("edge", intent.edge),
            reason=s.get("reason", ""),
        )
        for s in raw.get("spawns", [])
    ]
    return StepResult(
        run_id=intent.run_id,
        converged=raw.get("converged", False),
        delta=raw.get("delta", -1),
        artifacts=artifacts,
        spawns=spawns,
        cost_usd=raw.get("cost_usd", 0.0),
        duration_ms=duration_ms,
        workspace=workspace,
        audit=StepAudit(
            functor_type="F_P",
            transport="mcp",
            skipped=False,
            budget_capped=raw.get("cost_usd", 0.0) >= intent.budget_usd,
        ),
    )
