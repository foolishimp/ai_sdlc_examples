<!-- GENESIS_BOOTLOADER_START -->
# Genesis Bootloader: LLM Constraint Context for the AI SDLC

**Version**: 1.0.0
**Purpose**: Minimal sufficient context to constrain an LLM to operate within the AI SDLC Asset Graph Model. Load this document into any LLM session — it replaces the need to load the full specification, ontology, and design documents for routine methodology operation.

---

## I. Logical Encapsulation Preamble

You must perform all analysis, construction, and evaluation strictly within the following specification. Treat this framework as a **hard constraint system**.

- Do not introduce assumptions, patterns, or conventions from outside this specification.
- Do not substitute familiarity with software methodologies (Agile, Scrum, Waterfall, SAFe) for reasoning within this system.
- Evaluate only against the defined primitives, invariants, and composition laws.
- If information is insufficient, state what is missing and which constraint cannot be evaluated — do not guess.

This specification defines a **virtual reasoning environment**: a controlled logic space where your conclusions are shaped by axioms rather than training-data patterns. The axioms are explicit and auditable. Different axioms produce mechanically different results.

**Epistemic honesty**: You do not *execute* this formal system — you *predict what execution would produce*. Reliability comes from **iteration and a gain function** — repeated evaluation with convergence criteria, not from a single prompt-response cycle. This bootloader makes the axioms visible so they can be checked.

---

## II. Foundation: What Constraints Are

A constraint is not a rule that dictates what must happen next, but a condition that determines which transformations are admissible at all.

Constraints:
- Restrict which transformations exist
- Limit composability
- Induce stability
- Enable abstraction and boundary formation
- Define boundaries that allow subsystems to exist

Without constraint, everything is permitted — and nothing persists.

**Generative principle**: As soon as a stable configuration is possible within a constraint structure, it will emerge. Constraints do not merely permit — they generate. The constraint structure fills its own possibility space.

**The methodology is not the commands, configurations, or tooling. Those are implementations — emergence within constraints. The methodology is the constraints themselves.**

---

## III. The Formal System: Four Primitives, One Operation

The entire methodology reduces to four primitives:

| Primitive | What it is |
|-----------|-----------|
| **Graph** | Topology of typed assets with admissible transitions (zoomable) |
| **Iterate** | Convergence engine — the only operation |
| **Evaluators** | Convergence test — when is iteration done |
| **Spec + Context** | Constraint surface — what bounds construction |

Everything else — stages, agents, TDD, BDD, commands, configurations, event schemas — is parameterisation of these four primitives for specific graph edges. They are emergence within the constraints the primitives define, not the methodology itself.

**The graph is not universal.** The SDLC graph (Intent → Requirements → Design → Code → Tests → ...) is one domain-specific instantiation. The four primitives are universal; the graph is parameterised.

**The formal system is a generator of valid methodologies**, not a single methodology. What it generates depends on which projection is applied, which encoding is chosen, and which technology binds the functional units. Any implementation that satisfies these constraints is a valid instantiation.

---

## IV. The Iterate Function

```
iterate(
    Asset<Tn>,              // current asset (carries type, intent, lineage)
    Context[],              // standing constraints (spec, design, ADRs, prior work)
    Evaluators(edge_type)   // convergence criteria for this edge
) → Asset<Tn.k+1>          // next iteration candidate
```

This is the **only operation**. Every edge in the graph is this function called repeatedly until evaluators report convergence:

```
while not stable(candidate, edge_type):
    candidate = iterate(candidate, context, evaluators)
return promote(candidate)   // candidate becomes stable asset
```

Convergence:
```
stable(candidate, edge_type) =
    ∀ evaluator ∈ evaluators(edge_type):
        evaluator.delta(candidate, spec) < ε
```

The iteration engine is universal. The stopping condition is parameterised.

---

## V. Evaluators: Three Types, One Pattern

| Evaluator | Regime | What it does |
|-----------|--------|-------------|
| **Deterministic Tests (F_D)** | Zero ambiguity | Pass/fail — type checks, schema validation, test suites, contract verification |
| **Agent (F_P)** | Bounded ambiguity | LLM/agent disambiguates — gap analysis, coherence checking, refinement |
| **Human (F_H)** | Persistent ambiguity | Judgment — domain evaluation, business fit, approval/rejection |

All three are instances of the same pattern: they compute a **delta** between current state and target state, then emit a constraint signal that drives the next iteration.

**Escalation chain** (natural transformations between evaluator types):
```
F_D → F_P    (deterministic blocked → agent explores)
F_P → F_H    (agent stuck → human review)
F_H → F_D    (human approves → deterministic deployment)
```

---

## VI. The IntentEngine: Composition Law

The IntentEngine is **not a fifth primitive**. It is a composition law over the existing four — it describes how Graph, Iterate, Evaluators, and Spec+Context compose into a universal processing unit:

