// Implements: REQ-F-TRV-006, REQ-F-INT-004, REQ-F-INT-006, REQ-NFR-SEC-001
// ADR-006: Content-Addressed Versioning
package cdme.compiler

import cdme.model.category.ContentHash
import cdme.model.error.ValidationError

import java.time.Instant

/**
 * A pinned lookup version snapshot used in an execution artifact.
 *
 * @param lookupId the lookup identifier
 * @param version  the pinned version string
 * @param pinnedAt when the version was pinned
 */
final case class LookupVersionPin(
    lookupId: String,
    version: String,
    pinnedAt: Instant
)

/**
 * Configuration for deterministic surrogate key generation.
 *
 * @param algorithm the hash algorithm (e.g., "SHA-256", "MurmurHash3")
 * @param seed      optional seed for determinism
 */
final case class SurrogateKeyConfig(
    algorithm: String = "SHA-256",
    seed: Option[Long] = None
)

/**
 * Error sink configuration for an execution artifact.
 */
final case class ErrorSinkConfig(
    sinkType: String, // "filesystem", "database", "messagequeue"
    location: String,
    format: String = "parquet" // "parquet", "json"
)

/**
 * Configuration for batch failure thresholds.
 *
 * @param maxAbsoluteErrors maximum absolute number of errors before halting
 * @param maxErrorPercentage maximum error percentage before halting
 * @param strategy what to do when threshold is exceeded: "halt" or "continue_with_warning"
 */
final case class BatchThresholdConfig(
    maxAbsoluteErrors: Option[Long] = None,
    maxErrorPercentage: Option[Double] = Some(0.01), // 1% default
    strategy: String = "halt"
)

/**
 * Configuration for the circuit breaker pre-phase.
 *
 * @param sampleSize              number of records to sample (default 10,000)
 * @param structuralErrorThreshold percentage threshold for structural errors (default 5%)
 */
final case class CircuitBreakerConfig(
    sampleSize: Int = 10000,
    structuralErrorThreshold: Double = 0.05
)

/**
 * A sealed, validated execution artifact.
 *
 * The artifact is immutable and content-addressed (SHA-256 hash of canonical
 * serialised form). Deterministic reproducibility is guaranteed by:
 *   - Fixed lookup versions
 *   - Seeded randomness
 *   - Immutable business logic
 *   - Deterministic key generation
 *
 * @param hash                content-addressed hash
 * @param compiledPaths       all validated compiled paths
 * @param lookupVersionPins   pinned lookup versions
 * @param errorSink           error sink configuration
 * @param budget              cardinality budget
 * @param batchThreshold      batch failure threshold
 * @param circuitBreakerConfig circuit breaker configuration
 * @param surrogateKeyConfig  surrogate key generation configuration
 * @param createdAt           when this artifact was built
 */
final case class ExecutionArtifact(
    hash: ContentHash,
    compiledPaths: List[CompiledPath],
    lookupVersionPins: Map[String, LookupVersionPin],
    errorSink: ErrorSinkConfig,
    budget: CardinalityBudget,
    batchThreshold: BatchThresholdConfig,
    circuitBreakerConfig: CircuitBreakerConfig,
    surrogateKeyConfig: SurrogateKeyConfig = SurrogateKeyConfig(),
    createdAt: Instant = Instant.now()
)

/**
 * Builds sealed execution artifacts from compiled paths.
 */
object ArtifactBuilder {

  /**
   * Build an execution artifact from compiled paths and configuration.
   *
   * @param compiledPaths    validated compiled paths
   * @param errorSink        error sink configuration
   * @param budget           cardinality budget
   * @param lookupVersionPins lookup version pins
   * @param batchThreshold   batch failure threshold
   * @param circuitBreaker   circuit breaker configuration
   * @param surrogateKey     surrogate key configuration
   * @return Right(artifact) or Left(errors) if validation fails
   */
  def build(
      compiledPaths: List[CompiledPath],
      errorSink: ErrorSinkConfig,
      budget: CardinalityBudget,
      lookupVersionPins: Map[String, LookupVersionPin] = Map.empty,
      batchThreshold: BatchThresholdConfig = BatchThresholdConfig(),
      circuitBreaker: CircuitBreakerConfig = CircuitBreakerConfig(),
      surrogateKey: SurrogateKeyConfig = SurrogateKeyConfig()
  ): Either[List[ValidationError], ExecutionArtifact] = {
    val errors = scala.collection.mutable.ListBuffer[ValidationError]()

    if (compiledPaths.isEmpty)
      errors += ValidationError.General("No compiled paths provided")

    if (errorSink.location.isEmpty)
      errors += ValidationError.General("Error sink location must be specified")

    if (errors.nonEmpty)
      Left(errors.toList)
    else {
      val canonical = canonicalForm(compiledPaths, errorSink, budget)
      val hash = sha256(canonical)
      Right(ExecutionArtifact(
        hash = ContentHash(hash),
        compiledPaths = compiledPaths,
        lookupVersionPins = lookupVersionPins,
        errorSink = errorSink,
        budget = budget,
        batchThreshold = batchThreshold,
        circuitBreakerConfig = circuitBreaker,
        surrogateKeyConfig = surrogateKey
      ))
    }
  }

  private def canonicalForm(
      paths: List[CompiledPath],
      sink: ErrorSinkConfig,
      budget: CardinalityBudget
  ): String = {
    val pathParts = paths.map(p =>
      s"P[${p.resultType.typeName}|${p.resultGrain.level}|${p.steps.size}]"
    )
    s"${pathParts.mkString(";")}|${sink.sinkType}:${sink.location}|${budget.maxOutputRows}"
  }

  private def sha256(input: String): String = {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    val hashBytes = digest.digest(input.getBytes("UTF-8"))
    hashBytes.map(b => String.format("%02x", b)).mkString
  }
}
