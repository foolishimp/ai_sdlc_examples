# Validates: REQ-F-SENSE-001, REQ-F-SENSE-002, REQ-F-SENSE-003, REQ-F-MAGT-001
# Validates: REQ-F-MAGT-002, REQ-F-MAGT-003, REQ-F-IENG-001, REQ-F-FUNC-001
# Validates: REQ-F-ETIM-001, REQ-F-ETIM-003, REQ-F-CTOL-001, REQ-F-CTOL-002
"""Tests for v2.8 parser extensions."""

import json
from pathlib import Path

import pytest
import yaml

from genesis_monitor.models.events import (
    AffectTriageEvent,
    CheckpointCreatedEvent,
    ClaimExpiredEvent,
    ClaimRejectedEvent,
    CommandErrorEvent,
    ConvergenceEscalatedEvent,
    EdgeReleasedEvent,
    EdgeStartedEvent,
    EncodingEscalatedEvent,
    EvaluatorDetailEvent,
    Event,
    ExteroceptiveSignalEvent,
    GapsValidatedEvent,
    HealthCheckedEvent,
    InteroceptiveSignalEvent,
    IterationAbandonedEvent,
    IterationCompletedEvent,
    ProjectInitializedEvent,
    ReleaseCreatedEvent,
    ReviewCompletedEvent,
)
from genesis_monitor.parsers.events import classify_intent_engine_output, parse_events
from genesis_monitor.projections.convergence import build_convergence_table_from_events
from genesis_monitor.parsers.features import parse_feature_vectors
from genesis_monitor.parsers.topology import parse_graph_topology


# ── v2.8 Event Parsing ────────────────────────────────────────────


