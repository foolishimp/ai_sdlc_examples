# AISDLC Implementation Requirements
## Categorical Data Mapping & Computation Engine (CDME)

**Project**: data_mapper.test02
**Version**: 7.2
**Status**: Approved for Design Stage
**Date**: 2025-12-10

---

## 1. Document Purpose

This document contains formal AISDLC requirements extracted from the CDME v7.2 specification. All requirements trace back to the core intents defined in INTENT.md:

- **INT-001**: Categorical Data Mapping Engine (Core Topology)
- **INT-002**: Core Philosophy (The 10 Axioms)
- **INT-003**: Universal Applicability (Computations as Morphisms)
- **INT-004**: AI Assurance Layer (Hallucination Prevention)

---

## 2. Requirements by Domain

### 2.1. Logical Topology (LDM)

#### REQ-LDM-01: Strict Graph Structure

**Priority**: Critical
**Type**: Functional
**Traces To**: INT-001, INT-002 (Axiom 1: Schema is a Topology)

**Description**: The Logical Data Model (LDM) must be defined as a directed multigraph where entities are Objects (nodes) and relationships are Morphisms (edges).

**Acceptance Criteria**:
- LDM can represent multiple edges between the same pair of entities (multigraph)
- Each entity is a valid Object with identity morphism (Id_E: E → E)
- All relationships are directed edges with explicit source and target
- The graph structure is queryable and traversable programmatically

---

#### REQ-LDM-02: Cardinality Types

**Priority**: Critical
**Type**: Functional
**Traces To**: INT-001, INT-002 (Axiom 3: Transformations are Paths)

**Description**: Every edge (morphism) in the LDM must declare its categorical type to enable proper context lifting and type safety.

**Acceptance Criteria**:
- Every morphism has exactly one cardinality type from: `1:1`, `N:1`, `1:N`
- `1:1` morphisms are validated as isomorphisms/bijections
- `N:1` morphisms are validated as standard functions
- `1:N` morphisms are validated as Kleisli arrows (List monad context)
- Cardinality metadata is queryable at runtime

---

#### REQ-LDM-03: Strict Dot Hierarchy & Composition Validity

**Priority**: Critical
**Type**: Functional
**Traces To**: INT-001, INT-002 (Axiom 3: Transformations are Paths), INT-004 (AI Assurance)

**Description**: A path must be expressed using Symbolic Dot Notation (e.g., `Entity.Relationship.Attribute`). A path `a.b.c` is valid if and only if all composition rules are satisfied.

**Acceptance Criteria**:
- Each referenced morphism (`a → b`, `b → c`) is defined in the LDM topology
- The codomain of each morphism equals the domain of the next (or satisfies subtype rules per REQ-TYP-06)
- Grain safety is not violated along the path (REQ-TRV-02)
- The traversing principal has permission on each morphism (REQ-LDM-05)
- Invalid paths are rejected at definition/compile time with clear error messages
- Error messages identify which composition constraint failed

---

#### REQ-LDM-04: Algebraic Aggregation (Monoid Laws)

**Priority**: Critical
**Type**: Functional
**Traces To**: INT-001, INT-002 (Axiom 5: Grain is a Dimension)

**Description**: Any function defined for aggregation/fold operations must satisfy Monoid Laws to guarantee distributed consistency and re-aggregation safety.

**Acceptance Criteria**:
- Aggregation function has an associative binary operation (a ⊗ b) ⊗ c = a ⊗ (b ⊗ c)
- Aggregation function has an identity element `e` where `e ⊗ a = a ⊗ e = a`
- Non-associative aggregates (e.g., "First Value" without deterministic ordering) are rejected
- Monoid properties are validated at mapping definition time
- Empty input sets return the identity element (REQ-LDM-04-A)

---

#### REQ-LDM-04-A: Empty Aggregation Behaviour

**Priority**: High
**Type**: Functional
**Traces To**: INT-001, INT-002 (Axiom 5: Grain is a Dimension)

**Description**: Aggregation over an empty input set must yield the identity element defined for that monoid.

**Acceptance Criteria**:
- Empty aggregation returns identity element (e.g., 0 for Sum, 1 for Product)
- Behavior is deterministic and documented per monoid type
- No exceptions or null values returned for empty input
- Identity element is configurable per aggregation type

---

#### REQ-LDM-05: Topological Access Control

**Priority**: High
**Type**: Non-Functional (Security)
**Traces To**: INT-001, INT-002 (Axiom 8: Topology is the Guardrail)

**Description**: The LDM must support Role-Based Access Control (RBAC) on morphisms. Access control implies denial of traversal - morphisms without permission do not exist for that principal.

**Acceptance Criteria**:
- Morphisms have RBAC metadata (roles/permissions)
- Unauthorized users cannot traverse restricted morphisms
- Path validation respects access control (morphism appears non-existent)
- Denied traversal attempts are logged for audit
- Access control is evaluated at compile time, not runtime

---

#### REQ-LDM-06: Grain & Type Metadata

**Priority**: Critical
**Type**: Functional
**Traces To**: INT-001, INT-002 (Axiom 5: Grain is a Dimension, Axiom 7: Types are Contracts)

**Description**: Every Entity in the LDM must be explicitly tagged with Grain (atomic, aggregate) and typed attributes using the Extended Type System.

**Acceptance Criteria**:
- Every entity has explicit Grain metadata (e.g., Atomic, Daily Aggregate, Monthly Aggregate)
- Grain metadata is used for composition validation, not just documentation
- All entity attributes are typed per REQ-TYP-01 (Extended Type System)
- Type metadata includes semantic types where applicable (REQ-TYP-07)
- Grain hierarchy is queryable for validation of multi-level aggregation

---

### 2.2. Physical Binding & System Boundaries (PDM)

#### REQ-PDM-01: Functorial Mapping

**Priority**: High
**Type**: Functional
**Traces To**: INT-001, INT-002 (Axiom 2: Separation of Concerns)

**Description**: The system must abstract physical storage as a Functor mapping LDM to PDM. Re-pointing storage must not require business logic changes.

**Acceptance Criteria**:
- Logical entities can be re-bound to different storage (file → table → API) without LDM changes
- Physical bindings are defined separately from logical topology
- Business logic references only logical entity names, not physical paths
- Storage re-binding does not invalidate existing mappings

---

#### REQ-PDM-02: Generation Grain

