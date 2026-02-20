package com.cdme.testkit.fixtures

// Implements: (test fixtures for all modules)

import com.cdme.model.adjoint.{AdjointPair, SelfAdjoint, LossyContainment}
import com.cdme.model.config._
import com.cdme.model.entity._
import com.cdme.model.grain._
import com.cdme.model.graph.LdmGraph
import com.cdme.model.monoid.SumMonoid
import com.cdme.model.morphism._
import com.cdme.model.pdm._
import com.cdme.model.types._
import com.cdme.model.version.ArtifactVersion

/**
 * Pre-built test data for use across all CDME test suites.
 *
 * Provides a small but complete domain model with three entities
 * (Trade, Counterparty, DailyAggregate) connected by structural,
 * computational, and algebraic morphisms. PDM bindings, mapping
 * definitions, job configuration, and epoch instances are included.
 *
 * All fixtures use deterministic values (no randomness) so that
 * test assertions are reproducible.
 *
 * Usage:
 * {{{
 * import com.cdme.testkit.fixtures.TestFixtures._
 *
 * val graph = sampleGraph
 * val bindings = samplePdmBindings
 * }}}
 */
object TestFixtures {

  // -------------------------------------------------------------------------
  // Artifact versions
  // -------------------------------------------------------------------------

  /** Standard version for test fixtures. */
  val testVersion: ArtifactVersion = ArtifactVersion("test", "1.0.0")

  // -------------------------------------------------------------------------
  // Entity IDs
  // -------------------------------------------------------------------------

  val tradeEntityId: EntityId         = EntityId("trade")
  val counterpartyEntityId: EntityId  = EntityId("counterparty")
  val dailyAggregateEntityId: EntityId = EntityId("daily_aggregate")

  // -------------------------------------------------------------------------
  // Entities
  // -------------------------------------------------------------------------

  /**
   * Trade entity: atomic grain, 3 attributes.
   */
  val tradeEntity: Entity = Entity(
    id = tradeEntityId,
    name = "Trade",
    grain = Atomic,
    attributes = List(
      Attribute("trade_id", PrimitiveType(StringKind)),
      Attribute("counterparty_id", PrimitiveType(StringKind)),
      Attribute("notional", PrimitiveType(DoubleKind))
    ),
    acl = None,
    version = testVersion
  )

  /**
   * Counterparty entity: atomic grain, 2 attributes.
   */
  val counterpartyEntity: Entity = Entity(
    id = counterpartyEntityId,
    name = "Counterparty",
    grain = Atomic,
    attributes = List(
      Attribute("counterparty_id", PrimitiveType(StringKind)),
      Attribute("region", PrimitiveType(StringKind))
    ),
    acl = None,
    version = testVersion
  )

  /**
   * DailyAggregate entity: daily grain, 3 attributes.
   */
  val dailyAggregateEntity: Entity = Entity(
    id = dailyAggregateEntityId,
    name = "DailyAggregate",
    grain = DailyAggregate,
    attributes = List(
      Attribute("counterparty_id", PrimitiveType(StringKind)),
      Attribute("trade_date", PrimitiveType(DateKind)),
      Attribute("total_notional", PrimitiveType(DoubleKind))
    ),
    acl = None,
    version = testVersion
  )

  // -------------------------------------------------------------------------
  // Morphism IDs
  // -------------------------------------------------------------------------

  val tradeToCounterpartyId: MorphismId = MorphismId("trade_to_counterparty")
  val tradeNotionalCalcId: MorphismId   = MorphismId("trade_notional_calc")
  val tradeToDailyAggId: MorphismId     = MorphismId("trade_to_daily_agg")

  // -------------------------------------------------------------------------
  // Morphisms
  // -------------------------------------------------------------------------

  /**
   * Structural morphism: Trade -> Counterparty (join on counterparty_id).
   */
  val tradeToCounterparty: StructuralMorphism = StructuralMorphism(
    id = tradeToCounterpartyId,
    domain = tradeEntityId,
    codomain = counterpartyEntityId,
    cardinality = ManyToOne,
    adjoint = AdjointPair[Any, Any](
      forward = (a: Any) => Right(a),
      backward = (b: Any) => Set(b),
      containmentType = SelfAdjoint
    ),
    joinCondition = ColumnEquality("counterparty_id", "counterparty_id"),
    acl = None,
    version = testVersion
  )

