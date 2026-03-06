# Implements: REQ-ROBUST-002 (Supervisor Pattern for F_P Calls), REQ-ITER-001 (Universal Iteration)
"""ADR-024 contracts — typed interface for functor invocation.

Intent → invoke() → StepResult

These dataclasses are the shared vocabulary between the engine (F_D supervisor)
and functors (F_P actor, F_D checks, F_H human). The engine does not know the
transport; functors do not know the iteration loop.
"""

from __future__ import annotations

import uuid
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any


# ── Intent ────────────────────────────────────────────────────────────────────


@dataclass
class Intent:
    """Parameterised operation — what to do, on what, against what constraints.

    budget_usd:       cost cap → passed as --max-budget-usd to actor. NOT a timeout.
    max_depth:        spawn depth limit — recursion structure bound. NOT cost.
    wall_timeout_ms:  hard wall clock — engine kills the invocation after this.
    stall_timeout_ms: silence threshold — engine kills after no liveness signal.
    """

    edge: str
    feature: str
    grain: str = "iteration"              # "iteration" | "edge" | "feature"
    constraints: dict[str, Any] = field(default_factory=dict)
    context: list[str] = field(default_factory=list)  # serialised Asset blobs
    failures: list[str] = field(default_factory=list)  # F_D failure messages
    budget_usd: float = 2.0
    max_depth: int = 3
    wall_timeout_ms: int = 300_000        # 5 min
    stall_timeout_ms: int = 60_000        # 1 min silence
    run_id: str = field(default_factory=lambda: str(uuid.uuid4()))


# ── StepResult components ─────────────────────────────────────────────────────


@dataclass
class VersionedArtifact:
    """A file that was written during the step, with before/after hashes."""

    path: str
    content_hash: str    # sha256 of output — written to COMPLETE event outputs[]
    previous_hash: str   # sha256 of input before modification


@dataclass
class SpawnRecord:
    """A child unit of work spawned during the step."""

    child_run_id: str
    feature: str
    edge: str
    reason: str


@dataclass
class StepAudit:
    """Execution metadata for observability."""

    functor_type: str          # "F_D" | "F_P" | "F_H"
    transport: str             # "subprocess" | "mcp" | "api" | "human" | "none"
    skipped: bool = False
    stall_killed: bool = False
    budget_capped: bool = False
    exit_code: int | None = None


# ── StepResult ────────────────────────────────────────────────────────────────


@dataclass
class StepResult:
    """Outcome of a single functor invocation.

    run_id matches intent.run_id — links to the OL START/COMPLETE events.
    artifacts populates COMPLETE event outputs[] (ADR-S-015).
    spawns encodes child units of work (ADR-S-017 zoom-in).
    """

    run_id: str
    converged: bool
    delta: int                              # 0 = converged, >0 = failures remaining, -1 = skipped
    artifacts: list[VersionedArtifact] = field(default_factory=list)
    spawns: list[SpawnRecord] = field(default_factory=list)
    cost_usd: float = 0.0
    duration_ms: int = 0
    audit: StepAudit = field(default_factory=lambda: StepAudit(functor_type="F_P", transport="none"))
    workspace: Path | None = None