**Priority**: High
**Type**: Functional
**Traces To**: INT-001, INT-002 (Axiom 4: Data Has Contextual Extent)

**Description**: The PDM must declare the "physics" of the source system's data generation grain.

**Acceptance Criteria**:
- Generation grain is one of: `Event`, `Snapshot`
- Generation grain is validated against allowed list (extensible via configuration)
- Grain declaration is required for all physical sources
- Invalid grain values are rejected at PDM definition time

---

#### REQ-PDM-02-A: Generation Grain Semantics

**Priority**: High
**Type**: Functional
**Traces To**: INT-001, INT-002 (Axiom 4: Data Has Contextual Extent)

**Description**: The system must enforce correct semantics for Event and Snapshot generation grains.

**Acceptance Criteria**:
- **Event semantics**: Immutable occurrences sliced by temporal windows
- **Snapshot semantics**: State at a point in time where each snapshot supersedes prior states
- Boundaries align with generation grain semantics (REQ-PDM-03)
- Incompatible operations for each grain type are rejected (e.g., "as-of" queries on pure Event streams without snapshots)

---

#### REQ-PDM-03: Boundary Definition

**Priority**: High
**Type**: Functional
**Traces To**: INT-001, INT-002 (Axiom 4: Data Has Contextual Extent)

**Description**: The PDM must define how continuous data flow is sliced into processing epochs, consistent with Generation Grain semantics.

**Acceptance Criteria**:
- Boundary definition specifies slicing strategy (temporal window, version, batch ID)
- Boundaries are consistent with Generation Grain (Event → time windows, Snapshot → versions)
- Multiple boundary strategies supported (daily, hourly, incremental)
- Boundary definitions are explicit, not implicit or defaulted

---

#### REQ-PDM-04: Lookup Binding

**Priority**: Medium
**Type**: Functional
**Traces To**: INT-001, INT-002 (Axiom 6: Lookups are Arguments)

**Description**: The PDM must support binding Reference Data (lookups) as either data-backed (tables/files) or logic-backed (functions).

**Acceptance Criteria**:
- Lookups can be bound to physical tables/files
- Lookups can be bound to pure functions (logic-backed)
- Lookup versioning is enforced (REQ-INT-06)
- Both binding types provide same logical interface

---

#### REQ-PDM-05: Temporal Binding

**Priority**: Medium
**Type**: Functional
**Traces To**: INT-001, INT-002 (Axiom 4: Data Has Contextual Extent)

**Description**: The PDM must support Temporal Binding where a single Logical Entity maps to different Physical Tables as a function of Data Epoch.

**Acceptance Criteria**:
- Single logical entity can resolve to multiple physical tables by epoch
- Resolution is deterministic for a given epoch
- Temporal binding function is explicitly defined in PDM
- Schema compatibility is validated across temporal physical targets

---

### 2.3. Traversal Engine (TRV)

#### REQ-TRV-01: Context Lifting (Kleisli Lift)

**Priority**: Critical
**Type**: Functional
**Traces To**: INT-001, INT-002 (Axiom 3: Transformations are Paths), INT-003 (Universal Applicability)

**Description**: When traversing a `1:N` edge, the engine must lift execution context from Scalar to List, implementing proper flattening or explosion operations.

**Acceptance Criteria**:
- Scalar context automatically lifts to List context on `1:N` traversal
- Flattening/explosion is implemented via proper Kleisli arrow semantics
- Lifted context propagates correctly through subsequent morphisms
- Context can be collapsed via Monoidal Fold (REQ-TRV-01 + REQ-LDM-04)

---

#### REQ-TRV-02: Grain Safety

**Priority**: Critical
**Type**: Functional
**Traces To**: INT-001, INT-002 (Axiom 5: Grain is a Dimension), INT-004 (AI Assurance)

**Description**: The engine must block operations that combine attributes from incompatible grains without explicit aggregation.

**Acceptance Criteria**:
- Projecting attributes from incompatible grains into the same output entity is blocked
- Using attributes from incompatible grains in the same expression (arithmetic, concatenation) is blocked
- Joining entities at different grains without aggregation morphism is blocked
- Violations are detected at compile time with clear error messages
- Explicit aggregation morphisms allow grain mixing when valid

---

#### REQ-TRV-03: Boundary Alignment & Temporal Semantics

**Priority**: High
**Type**: Functional
**Traces To**: INT-001, INT-002 (Axiom 4: Data Has Contextual Extent)

**Description**: The engine must detect cross-boundary traversals (e.g., joining "Live Data" to "Prior Day Reference") and require declared temporal semantics.

**Acceptance Criteria**:
- Cross-boundary traversals are detected at compile time
- Mapping must declare temporal semantics (As-Of, Latest, Exact)
- Temporal semantics are applied during validation and execution planning
- Cross-boundary traversals without declared semantics are rejected
- Temporal semantics validation is part of topological correctness check

---

#### REQ-TRV-04: Operational Telemetry (Writer Monad)

**Priority**: Medium
**Type**: Non-Functional (Observability)
**Traces To**: INT-001

**Description**: The execution context must implement a Writer Effect to capture row counts, quality metrics, and latency stats at every node traversal.

**Acceptance Criteria**:
- Telemetry captured at every morphism execution
- Metrics include: row counts, latency, quality stats
- Telemetry does not affect determinism of computation
- Telemetry is exported via OpenLineage API
- Telemetry overhead is minimal (< 5% execution time)

---

#### REQ-TRV-05: Deterministic Reproducibility & Audit Traceability

**Priority**: Critical
**Type**: Functional
**Traces To**: INT-001, INT-002 (Axiom 6: Lookups are Arguments, Axiom 8: Topology is the Guardrail)

**Description**: Because transformations and lookups are immutable and versioned, any target value must be deterministically derivable from source inputs.

**Acceptance Criteria**:
- Same inputs + same mapping + same lookups → same outputs (bit-identical)
- All dependencies are versioned and immutable within execution context
- Execution is deterministic across multiple runs
- Audit trail captures all versions and inputs used
- Non-deterministic operations (random, timestamps) are explicitly forbidden or wrapped

---

#### REQ-TRV-06: Computational Cost Governance

**Priority**: High
**Type**: Non-Functional (Performance)
**Traces To**: INT-001

**Description**: The engine must implement a Cost Estimation Functor to estimate cardinality explosion and enforce execution budgets.