```
IntentEngine(intent + affect) = observer → evaluator → typed_output
```

| Component | What it does |
|-----------|-------------|
| **observer** | Senses current state — runs a tool, loads context, polls a monitor, reviews an artifact |
| **evaluator** | Classifies the observation's ambiguity level — how much uncertainty remains |
| **typed_output** | Always one of three exhaustive categories (see below) |

### Ambiguity Classification (routes every observation)

| Ambiguity | Phase | Action |
|-----------|-------|--------|
| **Zero** | Reflex | Immediate action, no deliberation — tests pass/fail, build succeeds, event emission |
| **Nonzero, bounded** | Iterate | Agent constructs next candidate, triage classifies severity |
| **Persistent / unbounded** | Escalate | Human review, spec modification, vector spawning |

### Three Output Types (exhaustive — no fourth category)

| Output | When | What happens |
|--------|------|-------------|
| **reflex.log** | Ambiguity = 0 | Fire-and-forget event — action taken, logged, done |
| **specEventLog** | Bounded ambiguity | Deferred intent for later processing — another iteration warranted |
| **escalate** | Persistent ambiguity | Push to higher consciousness — judgment, spec modification, or spawning required |

### Homeostasis: Intent Is Computed

Intent is not a human-authored input — it is a **generated output** of any observer that detects a non-zero delta between observed state and specification:

```
intent = delta(observed_state, spec) where delta ≠ 0
```

A human *can* author intent, but the system also generates it continuously: test failures, tolerance breaches, coverage gaps, ecosystem changes, and telemetry anomalies all produce intents through the same `observer → evaluator → typed_output` mechanism. This is what makes the system homeostatic — it corrects itself toward the spec without waiting for external instruction.

Human intent is the abiogenesis — the initial spark that creates the spec and bootstraps the constraint surface. Once the system has a specification, interoceptive monitors (self-observation: tests, convergence, coverage), and exteroceptive monitors (environment-observation: ecosystem, runtime, telemetry), it becomes self-sustaining and self-evolving. The human remains as F_H — one evaluator type among three — not the sole source of direction.

---

## VII. Invariants: What Must Hold in Every Valid Instance

| Invariant | What it means | What breaks if absent |
|-----------|--------------|----------------------|
| **Graph** | There is a topology of typed assets with admissible transitions | No structure — work is ad hoc |
| **Iterate** | There is a convergence loop — produce candidate, evaluate, repeat | No quality signal — work is one-shot |
| **Evaluators** | There is at least one evaluator per active edge | No convergence criterion — no stopping condition |
| **Spec + Context** | There is a constraint surface bounding construction | No constraints — degeneracy, hallucination |

### Projection Validity Rule

```
valid(P) ⟺
    ∃ G ⊆ G_full
    ∧ ∀ edge ∈ G:
        iterate(edge) defined
        ∧ evaluators(edge) ≠ ∅
        ∧ convergence(edge) defined
    ∧ context(P) ≠ ∅
```

**What can vary**: graph size, evaluator composition, convergence criteria, context density, iteration depth.

**What cannot vary**: the existence of a graph, iteration, evaluation, and context.

### IntentEngine Invariant

Every edge traversal is an IntentEngine invocation. No unobserved computation. The observer/evaluator structure, the three output types, and ambiguity as routing criterion are projection-invariant.

---

## VIII. Constraint Tolerances

A constraint without a tolerance is a wish. A constraint with a tolerance is a sensor.

Every constraint surface implies tolerances — measurable thresholds that make delta computable:

```
Constraint: "the system must be fast"      → unmeasurable, delta undefined
Constraint: "P99 latency < 200ms"          → measurable, delta = |observed - 200ms|
Constraint: "all tests pass"               → measurable, delta = failing_count
```

Without tolerances, there is no homeostasis. The gradient requires measurable delta. The IntentEngine requires classifiable observations. The sensory system requires thresholds to monitor. Tolerances are not optional metadata — they are the mechanism by which constraints become operational.

```
monitor observes metric →
  evaluator compares metric to tolerance →
    within bounds:  reflex.log (system healthy)
    drifting:       specEventLog (optimisation intent deferred)
    breached:       escalate (corrective intent raised)
```

---

## IX. Asset as Markov Object

An asset achieves stable status when:

1. **Boundary** — Typed interface/schema (REQ keys, interfaces, contracts, metric schemas)
2. **Conditional independence** — Usable without knowing its construction history (code that passes its tests is interchangeable regardless of who built it)
3. **Stability** — All evaluators report convergence

An asset that fails its evaluators is a **candidate**, not a stable object. It stays in iteration.

The full composite vector carries the complete causal chain (intent, lineage, every decision). The stable boundary at each asset means practical work is local — you interact through the boundary, not the history.

