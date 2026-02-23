# Validates: REQ-F-EVSCHEMA-001, REQ-F-EVSCHEMA-002, REQ-F-VREL-001, REQ-F-TBOX-001, REQ-F-CDIM-001, REQ-F-PROF-001
"""Tests for v2.5 parser extensions."""

import json
from pathlib import Path

import pytest
import yaml

from genesis_monitor.models.events import (
    EdgeConvergedEvent,
    Event,
    FeatureSpawnedEvent,
    IntentRaisedEvent,
    IterationCompletedEvent,
    SpecModifiedEvent,
)
from genesis_monitor.models.features import ConstraintDimension, ProjectionProfile
from genesis_monitor.parsers.events import parse_events
from genesis_monitor.parsers.features import parse_feature_vectors
from genesis_monitor.parsers.topology import parse_graph_topology


# ── Typed Event Parsing ─────────────────────────────────────────


@pytest.fixture
def v25_events_workspace(tmp_path: Path) -> Path:
    """Create workspace with v2.5 typed events."""
    ws = tmp_path / ".ai-workspace"
    events_dir = ws / "events"
    events_dir.mkdir(parents=True)

    events = [
        {
            "timestamp": "2026-02-21T08:00:00",
            "event_type": "iteration_completed",
            "project": "test",
            "edge": "design→code",
            "feature": "REQ-F-001",
            "iteration": 2,
            "evaluators": {"agent": "pass", "pytest": "fail"},
            "context_hash": "sha256:abc123",
        },
        {
            "timestamp": "2026-02-21T08:10:00",
            "event_type": "edge_converged",
            "project": "test",
            "edge": "design→code",
            "feature": "REQ-F-001",
            "convergence_time": "10m",
        },
        {
            "timestamp": "2026-02-21T08:15:00",
            "event_type": "feature_spawned",
            "project": "test",
            "parent_vector": "REQ-F-001",
            "child_vector": "REQ-F-002",
            "reason": "gap",
        },
        {
            "timestamp": "2026-02-21T08:20:00",
            "event_type": "intent_raised",
            "project": "test",
            "trigger": "gap_found",
            "signal_source": "TELEM-001",
            "prior_intents": ["INT-001", "INT-042"],
        },
        {
            "timestamp": "2026-02-21T08:25:00",
            "event_type": "spec_modified",
            "project": "test",
            "previous_hash": "sha256:old",
            "new_hash": "sha256:new",
            "delta": "Added REQ-F-003",
            "trigger_intent": "INT-042",
        },
        {
            "timestamp": "2026-02-21T08:30:00",
            "event_type": "unknown_future_event",
            "project": "test",
            "some_field": "some_value",
        },
    ]
    (events_dir / "events.jsonl").write_text(
        "\n".join(json.dumps(e) for e in events) + "\n"
    )
    return ws


class TestTypedEventParsing:
    def test_parses_iteration_completed(self, v25_events_workspace: Path):
        events = parse_events(v25_events_workspace)
        ic = [e for e in events if isinstance(e, IterationCompletedEvent)]
        assert len(ic) == 1
        assert ic[0].edge == "design→code"
        assert ic[0].iteration == 2
        assert ic[0].evaluators["pytest"] == "fail"
        assert ic[0].context_hash == "sha256:abc123"

    def test_parses_edge_converged(self, v25_events_workspace: Path):
        events = parse_events(v25_events_workspace)
        ec = [e for e in events if isinstance(e, EdgeConvergedEvent)]
        assert len(ec) == 1
        assert ec[0].convergence_time == "10m"

    def test_parses_feature_spawned(self, v25_events_workspace: Path):
        events = parse_events(v25_events_workspace)
        fs = [e for e in events if isinstance(e, FeatureSpawnedEvent)]
        assert len(fs) == 1
        assert fs[0].parent_vector == "REQ-F-001"
        assert fs[0].reason == "gap"

    def test_parses_intent_raised_with_causal_chain(self, v25_events_workspace: Path):
        events = parse_events(v25_events_workspace)
        ir = [e for e in events if isinstance(e, IntentRaisedEvent)]
        assert len(ir) == 1
        assert ir[0].prior_intents == ["INT-001", "INT-042"]

    def test_parses_spec_modified(self, v25_events_workspace: Path):
        events = parse_events(v25_events_workspace)
        sm = [e for e in events if isinstance(e, SpecModifiedEvent)]
        assert len(sm) == 1
        assert sm[0].trigger_intent == "INT-042"

    def test_unknown_event_falls_back_to_base(self, v25_events_workspace: Path):
        events = parse_events(v25_events_workspace)
        unknowns = [e for e in events if type(e) is Event]
        assert len(unknowns) == 1
        assert unknowns[0].event_type == "unknown_future_event"
        assert unknowns[0].data["some_field"] == "some_value"

    def test_total_event_count(self, v25_events_workspace: Path):
        events = parse_events(v25_events_workspace)
        assert len(events) == 6

    def test_backward_compatible_with_v21_events(self, tmp_path: Path):
        """v2.1-style events with unknown types still parse as base Event."""
        ws = tmp_path / ".ai-workspace"
        events_dir = ws / "events"
        events_dir.mkdir(parents=True)
        events = [
            {"timestamp": "2026-02-01T10:00:00", "event_type": "some_v21_custom_event", "project": "test"},
        ]
        (events_dir / "events.jsonl").write_text(json.dumps(events[0]) + "\n")
        result = parse_events(ws)
        assert len(result) == 1
        assert type(result[0]) is Event


