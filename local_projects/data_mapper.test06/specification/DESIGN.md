# CDME Design Specification — scala_spark Variant

**Project**: data_mapper.test06
**Version**: 1.0.0
**Design Variant**: scala_spark (single design)
**Date**: 2026-02-23

---

## 1. Module Architecture (8 sbt Sub-Projects)

```
cdme-model          → Pure domain types (zero external deps)
cdme-compiler       → Topological compiler (depends: model)
cdme-runtime        → Execution engine (depends: compiler)
cdme-spark          → Spark binding (depends: runtime)
cdme-lineage        → OpenLineage integration (depends: runtime)
cdme-adjoint        → Adjoint/Frobenius backward traversal (depends: model)
cdme-ai-assurance   → AI validation layer (depends: compiler)
cdme-api            → REST/gRPC API (depends: all)
```

### 1.1 Dependency DAG

```
model ← compiler ← runtime ← spark
                            ← lineage
model ← adjoint
         compiler ← ai-assurance
model, compiler, runtime, spark, lineage, adjoint, ai-assurance ← api
```

---

## 2. Module: cdme-model

**Package**: `com.cdme.model`
**Dependencies**: None (pure Scala)
**Requirements**: REQ-LDM-01 through REQ-LDM-06, REQ-TYP-01, REQ-TYP-02, REQ-TYP-07, REQ-ERROR-01

### 2.1 Core Domain Types

```scala
// Implements: REQ-LDM-01 (Strict Graph Structure)
case class Entity(
  name: String,
  grain: Grain,
  attributes: Map[String, SemanticType],
  identityMorphism: Morphism
)

// Implements: REQ-LDM-02 (Cardinality Types)
sealed trait Cardinality
object Cardinality {
  case object OneToOne extends Cardinality
  case object NToOne extends Cardinality
  case object OneToN extends Cardinality
}

// Implements: REQ-LDM-01, REQ-LDM-02, REQ-LDM-03
sealed trait MorphismType
object MorphismType {
  case object Structural extends MorphismType    // FK relationships
  case object Computational extends MorphismType // Pure functions
  case object Algebraic extends MorphismType     // Folds/monoids
}

case class Morphism(
  name: String,
  source: String,        // Entity name (domain)
  target: String,        // Entity name (codomain)
  cardinality: Cardinality,
  morphismType: MorphismType,
  domainType: SemanticType,
  codomainType: SemanticType,
  rbac: Set[String],     // REQ-LDM-05: access control roles
  metadata: MorphismMetadata
)

// Implements: REQ-LDM-06 (Grain & Type Metadata)
sealed trait Grain extends Ordered[Grain]
object Grain {
  case object Atomic extends Grain { def compare(that: Grain) = grainOrd(this) - grainOrd(that) }
  case object Daily extends Grain { def compare(that: Grain) = grainOrd(this) - grainOrd(that) }
  case object Monthly extends Grain { def compare(that: Grain) = grainOrd(this) - grainOrd(that) }
  case object Yearly extends Grain { def compare(that: Grain) = grainOrd(this) - grainOrd(that) }
  case class Custom(name: String, level: Int) extends Grain {
    def compare(that: Grain) = grainOrd(this) - grainOrd(that)
  }

  private def grainOrd(g: Grain): Int = g match {
    case Atomic => 0; case Daily => 1; case Monthly => 2; case Yearly => 3
    case Custom(_, l) => l
  }
}
```

### 2.2 Type System

```scala
// Implements: REQ-TYP-01 (Extended Type System)
sealed trait SemanticType
object SemanticType {
  // Primitives
  case object IntType extends SemanticType
  case object FloatType extends SemanticType
  case object StringType extends SemanticType
  case object BooleanType extends SemanticType
  case object DateType extends SemanticType
  case object TimestampType extends SemanticType

  // Composite
  case class OptionType(inner: SemanticType) extends SemanticType
  case class EitherType(left: SemanticType, right: SemanticType) extends SemanticType
  case class ProductType(fields: Map[String, SemanticType]) extends SemanticType
  case class ListType(inner: SemanticType) extends SemanticType

  // Implements: REQ-TYP-02 (Refinement Types)
  case class RefinementType(base: SemanticType, predicate: String) extends SemanticType

  // Implements: REQ-TYP-07 (Semantic Types)
  case class DomainType(name: String, base: SemanticType) extends SemanticType
}
```

