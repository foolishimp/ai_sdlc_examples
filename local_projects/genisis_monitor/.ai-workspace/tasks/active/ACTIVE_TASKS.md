# Active Tasks — Genesis Monitor

## Completed (v1.0 — INT-GMON-001/002/003)

| # | Title | Status | Priority |
|---|-------|--------|----------|
| 1 | Write INTENT.md | completed | critical |
| 2 | Derive REQUIREMENTS.md from intent | completed | critical |
| 3 | Derive DESIGN.md from requirements | completed | critical |
| 4 | Implement code from design | completed | critical |
| 5 | Write unit tests | completed | critical |
| 6 | Write UAT/integration tests | completed | critical |

## Active (v2.0 — INT-GMON-004: v2.5 Alignment)

| # | Title | Status | Feature Vector | Priority |
|---|-------|--------|----------------|----------|
| 7 | Update INTENT.md with INT-GMON-004 | completed | - | critical |
| 8 | Update REQUIREMENTS.md (17 new REQs) | completed | - | critical |
| 9 | Update DESIGN.md (models, parsers, projections, routes) | completed | - | critical |
| 10 | Create feature vectors (REQ-F-GMON-002, 003) | completed | - | critical |
| 11 | Implement REQ-F-GMON-002: models + parsers | not_started | REQ-F-GMON-002 | critical |
| 12 | Implement REQ-F-GMON-003: projections + dashboard | not_started | REQ-F-GMON-003 | high |

## Active (v3.1 — INT-GMON-009: Multi-Design Tenant Observability)

| # | Title | Status | Feature Vector | Priority |
|---|-------|--------|----------------|----------|
| 13 | Add INT-GMON-009 to INTENT.md | completed | REQ-F-MTEN-001 | high |
| 14 | Add REQ-F-MTEN-001/002/003 to REQUIREMENTS.md | completed | REQ-F-MTEN-001 | high |
| 15 | Create ADR-004: tenant switching + filter propagation | completed | REQ-F-MTEN-001 | high |
| 16 | Implement design selector nav (project.html) | completed | REQ-F-MTEN-001 | high |
| 17 | Implement HTMX configRequest interceptor (project.html) | completed | REQ-F-MTEN-001 | high |
| 18 | Fix triggerReload() to preserve ?design= in scrubber | completed | REQ-F-MTEN-001 | high |
| 19 | Fix _get_historical_state design-only bug (routes.py) | completed | REQ-F-MTEN-001 | high |
| 20 | Fix PhaseEntry delta_curve kwarg bug (temporal.py) | completed | REQ-F-MTEN-001 | high |
| 21 | Add TestDesignTenancy (6 tests) + multi-tenant fixture events | completed | REQ-F-MTEN-001 | high |
| 22 | Create feature vector REQ-F-MTEN-001.yml | completed | REQ-F-MTEN-001 | medium |

## Dependency Graph

```
REQ-F-GMON-002 (models + parsers)
    └──► REQ-F-GMON-003 (projections + dashboard)

INT-GMON-009 (multi-tenant)
    └──► REQ-F-MTEN-001 (filter + selector + propagation) — CONVERGED
```

REQ-F-GMON-002 must converge before REQ-F-GMON-003 can begin code iteration.