# ── Feature Vector v2.5 Parsing ─────────────────────────────────


@pytest.fixture
def v25_features_workspace(tmp_path: Path) -> Path:
    """Create workspace with v2.5 feature vectors."""
    ws = tmp_path / ".ai-workspace"
    features_dir = ws / "features" / "active"
    features_dir.mkdir(parents=True)

    # Parent feature
    (features_dir / "REQ-F-001.yml").write_text(yaml.dump({
        "feature": "REQ-F-001",
        "title": "Main Feature",
        "status": "in_progress",
        "vector_type": "feature",
        "profile": "standard",
        "trajectory": {
            "requirements": {"status": "converged", "iteration": 1},
            "design": {"status": "in_progress", "iteration": 2},
        },
    }))

    # Spawned spike with time-box
    (features_dir / "REQ-F-002.yml").write_text(yaml.dump({
        "feature": "REQ-F-002",
        "title": "Security Spike",
        "status": "in_progress",
        "vector_type": "spike",
        "profile": "spike",
        "parent_id": "REQ-F-001",
        "spawned_by": "risk",
        "fold_back_status": "pending",
        "time_box": {
            "duration": "1 week",
            "check_in": "daily",
            "on_expiry": "fold_back",
            "partial_results": True,
        },
        "trajectory": {
            "hypothesis": {"status": "converged", "iteration": 1},
            "experiment": {"status": "in_progress", "iteration": 1},
        },
    }))

    # v2.1-style vector (no v2.5 fields)
    (features_dir / "REQ-F-003.yml").write_text(yaml.dump({
        "feature": "REQ-F-003",
        "title": "Legacy Feature",
        "status": "converged",
        "vector_type": "feature",
        "trajectory": {
            "requirements": {"status": "converged", "iteration": 1},
        },
    }))

    return ws


class TestFeatureVectorV25Parsing:
    def test_parses_profile(self, v25_features_workspace: Path):
        vectors = parse_feature_vectors(v25_features_workspace)
        parent = next(v for v in vectors if v.feature_id == "REQ-F-001")
        assert parent.profile == "standard"

    def test_parses_parent_id(self, v25_features_workspace: Path):
        vectors = parse_feature_vectors(v25_features_workspace)
        spike = next(v for v in vectors if v.feature_id == "REQ-F-002")
        assert spike.parent_id == "REQ-F-001"
        assert spike.spawned_by == "risk"

    def test_parses_fold_back_status(self, v25_features_workspace: Path):
        vectors = parse_feature_vectors(v25_features_workspace)
        spike = next(v for v in vectors if v.feature_id == "REQ-F-002")
        assert spike.fold_back_status == "pending"

    def test_parses_time_box(self, v25_features_workspace: Path):
        vectors = parse_feature_vectors(v25_features_workspace)
        spike = next(v for v in vectors if v.feature_id == "REQ-F-002")
        assert spike.time_box is not None
        assert spike.time_box.duration == "1 week"
        assert spike.time_box.check_in == "daily"
        assert spike.time_box.on_expiry == "fold_back"
        assert spike.time_box.partial_results is True

    def test_populates_children_from_parent_id(self, v25_features_workspace: Path):
        vectors = parse_feature_vectors(v25_features_workspace)
        parent = next(v for v in vectors if v.feature_id == "REQ-F-001")
        assert "REQ-F-002" in parent.children

    def test_v21_vector_backward_compatible(self, v25_features_workspace: Path):
        vectors = parse_feature_vectors(v25_features_workspace)
        legacy = next(v for v in vectors if v.feature_id == "REQ-F-003")
        assert legacy.profile is None
        assert legacy.parent_id is None
        assert legacy.time_box is None
        assert legacy.children == []