@pytest.fixture
def v28_events_workspace(tmp_path: Path) -> Path:
    """Create workspace with v2.8 typed events."""
    ws = tmp_path / ".ai-workspace"
    events_dir = ws / "events"
    events_dir.mkdir(parents=True)

    events = [
        # Lifecycle events
        {
            "timestamp": "2026-02-23T08:00:00",
            "event_type": "edge_started",
            "project": "test",
            "edge": "design→code",
            "feature": "REQ-F-001",
        },
        {
            "timestamp": "2026-02-23T08:05:00",
            "event_type": "project_initialized",
            "project": "test",
            "profile": "standard",
            "graph_edges": ["intent→req", "req→design"],
        },
        {
            "timestamp": "2026-02-23T08:10:00",
            "event_type": "checkpoint_created",
            "project": "test",
            "checkpoint_id": "chk-001",
            "edge": "design→code",
            "feature": "REQ-F-001",
        },
        {
            "timestamp": "2026-02-23T08:15:00",
            "event_type": "review_completed",
            "project": "test",
            "edge": "req→design",
            "feature": "REQ-F-001",
            "reviewer": "human",
            "outcome": "approved",
        },
        {
            "timestamp": "2026-02-23T08:20:00",
            "event_type": "gaps_validated",
            "project": "test",
            "total_gaps": 10,
            "resolved_gaps": 8,
            "unresolved_gaps": 2,
        },
        {
            "timestamp": "2026-02-23T08:25:00",
            "event_type": "release_created",
            "project": "test",
            "version": "1.0.0",
            "req_coverage": "95%",
            "features_included": ["REQ-F-001"],
        },
        # Sensory events
        {
            "timestamp": "2026-02-23T09:00:00",
            "event_type": "interoceptive_signal",
            "project": "test",
            "signal_type": "convergence_rate",
            "measurement": "0.3/h",
            "threshold": "0.5/h",
        },
        {
            "timestamp": "2026-02-23T09:05:00",
            "event_type": "exteroceptive_signal",
            "project": "test",
            "source": "npm_audit",
            "signal_type": "vulnerability",
            "payload": "CVE-2026-001",
        },
        {
            "timestamp": "2026-02-23T09:10:00",
            "event_type": "affect_triage",
            "project": "test",
            "signal_ref": "intero-001",
            "triage_result": "escalate",
            "rationale": "below threshold",
        },
        # Multi-agent events
        {
            "timestamp": "2026-02-23T10:00:00",
            "event_type": "claim_rejected",
            "project": "test",
            "agent_id": "agent-002",
            "edge": "design→code",
            "reason": "already claimed",
        },
        {
            "timestamp": "2026-02-23T10:05:00",
            "event_type": "edge_released",
            "project": "test",
            "agent_id": "agent-001",
            "edge": "design→code",
        },
        {
            "timestamp": "2026-02-23T10:10:00",
            "event_type": "claim_expired",
            "project": "test",
            "agent_id": "agent-001",
            "edge": "code→tests",
            "expiry_reason": "timeout",
        },
        {
            "timestamp": "2026-02-23T10:15:00",
            "event_type": "convergence_escalated",
            "project": "test",
            "edge": "code→tests",
            "reason": "no progress",
            "escalated_to": "human",
        },
        # Enriched iteration_completed (with delta)
        {
            "timestamp": "2026-02-23T11:00:00",
            "event_type": "iteration_completed",
            "project": "test",
            "edge": "design→code",
            "feature": "REQ-F-001",
            "iteration": 3,
            "evaluators": {"agent": "pass"},
            "context_hash": "sha256:xyz",
            "encoding": {"mode": "constructive", "valence": "+", "active_units": 3},
            "source_findings": ["gap in auth"],
            "process_gaps": ["missing test"],
            "convergence_type": "delta_zero",
            "intent_engine_output": "reflex.log",
            "delta": 8,
        },
        # Failure observability events
        {
            "timestamp": "2026-02-23T11:05:00",
            "event_type": "evaluator_detail",
            "project": "test",
            "edge": "code↔tests",
            "feature": "REQ-F-001",
            "iteration": 1,
            "check_name": "tests_pass",
            "check_type": "deterministic",
            "result": "fail",
        },
        {
            "timestamp": "2026-02-23T11:10:00",
            "event_type": "command_error",
            "project": "test",
            "command": "pytest",
            "error": "exit code 1",
            "edge": "code↔tests",
        },
        {
            "timestamp": "2026-02-23T11:15:00",
            "event_type": "health_checked",
            "project": "test",
            "workspace": "/tmp/ws",
            "status": "healthy",
        },
        {
            "timestamp": "2026-02-23T11:20:00",
            "event_type": "iteration_abandoned",
            "project": "test",
            "edge": "code↔tests",
            "feature": "REQ-F-001",
            "iteration": 3,
            "reason": "max_retries",
        },
        {
            "timestamp": "2026-02-23T11:25:00",
            "event_type": "encoding_escalated",
            "project": "test",
            "edge": "code↔tests",
            "feature": "REQ-F-001",
            "previous_valence": "medium",
            "new_valence": "high",
            "trigger": "evaluator_failures",
        },
    ]
    (events_dir / "events.jsonl").write_text(
        "\n".join(json.dumps(e) for e in events) + "\n"
    )
    return ws


