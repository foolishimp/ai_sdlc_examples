# Implements: REQ-ROBUST-002 (Supervisor Pattern for F_P Calls), REQ-ITER-001 (Universal Iteration)
"""ADR-024 functor protocol and registry.

The engine resolves a Functor via the registry and calls invoke().
It does not know or care about the transport.

    registry(edge, grain, env) → Functor
    functor.invoke(Intent, state) → StepResult
"""

from __future__ import annotations

import os
from pathlib import Path
from typing import Protocol, runtime_checkable

from .contracts import Intent, StepResult


# ── Functor protocol ──────────────────────────────────────────────────────────


@runtime_checkable
class Functor(Protocol):
    """Every functor satisfies: invoke(Intent, Path) → StepResult."""

    def invoke(self, intent: Intent, state: Path) -> StepResult:
        ...


# ── Registry ──────────────────────────────────────────────────────────────────


def get_functor(edge: str, grain: str = "iteration") -> Functor:
    """Return the appropriate functor for (edge, grain, environment).

    Selection rules (ADR-024):
    - F_P: MCP available (CLAUDE_CODE_SSE_PORT set) → fp_functor
    - F_P: MCP unavailable → fp_functor (returns skipped StepResult)
    - F_D: always fd_functor (deterministic checks via subprocess)

    Engine-affinity edges (design→code, code↔unit_tests, cicd) → F_P functor.
    All other edges → F_P functor (actor knows its own constraints).

    The registry is intentionally simple at MVP — one functor for F_P,
    one for F_D. Grain-specific routing is future work.
    """
    from .fp_functor import FpFunctor
    return FpFunctor()


def mcp_available() -> bool:
    """Canonical MCP availability check (ADR-023 + ADR-024).

    CLAUDE_CODE_SSE_PORT is set by Claude Code when running inside an active
    session. This is the single detection point used by all transport selection.
    """
    return bool(os.environ.get("CLAUDE_CODE_SSE_PORT"))