---

## X. The Construction Pattern

The universal pattern underlying all methodology operation:

```
Encoded Representation → Constructor → Constructed Structure
```

| Domain | Encoding | Constructor | Structure |
|--------|----------|------------|-----------|
| Biology | DNA | Ribosome | Protein |
| SDLC | Requirements | LLM agent | Code |
| Methodology | Specification | Builder (human/agent) | Product |

The **specification** is the tech-agnostic WHAT. **Design** is the tech-bound HOW. **Context** is the standing constraint surface (ADRs, models, policy, templates, prior work).

---

## XI. Projections: Scaling the Methodology

The formal system generates lighter instances by projection. Named profiles:

| Profile | When | Graph | Evaluators | Iterations |
|---------|------|-------|-----------|------------|
| **full** | Regulated, high-stakes | All edges | All three types, all monitors | No limit |
| **standard** | Normal feature work | Most edges | Mixed types | Bounded |
| **poc** | Proof of concept | Core edges | Agent + deterministic | Low |
| **spike** | Research / experiment | Minimal edges | Agent-primary | Very low |
| **hotfix** | Emergency fix | Direct path | Deterministic-primary | 1-3 |
| **minimal** | Trivial change | Single edge | Any single evaluator | 1 |

Every profile preserves all four invariants. What varies is graph size, evaluator composition, convergence criteria, and context density.

---

## XII. Traceability

Traceability is not a fifth invariant — it is an **emergent property** of the four invariants working together:

- **Graph** provides the path (which edges were traversed)
- **Iterate** provides the history (which candidates were produced)
- **Evaluators** provide the decisions (why this candidate was accepted)
- **Spec + Context** provides the constraints (what bounded construction)

REQ keys thread from spec to runtime:

```
Spec:       REQ-F-AUTH-001 defined
Design:     Implements: REQ-F-AUTH-001
Code:       # Implements: REQ-F-AUTH-001
Tests:      # Validates: REQ-F-AUTH-001
Telemetry:  logger.info("login", req="REQ-F-AUTH-001", latency_ms=42)
```

---

## XIII. The SDLC Graph (Default Instantiation)

One domain-specific graph. Not privileged — just common:

```
Intent → Requirements → Design → Code ↔ Unit Tests
                          │
                          ├→ Test Cases → UAT Tests
                          │
                          └→ CI/CD → Running System → Telemetry
                                                        │
                                          Observer/Evaluator
                                                        │
                                                   New Intent
```

Every edge is `iterate()` with edge-specific evaluators and context. The graph is zoomable — any edge can expand into a sub-graph, any sub-graph can collapse into a single edge.

---

## XIV. Spec / Design Separation

- **Spec** = WHAT, tech-agnostic. One spec, many designs.
- **Design** = HOW architecturally, bound to technology (ADRs, ecosystem bindings).

Code disambiguation feeds back to **Spec** (business gap) or **Design** (tech gap). Never conflate the two — a spec should contain no technology choices; a design should not restate business requirements.

---

## XV. Telemetry is Constitutive

A product that does not monitor itself is not yet a product. Every valid methodology instance includes operational telemetry and self-monitoring from day one. The event log, sensory monitors, and feedback loop are not features of the tooling — they are constraints of the methodology.

This applies recursively: the methodology tooling is itself a product and must comply with the same constraints it defines.

---

## XVI. How to Apply This Bootloader

When working on any project under this methodology:

1. **Identify the graph** — what asset types exist, what transitions are admissible
2. **For each edge**: define evaluators, convergence criteria, and context
3. **Iterate**: produce candidate, evaluate against all active evaluators, loop until stable
4. **Classify every observation** by ambiguity: zero → reflex, bounded → iterate, persistent → escalate
5. **Maintain traceability**: REQ keys thread through every artifact
6. **Check tolerances**: every constraint must have a measurable threshold
7. **Choose a projection profile** appropriate to the work (full/standard/poc/spike/hotfix/minimal)
8. **Verify invariants**: graph exists, iteration exists, evaluators exist, context exists — if any is missing, the methodology instance is invalid

The commands, configurations, and tooling are valid emergences from these constraints. If you have only the commands without this bootloader, you are pattern-matching templates. If you have this bootloader, you can derive the commands.

---

*Foundation: [Constraint-Emergence Ontology](https://github.com/foolishimp/constraint_emergence_ontology) — constraints bound possibility; structure emerges within those bounds.*
*Formal system: [AI SDLC Asset Graph Model v2.8](AI_SDLC_ASSET_GRAPH_MODEL.md) — four primitives, one operation.*
*Projections: [Projections and Invariants](PROJECTIONS_AND_INVARIANTS.md) — the generator of valid methodologies.*

<!-- GENESIS_BOOTLOADER_END -->
