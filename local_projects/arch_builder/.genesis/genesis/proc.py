# Implements: REQ-ROBUST-001 (Actor Isolation), REQ-ROBUST-002 (Supervisor Pattern)
"""Total-function subprocess runner — every call returns within bounded time.

Contract:
  run_bounded(cmd, ...) → BoundedResult

Guarantees (the total-function invariants):
  1. Returns within wall_timeout + 10s — always, regardless of child behaviour
  2. Returns a structured BoundedResult — never raises (all exceptions caught)
  3. No orphan processes — process group killed on timeout, SIGKILL if SIGTERM ignored
  4. Stall detection based on actual output bytes — a process writing output is alive
     even if slow; a process that has gone silent for stall_timeout seconds is killed

This provides a single auditable subprocess primitive for all deterministic
checks in the engine.

Callers:
  fd_evaluate.run_check()   — deterministic checks (pytest, ruff, mypy, ...)
"""

import os
import signal
import subprocess
import sys
import threading
import time
from dataclasses import dataclass, field
from pathlib import Path


@dataclass
class BoundedResult:
    """Result of a bounded subprocess call — always populated, never missing."""

    stdout: str = ""
    stderr: str = ""
    returncode: int = -1
    # Termination cause — exactly one will be True on non-zero exit, or all False on success
    stall_killed: bool = False   # no output for stall_timeout seconds
    wall_killed: bool = False    # exceeded wall_timeout seconds
    # Convenience
    timed_out: bool = False      # stall_killed or wall_killed
    duration_ms: int = 0
    pid: int = 0
    error: str = ""              # human-readable termination reason


def run_bounded(
    cmd: list[str] | str,
    *,
    cwd: str | Path | None = None,
    env: dict[str, str] | None = None,
    shell: bool = False,
    wall_timeout: float = 300.0,
    stall_timeout: float = 60.0,
    heartbeat_interval: float = 10.0,
    heartbeat_label: str = "",
) -> BoundedResult:
    """Run a subprocess with hard bounded runtime — total function.

    Args:
        cmd:                Command list (or string if shell=True).
        cwd:                Working directory.
        env:                Environment dict. None = inherit current env.
        shell:              If True, run via shell (for shell commands like pytest).
        wall_timeout:       Absolute ceiling in seconds. Process is killed after this.
        stall_timeout:      Kill if no stdout/stderr bytes for this many seconds.
                            Set to 0 to disable (wall_timeout is still enforced).
        heartbeat_interval: Print liveness line to stderr every N seconds. 0 = silent.
        heartbeat_label:    Label shown in heartbeat lines (e.g. check name).

    Returns:
        BoundedResult — always. Never raises.

    Timing guarantee:
        Returns within wall_timeout + 10 seconds in all cases.
    """
    start = time.monotonic()
    result = BoundedResult()

    try:
        proc = subprocess.Popen(
            cmd,
            shell=shell,
            cwd=str(cwd) if cwd else None,
            env=env,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            start_new_session=True,  # own process group → group kill works
        )
    except OSError as exc:
        result.error = f"Failed to start: {exc}"
        result.duration_ms = int((time.monotonic() - start) * 1000)
        return result

    result.pid = proc.pid

    # ── Output reader threads ────────────────────────────────────────────────
    # read1() returns immediately with whatever bytes are available — no newline
    # wait. This is critical: pytest emits dots without newlines; line-iteration
    # would block. read1() gives true byte-level output detection.

    stdout_chunks: list[bytes] = []
    stderr_chunks: list[bytes] = []
    last_output_time = [start]  # mutable cell shared with watchdog

    def _reader(pipe, buf: list[bytes]) -> None:
        while True:
            try:
                chunk = pipe.read1(4096)  # type: ignore[attr-defined]
            except Exception:
                break
            if not chunk:
                break
            buf.append(chunk)
            last_output_time[0] = time.monotonic()
        try:
            pipe.close()
        except Exception:
            pass

    t_out = threading.Thread(target=_reader, args=(proc.stdout, stdout_chunks), daemon=True)
    t_err = threading.Thread(target=_reader, args=(proc.stderr, stderr_chunks), daemon=True)
    t_out.start()
    t_err.start()

    # ── Watchdog loop ────────────────────────────────────────────────────────

    kill_reason: str | None = None
    next_hb = start + heartbeat_interval if heartbeat_interval > 0 else float("inf")

    while proc.poll() is None:
        time.sleep(0.5)
        now = time.monotonic()
        elapsed = now - start
        silent = now - last_output_time[0]

        # Hard wall ceiling — always enforced
        if elapsed >= wall_timeout:
            kill_reason = "wall"
            _kill(proc)
            break

        # Output-based stall detection — only if enabled
        if stall_timeout > 0 and silent >= stall_timeout:
            kill_reason = "stall"
            _kill(proc)
            break

        # Heartbeat
        if heartbeat_label and now >= next_hb:
            print(
                f"  ⏱  [{heartbeat_label}] {int(elapsed)}s elapsed"
                f"  (last output {int(silent)}s ago)",
                file=sys.stderr,
                flush=True,
            )
            next_hb += heartbeat_interval

    # ── Drain and join ────────────────────────────────────────────────────────
    # Give reader threads up to 5s to flush remaining pipe data after process exit.
    t_out.join(timeout=5)
    t_err.join(timeout=5)

    # Ensure process is fully reaped (timeout in case of zombie)
    try:
        proc.wait(timeout=5)
    except subprocess.TimeoutExpired:
        _kill(proc)

    duration_ms = int((time.monotonic() - start) * 1000)
    stdout = b"".join(stdout_chunks).decode("utf-8", errors="replace")
    stderr = b"".join(stderr_chunks).decode("utf-8", errors="replace")
    returncode = proc.returncode if proc.returncode is not None else -1

    # Build result
    result.stdout = stdout
    result.stderr = stderr
    result.returncode = returncode
    result.duration_ms = duration_ms
    result.stall_killed = kill_reason == "stall"
    result.wall_killed = kill_reason == "wall"
    result.timed_out = kill_reason is not None

    if kill_reason == "stall":
        result.error = f"Stall: no output for {stall_timeout:.0f}s"
    elif kill_reason == "wall":
        result.error = f"Wall timeout: {wall_timeout:.0f}s exceeded"
    elif returncode != 0:
        result.error = f"Exit code {returncode}"

    return result


# ── Process group kill ────────────────────────────────────────────────────────


def _kill(proc: subprocess.Popen) -> None:
    """Kill process group — SIGTERM then SIGKILL after 3s."""
    try:
        pgid = os.getpgid(proc.pid)
        os.killpg(pgid, signal.SIGTERM)
        for _ in range(30):  # 3s in 0.1s steps
            if proc.poll() is not None:
                return
            time.sleep(0.1)
        os.killpg(pgid, signal.SIGKILL)
    except (ProcessLookupError, PermissionError, OSError):
        try:
            proc.kill()
        except (ProcessLookupError, OSError):
            pass
