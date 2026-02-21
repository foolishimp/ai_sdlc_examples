# Implements: REQ-F-VREL-001, REQ-F-TBOX-001, REQ-F-PROF-001, REQ-F-CDIM-001, REQ-F-REGIME-001
"""v2.5 model extensions: TimeBox, EvaluatorResult, ConstraintDimension, ProjectionProfile."""

from dataclasses import dataclass, field
from datetime import datetime


@dataclass
class TimeBox:
    """Time-boxing configuration for discovery/spike/PoC vectors."""

    duration: str = ""
    check_in: str | None = None
    on_expiry: str = "fold_back"
    partial_results: bool = True
    deadline: datetime | None = None
    convergence_reason: str | None = None  # completed | timed_out


@dataclass
class EvaluatorResult:
    """An evaluator result with processing regime classification."""

    name: str = ""
    result: str = ""  # pass | fail | skip
    regime: str = ""  # conscious | reflex


@dataclass
class ConstraintDimension:
    """A constraint dimension from graph topology (v2.5 §2.6.1)."""

    name: str = ""
    mandatory: bool = False
    resolves_via: str = ""  # adr | design_section | adr_or_design_section
    resolved: bool | None = None


@dataclass
class ProjectionProfile:
    """A named projection profile (v2.5 §7, PROJECTIONS_AND_INVARIANTS.md)."""

    name: str = ""
    graph_edges: list[str] = field(default_factory=list)
    evaluator_types: list[str] = field(default_factory=list)
    convergence: str = ""
    context_density: str = ""
    iteration_budget: str | None = None
    vector_types: list[str] = field(default_factory=list)