### 2.3 Topology (LDM as Category)

```scala
// Implements: REQ-LDM-01 (Strict Graph Structure)
case class Category(
  name: String,
  objects: Map[String, Entity],
  morphisms: Map[String, Morphism]
) {
  def composePath(path: List[String]): Either[CompilationError, ComposedMorphism]
  def queryMorphisms(source: String, target: String): Set[Morphism]
  def grainHierarchy: Map[Grain, Set[Grain]]
}

// Implements: REQ-LDM-03 (Composition)
case class ComposedMorphism(
  steps: List[Morphism],
  resultType: SemanticType,
  resultGrain: Grain,
  resultCardinality: Cardinality
)
```

### 2.4 Error Domain

```scala
// Implements: REQ-TYP-03 (Error Domain), REQ-ERROR-01
case class ErrorObject(
  failureType: String,
  offendingValues: Map[String, Any],
  sourceEntity: String,
  sourceEpoch: String,
  morphismPath: List[String],
  constraintViolated: String,
  timestamp: java.time.Instant
)
```

### 2.5 Monoid Algebra

```scala
// Implements: REQ-LDM-04 (Monoid Laws)
trait Monoid[A] {
  def empty: A
  def combine(x: A, y: A): A
}

object Monoid {
  val sumInt: Monoid[Int] = new Monoid[Int] {
    def empty = 0; def combine(x: Int, y: Int) = x + y
  }
  val sumDouble: Monoid[Double] = new Monoid[Double] {
    def empty = 0.0; def combine(x: Double, y: Double) = x + y
  }
  val productDouble: Monoid[Double] = new Monoid[Double] {
    def empty = 1.0; def combine(x: Double, y: Double) = x * y
  }
  val concatString: Monoid[String] = new Monoid[String] {
    def empty = ""; def combine(x: String, y: String) = x + y
  }
}
```

---

## 3. Module: cdme-compiler

**Package**: `com.cdme.compiler`
**Dependencies**: cdme-model
**Requirements**: REQ-LDM-03, REQ-TRV-02, REQ-TYP-05, REQ-TYP-06, REQ-AI-01, REQ-TRV-06, REQ-SHF-01

### 3.1 Topological Compiler

```scala
// Implements: REQ-LDM-03, REQ-AI-01
sealed trait CompilationError
object CompilationError {
  case class InvalidPath(path: String, reason: String) extends CompilationError
  case class GrainViolation(source: Grain, target: Grain, path: String) extends CompilationError
  case class TypeMismatch(expected: SemanticType, actual: SemanticType, at: String) extends CompilationError
  case class AccessDenied(morphism: String, principal: String) extends CompilationError
  case class HallucinatedMorphism(name: String) extends CompilationError
  case class MonoidLawViolation(morphism: String, reason: String) extends CompilationError
  case class BudgetExceeded(estimated: Long, budget: Long) extends CompilationError
  case class SheafInconsistency(leftEpoch: String, rightEpoch: String) extends CompilationError
}

// The Topological Compiler
trait TopologicalCompiler {
  def compile(mapping: MappingDefinition, category: Category, principal: Principal): Either[List[CompilationError], CompiledPlan]
  def validatePath(path: String, category: Category): Either[CompilationError, ComposedMorphism]
  def validateGrainSafety(morphisms: List[Morphism]): Either[CompilationError, Unit]
  def validateTypeUnification(f: Morphism, g: Morphism): Either[CompilationError, Unit]
  def estimateCost(plan: CompiledPlan): CostEstimate
  def dryRun(plan: CompiledPlan): DryRunResult
}

// Implements: REQ-TRV-02 (Grain Safety)
object GrainChecker {
  def check(source: Grain, target: Grain, hasAggregation: Boolean): Either[CompilationError, Unit]
}

// Implements: REQ-TYP-06 (Type Unification)
object TypeUnifier {
  def unify(codomain: SemanticType, domain: SemanticType): Either[CompilationError, SemanticType]
}
```

