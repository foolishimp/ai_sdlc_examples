# Implements: REQ-ITER-003 (Functor Encoding Tracking)
"""genesis — F_D functor framework for deterministic methodology operations."""

from .models import (
    Category,
    CheckOutcome,
    CheckResult,
    ClassificationResult,
    EvaluationResult,
    Event,
    FoldBackResult,
    FunctionalUnit,
    ResolvedCheck,
    RouteResult,
    SenseResult,
    SpawnRequest,
    SpawnResult,
)
from .config_loader import (
    load_yaml,
    resolve_variable,
    resolve_variables,
    resolve_checklist,
)
from .fd_evaluate import evaluate_checklist, run_check
from .fd_emit import emit_event, make_event
from .fd_classify import (
    classify_req_tag,
    classify_source_finding,
    classify_signal_source,
)
from .fd_sense import (
    sense_event_freshness,
    sense_event_log_integrity,
    sense_feature_stall,
    sense_req_tag_coverage,
    sense_test_health,
)
from .fd_route import lookup_encoding, select_next_edge, select_profile
from .fd_spawn import (
    check_time_box,
    create_child_vector,
    detect_spawn_condition,
    emit_spawn_events,
    fold_back_child,
    link_parent_child,
    load_events,
)
from .dispatch import dispatch, lookup_and_dispatch

__all__ = [
    # Models
    "Category",
    "CheckOutcome",
    "CheckResult",
    "ClassificationResult",
    "EvaluationResult",
    "Event",
    "FoldBackResult",
    "FunctionalUnit",
    "ResolvedCheck",
    "RouteResult",
    "SenseResult",
    "SpawnRequest",
    "SpawnResult",
    # Config
    "load_yaml",
    "resolve_variable",
    "resolve_variables",
    "resolve_checklist",
    # Evaluate
    "run_check",
    "evaluate_checklist",
    # Emit
    "emit_event",
    "make_event",
    # Classify
    "classify_req_tag",
    "classify_source_finding",
    "classify_signal_source",
    # Sense
    "sense_event_freshness",
    "sense_event_log_integrity",
    "sense_feature_stall",
    "sense_req_tag_coverage",
    "sense_test_health",
    # Route
    "lookup_encoding",
    "select_next_edge",
    "select_profile",
    # Spawn
    "check_time_box",
    "create_child_vector",
    "detect_spawn_condition",
    "emit_spawn_events",
    "fold_back_child",
    "link_parent_child",
    "load_events",
    # Dispatch
    "dispatch",
    "lookup_and_dispatch",
]
