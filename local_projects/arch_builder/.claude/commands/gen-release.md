# /gen-release - Create Release with REQ Coverage

Generate a release manifest with semantic version, changelog, and REQ key coverage.

<!-- Implements: REQ-TOOL-004 (Release Management) -->

## Usage

```
/gen-release --version "{semver}" [--dry-run]
```

| Option | Description |
|--------|-------------|
| `--version` | Semantic version (e.g., "1.0.0", "0.2.0") |
| `--dry-run` | Preview release without creating tags/artifacts |

## Instructions

### Step 1: Validate Release Readiness

1. Run `/gen-gaps` to check REQ key coverage
2. Check all active features: are critical features converged?
3. Check test results: all passing?
4. Report any blockers

### Step 2: Generate Changelog

Scan git log since last release tag for commits with REQ key references:

```
## v{version} — {date}

### Features
- REQ-F-AUTH-001: User authentication with email/password
- REQ-F-DB-001: Database schema and migrations

### Improvements
- REQ-NFR-PERF-001: Login response optimised to < 500ms

### Bug Fixes
- REQ-F-AUTH-002: Fixed session timeout handling

### REQ Key Coverage
| Category | Covered | Total | % |
|----------|---------|-------|---|
| Functional | 8 | 10 | 80% |
| Non-Functional | 3 | 5 | 60% |
| Data | 2 | 2 | 100% |
| Business Rules | 1 | 3 | 33% |
```

### Step 3: Create Release Manifest

Write to `docs/releases/release-{version}.yml`:

```yaml
version: "{semver}"
date: "{date}"
context_hash: "sha256:{hash}"

features_included:
  - REQ-F-AUTH-001: converged
  - REQ-F-DB-001: converged

coverage:
  requirements: 14/20 (70%)
  design: 14/14 (100%)
  code: 14/14 (100%)
  tests: 12/14 (86%)
  uat: 8/14 (57%)

known_gaps:
  - REQ-NFR-SEC-002: Not yet implemented
  - REQ-F-REPORT-001: Design only
```

### Step 4: Emit Event

Unless `--dry-run`, append a `release_created` event to `.ai-workspace/events/events.jsonl`:

```json
{"event_type": "release_created", "timestamp": "{ISO 8601}", "project": "{project name from project_constraints.yml}", "data": {"version": "{semver}", "features_included": {count of converged features}, "coverage_pct": {overall coverage percentage}, "known_gaps": {count of known gaps}, "context_hash": "sha256:{hash}", "git_tag": "v{version}"}}
```

### Step 5: Tag and Report

Unless `--dry-run`:
- Create git tag `v{version}`
- Display release summary
