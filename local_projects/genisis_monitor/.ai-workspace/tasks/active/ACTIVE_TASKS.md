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

## Dependency Graph

```
REQ-F-GMON-002 (models + parsers)
    └──► REQ-F-GMON-003 (projections + dashboard)
```

REQ-F-GMON-002 must converge before REQ-F-GMON-003 can begin code iteration.
