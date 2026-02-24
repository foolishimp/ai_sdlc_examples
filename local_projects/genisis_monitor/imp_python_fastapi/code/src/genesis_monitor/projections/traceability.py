# Implements: REQ-F-TRACE-001
"""Build traceability view cross-referencing features, code, and tests."""

from __future__ import annotations

from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from genesis_monitor.models.core import FeatureVector
    from genesis_monitor.parsers.traceability import TraceabilityReport


def build_traceability_view(
    features: list[FeatureVector],
    traceability: TraceabilityReport | None,
) -> dict:
    """Build a traceability cross-reference view.

    Returns a dict with:
      - req_rows: list of dicts per REQ key with code/test coverage
      - summary: aggregate counts
      - feature_coverage: per-feature coverage summary
    """
    if not traceability:
        return {
            "req_rows": [],
            "summary": {
                "total_req_keys": 0,
                "with_code": 0,
                "with_tests": 0,
                "full_coverage": 0,
                "no_coverage": 0,
                "code_files": 0,
                "test_files": 0,
            },
            "feature_coverage": [],
        }

    # Collect all REQ keys from features + traceability scan
    all_req_keys: set[str] = set(traceability.all_req_keys)
    for f in features:
        all_req_keys.update(f.requirements)

    # Build per-REQ-key rows
    req_rows = []
    with_code = 0
    with_tests = 0
    full_coverage = 0
    no_coverage = 0

    for req_key in sorted(all_req_keys):
        code_files = traceability.code_coverage.get(req_key, [])
        test_files = traceability.test_coverage.get(req_key, [])

        # Which features reference this REQ key
        owning_features = [
            f.feature_id for f in features if req_key in f.requirements
        ]

        has_code = len(code_files) > 0
        has_tests = len(test_files) > 0

        if has_code:
            with_code += 1
        if has_tests:
            with_tests += 1
        if has_code and has_tests:
            full_coverage += 1
        if not has_code and not has_tests:
            no_coverage += 1

        status = "full" if (has_code and has_tests) else (
            "partial" if (has_code or has_tests) else "none"
        )

        req_rows.append({
            "req_key": req_key,
            "code_files": code_files,
            "test_files": test_files,
            "features": owning_features,
            "has_code": has_code,
            "has_tests": has_tests,
            "status": status,
        })

    # Per-feature coverage
    feature_coverage = []
    for f in features:
        total_reqs = len(f.requirements)
        covered = sum(
            1 for r in f.requirements
            if r in traceability.test_coverage
        )
        implemented = sum(
            1 for r in f.requirements
            if r in traceability.code_coverage
        )
        feature_coverage.append({
            "feature_id": f.feature_id,
            "title": f.title,
            "status": f.status,
            "total_reqs": total_reqs,
            "implemented": implemented,
            "tested": covered,
            "pct_implemented": round(100 * implemented / total_reqs) if total_reqs else 0,
            "pct_tested": round(100 * covered / total_reqs) if total_reqs else 0,
        })

    return {
        "req_rows": req_rows,
        "summary": {
            "total_req_keys": len(all_req_keys),
            "with_code": with_code,
            "with_tests": with_tests,
            "full_coverage": full_coverage,
            "no_coverage": no_coverage,
            "code_files": traceability.code_files_scanned,
            "test_files": traceability.test_files_scanned,
        },
        "feature_coverage": feature_coverage,
    }