### 3.2 Compiled Plan

```scala
case class CompiledPlan(
  steps: List[ExecutionStep],
  estimatedCost: CostEstimate,
  grainMap: Map[String, Grain],
  typeMap: Map[String, SemanticType],
  requiredLookups: Map[String, LookupVersion]
)

sealed trait ExecutionStep
case class TraverseStep(morphism: Morphism) extends ExecutionStep
case class AggregateStep(morphism: Morphism, monoid: String) extends ExecutionStep
case class FilterStep(predicate: String) extends ExecutionStep
case class SynthesisStep(expression: String, resultType: SemanticType) extends ExecutionStep
case class LookupStep(lookupName: String, version: String) extends ExecutionStep

case class CostEstimate(
  estimatedOutputRows: Long,
  maxJoinDepth: Int,
  maxIntermediateSize: Long
)
```

### 3.3 Mapping Definition

```scala
// Implements: REQ-INT-01 through REQ-INT-08
case class MappingDefinition(
  name: String,
  version: String,
  sourceCategory: String,
  targetEntity: String,
  fieldMappings: List[FieldMapping],
  filters: List[FilterDefinition],
  lookups: Map[String, LookupBinding],
  costBudget: Option[CostBudget]
)

case class FieldMapping(
  targetField: String,
  expression: MappingExpression,
  targetType: SemanticType
)

sealed trait MappingExpression
case class PathExpression(path: String) extends MappingExpression
case class FunctionExpression(fn: String, args: List[MappingExpression]) extends MappingExpression
case class AggregateExpression(path: String, monoid: String) extends MappingExpression
case class ConditionalExpression(
  condition: MappingExpression,
  ifTrue: MappingExpression,
  ifFalse: MappingExpression
) extends MappingExpression
case class LiteralExpression(value: Any, semanticType: SemanticType) extends MappingExpression
```

---

## 4. Module: cdme-runtime

**Package**: `com.cdme.runtime`
**Dependencies**: cdme-compiler
**Requirements**: REQ-TRV-01, REQ-TRV-03, REQ-TRV-04, REQ-TRV-05, REQ-TRV-05-A/B, REQ-SHF-01, REQ-ACC-01 through REQ-ACC-05

### 4.1 Execution Context

```scala
// Implements: REQ-TRV-05-A (Immutable Run Hierarchy)
case class RunManifest(
  runId: String,
  configHash: String,
  codeHash: String,
  designHash: String,
  mappingVersion: String,
  sourceBindings: Map[String, String],
  lookupVersions: Map[String, String],
  cdmeVersion: String,
  timestamp: java.time.Instant,
  inputChecksums: Map[String, String],
  outputChecksums: Map[String, String]
)

// Implements: REQ-TRV-01, REQ-TRV-03
case class ExecutionContext(
  runManifest: RunManifest,
  epoch: Epoch,
  compiledPlan: CompiledPlan,
  failureThreshold: Option[FailureThreshold],
  lineageMode: LineageMode
)

case class Epoch(
  id: String,
  startTime: java.time.Instant,
  endTime: java.time.Instant,
  generationGrain: GenerationGrain
)

sealed trait GenerationGrain
object GenerationGrain {
  case object Event extends GenerationGrain
  case object Snapshot extends GenerationGrain
}

sealed trait LineageMode
object LineageMode {
  case object Full extends LineageMode
  case object KeyDerivable extends LineageMode
  case object Sampled extends LineageMode
}
```

### 4.2 Execution Engine

```scala
// Implements: REQ-TRV-01, REQ-TRV-04
trait ExecutionEngine[F[_]] {
  def execute(ctx: ExecutionContext, data: F[Record]): ExecutionResult[F]
}

case class ExecutionResult[F[_]](
  output: F[Record],
  errors: F[ErrorObject],
  ledger: AccountingLedger,
  telemetry: ExecutionTelemetry,
  manifest: RunManifest
)
```

