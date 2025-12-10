package cdme

import cats.implicits._
import cdme.config._
import cdme.core._
import cdme.registry._
import cdme.compiler._
import cdme.executor._
import org.apache.spark.sql.SparkSession
import java.util.UUID

/**
 * CDME Main entry point - Steel Thread.
 * Implements: CONFIG → INIT → COMPILE → EXECUTE → OUTPUT
 */
object Main {

  def main(args: Array[String]): Unit = {
    if (args.length < 3) {
      println("Usage: cdme-spark <config-path> <mapping-name> <epoch>")
      println("Example: cdme-spark config/cdme_config.yaml order_summary 2024-12-15")
      System.exit(1)
    }

    val configPath = args(0)
    val mappingName = args(1)
    val epoch = args(2)

    run(configPath, mappingName, epoch) match {
      case Right(result) =>
        println(s"[SUCCESS] Execution completed")
        println(s"  Mapping: ${result.mappingName}")
        println(s"  Source rows: ${result.sourceRowCount}")
        println(s"  Output rows: ${result.outputRowCount}")
        System.exit(0)

      case Left(error) =>
        println(s"[ERROR] Execution failed")
        error match {
          case Left(configError) =>
            println(s"  Configuration error: ${configError.message}")
          case Right(cdmeError) =>
            println(s"  CDME error: ${cdmeError.errorType}")
            println(s"  Details: ${cdmeError.morphismPath}")
        }
        System.exit(1)
    }
  }

  /**
   * Main execution flow.
   */
  def run(
    configPath: String,
    mappingName: String,
    epoch: String
  ): Either[Either[ConfigError, CdmeError], ExecutionResult] = {

    // STAGE 1: CONFIG
    println(s"[CONFIG] Loading configuration from $configPath")
    val result = for {
      config <- ConfigLoader.load(configPath).leftMap(Left(_))

      // STAGE 2: INIT
      _ = println(s"[INIT] Initializing CDME engine for epoch $epoch")
      ctx <- initContext(config, epoch).leftMap(Left(_))

      _ = println(s"[INIT] Run ID: ${ctx.runId}")
      _ = println(s"[INIT] Loaded ${ctx.registry.asInstanceOf[SchemaRegistryImpl]} entities")

      // STAGE 3: COMPILE
      _ = println(s"[COMPILE] Compiling mapping: $mappingName")
      mapping <- loadMapping(config, mappingName).leftMap(Left(_))
      compiler = new Compiler(ctx.registry)
      plan <- compiler.compile(mapping, ctx).leftMap(Right(_))

      _ = println(s"[COMPILE] Plan generated with ${plan.morphisms.length} morphisms")
      _ = println(s"[COMPILE] Grain transition: ${plan.sourceGrain.level.name} → ${plan.targetGrain.level.name}")

      // STAGE 4: EXECUTE
      _ = println(s"[EXECUTE] Executing plan...")
      executor = new Executor(ctx)
      result <- executor.execute(plan).leftMap(Right(_))

      // STAGE 5: OUTPUT
      _ = println(s"[OUTPUT] Writing results...")
      _ <- writeOutput(result, config, ctx).leftMap(Right(_))

    } yield result

    result
  }

  /**
   * Initialize execution context.
   */
  private def initContext(
    config: CdmeConfig,
    epoch: String
  ): Either[ConfigError, ExecutionContext] = {

    for {
      // Load entities
      entities <- ConfigLoader.loadEntities(config.registry.ldm_path)

      // Load bindings
      bindings <- ConfigLoader.loadBindings(config.registry.pdm_path)

      // Create registry
      registry <- SchemaRegistryImpl.fromConfig(entities, bindings)

      // Create Spark session
      spark = SparkSession.builder()
        .appName("CDME")
        .master("local[*]")  // Use local for testing
        .getOrCreate()

      // Generate run ID
      runId = s"run_${java.time.LocalDateTime.now().toString.replace(":", "").replace(".", "")}_${UUID.randomUUID().toString.take(8)}"

    } yield ExecutionContext(
      runId = runId,
      epoch = epoch,
      spark = spark,
      registry = registry,
      config = config
    )
  }

  /**
   * Load mapping definition.
   */
  private def loadMapping(
    config: CdmeConfig,
    mappingName: String
  ): Either[ConfigError, MappingConfig] = {
    // For MVP, we'll require mapping file path
    // In production, this would load from mappings directory
    Left(ConfigError.ValidationError(
      s"Mapping loading not implemented in MVP. Please provide mapping configuration directly."
    ))
  }

  /**
   * Write output.
   */
  private def writeOutput(
    result: ExecutionResult,
    config: CdmeConfig,
    ctx: ExecutionContext
  ): Either[CdmeError, Unit] = {
    try {
      // Write data
      result.data.write
        .mode("overwrite")
        .parquet(s"${config.output.data_path}/${result.mappingName}")

      // Write errors if any
      if (result.errors.count() > 0) {
        result.errors.write
          .mode("overwrite")
          .parquet(s"${config.output.error_path}/${result.mappingName}")
      }

      println(s"[OUTPUT] Data written to: ${config.output.data_path}/${result.mappingName}")
      Right(())
    } catch {
      case e: Exception =>
        Left(CdmeError.CompilationError(s"Failed to write output: ${e.getMessage}"))
    }
  }
}

/**
 * Simplified CdmeEngine for testing.
 */
object CdmeEngine {

  /**
   * Execute mapping with direct configuration.
   */
  def executeDirect(
    entities: Map[String, Entity],
    bindings: Map[String, PhysicalBinding],
    mapping: MappingConfig,
    epoch: String
  ): Either[CdmeError, ExecutionResult] = {

    for {
      // Create registry
      registry <- SchemaRegistryImpl.fromConfig(entities, bindings)
        .leftMap(e => CdmeError.CompilationError(e.message))

      // Create Spark session
      spark = SparkSession.builder()
        .appName("CDME-Direct")
        .master("local[*]")
        .getOrCreate()

      // Create config (minimal)
      config = CdmeConfig(
        version = "1.0",
        registry = RegistryConfig("", ""),
        execution = ExecutionConfig("BATCH", 0.05, "BASIC"),
        output = OutputConfig("output/data", "output/errors", "output/lineage")
      )

      // Create context
      ctx = ExecutionContext(
        runId = UUID.randomUUID().toString,
        epoch = epoch,
        spark = spark,
        registry = registry,
        config = config
      )

      // Compile
      compiler = new Compiler(registry)
      plan <- compiler.compile(mapping, ctx)

      // Execute
      executor = new Executor(ctx)
      result <- executor.execute(plan)

    } yield result
  }
}