**Acceptance Criteria**:
- Cardinality budget is declared in Execution Artifact (Job Configuration)
- Cost estimation occurs before execution begins
- Budget includes: Max Output Rows, Max Join Depth, Max Intermediate Size
- Execution is blocked if estimated cost exceeds budget
- Budget violations are reported with cost breakdown

---

#### REQ-SHF-01: Sheaf / Context Consistency

**Priority**: Critical
**Type**: Functional
**Traces To**: INT-001, INT-002 (Axiom 4: Data Has Contextual Extent)

**Description**: When joining two data streams, their Contextual Fibers must share the same Epoch or use explicit temporal semantics, and have compatible partitioning.

**Acceptance Criteria**:
- Joins between same-epoch fibers are allowed
- Cross-epoch joins require explicit temporal semantics (REQ-TRV-03)
- Partitioning compatibility is validated (same partition key or explicit reconciliation)
- Incompatible fiber joins are rejected at validation time
- Rejection messages identify the fiber incompatibility

---

### 2.4. Integration & Synthesis (INT)

#### REQ-INT-01: Isomorphic Synthesis

**Priority**: High
**Type**: Functional
**Traces To**: INT-001, INT-003 (Universal Applicability)

**Description**: Users can define new attributes via Pure Functions over existing entities and attributes.

**Acceptance Criteria**:
- Pure functions can be registered as synthesis morphisms
- Functions are stateless and deterministic
- Functions respect type system (REQ-TYP-01)
- Synthesized attributes are first-class members of the entity
- Synthesis functions compose with other morphisms

---

#### REQ-INT-02: Subsequent Aggregation

**Priority**: Medium
**Type**: Functional
**Traces To**: INT-001, INT-002 (Axiom 5: Grain is a Dimension)

**Description**: The system must support aggregating over already-aggregated data (multi-level aggregation) under strict conditions.

**Acceptance Criteria**:
- Each aggregation level satisfies Monoid Laws (REQ-LDM-04)
- Grain hierarchy permits composition (Atomic → Daily → Monthly → Yearly)
- Morphism path explicitly encodes multi-level aggregation steps
- Invalid multi-level aggregations are rejected (e.g., non-composable monoids)
- Lineage captures each aggregation level

---

#### REQ-INT-03: Traceability

**Priority**: Critical
**Type**: Functional
**Traces To**: INT-001, INT-004 (AI Assurance - Triangulation)

**Description**: The system must provide full lineage, mapping every target value back to the Source Epoch, Entity, and morphism path that generated it.

**Acceptance Criteria**:
- Every target value traces to specific source entities and epochs
- Morphism path (traversal sequence) is recorded in lineage
- Lineage includes lookup versions used (REQ-INT-06)
- Lineage is exportable via OpenLineage API
- Lineage supports backward traversal (target → sources)

---

#### REQ-INT-04: Complex Business Logic

**Priority**: High
**Type**: Functional
**Traces To**: INT-001, INT-003 (Universal Applicability)

**Description**: The system must support advanced formulations within synthesis including conditionals, fallbacks, composition, and tuple construction.

**Acceptance Criteria**:
- Conditional expressions (if-then-else) are supported
- Prioritized fallback logic (if A present, else B, else C) is supported
- Multiple morphisms can compose into single synthesis
- Product/tuple types can be constructed from multiple inputs
- Complex logic is validated for type correctness and determinism

---

#### REQ-INT-05: Multi-Grain Formulation

**Priority**: High
**Type**: Functional
**Traces To**: INT-001, INT-002 (Axiom 5: Grain is a Dimension)

**Description**: Formulas can reference attributes at different grains if finer-grained attributes are wrapped in explicit aggregation morphisms.

**Acceptance Criteria**:
- Finer-grained attributes must be wrapped in aggregation morphism (REQ-LDM-04)
- Aggregation scope aligns to coarser entity's grain
- Direct unaggregated reference to finer grain is rejected
- Validation occurs at compile time
- Error messages identify grain mismatch and required aggregation

---

#### REQ-INT-06: Versioned Lookups

**Priority**: Critical
**Type**: Functional
**Traces To**: INT-001, INT-002 (Axiom 6: Lookups are Arguments)

**Description**: All Reference Data usage in transformations must be explicitly versioned to ensure deterministic reproducibility.

**Acceptance Criteria**:
- Mappings using lookups must specify version semantics (explicit version, temporal constraint, or deterministic alias)
- Mappings without lookup version semantics are rejected
- Within single execution context, same lookup + key → same value (immutability)
- Lookup version is recorded in lineage (REQ-INT-03)
- Version resolution is deterministic

---

#### REQ-INT-07: Identity Synthesis

**Priority**: Medium
**Type**: Functional
**Traces To**: INT-001

**Description**: The system must support Deterministic Key Generation (hashing/surrogate keys) where same inputs always produce same keys.

**Acceptance Criteria**:
- Key generation functions are deterministic
- Same input attributes → same key (bit-identical)
- Key generation algorithms are configurable (hash functions, UUIDs v5)
- Keys are stable across re-runs for audit/debugging
- Key generation is recorded in lineage

---

#### REQ-INT-08: External Computational Morphisms

**Priority**: High
**Type**: Functional
**Traces To**: INT-001, INT-003 (Universal Applicability - Computations as Morphisms), INT-004 (AI Assurance)

**Description**: The system must support registration of Black Box Calculators (e.g., compiled Cashflow Engines) as standard Morphisms.

**Acceptance Criteria**:
- External calculators can be registered with domain/codomain type declarations
- Type signatures conform to LDM type system (REQ-TYP-01)
- Registrant declares determinism (same inputs → same outputs) as contract
- Calculator has stable identifier/version for lineage tracing
- Type compatibility is enforced at compile time (REQ-TYP-06)
- Determinism assertion is trusted, not runtime-verified
- Calculator version/identity logged in lineage (REQ-INT-03)

---

### 2.5. Typing & Quality (TYP)

#### REQ-TYP-01: Extended Type System

**Priority**: Critical
**Type**: Functional
**Traces To**: INT-001, INT-002 (Axiom 7: Types are Contracts)

**Description**: The LDM must support a Rich Type System including Primitives, Sum Types, and Product Types.