class TestV28EventParsing:
    def test_parses_edge_started(self, v28_events_workspace: Path):
        events = parse_events(v28_events_workspace)
        es = [e for e in events if isinstance(e, EdgeStartedEvent)]
        assert len(es) == 1
        assert es[0].edge == "design→code"

    def test_parses_project_initialized(self, v28_events_workspace: Path):
        events = parse_events(v28_events_workspace)
        pi = [e for e in events if isinstance(e, ProjectInitializedEvent)]
        assert len(pi) == 1
        assert pi[0].profile == "standard"
        assert len(pi[0].graph_edges) == 2

    def test_parses_checkpoint_created(self, v28_events_workspace: Path):
        events = parse_events(v28_events_workspace)
        cc = [e for e in events if isinstance(e, CheckpointCreatedEvent)]
        assert len(cc) == 1
        assert cc[0].checkpoint_id == "chk-001"

    def test_parses_review_completed(self, v28_events_workspace: Path):
        events = parse_events(v28_events_workspace)
        rc = [e for e in events if isinstance(e, ReviewCompletedEvent)]
        assert len(rc) == 1
        assert rc[0].outcome == "approved"

    def test_parses_gaps_validated(self, v28_events_workspace: Path):
        events = parse_events(v28_events_workspace)
        gv = [e for e in events if isinstance(e, GapsValidatedEvent)]
        assert len(gv) == 1
        assert gv[0].total_gaps == 10

    def test_parses_release_created(self, v28_events_workspace: Path):
        events = parse_events(v28_events_workspace)
        rc = [e for e in events if isinstance(e, ReleaseCreatedEvent)]
        assert len(rc) == 1
        assert rc[0].version == "1.0.0"

    def test_parses_interoceptive_signal(self, v28_events_workspace: Path):
        events = parse_events(v28_events_workspace)
        iss = [e for e in events if isinstance(e, InteroceptiveSignalEvent)]
        assert len(iss) == 1
        assert iss[0].signal_type == "convergence_rate"
        assert iss[0].threshold == "0.5/h"

    def test_parses_exteroceptive_signal(self, v28_events_workspace: Path):
        events = parse_events(v28_events_workspace)
        ess = [e for e in events if isinstance(e, ExteroceptiveSignalEvent)]
        assert len(ess) == 1
        assert ess[0].source == "npm_audit"

    def test_parses_affect_triage(self, v28_events_workspace: Path):
        events = parse_events(v28_events_workspace)
        at = [e for e in events if isinstance(e, AffectTriageEvent)]
        assert len(at) == 1
        assert at[0].triage_result == "escalate"

    def test_parses_claim_rejected(self, v28_events_workspace: Path):
        events = parse_events(v28_events_workspace)
        cr = [e for e in events if isinstance(e, ClaimRejectedEvent)]
        assert len(cr) == 1
        assert cr[0].agent_id == "agent-002"

    def test_parses_edge_released(self, v28_events_workspace: Path):
        events = parse_events(v28_events_workspace)
        er = [e for e in events if isinstance(e, EdgeReleasedEvent)]
        assert len(er) == 1
        assert er[0].agent_id == "agent-001"

    def test_parses_claim_expired(self, v28_events_workspace: Path):
        events = parse_events(v28_events_workspace)
        ce = [e for e in events if isinstance(e, ClaimExpiredEvent)]
        assert len(ce) == 1
        assert ce[0].expiry_reason == "timeout"

    def test_parses_convergence_escalated(self, v28_events_workspace: Path):
        events = parse_events(v28_events_workspace)
        ce = [e for e in events if isinstance(e, ConvergenceEscalatedEvent)]
        assert len(ce) == 1
        assert ce[0].escalated_to == "human"

    def test_parses_enriched_iteration_completed(self, v28_events_workspace: Path):
        events = parse_events(v28_events_workspace)
        ic = [e for e in events if isinstance(e, IterationCompletedEvent)]
        assert len(ic) == 1
        assert ic[0].encoding == {"mode": "constructive", "valence": "+", "active_units": 3}
        assert ic[0].source_findings == ["gap in auth"]
        assert ic[0].process_gaps == ["missing test"]
        assert ic[0].convergence_type == "delta_zero"
        assert ic[0].intent_engine_output == "reflex.log"
        assert ic[0].delta == 8

    def test_parses_evaluator_detail(self, v28_events_workspace: Path):
        events = parse_events(v28_events_workspace)
        ed = [e for e in events if isinstance(e, EvaluatorDetailEvent)]
        assert len(ed) == 1
        assert ed[0].edge == "code↔tests"
        assert ed[0].check_name == "tests_pass"
        assert ed[0].check_type == "deterministic"
        assert ed[0].result == "fail"

    def test_parses_command_error(self, v28_events_workspace: Path):
        events = parse_events(v28_events_workspace)
        ce = [e for e in events if isinstance(e, CommandErrorEvent)]
        assert len(ce) == 1
        assert ce[0].command == "pytest"
        assert ce[0].error == "exit code 1"

    def test_parses_health_checked(self, v28_events_workspace: Path):
        events = parse_events(v28_events_workspace)
        hc = [e for e in events if isinstance(e, HealthCheckedEvent)]
        assert len(hc) == 1
        assert hc[0].workspace == "/tmp/ws"
        assert hc[0].status == "healthy"

    def test_parses_iteration_abandoned(self, v28_events_workspace: Path):
        events = parse_events(v28_events_workspace)
        ia = [e for e in events if isinstance(e, IterationAbandonedEvent)]
        assert len(ia) == 1
        assert ia[0].edge == "code↔tests"
        assert ia[0].reason == "max_retries"
        assert ia[0].iteration == 3

    def test_parses_encoding_escalated(self, v28_events_workspace: Path):
        events = parse_events(v28_events_workspace)
        ee = [e for e in events if isinstance(e, EncodingEscalatedEvent)]
        assert len(ee) == 1
        assert ee[0].previous_valence == "medium"
        assert ee[0].new_valence == "high"
        assert ee[0].trigger == "evaluator_failures"

    def test_total_event_count(self, v28_events_workspace: Path):
        events = parse_events(v28_events_workspace)
        assert len(events) == 19

    def test_no_generic_event_fallbacks(self, v28_events_workspace: Path):
        """All 19 events should be typed, no generic Event fallback."""
        events = parse_events(v28_events_workspace)
        generics = [e for e in events if type(e) is Event]
        assert len(generics) == 0