### 4.3 Accounting Ledger

```scala
// Implements: REQ-ACC-01 through REQ-ACC-05
case class AccountingLedger(
  runId: String,
  inputCount: Long,
  processedCount: Long,
  filteredCount: Long,
  erroredCount: Long,
  morphismAccounting: Map[String, MorphismAccounting],
  adjointMetadata: Map[String, AdjointCaptured],
  verified: Boolean
) {
  // Invariant: inputCount == processedCount + filteredCount + erroredCount
  def isBalanced: Boolean =
    inputCount == processedCount + filteredCount + erroredCount
}

case class MorphismAccounting(
  morphismName: String,
  inputCount: Long,
  outputCount: Long,
  errorCount: Long,
  filterCount: Long
)
```

### 4.4 Writer Monad for Telemetry

```scala
// Implements: REQ-TRV-04
case class ExecutionTelemetry(
  entries: List[TelemetryEntry]
)

case class TelemetryEntry(
  morphismName: String,
  rowCount: Long,
  latencyMs: Long,
  qualityStats: Map[String, Double],
  timestamp: java.time.Instant
)
```

---

## 5. Module: cdme-spark

**Package**: `com.cdme.spark`
**Dependencies**: cdme-runtime
**Requirements**: REQ-PDM-01 through REQ-PDM-05, REQ-TRV-01, RIC-SKW-01, RIC-PHY-01

### 5.1 Spark Binding (Implementation Functor)

```scala
// Implements: REQ-PDM-01 (Functorial Mapping)
trait SparkFunctor {
  def bind(entity: Entity, physical: PhysicalBinding): SparkSource
  def rebind(entity: Entity, newPhysical: PhysicalBinding): SparkSource
}

case class PhysicalBinding(
  storageType: StorageType,
  path: String,
  format: String,
  partitionKeys: List[String],
  generationGrain: GenerationGrain,
  boundary: BoundaryDefinition
)

sealed trait StorageType
object StorageType {
  case object File extends StorageType
  case object Table extends StorageType
  case object Api extends StorageType
}

// Implements: REQ-PDM-03
case class BoundaryDefinition(
  strategy: BoundaryStrategy,
  parameters: Map[String, String]
)

sealed trait BoundaryStrategy
object BoundaryStrategy {
  case object Temporal extends BoundaryStrategy
  case object Version extends BoundaryStrategy
  case object BatchId extends BoundaryStrategy
}
```

### 5.2 Spark Execution Engine

```scala
// Implements: REQ-TRV-01 (Kleisli Lift via DataFrame explode)
class SparkExecutionEngine(spark: SparkSession) extends ExecutionEngine[Dataset] {
  def execute(ctx: ExecutionContext, data: Dataset[Record]): ExecutionResult[Dataset]

  // Kleisli lift: 1:N morphism → DataFrame.explode()
  def kleisliLift(df: DataFrame, morphism: Morphism): DataFrame

  // Aggregation: N:1 morphism → groupBy.agg with reverse-join capture
  def aggregate(df: DataFrame, morphism: Morphism, monoid: String): (DataFrame, ReverseJoinTable)

  // Filter: with filtered-key capture
  def filter(df: DataFrame, predicate: String): (DataFrame, FilteredKeys)
}
```

---

## 6. Module: cdme-lineage

**Package**: `com.cdme.lineage`
**Dependencies**: cdme-runtime
**Requirements**: REQ-INT-03, RIC-LIN-01, RIC-LIN-04, RIC-LIN-06, RIC-LIN-07

### 6.1 Lineage Collector

```scala
// Implements: REQ-INT-03 (Traceability)
trait LineageCollector {
  def captureTraversal(morphism: Morphism, sourceKeys: Set[String], targetKeys: Set[String]): Unit
  def captureLookupUsage(lookupName: String, version: String): Unit
  def exportOpenLineage(): OpenLineageEvent
  def getLineage(targetKey: String): LineageGraph
}

// Implements: RIC-LIN-06
sealed trait MorphismClassification
object MorphismClassification {
  case object Lossless extends MorphismClassification
  case object Lossy extends MorphismClassification
}

// Implements: RIC-LIN-07
case class KeyEnvelope(
  segmentId: String,
  startKey: String,
  endKey: String,
  keyGenFunction: String,
  offset: Long,
  count: Long
)
```

