# Todo Dashboard — Genesis Demo Project

This directory is the **persistent observable workspace** for the Genesis Monitor UAT tests.

## Purpose

- **Observable in Genesis Monitor**: genesis_monitor watches the parent directory so this
  project always appears in the browser at `http://localhost:8000`.
- **Full lifecycle recorded**: `.ai-workspace/events/events.jsonl` contains all 17 events
  for a complete intent→requirements→design→code↔unit_tests journey.
- **Temporal scrubber enabled**: Events are timestamped so you can drag the scrubber
  and watch each edge converge in sequence.

## How to observe

1. Start Genesis Monitor: `python -m genesis_monitor --watch-dir /Users/jim/src/apps`
2. Open `http://localhost:8000` — find `todo-demo` in the project tree
3. Click to open the project detail dashboard
4. Drag the temporal scrubber left to replay the lifecycle from the beginning

## UAT Tests

The test suite at
`genisis_monitor/imp_python_fastapi/tests/test_uat_todo_lifecycle.py`
drives data into this workspace. Run:

```bash
cd genisis_monitor/imp_python_fastapi
pytest tests/test_uat_todo_lifecycle.py -v
```

The committed `events.jsonl` represents the final converged state.
During test runs, the workspace is rebuilt incrementally.

## Lifecycle Summary

| Timestamp | Event |
|-----------|-------|
| T+08:00 | project_initialized |
| T+08:05 | spawn_created (REQ-F-TODO-001) |
| T+08:10 | edge_started (intent→requirements) |
| T+08:30 | iteration_completed δ=1 (human review pending) |
| T+08:45 | review_completed (refined: add priority + ISO dates) |
| T+09:00 | iteration_completed δ=0 ✓ |
| T+09:00 | edge_converged (intent→requirements) |
| T+09:05 | edge_started (requirements→design) |
| T+09:30 | iteration_completed δ=1 (human review pending) |
| T+09:45 | review_completed (approved) |
| T+09:50 | iteration_completed δ=0 ✓ |
| T+09:50 | edge_converged (requirements→design) |
| T+09:55 | edge_started (code↔unit_tests) |
| T+10:15 | iteration_completed δ=3 ✗ (coverage 62%, lint, missing REQ tag) |
| T+10:30 | review_completed (refined: fix coverage + lint + tag routes.py) |
| T+10:45 | iteration_completed δ=0 ✓ (coverage 84%, all checks pass) |
| T+10:45 | edge_converged (code↔unit_tests) — ALL DONE |