**Acceptance Criteria**:
- Primitives supported: Int, Float, String, Boolean, Date, Timestamp
- Sum types (Either, Option) are first-class types
- Product types (tuples, records) are first-class types
- Types are composable (nested products/sums)
- Type system is extensible for domain-specific types

---

#### REQ-TYP-02: Refinement Types

**Priority**: High
**Type**: Functional
**Traces To**: INT-001, INT-002 (Axiom 7: Types are Contracts)

**Description**: The system must support Refinement Types (base type + predicate) for data quality enforcement.

**Acceptance Criteria**:
- Refinement types combine base type with predicate (e.g., PositiveInteger = Int where x > 0)
- Predicate violations route to Error Domain (REQ-TYP-03)
- Refinements are declared in LDM entity definitions
- Runtime validation enforces refinement predicates
- Error objects capture violated refinement (REQ-ERROR-01)

---

#### REQ-TYP-03: Error Domain Semantics

**Priority**: Critical
**Type**: Functional
**Traces To**: INT-001, INT-002 (Axiom 10: Failures are Data)

**Description**: The system must handle failures via Either Monad strategy where failures are data, not exceptions.

**Acceptance Criteria**:
- Failures modeled as `Left(Error)`, valid data as `Right(Value)`
- Failures do NOT short-circuit entire execution (unless batch threshold exceeded per REQ-TYP-03-A)
- Invalid records routed to Error Sink, never silently dropped
- Error Domain is queryable and exportable
- Execution continues processing valid records after failures

---

#### REQ-TYP-03-A: Batch Failure Threshold

**Priority**: High
**Type**: Functional
**Traces To**: INT-001, INT-002 (Axiom 10: Failures are Data)

**Description**: The Execution Artifact (Job Configuration) may define a Batch Failure Threshold to halt execution on systemic failures.

**Acceptance Criteria**:
- Threshold configurable as absolute count (e.g., > 1000 errors) or percentage (e.g., > 5%)
- Execution halts when threshold exceeded
- Job configuration controls commit/rollback of successful records on threshold breach
- Error Domain contains all failures encountered prior to halt
- Threshold breach logged with failure statistics

---

#### REQ-TYP-04: Idempotency of Failure

**Priority**: High
**Type**: Functional
**Traces To**: INT-001, INT-002 (Axiom 8: Topology is the Guardrail)

**Description**: Error handling logic must be Idempotent - re-processing same failing records under same conditions produces same Error results.

**Acceptance Criteria**:
- Same failing input + same mapping → same error (bit-identical)
- Error routing is deterministic
- Error object content is deterministic (REQ-ERROR-01)
- Re-runs produce identical Error Domain contents
- Idempotency verified via regression testing

---

#### REQ-TYP-05: Semantic Casting

**Priority**: High
**Type**: Functional
**Traces To**: INT-001, INT-002 (Axiom 7: Types are Contracts), INT-004 (AI Assurance)

**Description**: Implicit casting is forbidden. All type changes must be expressed as explicit morphisms in the LDM.

**Acceptance Criteria**:
- No implicit type coercion (String → Int, Int → Float)
- All type conversions are explicit morphisms with defined semantics
- Implicit cast attempts are rejected at compile time
- Conversion morphisms handle failure cases (e.g., non-numeric string)
- Type conversion is part of LDM topology

---

#### REQ-TYP-06: Type Unification Rules

**Priority**: Critical
**Type**: Functional
**Traces To**: INT-001, INT-002 (Axiom 7: Types are Contracts), INT-004 (AI Assurance)

**Description**: When composing morphisms `f: A → B` and `g: B → C`, type compatibility must be validated according to strict unification rules.

**Acceptance Criteria**:
- Composition valid if codomain(f) = domain(g)
- Composition valid if codomain(f) is subtype of domain(g) per LDM type hierarchy
- Composition invalid otherwise → mapping rejected
- No implicit coercion allowed (REQ-TYP-05)
- Type unification errors identify incompatible morphisms

---

#### REQ-TYP-07: Semantic Type Enforcement

**Priority**: Medium
**Type**: Functional
**Traces To**: INT-001, INT-002 (Axiom 7: Types are Contracts)

**Description**: The type system may support semantic type distinctions (Money, Date, Percent) beyond primitive types to prevent category errors.

**Acceptance Criteria**:
- Semantic types are definable in LDM (Money, Date, Percent, etc.)
- Operations between incompatible semantic types are rejected (Money + Date)
- Conversion requires explicit morphisms (REQ-TYP-05)
- Semantic enforcement is opt-in (system works with primitives alone)
- When enabled, semantic violations detected at compile time

---

#### REQ-ERROR-01: Minimal Error Object Content

**Priority**: High
**Type**: Functional
**Traces To**: INT-001, INT-002 (Axiom 10: Failures are Data)

**Description**: Each Error Object routed to the Error Domain must include minimum diagnostic information for debugging and audit.

**Acceptance Criteria**:
- Error object contains: failed constraint/refinement type
- Error object contains: offending value(s)
- Error object contains: source Entity and Epoch
- Error object contains: morphism path where failure occurred
- Error objects are structured for programmatic analysis
- Error Domain schema is formally defined in LDM

---

### 2.6. AI Assurance (AI)

#### REQ-AI-01: Topological Validity Check

**Priority**: Critical
**Type**: Functional
**Traces To**: INT-004 (AI Assurance - Hallucination Prevention), INT-002 (Axiom 8: Topology is the Guardrail)

**Description**: The engine must validate AI-generated mappings using the same structural rules as human-authored mappings and reject hallucinations.

**Acceptance Criteria**:
- AI-generated mappings validated against LDM topology
- Hallucinated morphisms (non-existent relationships) are rejected
- Type unification violations are rejected (REQ-TYP-06)
- Grain safety violations are rejected (REQ-TRV-02)
- Access control violations are rejected (REQ-LDM-05)
- Rejection occurs at definition/compile time, not runtime
- Clear error messages identify specific topological violations
- Domain-semantic correctness (business logic) is NOT validated beyond type system

---

#### REQ-AI-02: Triangulation of Assurance

**Priority**: High
**Type**: Functional
**Traces To**: INT-004 (AI Assurance - Triangulation)

**Description**: The system must support linkage of Intent, Logic, and Proof for real-time verification of AI-generated pipelines.

**Acceptance Criteria**:
- **Proof** includes: Lineage Graph, Execution Trace, Type Unification Report
- Proof demonstrates output data structurally matches Intent
- Intent → Logic → Proof linkage is traceable
- Proof artifacts are exportable for human review
- Real-time verification occurs before execution (REQ-AI-03)