---

## 7. Module: cdme-adjoint

**Package**: `com.cdme.adjoint`
**Dependencies**: cdme-model
**Requirements**: REQ-ADJ-01 through REQ-ADJ-11

### 7.1 Adjoint Interface

```scala
// Implements: REQ-ADJ-01 (Adjoint Interface Structure)
trait Adjoint[T, U] {
  def forward(input: T): U
  def backward(output: U): T
  def classification: AdjointClassification
}

// Implements: REQ-ADJ-02 (Classification)
sealed trait AdjointClassification
object AdjointClassification {
  case object Isomorphism extends AdjointClassification  // f⁻(f(x)) = x, f(f⁻(y)) = y
  case object Embedding extends AdjointClassification    // f⁻(f(x)) = x, f(f⁻(y)) ⊆ y
  case object Projection extends AdjointClassification   // f⁻(f(x)) ⊇ x, f(f⁻(y)) = y
  case object Lossy extends AdjointClassification        // f⁻(f(x)) ⊇ x, f(f⁻(y)) ⊆ y
}

// Implements: REQ-ADJ-07 (Composition)
object AdjointComposition {
  def compose[A, B, C](
    f: Adjoint[A, B],
    g: Adjoint[B, C]
  ): Adjoint[A, C] = new Adjoint[A, C] {
    def forward(a: A): C = g.forward(f.forward(a))
    def backward(c: C): A = f.backward(g.backward(c))  // contravariant!
    def classification: AdjointClassification = composeClassifications(f.classification, g.classification)
  }

  def composeClassifications(a: AdjointClassification, b: AdjointClassification): AdjointClassification
}
```

### 7.2 Backward Metadata Capture

```scala
// Implements: REQ-ADJ-04, REQ-ADJ-05, REQ-ADJ-06
case class ReverseJoinTable(
  groupKey: String,
  constituentKeys: Set[String]
)

case class FilteredKeys(
  passedKeys: Set[String],
  filteredOutKeys: Set[String]
)

case class KleisliParentMap(
  childKey: String,
  parentKey: String
)

// Implements: REQ-ADJ-11 (Metadata Storage)
sealed trait AdjointStorageStrategy
object AdjointStorageStrategy {
  case object Inline extends AdjointStorageStrategy
  case object SeparateTable extends AdjointStorageStrategy
  case object Compressed extends AdjointStorageStrategy
}
```

### 7.3 Reconciliation & Impact Analysis

```scala
// Implements: REQ-ADJ-08 (Data Reconciliation)
trait ReconciliationEngine {
  def reconcile[T, U](adjoint: Adjoint[T, U], input: Set[T]): ReconciliationResult[T]
}

case class ReconciliationResult[T](
  original: Set[T],
  roundTripped: Set[T],
  missing: Set[T],
  isExact: Boolean
)

// Implements: REQ-ADJ-09 (Impact Analysis)
trait ImpactAnalyzer {
  def analyzeImpact[T, U](adjoint: Adjoint[T, U], targetSubset: Set[U]): Set[T]
}
```

---

## 8. Module: cdme-ai-assurance

**Package**: `com.cdme.ai.assurance`
**Dependencies**: cdme-compiler
**Requirements**: REQ-AI-01, REQ-AI-02, REQ-AI-03

### 8.1 AI Assurance Layer

