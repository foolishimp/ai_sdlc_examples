# Implements: REQ-ITER-003 (Functor Encoding Tracking), REQ-EVAL-002 (Evaluator Composition), REQ-SUPV-001 (Heartbeat Observability)
"""F_D evaluate — run deterministic checks via subprocess.

Actor pattern:
  subprocess  = actor (isolated unit of work)
  engine      = supervisor (liveness monitor + circuit breaker)

Timeout semantics:
  stall_timeout  = kill if no stdout/stderr for N seconds  (DEFAULT: 60s)
                   Detects genuinely hung processes, not slow-but-healthy ones.
  wall_timeout   = absolute ceiling = stall_timeout * WALL_CEILING
                   Safety net for runaway processes with infinite output.

Heartbeat:
  Every HEARTBEAT_INTERVAL seconds, prints to stderr:
    ⏱  [check_name] 42s elapsed  (last output 3s ago)
  Visible during long test runs; JSON result still goes to stdout.
"""

import subprocess
from pathlib import Path

from .models import (
    CheckOutcome,
    CheckResult,
    EvaluationResult,
    ResolvedCheck,
)
from .proc import run_bounded

DEFAULT_TIMEOUT = 60  # stall timeout: kill if no output for N seconds
HEARTBEAT_INTERVAL = 10  # seconds between heartbeat lines on stderr
WALL_CEILING = 20  # wall_timeout = stall_timeout * WALL_CEILING


def run_check(
    check: ResolvedCheck, cwd: Path, timeout: int = DEFAULT_TIMEOUT
) -> CheckResult:
    """Run a single deterministic check with stall detection and heartbeat.

    Args:
        check:   Resolved check config.
        cwd:     Working directory for the subprocess.
        timeout: Stall timeout — kill if no stdout/stderr for this many seconds.
                 NOT a wall-clock timeout. A process producing output will run
                 to completion regardless of total elapsed time.

    Non-deterministic checks (agent, human) are returned as SKIP.
    Checks with unresolved $variables are returned as SKIP.
    """
    if check.check_type != "deterministic":
        return CheckResult(
            name=check.name,
            outcome=CheckOutcome.SKIP,
            required=check.required,
            check_type=check.check_type,
            functional_unit=check.functional_unit,
            message=f"Skipped: {check.check_type} check (not F_D)",
        )

    if check.unresolved:
        return CheckResult(
            name=check.name,
            outcome=CheckOutcome.SKIP,
            required=check.required,
            check_type=check.check_type,
            functional_unit=check.functional_unit,
            message=f"Skipped: unresolved variables: {', '.join(check.unresolved)}",
        )

    if not check.command:
        return CheckResult(
            name=check.name,
            outcome=CheckOutcome.SKIP,
            required=check.required,
            check_type=check.check_type,
            functional_unit=check.functional_unit,
            message="Skipped: no command specified",
        )

    wall_timeout = max(timeout * WALL_CEILING, 3600)  # at least 1h ceiling

    r = run_bounded(
        check.command,
        shell=True,
        cwd=cwd,
        wall_timeout=wall_timeout,
        stall_timeout=timeout,
        heartbeat_interval=HEARTBEAT_INTERVAL,
        heartbeat_label=check.name,
    )

    if r.stall_killed:
        return CheckResult(
            name=check.name,
            outcome=CheckOutcome.ERROR,
            required=check.required,
            check_type=check.check_type,
            functional_unit=check.functional_unit,
            message=f"Stall: no output for {timeout}s — process may be hung",
            command=check.command,
        )
    if r.wall_killed:
        return CheckResult(
            name=check.name,
            outcome=CheckOutcome.ERROR,
            required=check.required,
            check_type=check.check_type,
            functional_unit=check.functional_unit,
            message=f"Wall ceiling reached after {r.duration_ms // 1000}s (stall_timeout={timeout}s × {WALL_CEILING})",
            command=check.command,
        )
    if r.error and not r.stall_killed and not r.wall_killed and r.returncode == -1:
        return CheckResult(
            name=check.name,
            outcome=CheckOutcome.ERROR,
            required=check.required,
            check_type=check.check_type,
            functional_unit=check.functional_unit,
            message=r.error,
            command=check.command,
        )

    completed = subprocess.CompletedProcess(
        check.command,
        r.returncode,
        r.stdout,
        r.stderr,
    )
    outcome = _interpret_result(completed, check.pass_criterion)
    return CheckResult(
        name=check.name,
        outcome=outcome,
        required=check.required,
        check_type=check.check_type,
        functional_unit=check.functional_unit,
        message="" if outcome == CheckOutcome.PASS else r.stderr or r.stdout,
        command=check.command,
        exit_code=completed.returncode,
        stdout=r.stdout,
        stderr=r.stderr,
    )


def evaluate_checklist(
    checks: list[ResolvedCheck],
    cwd: Path,
    edge: str = "",
    timeout: int = DEFAULT_TIMEOUT,
) -> EvaluationResult:
    """Evaluate all checks in a checklist. Returns aggregate result with delta."""
    results = []
    escalations = []

    for check in checks:
        cr = run_check(check, cwd, timeout=timeout)
        results.append(cr)

        # η detection: deterministic check fails → candidate for escalation to F_P
        if (
            cr.check_type == "deterministic"
            and cr.outcome in (CheckOutcome.FAIL, CheckOutcome.ERROR)
            and cr.required
        ):
            escalations.append(f"η_D→P: {cr.name} — deterministic failure")

    delta = sum(
        1
        for cr in results
        if cr.required and cr.outcome in (CheckOutcome.FAIL, CheckOutcome.ERROR)
    )

    return EvaluationResult(
        edge=edge,
        checks=results,
        delta=delta,
        converged=(delta == 0),
        escalations=escalations,
    )



def _interpret_result(
    result: subprocess.CompletedProcess, pass_criterion: str | None
) -> CheckOutcome:
    """Interpret subprocess result against pass criterion."""
    if not pass_criterion or "exit code 0" in pass_criterion.lower():
        return CheckOutcome.PASS if result.returncode == 0 else CheckOutcome.FAIL

    criterion_lower = pass_criterion.lower()

    # "coverage percentage >= N" — parse number from stdout
    if "coverage" in criterion_lower and ">=" in criterion_lower:
        import re

        threshold_match = re.search(r">=\s*([\d.]+)", pass_criterion)
        if not threshold_match:
            return CheckOutcome.PASS if result.returncode == 0 else CheckOutcome.FAIL
        threshold = float(threshold_match.group(1))

        combined = result.stdout + result.stderr
        total_match = re.search(r"TOTAL\s+\d+\s+\d+\s+(\d+(?:\.\d+)?)%", combined)
        if not total_match:
            total_match = re.search(r"Total coverage:\s*(\d+(?:\.\d+)?)%", combined)
        if total_match:
            actual = float(total_match.group(1)) / 100.0
            return CheckOutcome.PASS if actual >= threshold else CheckOutcome.FAIL

    # "zero violations" / "zero errors"
    if "zero" in criterion_lower:
        return CheckOutcome.PASS if result.returncode == 0 else CheckOutcome.FAIL

    # Default: exit code 0
    return CheckOutcome.PASS if result.returncode == 0 else CheckOutcome.FAIL