---

#### REQ-AI-03: Real-Time Dry Run

**Priority**: High
**Type**: Functional
**Traces To**: INT-004 (AI Assurance)

**Description**: The system must support "Dry Run" mode for immediate assurance signals without committing results.

**Acceptance Criteria**:
- Dry Run executes validation and execution planning without sinks
- Dry Run produces: Lineage Graph, Type Unification Report, Cost Estimate
- Dry Run completes in < 10% of full execution time (target)
- Dry Run mode is configurable in Execution Artifact (Job Configuration)
- Dry Run results are exportable for review before production run

---

### 2.7. Adjoint Morphisms (ADJ)

#### REQ-ADJ-01: Adjoint Interface Structure

**Priority**: High
**Type**: Functional
**Traces To**: INT-005 (Adjoint Morphisms), INT-002 (Axiom 3: Transformations are Paths)

**Description**: The CDME must implement an Adjoint interface where every morphism `f: A → B` has a corresponding backward morphism `f⁻: B → A` forming a Galois connection.

**Acceptance Criteria**:
- Every morphism implements the `Adjoint<T, U>` interface with `forward` and `backward`
- Round-trip containment: `backward(forward(x)) ⊇ x` (lower closure)
- Round-trip contraction: `forward(backward(y)) ⊆ y` (upper closure)
- Contravariant composition: `(g ∘ f)⁻ = f⁻ ∘ g⁻`
- Identity is self-adjoint: `id⁻ = id`
- Adjoint properties are validated at compile time
- Adjoint morphisms are first-class citizens in the topology

---

#### REQ-ADJ-02: Adjoint Classification

**Priority**: High
**Type**: Functional
**Traces To**: INT-005 (Adjoint Morphisms)

**Description**: Every morphism must be classified by its adjoint properties, indicating the precision of the round-trip.

**Acceptance Criteria**:
- Classifications include: `Isomorphism`, `Embedding`, `Projection`, `Lossy`
- `Isomorphism`: Exact inverse - `f⁻(f(x)) = x` and `f(f⁻(y)) = y`
- `Embedding`: Injective - `f⁻(f(x)) = x` but `f(f⁻(y)) ⊆ y`
- `Projection`: Surjective - `f⁻(f(x)) ⊇ x` but `f(f⁻(y)) = y`
- `Lossy`: Neither - `f⁻(f(x)) ⊇ x` and `f(f⁻(y)) ⊆ y`
- Classification is inferred automatically where possible
- Classification affects composition rules and validation

---

#### REQ-ADJ-03: Self-Adjoint Morphisms (Isomorphisms)

**Priority**: High
**Type**: Functional
**Traces To**: INT-005 (Adjoint Morphisms), RIC-LIN-06 (Lossless vs Lossy)

**Description**: Self-adjoint morphisms (isomorphisms) must have exact backward computed automatically by the system.

**Acceptance Criteria**:
- 1:1 morphisms have automatic inverse backward
- Pure field derivations have backward computed from formula (if algebraically invertible)
- Reshape operations that retain keys have automatic backward
- Enrichments using versioned lookups have backward via lookup reversal
- Automatic backward computation is verified for correctness
- Non-invertible formulas (e.g., hash functions) are classified as `Lossy`

---

#### REQ-ADJ-04: Adjoint Backward for Aggregations

**Priority**: Critical
**Type**: Functional
**Traces To**: INT-005 (Adjoint Morphisms), REQ-LDM-04 (Monoid Laws)

**Description**: Aggregation (fold) morphisms must capture reverse-join metadata during forward execution to enable backward traversal.

**Acceptance Criteria**:
- Aggregation wrappers capture mapping of group keys → constituent record keys
- Reverse-join table is stored alongside aggregation output
- Backward operation uses reverse-join table to return contributing keys
- `backward(group_key)` returns set of original record keys (⊇ relationship)
- Storage format is configurable (inline, separate table, compressed)
- Reverse-join metadata is subject to lineage modes (RIC-LIN-01)
- Classification: `Projection` (many-to-one with full backward reconstruction)

---

#### REQ-ADJ-05: Adjoint Backward for Filters

**Priority**: High
**Type**: Functional
**Traces To**: INT-005 (Adjoint Morphisms)

**Description**: Filter morphisms must capture filtered-out keys during forward execution to enable complete set reconstruction via backward.

**Acceptance Criteria**:
- Filter wrappers capture keys of both passed and filtered-out records
- Backward operation reconstructs full input set from output + filtered-out
- `backward(passed_keys) ∪ filtered_out_keys = original_input_keys`
- Filtered-out key storage is optional and configurable
- When disabled, classification is `Lossy` with partial backward
- When enabled, classification is `Embedding` (injective into subset)

---

#### REQ-ADJ-06: Adjoint Backward for Kleisli Arrows (1:N)

**Priority**: High
**Type**: Functional
**Traces To**: INT-005 (Adjoint Morphisms), REQ-TRV-01 (Context Lifting)

**Description**: Kleisli arrow (1:N expansion) morphisms must capture the parent-child relationship during forward execution to enable backward.

**Acceptance Criteria**:
- 1:N expansion captures mapping of child keys → parent key
- Backward operation maps children back to parent
- `backward(child_keys)` returns the unique parent key
- Multiple children map to same parent (many-to-one backward)
- Parent-child mapping is stored as part of expansion metadata
- Classification: `Projection` (expansion in forward, collapse in backward)

---

#### REQ-ADJ-07: Adjoint Composition Validation

**Priority**: High
**Type**: Functional
**Traces To**: INT-005 (Adjoint Morphisms), REQ-LDM-03 (Composition Validity)

**Description**: The compiler must validate that adjoint composition follows contravariant rules and propagates classification correctly.

**Acceptance Criteria**:
- Composed backward `(g ∘ f)⁻` is computed as `f⁻ ∘ g⁻`
- Composition of `Isomorphism` adjoints yields `Isomorphism`
- Composition involving `Embedding` or `Projection` yields appropriate classification
- Composition involving `Lossy` adjoints yields `Lossy`
- Round-trip bounds are computed from constituent adjoint bounds
- Adjoint composition validation occurs at compile time

---

#### REQ-ADJ-08: Data Reconciliation via Adjoints