```scala
// Implements: REQ-AI-01 (Topological Validity)
trait AIAssuranceValidator {
  def validateMapping(
    mapping: MappingDefinition,
    category: Category,
    source: MappingSource  // human or AI
  ): Either[List[AssuranceViolation], AssuranceCertificate]
}

sealed trait MappingSource
object MappingSource {
  case object Human extends MappingSource
  case object AI extends MappingSource
}

// Implements: REQ-AI-02 (Triangulation)
case class AssuranceCertificate(
  intentLink: String,
  logicProof: TypeUnificationReport,
  lineageProof: LineageGraph,
  executionTrace: List[ExecutionStep],
  timestamp: java.time.Instant
)

sealed trait AssuranceViolation
object AssuranceViolation {
  case class HallucinatedMorphism(name: String) extends AssuranceViolation
  case class InvalidType(expected: SemanticType, actual: SemanticType) extends AssuranceViolation
  case class GrainViolation(detail: String) extends AssuranceViolation
  case class AccessViolation(morphism: String) extends AssuranceViolation
}
```

---

## 9. Module: cdme-api

**Package**: `com.cdme.api`
**Dependencies**: All modules
**Requirements**: External interface (REST/gRPC)

### 9.1 API Endpoints

```
POST   /api/v1/mappings/compile     → Compile & validate mapping
POST   /api/v1/mappings/execute     → Execute compiled plan
POST   /api/v1/mappings/dry-run     → Dry run (REQ-AI-03)
GET    /api/v1/lineage/{targetKey}  → Get lineage graph
POST   /api/v1/reconcile            → Run reconciliation
POST   /api/v1/impact-analysis      → Run impact analysis
GET    /api/v1/runs/{runId}         → Get run manifest
GET    /api/v1/ledger/{runId}       → Get accounting ledger
POST   /api/v1/fidelity/verify     → Verify cross-domain fidelity
GET    /api/v1/fidelity/chain      → Get fidelity certificate chain
```

---

## 10. Cross-Domain Fidelity Components

### 10.1 Covariance Contracts

```scala
// Implements: REQ-COV-01 through REQ-COV-08
case class CovarianceContract(
  name: String,
  version: String,
  sourceDomain: String,
  targetDomain: String,
  morphisms: List[CrossDomainMorphism],
  invariants: List[FidelityInvariant],
  enforcementMode: EnforcementMode,
  propagationDirection: PropagationDirection
)

sealed trait FidelityInvariantType
object FidelityInvariantType {
  case object Conservation extends FidelityInvariantType  // sum(A) == sum(B)
  case object Coverage extends FidelityInvariantType      // count(B) >= count(A)
  case object Alignment extends FidelityInvariantType     // A.date == B.date
  case object Containment extends FidelityInvariantType   // keys(A) ⊆ keys(B)
}

case class FidelityInvariant(
  id: String,
  invariantType: FidelityInvariantType,
  expression: String,
  materialityThreshold: Option[Double],
  grain: Grain
)

sealed trait EnforcementMode
object EnforcementMode {
  case object Strict extends EnforcementMode
  case object Deferred extends EnforcementMode
  case object Advisory extends EnforcementMode
}

// Implements: REQ-COV-03 (Fidelity Certificate)
case class FidelityCertificate(
  certificateId: String,
  contractName: String,
  contractVersion: String,
  sourceStateHash: String,
  targetStateHash: String,
  invariantResults: List[InvariantResult],
  runId: String,
  timestamp: java.time.Instant,
  previousCertificateHash: Option[String]  // REQ-COV-08: chain
)

case class InvariantResult(
  invariantId: String,
  passed: Boolean,
  expectedValue: String,
  actualValue: String,
  materialityThreshold: Option[Double],
  breachSeverity: Option[BreachSeverity]
)

sealed trait BreachSeverity
object BreachSeverity {
  case object Immaterial extends BreachSeverity
  case object Material extends BreachSeverity
  case object Critical extends BreachSeverity
}
```

---

## 11. Data Quality Components

```scala
// Implements: REQ-PDM-06, REQ-DQ-01 through REQ-DQ-04
sealed trait LateArrivalStrategy
object LateArrivalStrategy {
  case object Reject extends LateArrivalStrategy
  case object Reprocess extends LateArrivalStrategy
  case object Accumulate extends LateArrivalStrategy
  case object Backfill extends LateArrivalStrategy
}

case class VolumeThreshold(
  minRecords: Option[Long],
  maxRecords: Option[Long],
  baselineWindow: Int,
  action: ThresholdAction
)

sealed trait ThresholdAction
object ThresholdAction {
  case object Warn extends ThresholdAction
  case object Halt extends ThresholdAction
  case object Quarantine extends ThresholdAction
}

case class CustomValidationRule(
  id: String,
  description: String,
  predicate: String,
  severity: ValidationSeverity,
  errorMessageTemplate: String
)

sealed trait ValidationSeverity
object ValidationSeverity {
  case object Error extends ValidationSeverity
  case object Warning extends ValidationSeverity
}
```