# ── v2.5 events still parse as typed (backward compat) ──────────


class TestNestedDataFieldParsing:
    """E2E runs emit some events with typed fields nested inside 'data'."""

    def test_evaluator_detail_nested_in_data(self, tmp_path: Path):
        ws = tmp_path / ".ai-workspace"
        events_dir = ws / "events"
        events_dir.mkdir(parents=True)
        event = {
            "event_type": "evaluator_detail",
            "timestamp": "2026-02-23T08:00:01",
            "project": "test",
            "data": {
                "feature": "REQ-F-CONV-001",
                "edge": "code↔unit_tests",
                "iteration": 1,
                "check_name": "tests_pass",
                "check_type": "deterministic",
                "result": "fail",
            },
        }
        (events_dir / "events.jsonl").write_text(json.dumps(event) + "\n")
        result = parse_events(ws)
        assert len(result) == 1
        assert isinstance(result[0], EvaluatorDetailEvent)
        assert result[0].edge == "code↔unit_tests"
        assert result[0].check_name == "tests_pass"
        assert result[0].result == "fail"
        assert result[0].iteration == 1

    def test_top_level_fields_take_precedence_over_nested(self, tmp_path: Path):
        ws = tmp_path / ".ai-workspace"
        events_dir = ws / "events"
        events_dir.mkdir(parents=True)
        event = {
            "event_type": "evaluator_detail",
            "timestamp": "2026-02-23T08:00:01",
            "project": "test",
            "edge": "top-level-edge",
            "data": {"edge": "nested-edge", "check_name": "tests_pass"},
        }
        (events_dir / "events.jsonl").write_text(json.dumps(event) + "\n")
        result = parse_events(ws)
        assert isinstance(result[0], EvaluatorDetailEvent)
        # Top-level should win
        assert result[0].edge == "top-level-edge"
        # Nested-only field should still be extracted
        assert result[0].check_name == "tests_pass"


class TestV25EventsStillWork:
    def test_v25_edge_started_now_typed(self, tmp_path: Path):
        """edge_started was generic in v2.5, now typed in v2.8."""
        ws = tmp_path / ".ai-workspace"
        events_dir = ws / "events"
        events_dir.mkdir(parents=True)
        events = [
            {"timestamp": "2026-02-01T10:00:00", "event_type": "edge_started", "project": "test"},
        ]
        (events_dir / "events.jsonl").write_text(json.dumps(events[0]) + "\n")
        result = parse_events(ws)
        assert len(result) == 1
        # Now typed as EdgeStartedEvent instead of generic Event
        assert isinstance(result[0], EdgeStartedEvent)


# ── IntentEngine Classification ──────────────────────────────────


