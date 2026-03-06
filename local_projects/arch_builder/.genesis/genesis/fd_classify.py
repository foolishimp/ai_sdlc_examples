# Implements: REQ-ITER-003 (Functor Encoding Tracking)
"""F_D classify — deterministic classification of REQ tags, source findings, signals."""

import re

from .models import ClassificationResult

_REQ_TAG_PATTERN = re.compile(r"(Implements|Validates):\s*REQ-[A-Z]+(?:-[A-Z]+)*-\d+")

_REQ_KEY_PATTERN = re.compile(r"REQ-[A-Z]+(?:-[A-Z]+)*-\d+")

# Keyword sets for source finding classification
_AMBIGUITY_KEYWORDS = {
    "unclear",
    "ambiguous",
    "vague",
    "undefined",
    "unspecified",
    "unknown",
    "uncertain",
    "implicit",
    "assumed",
    "tbd",
}
_GAP_KEYWORDS = {
    "missing",
    "absent",
    "gap",
    "omitted",
    "incomplete",
    "no mention",
    "not defined",
    "not specified",
    "lacks",
}
_UNDERSPEC_KEYWORDS = {
    "underspecified",
    "under-specified",
    "insufficient detail",
    "needs clarification",
    "needs refinement",
    "placeholder",
}


def classify_req_tag(text: str) -> ClassificationResult:
    """Validate a REQ tag string against the expected format.

    Returns classification: VALID, INVALID_FORMAT, or MISSING.
    """
    text = text.strip()
    if not text:
        return ClassificationResult(
            input_text=text,
            classification="MISSING",
            evidence="Empty input",
        )

    if _REQ_TAG_PATTERN.search(text):
        return ClassificationResult(
            input_text=text,
            classification="VALID",
            evidence=f"Matches pattern: {_REQ_TAG_PATTERN.pattern}",
        )

    # Check if there's a REQ key but wrong format
    if _REQ_KEY_PATTERN.search(text):
        return ClassificationResult(
            input_text=text,
            classification="INVALID_FORMAT",
            evidence="REQ key found but missing 'Implements:' or 'Validates:' prefix",
        )

    return ClassificationResult(
        input_text=text,
        classification="MISSING",
        evidence="No REQ tag found",
    )


def classify_source_finding(description: str) -> ClassificationResult:
    """Classify a source analysis finding by keyword matching.

    Returns: SOURCE_AMBIGUITY, SOURCE_GAP, SOURCE_UNDERSPEC, or UNCLASSIFIED.
    """
    lower = description.lower()

    for kw in _UNDERSPEC_KEYWORDS:
        if kw in lower:
            return ClassificationResult(
                input_text=description,
                classification="SOURCE_UNDERSPEC",
                evidence=f"Matched keyword: '{kw}'",
            )

    for kw in _AMBIGUITY_KEYWORDS:
        if kw in lower:
            return ClassificationResult(
                input_text=description,
                classification="SOURCE_AMBIGUITY",
                evidence=f"Matched keyword: '{kw}'",
            )

    for kw in _GAP_KEYWORDS:
        if kw in lower:
            return ClassificationResult(
                input_text=description,
                classification="SOURCE_GAP",
                evidence=f"Matched keyword: '{kw}'",
            )

    return ClassificationResult(
        input_text=description,
        classification="UNCLASSIFIED",
        evidence="No classification keywords matched",
    )


def classify_signal_source(event: dict) -> str:
    """Deterministic signal source classification from event fields.

    Maps event_type to signal source category.
    """
    event_type = event.get("event_type", "")

    signal_map = {
        "iteration_completed": "iteration",
        "edge_started": "edge_transition",
        "edge_converged": "convergence",
        "spawn_created": "spawn",
        "spawn_folded_back": "spawn",
        "checkpoint_created": "checkpoint",
        "review_completed": "review",
        "gaps_validated": "traceability",
        "release_created": "release",
        "project_initialized": "lifecycle",
        "health_checked": "health",
        "intent_raised": event.get("data", {}).get("signal_source", "unknown"),
        "encoding_escalated": "escalation",
        "artifact_modified": "artifact",
    }

    return signal_map.get(event_type, "unknown")