**Priority**: High
**Type**: Functional
**Traces To**: INT-005 (Adjoint Morphisms - Data Reconciliation)

**Description**: The system must support data reconciliation by computing `backward(forward(x))` and validating containment against original input `x`.

**Acceptance Criteria**:
- Reconciliation operation computes `backward(forward(x))` for a given input set `x`
- For isomorphisms: `backward(forward(x)) = x` (exact equality)
- For projections/lossy: `backward(forward(x)) ⊇ x` (containment validated)
- Missing records in containment are reported for investigation
- Reconciliation can run as validation step after forward execution
- Reconciliation results are exportable for audit

---

#### REQ-ADJ-09: Impact Analysis via Adjoints

**Priority**: High
**Type**: Functional
**Traces To**: INT-005 (Adjoint Morphisms - Impact Analysis)

**Description**: The system must support impact analysis by computing `backward(target_subset)` to identify contributing source records.

**Acceptance Criteria**:
- Given a subset of target records, compute contributing source records via backward
- Impact analysis traverses composed backward path `(fn ∘ ... ∘ f1)⁻ = f1⁻ ∘ ... ∘ fn⁻`
- Result includes all source entities and records on the path
- Impact analysis respects epoch/context boundaries
- Result is exportable for downstream analysis
- Performance: impact analysis should be O(|target_subset|) not O(|full_dataset|)

---

#### REQ-ADJ-10: Bidirectional Sync Support

**Priority**: Medium
**Type**: Functional
**Traces To**: INT-005 (Adjoint Morphisms - Bidirectional Sync)

**Description**: The system must support bidirectional synchronization where a single adjoint definition enables sync in both directions.

**Acceptance Criteria**:
- Single adjoint `f: A ⇌ B` with `forward` and `backward` enables bidirectional sync
- Changes to A can be propagated to B via `forward`
- Changes to B can be propagated to A via `backward`
- Conflict detection when both sides have changes
- Conflict resolution strategies: source-wins, target-wins, merge, fail
- Bidirectional sync respects epoch/context constraints
- Round-trip containment bounds inform conflict detection

---

#### REQ-ADJ-11: Adjoint Metadata Storage

**Priority**: Medium
**Type**: Non-Functional (Performance)
**Traces To**: INT-005 (Adjoint Morphisms), RIC-LIN-01 (Lineage Modes)

**Description**: Adjoint backward metadata (reverse-join tables, filtered keys) must be stored efficiently with configurable strategies.

**Acceptance Criteria**:
- Storage strategies: inline (with output), separate table, compressed
- Compression options: Roaring Bitmaps for key sets, range encoding for sorted keys
- Storage overhead budget configurable per job (e.g., max 20% of output size)
- Metadata can be pruned after configurable retention period
- Metadata storage is integrated with lineage mode selection
- Full lineage mode implies full adjoint metadata; sampled mode implies sampled metadata

---

## 3. Regulatory Compliance Mapping

### 3.1. BCBS 239 (Risk Data Aggregation & Reporting)

| Principle | Requirements | Description |
|-----------|-------------|-------------|
| Principle 3 (Accuracy & Integrity) | REQ-TYP-01, REQ-TYP-02, REQ-TYP-06, REQ-TRV-05 | Type enforcement and deterministic reproducibility |
| Principle 4 (Completeness) | REQ-LDM-03, REQ-TRV-02, REQ-INT-03, REQ-ERROR-01 | Path validation, grain safety, lineage, error capture |
| Principle 6 (Adaptability) | REQ-PDM-01 | LDM/PDM separation |

### 3.2. FRTB (Fundamental Review of the Trading Book)

| Requirement | Requirements | Description |
|-------------|-------------|-------------|
| Granular Risk Attribution | REQ-INT-03, REQ-TRV-05, REQ-TRV-02 | Full lineage and grain correctness |

### 3.3. GDPR / CCPA (Data Privacy)

| Requirement | Requirements | Description |
|-------------|-------------|-------------|
| Right to be Forgotten | REQ-INT-07, REQ-PDM-01 | Deterministic identity with LDM/PDM separation |

### 3.4. EU AI Act (Artificial Intelligence)

| Article | Requirements | Description |
|---------|-------------|-------------|
| Article 14 (Human Oversight) | REQ-AI-01, REQ-AI-02, REQ-AI-03 | Hallucination prevention and triangulation |
| Article 15 (Robustness) | REQ-LDM-03, REQ-TYP-06, REQ-TRV-05 | Topological strictness and determinism |

---

## 4. Implementation Constraints (Appendix A)

### 4.1. Lineage & Backward Traversal

#### RIC-LIN-01: Lineage Modes

**Priority**: Medium
**Type**: Non-Functional (Performance)
**Traces To**: INT-001, REQ-INT-03

**Description**: The engine must support multiple lineage modes selectable via Job Configuration for performance optimization.

**Acceptance Criteria**:
- **Full Lineage Mode**: All intermediate keys persisted
- **Key-Derivable Mode**: Compressed envelopes with deterministic reconstruction
- **Summary/Sampled Mode**: Statistical/sampled lineage
- Mode selection is per-job in Execution Artifact
- Mode selection does not affect determinism guarantees

---

#### RIC-LIN-06: Lossless vs Lossy Morphisms

**Priority**: Medium
**Type**: Non-Functional (Performance)
**Traces To**: INT-001, REQ-INT-03

**Description**: Every morphism must be classified as Lossless or Lossy for lineage checkpointing.

**Acceptance Criteria**:
- **Lossless**: Deterministic, injective, no drops/merges, versioned dependencies
- **Lossy**: Aggregations, filters, joins dropping unmatched, non-versioned state
- Classification is queryable at compile time
- Checkpointing policy enforced based on classification (RIC-LIN-07)

---

#### RIC-LIN-07: Checkpointing Policy

**Priority**: Medium
**Type**: Non-Functional (Performance)
**Traces To**: INT-001, REQ-INT-03

**Description**: Key envelopes must be persisted at strategic points to enable lineage reconstruction.

**Acceptance Criteria**:
- Persist envelopes at: all inputs, all outputs, after every lossy morphism
- May omit capture at intermediate lossless morphism chains over sorted keys
- Envelope includes: Segment ID, Start/End Key, Key Gen Function, Offset/Count
- Checkpointing ensures lineage reconstructability (RIC-LIN-04)