  /**
   * Computational morphism: Trade -> Trade (notional calculation).
   */
  val tradeNotionalCalc: ComputationalMorphism = ComputationalMorphism(
    id = tradeNotionalCalcId,
    domain = tradeEntityId,
    codomain = tradeEntityId,
    cardinality = OneToOne,
    adjoint = AdjointPair[Any, Any](
      forward = (a: Any) => Right(a),
      backward = (b: Any) => Set(b),
      containmentType = SelfAdjoint
    ),
    expression = ColumnRef("notional"),
    deterministic = true,
    acl = None,
    version = testVersion
  )

  /**
   * Algebraic morphism: Trade -> DailyAggregate (sum notional by day).
   */
  val tradeToDailyAgg: AlgebraicMorphism = AlgebraicMorphism(
    id = tradeToDailyAggId,
    domain = tradeEntityId,
    codomain = dailyAggregateEntityId,
    cardinality = ManyToOne,
    adjoint = AdjointPair[Any, Any](
      forward = (a: Any) => Right(a),
      backward = (b: Any) => Set(b),
      containmentType = LossyContainment
    ),
    monoid = SumMonoid.asInstanceOf[com.cdme.model.monoid.Monoid[Any]],
    targetGrain = DailyAggregate,
    acl = None,
    version = testVersion
  )

  // -------------------------------------------------------------------------
  // LDM Graph
  // -------------------------------------------------------------------------

  /**
   * A small sample LDM graph with 3 entities and 3 morphisms.
   */
  val sampleGraph: LdmGraph = LdmGraph(
    nodes = Map(
      tradeEntityId -> tradeEntity,
      counterpartyEntityId -> counterpartyEntity,
      dailyAggregateEntityId -> dailyAggregateEntity
    ),
    edges = Map(
      tradeToCounterpartyId -> tradeToCounterparty,
      tradeNotionalCalcId -> tradeNotionalCalc,
      tradeToDailyAggId -> tradeToDailyAgg
    ),
    version = testVersion
  )

  // -------------------------------------------------------------------------
  // PDM Bindings
  // -------------------------------------------------------------------------

  /**
   * Sample PDM bindings mapping each entity to Parquet storage.
   */
  val samplePdmBindings: Map[EntityId, PdmBinding] = Map(
    tradeEntityId -> PdmBinding(
      logicalEntity = tradeEntityId,
      location = ParquetLocation("/data/trades/"),
      generationGrain = EventGrain,
      boundary = BoundaryDef("daily", Map("column_name" -> "trade_date")),
      temporalBinding = None,
      version = testVersion
    ),
    counterpartyEntityId -> PdmBinding(
      logicalEntity = counterpartyEntityId,
      location = ParquetLocation("/data/counterparties/"),
      generationGrain = SnapshotGrain,
      boundary = BoundaryDef("snapshot", Map.empty),
      temporalBinding = None,
      version = testVersion
    ),
    dailyAggregateEntityId -> PdmBinding(
      logicalEntity = dailyAggregateEntityId,
      location = ParquetLocation("/data/daily_aggregates/"),
      generationGrain = SnapshotGrain,
      boundary = BoundaryDef("daily", Map("column_name" -> "trade_date")),
      temporalBinding = None,
      version = testVersion
    )
  )

  // -------------------------------------------------------------------------
  // Job Configuration
  // -------------------------------------------------------------------------

  /**
   * Sample job configuration for testing.
   */
  val sampleJobConfig: JobConfig = JobConfig(
    id = "test-job-001",
    version = testVersion,
    lineageMode = KeyDerivableLineage,
    failureThreshold = Some(PercentageThreshold(5.0, 10000)),
    cardinalityBudget = CardinalityBudget(
      maxOutputRows = 10000000L,
      maxJoinDepth = 5,
      maxIntermediateSize = 50000000L
    ),
    artifactRefs = Map(
      "ldm" -> ArtifactRef("ldm/test_v1.0.0.yml", "1.0.0"),
      "pdm" -> ArtifactRef("pdm/test_v1.0.0.yml", "1.0.0")
    ),
    epochConfig = EpochConfig("daily", Map("timezone" -> "UTC")),
    sinkConfig = SinkConfig(
      successSink = ParquetLocation("/output/success/"),
      errorSink = ParquetLocation("/output/errors/"),
      ledgerSink = ParquetLocation("/output/ledger/")
    )
  )

  // -------------------------------------------------------------------------
  // Epoch
  // -------------------------------------------------------------------------

  /**
   * A sample processing epoch (2024-01-15, full day).
   */
  val sampleEpoch: Epoch = Epoch(
    id = "2024-01-15",
    start = java.time.Instant.parse("2024-01-15T00:00:00Z"),
    end = java.time.Instant.parse("2024-01-16T00:00:00Z"),
    parameters = Map("business_date" -> "2024-01-15")
  )
}
