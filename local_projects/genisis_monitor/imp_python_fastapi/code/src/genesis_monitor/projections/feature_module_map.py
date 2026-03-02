# Implements: REQ-F-TRACE-002
"""Build a feature × module bipartite map from traceability data.

Each feature vector declares a list of REQ keys.  The traceability scanner
records which code files carry `# Implements: REQ-*` tags.  This projection
joins those two sources:

    feature.requirements → REQ key → TraceabilityReport.code_coverage → files

The result is a bipartite map: each feature row lists the code modules that
implement it, plus any REQ keys that have no implementing file yet (gaps).

This is the Zoom-2 design-layer projection described in ADR-022.
"""

from __future__ import annotations

from dataclasses import dataclass, field

from genesis_monitor.models.core import FeatureVector
from genesis_monitor.parsers.traceability import TraceabilityReport


@dataclass
class FeatureModuleRow:
    """One feature with the modules that implement its REQ keys."""

    feature_id: str
    title: str
    req_keys: list[str]       # REQ keys declared in the feature vector
    modules: list[str]        # unique code files that implement those keys
    untraced_keys: list[str]  # req keys with no code implementation found


@dataclass
class FeatureModuleMap:
    """Feature × module bipartite map."""

    rows: list[FeatureModuleRow] = field(default_factory=list)
    all_modules: list[str] = field(default_factory=list)
    total_features: int = 0
    total_modules: int = 0
    total_req_keys: int = 0
    traced_req_keys: int = 0   # req keys with at least one implementing file
    coverage_pct: int = 0


def build_feature_module_map(
    features: list[FeatureVector] | None,
    traceability: TraceabilityReport | None,
) -> FeatureModuleMap | None:
    """Join feature REQ keys with traceability code_coverage to produce the map.

    Returns None if there are no features with declared requirements, or if
    traceability data is unavailable.
    """
    if not features or not traceability:
        return None

    features_with_reqs = [f for f in features if f.requirements]
    if not features_with_reqs:
        return None

    rows: list[FeatureModuleRow] = []
    all_modules: set[str] = set()
    total_keys = 0
    traced_keys = 0

    for feat in features_with_reqs:
        modules: set[str] = set()
        untraced: list[str] = []

        for req_key in feat.requirements:
            total_keys += 1
            files = traceability.code_coverage.get(req_key, [])
            if files:
                modules.update(files)
                traced_keys += 1
            else:
                untraced.append(req_key)

        all_modules.update(modules)
        rows.append(FeatureModuleRow(
            feature_id=feat.feature_id,
            title=feat.title or "",
            req_keys=list(feat.requirements),
            modules=sorted(modules),
            untraced_keys=untraced,
        ))

    coverage_pct = int(traced_keys / total_keys * 100) if total_keys else 0
    sorted_modules = sorted(all_modules)

    return FeatureModuleMap(
        rows=rows,
        all_modules=sorted_modules,
        total_features=len(rows),
        total_modules=len(sorted_modules),
        total_req_keys=total_keys,
        traced_req_keys=traced_keys,
        coverage_pct=coverage_pct,
    )