# ── Topology v2.5 Parsing ───────────────────────────────────────


@pytest.fixture
def v25_topology_workspace(tmp_path: Path) -> Path:
    """Create workspace with v2.5 graph topology."""
    ws = tmp_path / ".ai-workspace"
    graph_dir = ws / "graph"
    graph_dir.mkdir(parents=True)

    (graph_dir / "graph_topology.yml").write_text(yaml.dump({
        "asset_types": {
            "intent": {"description": "Business intent"},
            "requirements": {"description": "Requirements"},
            "design": {"description": "Design"},
            "code": {"description": "Code"},
        },
        "transitions": [
            {"source": "intent", "target": "requirements"},
            {"source": "requirements", "target": "design"},
            {"source": "design", "target": "code"},
        ],
        "constraint_dimensions": {
            "ecosystem_compatibility": {
                "mandatory": True,
                "resolves_via": "adr",
            },
            "security_model": {
                "mandatory": True,
                "resolves_via": "adr",
            },
            "performance_envelope": {
                "mandatory": False,
                "resolves_via": "design_section",
            },
        },
        "profiles": {
            "full": {
                "graph_edges": ["all"],
                "evaluator_types": ["human", "agent", "deterministic"],
                "convergence": "delta_zero",
                "context_density": "full",
                "iteration_budget": None,
                "vector_types": ["feature", "discovery", "spike"],
            },
            "spike": {
                "graph_edges": ["hypothesis→experiment", "experiment→assessment"],
                "evaluator_types": ["agent", "human"],
                "convergence": "risk_assessed_or_timeout",
                "context_density": "intent+technical",
                "iteration_budget": "1 week",
                "vector_types": ["spike", "discovery"],
            },
        },
    }))
    return ws


class TestTopologyV25Parsing:
    def test_parses_constraint_dimensions(self, v25_topology_workspace: Path):
        topo = parse_graph_topology(v25_topology_workspace)
        assert topo is not None
        assert len(topo.constraint_dimensions) == 3
        eco = next(d for d in topo.constraint_dimensions if d.name == "ecosystem_compatibility")
        assert eco.mandatory is True
        assert eco.resolves_via == "adr"

    def test_parses_mandatory_flags(self, v25_topology_workspace: Path):
        topo = parse_graph_topology(v25_topology_workspace)
        mandatory = [d for d in topo.constraint_dimensions if d.mandatory]
        assert len(mandatory) == 2

    def test_parses_profiles(self, v25_topology_workspace: Path):
        topo = parse_graph_topology(v25_topology_workspace)
        assert len(topo.profiles) == 2
        full = next(p for p in topo.profiles if p.name == "full")
        assert "human" in full.evaluator_types
        assert full.iteration_budget is None

    def test_parses_spike_profile(self, v25_topology_workspace: Path):
        topo = parse_graph_topology(v25_topology_workspace)
        spike = next(p for p in topo.profiles if p.name == "spike")
        assert spike.iteration_budget == "1 week"
        assert "spike" in spike.vector_types

    def test_backward_compatible_no_dimensions(self, tmp_path: Path):
        """v2.1 topology (no constraint_dimensions/profiles) still parses."""
        ws = tmp_path / ".ai-workspace"
        graph_dir = ws / "graph"
        graph_dir.mkdir(parents=True)
        (graph_dir / "graph_topology.yml").write_text(yaml.dump({
            "asset_types": {"intent": {"description": "Intent"}},
            "transitions": [{"source": "intent", "target": "req"}],
        }))
        topo = parse_graph_topology(ws)
        assert topo is not None
        assert topo.constraint_dimensions == []
        assert topo.profiles == []
        assert len(topo.asset_types) == 1