---

#### RIC-LIN-04: Reconstructability Invariant

**Priority**: Medium
**Type**: Non-Functional (Performance)
**Traces To**: INT-001, REQ-INT-03

**Description**: In Key-Derivable Lineage Mode, source keys for any target must be reconstructable from envelopes and plan logic.

**Acceptance Criteria**:
- Reconstruction uses: immutable inputs, checkpoint envelopes, compiled plan
- Non-reconstructable plans insert additional checkpoints or downgrade to Full Lineage
- Reconstruction algorithm is deterministic
- Reconstruction tested via automated verification

---

### 4.2. Skew Management

#### RIC-SKW-01: Skew Mitigation (Salted Joins)

**Priority**: Medium
**Type**: Non-Functional (Performance)
**Traces To**: INT-001, REQ-TRV-01, REQ-TRV-06

**Description**: The engine should support skew mitigation strategies for high-cardinality keys to avoid executor imbalance.

**Acceptance Criteria**:
- Salted join strategy available for 1:N expansions
- Random salt added to source, target exploded N times
- Join performed on (Key, Salt) for distribution
- Skew detection via sampling before execution (REQ-TRV-06)
- Mitigation applied when estimated skew exceeds threshold

---

### 4.3. Error Domain Optimization

#### RIC-ERR-01: Probabilistic Circuit Breakers

**Priority**: Medium
**Type**: Non-Functional (Performance)
**Traces To**: INT-001, REQ-TYP-03, REQ-TYP-03-A

**Description**: To avoid DLQ overflow on systemic failures, engine should implement early failure detection.

**Acceptance Criteria**:
- Failure counter tracks early execution sample (e.g., first 10k rows)
- If failure rate exceeds threshold (e.g., 5%), halt processing
- Emit structural/configuration error, not data quality error
- Prevent mass error routing on systemic failures
- Threshold configurable in Execution Artifact

---

### 4.4. Approximate Aggregation

#### RIC-AGG-01: Sketch-Based Aggregations

**Priority**: Low
**Type**: Functional (Optional)
**Traces To**: INT-001, REQ-LDM-04

**Description**: For large datasets, support approximate monoidal aggregations via sketch data structures.

**Acceptance Criteria**:
- Support approximate structures: t-Digest (quantiles), HyperLogLog (distinct counts)
- Sketches behave as monoids (associative, identity, mergeable)
- LDM distinguishes ExactAgg vs ApproxAgg
- Bounded error approximations with predictable memory
- Governance controls where approximations are allowed

---

### 4.5. Physical Optimization

#### RIC-PHY-01: Partition Homomorphism

**Priority**: Medium
**Type**: Non-Functional (Performance)
**Traces To**: INT-001, REQ-PDM-01

**Description**: Compiler should optimize joins by aligning physical partitioning schemes.

**Acceptance Criteria**:
- Detect compatible partitioning schemes (same key)
- Perform local joins where partitioning is compatible
- Inject repartition/sort morphisms when required
- Minimize global shuffles to strictly necessary cases
- Optimization respects logical correctness (no semantic changes)

---

## 5. Requirements Summary

### 5.1. By Category

| Category | Count | Description |
|----------|-------|-------------|
| Logical Topology (LDM) | 7 | Entity/morphism graph structure and semantics |
| Physical Binding (PDM) | 6 | Storage abstraction and system boundaries |
| Traversal Engine (TRV/SHF) | 7 | Path execution, grain safety, context management |
| Integration & Synthesis (INT) | 8 | Transformations, aggregations, lineage |
| Typing & Quality (TYP/ERROR) | 9 | Type system, refinements, error handling |
| AI Assurance (AI) | 3 | Hallucination prevention, triangulation |
| Adjoint Morphisms (ADJ) | 11 | Reverse transformations, reconciliation, impact analysis |
| Implementation Constraints (RIC) | 9 | Performance optimizations, lineage modes |
| **Total** | **60** | |

### 5.2. By Priority

| Priority | Count | Percentage |
|----------|-------|------------|
| Critical | 18 | 30% |
| High | 25 | 42% |
| Medium | 15 | 25% |
| Low | 2 | 3% |
| **Total** | **60** | **100%** |

### 5.3. By Type

| Type | Count | Percentage |
|------|-------|------------|
| Functional | 45 | 75% |
| Non-Functional | 15 | 25% |
| **Total** | **60** | **100%** |

### 5.4. Critical Path Requirements

These requirements form the critical path for MVP implementation:

1. **REQ-LDM-01, REQ-LDM-02, REQ-LDM-03** - Graph topology foundation
2. **REQ-LDM-04, REQ-LDM-06** - Grain and type metadata
3. **REQ-PDM-01** - LDM/PDM separation
4. **REQ-TRV-01, REQ-TRV-02** - Context lifting and grain safety
5. **REQ-TYP-01, REQ-TYP-03, REQ-TYP-06** - Type system and error handling
6. **REQ-INT-01, REQ-INT-03** - Synthesis and lineage
7. **REQ-AI-01** - Topological validity (hallucination prevention)

---

## 6. Traceability Matrix