class TestClassifyIntentEngineOutput:
    def test_reflex_log_types(self):
        for t in ["iteration_completed", "edge_converged", "evaluator_ran",
                   "edge_started", "checkpoint_created", "edge_released",
                   "interoceptive_signal", "telemetry_signal_emitted",
                   "evaluator_detail", "command_error", "health_checked",
                   "artifact_modified"]:
            assert classify_intent_engine_output(t) == "reflex.log", f"{t} should be reflex.log"

    def test_spec_event_log_types(self):
        for t in ["spec_modified", "feature_spawned", "feature_folded_back",
                   "finding_raised", "project_initialized", "gaps_validated",
                   "release_created", "exteroceptive_signal", "affect_triage",
                   "encoding_escalated"]:
            assert classify_intent_engine_output(t) == "specEventLog", f"{t} should be specEventLog"

    def test_escalate_types(self):
        for t in ["intent_raised", "convergence_escalated", "review_completed",
                   "claim_rejected", "claim_expired",
                   "iteration_abandoned"]:
            assert classify_intent_engine_output(t) == "escalate", f"{t} should be escalate"

    def test_unknown_type(self):
        assert classify_intent_engine_output("something_unknown") == "unclassified"

    def test_reflex_includes_failure_observability(self):
        for t in ["evaluator_detail", "command_error", "health_checked"]:
            assert classify_intent_engine_output(t) == "reflex.log"

    def test_escalate_includes_iteration_abandoned(self):
        assert classify_intent_engine_output("iteration_abandoned") == "escalate"

    def test_spec_includes_encoding_escalated(self):
        assert classify_intent_engine_output("encoding_escalated") == "specEventLog"


# ── Feature Vector v2.8 Parsing ──────────────────────────────────


@pytest.fixture
def v28_features_workspace(tmp_path: Path) -> Path:
    """Create workspace with v2.8 feature vectors."""
    ws = tmp_path / ".ai-workspace"
    features_dir = ws / "features" / "active"
    features_dir.mkdir(parents=True)

    # Feature with encoding and edge timestamps
    (features_dir / "REQ-F-001.yml").write_text(yaml.dump({
        "feature": "REQ-F-001",
        "title": "Auth Feature",
        "status": "converged",
        "vector_type": "feature",
        "profile": "standard",
        "encoding": {
            "mode": "constructive",
            "valence": "+",
            "active_units": 3,
        },
        "trajectory": {
            "requirements": {
                "status": "converged",
                "iteration": 1,
                "started_at": "2026-02-20T10:00:00",
                "converged_at": "2026-02-20T14:30:00",
                "convergence_type": "delta_zero",
            },
            "design": {
                "status": "converged",
                "iteration": 2,
                "started_at": "2026-02-21T09:00:00",
                "converged_at": "2026-02-22T16:00:00",
                "convergence_type": "delta_zero",
                "escalations": ["human review requested"],
            },
        },
    }))

    # Feature without v2.8 fields (backward compat)
    (features_dir / "REQ-F-002.yml").write_text(yaml.dump({
        "feature": "REQ-F-002",
        "title": "Legacy Feature",
        "status": "in_progress",
        "vector_type": "feature",
        "trajectory": {
            "requirements": {"status": "converged", "iteration": 1},
        },
    }))

    return ws


class TestFeatureVectorV28Parsing:
    def test_parses_encoding(self, v28_features_workspace: Path):
        vectors = parse_feature_vectors(v28_features_workspace)
        v = next(v for v in vectors if v.feature_id == "REQ-F-001")
        assert v.encoding is not None
        assert v.encoding["mode"] == "constructive"
        assert v.encoding["active_units"] == 3

    def test_parses_edge_timestamps(self, v28_features_workspace: Path):
        vectors = parse_feature_vectors(v28_features_workspace)
        v = next(v for v in vectors if v.feature_id == "REQ-F-001")
        req_traj = v.trajectory["requirements"]
        assert req_traj.started_at is not None
        assert req_traj.converged_at is not None
        assert req_traj.convergence_type == "delta_zero"

    def test_parses_escalations(self, v28_features_workspace: Path):
        vectors = parse_feature_vectors(v28_features_workspace)
        v = next(v for v in vectors if v.feature_id == "REQ-F-001")
        design_traj = v.trajectory["design"]
        assert len(design_traj.escalations) == 1
        assert design_traj.escalations[0] == "human review requested"

    def test_missing_v28_fields_default(self, v28_features_workspace: Path):
        vectors = parse_feature_vectors(v28_features_workspace)
        v = next(v for v in vectors if v.feature_id == "REQ-F-002")
        assert v.encoding is None
        req_traj = v.trajectory["requirements"]
        assert req_traj.started_at is None
        assert req_traj.converged_at is None
        assert req_traj.convergence_type == ""
        assert req_traj.escalations == []


