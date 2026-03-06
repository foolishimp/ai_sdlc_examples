# Implements: REQ-ITER-003 (Functor Encoding Tracking), REQ-CTX-001 (Context as Constraint Surface)
"""YAML loading and $variable resolution for edge configs and project constraints."""

import pathlib
import re
from typing import Optional

import yaml

from .models import ResolvedCheck

_VAR_PATTERN = re.compile(r"\$(\w+(?:\.\w+)*)")


def load_yaml(path: pathlib.Path) -> dict:
    """Load a YAML file, merging multiple documents into one dict."""
    with open(path) as f:
        docs = list(yaml.safe_load_all(f))
    result = {}
    for doc in docs:
        if doc is not None:
            result.update(doc)
    return result


def resolve_variable(ref: str, constraints: dict) -> Optional[str]:
    """Resolve a single $variable reference against constraints.

    ref: dotted path without the leading $, e.g. "tools.test_runner.command"
    Returns the resolved value as a string, or None if not found.
    """
    parts = ref.split(".")
    current = constraints
    for part in parts:
        if isinstance(current, dict) and part in current:
            current = current[part]
        else:
            return None
    if current is None:
        return None
    return str(current)


def resolve_variables(text: str, constraints: dict) -> tuple[str, list[str]]:
    """Resolve all $variable references in a text string.

    Returns (resolved_text, list_of_unresolved_refs).
    """
    unresolved = []

    def _replace(match: re.Match) -> str:
        ref = match.group(1)
        value = resolve_variable(ref, constraints)
        if value is None:
            unresolved.append(ref)
            return match.group(0)  # leave original $ref
        return value

    resolved = _VAR_PATTERN.sub(_replace, text)
    return resolved, unresolved


def resolve_checklist(edge_config: dict, constraints: dict) -> list[ResolvedCheck]:
    """Resolve all $variables in an edge config's checklist.

    Returns a list of ResolvedCheck with concrete values.
    """
    checklist = edge_config.get("checklist", [])
    results = []

    for entry in checklist:
        all_unresolved = []

        # Resolve criterion
        criterion = entry.get("criterion", "")
        if isinstance(criterion, str):
            criterion, unr = resolve_variables(criterion, constraints)
            all_unresolved.extend(unr)

        # Resolve command (deterministic only)
        command = entry.get("command")
        if command and isinstance(command, str):
            command, unr = resolve_variables(command, constraints)
            all_unresolved.extend(unr)

        # Resolve pass_criterion
        pass_criterion = entry.get("pass_criterion")
        if pass_criterion and isinstance(pass_criterion, str):
            pass_criterion, unr = resolve_variables(pass_criterion, constraints)
            all_unresolved.extend(unr)

        # Resolve required (can be a $variable string like "$tools.type_checker.required")
        required_raw = entry.get("required", True)
        if isinstance(required_raw, str):
            resolved_req, unr = resolve_variables(required_raw, constraints)
            all_unresolved.extend(unr)
            required = resolved_req.lower() not in ("false", "0", "no", "none")
        else:
            required = bool(required_raw)

        results.append(
            ResolvedCheck(
                name=entry.get("name", ""),
                check_type=entry.get("type", "agent"),
                functional_unit=entry.get("functional_unit", "evaluate"),
                criterion=criterion,
                source=entry.get("source", "default"),
                required=required,
                command=command,
                pass_criterion=pass_criterion,
                unresolved=all_unresolved,
            )
        )

    return results