---

## 12. Architecture Decision Records (ADRs)

### ADR-001: Category Theory as Foundation

**Decision**: Model all data relationships as a Category (objects + morphisms) with functorial bindings.
**Rationale**: Provides mathematical guarantees for composition, type safety, and grain correctness.
**Consequence**: All transformations must be expressed as morphisms, even simple field renames.

### ADR-002: Compile-then-Execute Pattern

**Decision**: All mappings are compiled to a plan before execution.
**Rationale**: Catches errors at definition time (hallucinations, grain violations, type mismatches).
**Consequence**: No ad-hoc query execution; all paths must be pre-validated.

### ADR-003: Errors as Data (Either Monad)

**Decision**: Use Either[ErrorObject, Value] instead of exceptions.
**Rationale**: INT-002 Axiom 10 — failures are data in the Error Domain.
**Consequence**: All morphisms return Either; error routing is explicit.

### ADR-004: Adjoint Pairs for All Morphisms

**Decision**: Every morphism implements Adjoint[T,U] with forward/backward.
**Rationale**: INT-005 — enables reconciliation, impact analysis, backward traversal.
**Consequence**: Aggregation wrappers must capture reverse-join metadata during forward execution.

### ADR-005: Immutable Run Hierarchy

**Decision**: Every run is bound to immutable snapshots of config, code, and design artifacts.
**Rationale**: REQ-TRV-05-A — regulatory requirement for reproducibility.
**Consequence**: RunManifest must include cryptographic hashes of all artifacts.

### ADR-006: Spark as Default Runtime with Hexagonal Boundary

**Decision**: Spark binding is in cdme-spark only; core model/compiler are runtime-agnostic.
**Rationale**: REQ-PDM-01 — LDM/PDM separation via hexagonal architecture.
**Consequence**: cdme-model and cdme-compiler must never import Spark classes.

### ADR-007: sbt Multi-Module with Strict Dependency DAG

**Decision**: 8 sub-projects with acyclic dependency graph.
**Rationale**: Enforces architectural boundaries; prevents circular dependencies.
**Consequence**: Each module has explicit, minimal dependencies.

---

## 13. Requirements-to-Module Mapping

| Module | Requirements |
|--------|-------------|
| cdme-model | REQ-LDM-01..06, REQ-TYP-01..02, REQ-TYP-07, REQ-ERROR-01, REQ-LDM-04-A |
| cdme-compiler | REQ-LDM-03, REQ-TRV-02, REQ-TYP-05..06, REQ-AI-01, REQ-TRV-06, REQ-SHF-01, REQ-INT-05, RIC-LIN-06 |
| cdme-runtime | REQ-TRV-01, REQ-TRV-03..05, REQ-TRV-05-A/B, REQ-SHF-01, REQ-ACC-01..05, REQ-TYP-03, REQ-TYP-03-A, REQ-TYP-04, REQ-INT-01..08, RIC-ERR-01 |
| cdme-spark | REQ-PDM-01..06, REQ-TRV-01, RIC-SKW-01, RIC-PHY-01, REQ-DQ-01..04 |
| cdme-lineage | REQ-INT-03, RIC-LIN-01, RIC-LIN-04, RIC-LIN-06, RIC-LIN-07 |
| cdme-adjoint | REQ-ADJ-01..11 |
| cdme-ai-assurance | REQ-AI-01..03, REQ-COV-01..08 |
| cdme-api | External interface requirements |

---

**Document Version**: 1.0.0
**Generated By**: AISDLC Design Agent (full-auto)
**Date**: 2026-02-23