# ── Topology v2.8 Parsing (tolerance/breach) ─────────────────────


@pytest.fixture
def v28_topology_workspace(tmp_path: Path) -> Path:
    """Create workspace with v2.8 topology (tolerances)."""
    ws = tmp_path / ".ai-workspace"
    graph_dir = ws / "graph"
    graph_dir.mkdir(parents=True)

    (graph_dir / "graph_topology.yml").write_text(yaml.dump({
        "asset_types": {
            "intent": {"description": "Business intent"},
            "code": {"description": "Implementation"},
        },
        "transitions": [
            {"source": "intent", "target": "code"},
        ],
        "constraint_dimensions": {
            "performance": {
                "mandatory": True,
                "resolves_via": "adr",
                "tolerance": "≤ 5% degradation",
                "breach_status": "ok",
            },
            "security": {
                "mandatory": True,
                "resolves_via": "adr",
                "tolerance": "zero known CVEs",
                "breach_status": "breached",
            },
            "legacy_dim": {
                "mandatory": False,
                "resolves_via": "design_section",
            },
        },
    }))
    return ws


class TestTopologyV28Parsing:
    def test_parses_tolerance(self, v28_topology_workspace: Path):
        topo = parse_graph_topology(v28_topology_workspace)
        perf = next(d for d in topo.constraint_dimensions if d.name == "performance")
        assert perf.tolerance == "≤ 5% degradation"

    def test_parses_breach_status(self, v28_topology_workspace: Path):
        topo = parse_graph_topology(v28_topology_workspace)
        sec = next(d for d in topo.constraint_dimensions if d.name == "security")
        assert sec.breach_status == "breached"

    def test_missing_tolerance_defaults_empty(self, v28_topology_workspace: Path):
        topo = parse_graph_topology(v28_topology_workspace)
        legacy = next(d for d in topo.constraint_dimensions if d.name == "legacy_dim")
        assert legacy.tolerance == ""
        assert legacy.breach_status == ""


# ── Convergence Delta Curve Projection ────────────────────────────


class TestConvergenceDeltaCurve:
    def test_delta_curve_from_iteration_events(self):
        """build_convergence_table_from_events extracts delta_curve."""
        events = parse_events  # we build events manually below
        from genesis_monitor.models.events import IterationCompletedEvent, EdgeStartedEvent

        evts = [
            EdgeStartedEvent(
                event_type="edge_started", project="test",
                edge="code↔tests", feature="REQ-F-001",
            ),
            IterationCompletedEvent(
                event_type="iteration_completed", project="test",
                edge="code↔tests", feature="REQ-F-001",
                iteration=1, delta=8,
            ),
            IterationCompletedEvent(
                event_type="iteration_completed", project="test",
                edge="code↔tests", feature="REQ-F-001",
                iteration=2, delta=0,
            ),
        ]
        rows = build_convergence_table_from_events(evts)
        assert len(rows) == 1
        assert rows[0].delta_curve == [8, 0]

    def test_delta_curve_empty_when_no_delta(self):
        """delta_curve stays empty when iterations have no delta."""
        from genesis_monitor.models.events import IterationCompletedEvent

        evts = [
            IterationCompletedEvent(
                event_type="iteration_completed", project="test",
                edge="design→code", feature="REQ-F-001",
                iteration=1,
            ),
        ]
        rows = build_convergence_table_from_events(evts)
        assert len(rows) == 1
        assert rows[0].delta_curve == []
