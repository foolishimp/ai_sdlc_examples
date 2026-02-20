# Implements: REQ-F-PARSE-006
"""Parse .ai-workspace/context/project_constraints.yml."""

from pathlib import Path

import yaml

from genesis_monitor.models import ProjectConstraints


def parse_constraints(workspace: Path) -> ProjectConstraints | None:
    """Parse project constraints YAML.

    Returns None if the file doesn't exist or can't be parsed.
    Unknown fields are preserved in the raw dict (forward-compatible).
    """
    constraints_path = workspace / "context" / "project_constraints.yml"
    if not constraints_path.exists():
        return None

    try:
        with open(constraints_path) as f:
            data = yaml.safe_load(f)
    except (OSError, yaml.YAMLError):
        return None

    if not isinstance(data, dict):
        return None

    language_data = data.get("language", {})
    language = (
        language_data.get("primary", str(language_data))
        if isinstance(language_data, dict)
        else str(language_data)
    )

    return ProjectConstraints(
        language=language,
        tools={str(k): v for k, v in (data.get("tools", {}) or {}).items() if isinstance(v, dict)},
        thresholds={str(k): str(v) for k, v in (data.get("thresholds", {}) or {}).items()},
        raw=data,
    )