| Requirement ID | Intent | Design Component | Priority | Status |
|----------------|--------|------------------|----------|--------|
| REQ-LDM-01 | INT-001, INT-002 | TopologicalCompiler | Critical | Pending |
| REQ-LDM-02 | INT-001, INT-002 | TopologicalCompiler | Critical | Pending |
| REQ-LDM-03 | INT-001, INT-002, INT-004 | TopologicalCompiler | Critical | Pending |
| REQ-LDM-04 | INT-001, INT-002 | MorphismExecutor | Critical | Pending |
| REQ-LDM-04-A | INT-001, INT-002 | MorphismExecutor | High | Pending |
| REQ-LDM-05 | INT-001, INT-002 | TopologicalCompiler | High | Pending |
| REQ-LDM-06 | INT-001, INT-002 | TopologicalCompiler | Critical | Pending |
| REQ-PDM-01 | INT-001, INT-002 | ImplementationFunctor | High | Pending |
| REQ-PDM-02 | INT-001, INT-002 | ImplementationFunctor | High | Pending |
| REQ-PDM-02-A | INT-001, INT-002 | ImplementationFunctor | High | Pending |
| REQ-PDM-03 | INT-001, INT-002 | SheafManager | High | Pending |
| REQ-PDM-04 | INT-001, INT-002 | ImplementationFunctor | Medium | Pending |
| REQ-PDM-05 | INT-001, INT-002 | ImplementationFunctor | Medium | Pending |
| REQ-TRV-01 | INT-001, INT-002, INT-003 | MorphismExecutor | Critical | Pending |
| REQ-TRV-02 | INT-001, INT-002, INT-004 | TopologicalCompiler | Critical | Pending |
| REQ-TRV-03 | INT-001, INT-002 | SheafManager | High | Pending |
| REQ-TRV-04 | INT-001 | MorphismExecutor | Medium | Pending |
| REQ-TRV-05 | INT-001, INT-002 | MorphismExecutor | Critical | Pending |
| REQ-TRV-06 | INT-001 | TopologicalCompiler | High | Pending |
| REQ-SHF-01 | INT-001, INT-002 | SheafManager | Critical | Pending |
| REQ-INT-01 | INT-001, INT-003 | MorphismExecutor | High | Pending |
| REQ-INT-02 | INT-001, INT-002 | MorphismExecutor | Medium | Pending |
| REQ-INT-03 | INT-001, INT-004 | ResidueCollector | Critical | Pending |
| REQ-INT-04 | INT-001, INT-003 | MorphismExecutor | High | Pending |
| REQ-INT-05 | INT-001, INT-002 | TopologicalCompiler | High | Pending |
| REQ-INT-06 | INT-001, INT-002 | MorphismExecutor | Critical | Pending |
| REQ-INT-07 | INT-001 | MorphismExecutor | Medium | Pending |
| REQ-INT-08 | INT-001, INT-003, INT-004 | MorphismExecutor | High | Pending |
| REQ-TYP-01 | INT-001, INT-002 | TopologicalCompiler | Critical | Pending |
| REQ-TYP-02 | INT-001, INT-002 | TopologicalCompiler | High | Pending |
| REQ-TYP-03 | INT-001, INT-002 | ErrorDomain | Critical | Pending |
| REQ-TYP-03-A | INT-001, INT-002 | ErrorDomain | High | Pending |
| REQ-TYP-04 | INT-001, INT-002 | ErrorDomain | High | Pending |
| REQ-TYP-05 | INT-001, INT-002, INT-004 | TopologicalCompiler | High | Pending |
| REQ-TYP-06 | INT-001, INT-002, INT-004 | TopologicalCompiler | Critical | Pending |
| REQ-TYP-07 | INT-001, INT-002 | TopologicalCompiler | Medium | Pending |
| REQ-ERROR-01 | INT-001, INT-002 | ErrorDomain | High | Pending |
| REQ-AI-01 | INT-004, INT-002 | TopologicalCompiler | Critical | Pending |
| REQ-AI-02 | INT-004 | ResidueCollector | High | Pending |
| REQ-AI-03 | INT-004 | TopologicalCompiler | High | Pending |
| RIC-LIN-01 | INT-001, REQ-INT-03 | ResidueCollector | Medium | Pending |
| RIC-LIN-06 | INT-001, REQ-INT-03 | TopologicalCompiler | Medium | Pending |
| RIC-LIN-07 | INT-001, REQ-INT-03 | ResidueCollector | Medium | Pending |
| RIC-LIN-04 | INT-001, REQ-INT-03 | ResidueCollector | Medium | Pending |
| RIC-SKW-01 | INT-001, REQ-TRV-01, REQ-TRV-06 | MorphismExecutor | Medium | Pending |
| RIC-ERR-01 | INT-001, REQ-TYP-03, REQ-TYP-03-A | ErrorDomain | Medium | Pending |
| RIC-AGG-01 | INT-001, REQ-LDM-04 | MorphismExecutor | Low | Pending |
| RIC-PHY-01 | INT-001, REQ-PDM-01 | ImplementationFunctor | Medium | Pending |
| REQ-ADJ-01 | INT-005, INT-002 | AdjointCompiler | High | Pending |
| REQ-ADJ-02 | INT-005 | AdjointCompiler | High | Pending |
| REQ-ADJ-03 | INT-005, RIC-LIN-06 | AdjointCompiler | High | Pending |
| REQ-ADJ-04 | INT-005, REQ-LDM-04 | MorphismExecutor | Critical | Pending |
| REQ-ADJ-05 | INT-005 | MorphismExecutor | High | Pending |
| REQ-ADJ-06 | INT-005, REQ-TRV-01 | MorphismExecutor | High | Pending |
| REQ-ADJ-07 | INT-005, REQ-LDM-03 | AdjointCompiler | High | Pending |
| REQ-ADJ-08 | INT-005 | ReconciliationEngine | High | Pending |
| REQ-ADJ-09 | INT-005 | ImpactAnalyzer | High | Pending |
| REQ-ADJ-10 | INT-005 | BidirectionalSyncManager | Medium | Pending |
| REQ-ADJ-11 | INT-005, RIC-LIN-01 | ResidueCollector | Medium | Pending |

---

## 7. Next Steps

**Status**: Requirements stage complete. Ready for Design Stage.

**Handoff to Design Agent**:
1. All 49 requirements are formally documented with acceptance criteria
2. Traceability matrix maps requirements to intents and design components
3. Regulatory compliance requirements are explicitly identified
4. Critical path requirements are prioritized for MVP
5. Implementation constraints provide optimization guidance

**Design Stage Responsibilities**:
1. Create component architecture for: TopologicalCompiler, SheafManager, MorphismExecutor, ErrorDomain, ImplementationFunctor, ResidueCollector
2. Create adjoint-specific components: AdjointCompiler, ReconciliationEngine, ImpactAnalyzer, BidirectionalSyncManager
3. Define interfaces and contracts between components
4. Create ADRs (Architecture Decision Records) for key design choices
5. Map requirements to specific modules and classes
6. Define data structures for LDM, PDM, Mapping, and Execution artifacts
7. Design lineage, telemetry, and adjoint metadata schemas
8. Design adjoint backward capture wrappers for Spark/distributed operations
9. Define `Adjoint<T,U>` interface with `forward`, `backward`, and `compositionalConsistency` laws

---

**Document Version**: 1.1
**Generated By**: AISDLC Requirements Agent
**Date**: 2025-12-10
**Last Updated**: 2025-12-10 (Added INT-005 Adjoint Morphism requirements)
**Status**: Approved for Design Stage
