# Implements: REQ-F-PARSE-001, REQ-F-PARSE-002, REQ-F-PARSE-003, REQ-F-PARSE-004, REQ-F-PARSE-005, REQ-F-PARSE-006
# Implements: REQ-F-VREL-001, REQ-F-TBOX-001, REQ-F-PROF-001, REQ-F-CDIM-001
"""Core data models for Genesis Monitor."""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from genesis_monitor.models.features import (
        ConstraintDimension,
        ProjectionProfile,
        TimeBox,
    )


@dataclass
class PhaseEntry:
    """One row of the phase completion summary table."""

    edge: str
    status: str  # converged | in_progress | not_started
    iterations: int = 0
    evaluator_results: dict[str, str] = field(default_factory=dict)
    source_findings: int = 0
    process_gaps: int = 0


@dataclass
class TelemSignal:
    """A TELEM self-reflection signal."""

    signal_id: str
    category: str
    description: str
    project_id: str | None = None


@dataclass
class StatusReport:
    """Parsed STATUS.md data."""

    project_name: str = ""
    phase_summary: list[PhaseEntry] = field(default_factory=list)
    telem_signals: list[TelemSignal] = field(default_factory=list)
    gantt_mermaid: str | None = None
    metrics: dict[str, str] = field(default_factory=dict)


@dataclass
class EdgeTrajectory:
    """Per-edge trajectory within a feature vector."""

    status: str = "not_started"
    iteration: int = 0
    evaluator_results: dict[str, str] = field(default_factory=dict)
    # v2.8 additions
    started_at: datetime | None = None
    converged_at: datetime | None = None
    convergence_type: str = ""
    escalations: list[str] = field(default_factory=list)


@dataclass
class FeatureVector:
    """A tracked feature with per-edge convergence trajectory."""

    feature_id: str = ""
    title: str = ""
    status: str = "pending"
    vector_type: str = "feature"
    trajectory: dict[str, EdgeTrajectory] = field(default_factory=dict)
    # v2.5 additions
    profile: str | None = None
    parent_id: str | None = None
    spawned_by: str | None = None
    children: list[str] = field(default_factory=list)
    fold_back_status: str | None = None
    time_box: TimeBox | None = None
    # v2.8 additions
    encoding: dict | None = None


@dataclass
class AssetType:
    """A node type in the asset graph."""

    name: str = ""
    description: str = ""


@dataclass
class Transition:
    """An admissible edge in the asset graph."""

    source: str = ""
    target: str = ""
    edge_type: str = ""


@dataclass
class GraphTopology:
    """The asset graph topology definition."""

    asset_types: list[AssetType] = field(default_factory=list)
    transitions: list[Transition] = field(default_factory=list)
    # v2.5 additions
    constraint_dimensions: list[ConstraintDimension] = field(default_factory=list)
    profiles: list[ProjectionProfile] = field(default_factory=list)


@dataclass
class Task:
    """A task from ACTIVE_TASKS.md."""

    task_id: str = ""
    title: str = ""
    status: str = "pending"
    priority: str | None = None


@dataclass
class ProjectConstraints:
    """Parsed project_constraints.yml."""

    language: str = ""
    tools: dict[str, dict] = field(default_factory=dict)
    thresholds: dict[str, str] = field(default_factory=dict)
    raw: dict = field(default_factory=dict)


@dataclass
class EdgeConvergence:
    """Derived convergence view for a single edge."""

    edge: str = ""
    iterations: int = 0
    evaluator_summary: str = ""
    source_findings: int = 0
    process_gaps: int = 0
    status: str = "not_started"
    # v2.8 additions
    started_at: datetime | None = None
    converged_at: datetime | None = None
    duration: str = ""
    convergence_type: str = ""
    delta_curve: list[int] = field(default_factory=list)


@dataclass
class Project:
    """A discovered project with all parsed data."""

    project_id: str = ""
    path: Path = field(default_factory=Path)
    name: str = ""
    status: StatusReport | None = None
    features: list[FeatureVector] = field(default_factory=list)
    topology: GraphTopology | None = None
    events: list[Event] = field(default_factory=list)
    tasks: list[Task] = field(default_factory=list)
    constraints: ProjectConstraints | None = None
    has_bootloader: bool = False
    last_updated: datetime = field(default_factory=datetime.now)


@dataclass
class AppConfig:
    """Application configuration."""

    watch_dirs: list[Path] = field(default_factory=list)
    host: str = "0.0.0.0"
    port: int = 8000
    debounce_ms: int = 500
    exclude_patterns: list[str] = field(
        default_factory=lambda: [".git", "__pycache__", ".venv", "node_modules"]
    )
