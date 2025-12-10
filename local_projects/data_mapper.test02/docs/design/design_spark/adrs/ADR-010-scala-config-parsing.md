# ADR-010: YAML Configuration Parsing in Scala

**Status**: Accepted
**Date**: 2025-12-10
**Deciders**: Design Agent
**Depends On**: ADR-006 (Scala Type System), ADR-009 (Project Structure)
**Implements**: REQ-CFG-01, REQ-CFG-02, REQ-CFG-03

---

## Context

CDME requires loading configuration from YAML files:
- Master config (cdme_config.yaml)
- LDM entity definitions (ldm/*.yaml)
- PDM physical bindings (pdm/*.yaml)
- Mapping definitions (mappings/*.yaml)
- Type definitions (types/*.yaml)

Requirements:
- REQ-CFG-01: Type-safe parsing (catch errors at load time)
- REQ-CFG-02: Validation at parse time (cross-references, required fields)
- REQ-CFG-03: Auto-derive case classes from YAML structure
- REQ-CFG-04: Support for parameterized configs (e.g., `${epoch}`)

Scala YAML parsing libraries:
- **circe-yaml** - Functional, JSON AST-based, Cats integration
- **pureconfig** - Type-safe, convention-based, ConfigFactory integration
- **Typesafe Config** - Java-based, HOCON format (superset of JSON)
- **SnakeYAML** - Java library, imperative, low-level

---

## Decision

**Use circe-yaml with the following architecture:**

1. **circe-yaml** for YAML parsing
2. **circe-generic** for automatic Decoder derivation
3. **circe-refined** for refinement type validation
4. **Custom Decoder instances** for complex validations
5. **Parse-time validation** with `Decoder[A]` composition
6. **Two-phase loading**: Parse → Validate cross-references

---

## Rationale

### 1. circe-yaml vs Alternatives

| Feature | circe-yaml | pureconfig | Typesafe Config |
|---------|------------|------------|-----------------|
| Format | YAML | HOCON/YAML | HOCON |
| Functional | Yes (Cats) | Yes | No |
| Auto-derivation | Yes (generic) | Yes | Limited |
| Validation | Decoder-based | Reader-based | Manual |
| Refinement types | circe-refined | Built-in | Manual |
| Error accumulation | Either | Either | Try |
| Ecosystem | circe | pureconfig | Java |

**Decision: circe-yaml**
- Functional API with Cats integration (aligns with ADR-007)
- Powerful Decoder composition for validation
- Refinement type support via circe-refined
- JSON AST allows multi-format support (YAML, JSON, HOCON)

### 2. Automatic Case Class Derivation

```scala
import io.circe._
import io.circe.generic.semiauto._
import io.circe.yaml.parser

// Configuration model
case class CdmeConfig(
  version: String,
  registry: RegistryConfig,
  execution: ExecutionConfig,
  output: OutputConfig
)

case class RegistryConfig(
  ldm_path: String,
  pdm_path: String,
  types_path: String
)

case class ExecutionConfig(
  mode: ExecutionMode,
  error_threshold: Double,
  lineage_mode: LineageMode
)

sealed trait ExecutionMode
case object BATCH extends ExecutionMode
case object STREAMING extends ExecutionMode

sealed trait LineageMode
case object BASIC extends LineageMode
case object FULL extends LineageMode
case object SAMPLED extends LineageMode

// Automatic decoder derivation
implicit val executionModeDecoder: Decoder[ExecutionMode] = Decoder.decodeString.emap {
  case "BATCH" => Right(BATCH)
  case "STREAMING" => Right(STREAMING)
  case other => Left(s"Invalid execution mode: $other")
}

implicit val lineageModeDecoder: Decoder[LineageMode] = Decoder.decodeString.emap {
  case "BASIC" => Right(BASIC)
  case "FULL" => Right(FULL)
  case "SAMPLED" => Right(SAMPLED)
  case other => Left(s"Invalid lineage mode: $other")
}

implicit val registryConfigDecoder: Decoder[RegistryConfig] = deriveDecoder
implicit val executionConfigDecoder: Decoder[ExecutionConfig] = deriveDecoder
implicit val outputConfigDecoder: Decoder[OutputConfig] = deriveDecoder
implicit val cdmeConfigDecoder: Decoder[CdmeConfig] = deriveDecoder

// Parse YAML
def loadConfig(path: String): Either[Error, CdmeConfig] = {
  val yamlString = scala.io.Source.fromFile(path).mkString
  parser.parse(yamlString).flatMap(_.as[CdmeConfig])
}
```

**Benefits**:
- Automatic derivation via `deriveDecoder`
- Type-safe enum parsing (sealed traits)
- Compile-time guarantee that all fields are handled

### 3. Refinement Type Integration

```scala
import eu.timepit.refined._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric._
import eu.timepit.refined.collection._
import io.circe.refined._  // circe-refined integration

// LDM attribute with refinement
case class Attribute(
  name: String Refined NonEmpty,
  `type`: String,
  nullable: Boolean,
  primary_key: Option[Boolean],
  refinement: Option[String],
  allowed_values: Option[List[String]]
)

// Refinement automatically validated at parse time
implicit val attributeDecoder: Decoder[Attribute] = deriveDecoder

// Parse will fail if name is empty string
val result: Either[Error, Attribute] = parser.parse(yamlString).flatMap(_.as[Attribute])
// Left(DecodingFailure("Predicate isEmpty() did not fail.", ...))
```

**Benefits**:
- REQ-CFG-01: Refinement violations caught at parse time
- No runtime validation needed - type system guarantees correctness
- Self-documenting (type signature shows constraints)

### 4. Custom Decoder with Validation

```scala
// Entity decoder with cross-reference validation
case class Entity(
  name: String,
  grain: GrainConfig,
  attributes: List[Attribute],
  relationships: List[Relationship]
)

case class Relationship(
  name: String,
  target: String,
  cardinality: Cardinality,
  join_key: String
)

sealed trait Cardinality
case object OneToOne extends Cardinality
case object NToOne extends Cardinality
case object OneToN extends Cardinality

// Custom decoder with validation logic
implicit val cardinalityDecoder: Decoder[Cardinality] = Decoder.decodeString.emap {
  case "1:1" => Right(OneToOne)
  case "N:1" => Right(NToOne)
  case "1:N" => Right(OneToN)
  case other => Left(s"Invalid cardinality: $other. Must be 1:1, N:1, or 1:N")
}

// Validate that join_key exists in attributes
implicit val relationshipDecoder: Decoder[Relationship] = new Decoder[Relationship] {
  def apply(c: HCursor): Decoder.Result[Relationship] = {
    for {
      name <- c.downField("name").as[String]
      target <- c.downField("target").as[String]
      cardinality <- c.downField("cardinality").as[Cardinality]
      joinKey <- c.downField("join_key").as[String]
    } yield Relationship(name, target, cardinality, joinKey)
  }
}

// Entity decoder validates relationships reference valid attributes
implicit val entityDecoder: Decoder[Entity] = new Decoder[Entity] {
  def apply(c: HCursor): Decoder.Result[Entity] = {
    for {
      name <- c.downField("name").as[String]
      grain <- c.downField("grain").as[GrainConfig]
      attributes <- c.downField("attributes").as[List[Attribute]]
      relationships <- c.downField("relationships").as[List[Relationship]]

      // Validate join keys exist in attributes
      _ <- validateJoinKeys(attributes, relationships)
    } yield Entity(name, grain, attributes, relationships)
  }

  private def validateJoinKeys(
    attributes: List[Attribute],
    relationships: List[Relationship]
  ): Decoder.Result[Unit] = {
    val attrNames = attributes.map(_.name.value).toSet
    val invalidRels = relationships.filterNot(r => attrNames.contains(r.join_key))

    if (invalidRels.isEmpty) {
      Right(())
    } else {
      Left(DecodingFailure(
        s"Join keys not found in attributes: ${invalidRels.map(_.join_key).mkString(", ")}",
        Nil
      ))
    }
  }
}
```

**Benefits**:
- REQ-CFG-02: Validation at parse time
- Rich error messages with context
- Composable validation logic

### 5. Two-Phase Loading for Cross-References

Phase 1: Parse all files independently
Phase 2: Validate cross-entity references

```scala
case class LoadedRegistry(
  entities: Map[String, Entity],
  bindings: Map[String, PhysicalBinding],
  mappings: Map[String, Mapping]
)

object ConfigLoader {
  def loadRegistry(config: CdmeConfig): Either[ConfigError, LoadedRegistry] = {
    for {
      // Phase 1: Parse all files
      entities <- loadAllEntities(config.registry.ldm_path)
      bindings <- loadAllBindings(config.registry.pdm_path)
      mappings <- loadAllMappings(config.registry.mappings_path)

      // Phase 2: Validate cross-references
      _ <- validateEntityReferences(entities)
      _ <- validateBindingReferences(entities, bindings)
      _ <- validateMappingReferences(entities, mappings)
    } yield LoadedRegistry(entities, bindings, mappings)
  }

  private def loadAllEntities(path: String): Either[ConfigError, Map[String, Entity]] = {
    val files = listYamlFiles(path)
    files.traverse { file =>
      loadEntity(file).map(e => e.name -> e)
    }.map(_.toMap)
  }

  private def validateEntityReferences(
    entities: Map[String, Entity]
  ): Either[ConfigError, Unit] = {
    val allRelationships = entities.values.flatMap(_.relationships)
    val invalidTargets = allRelationships
      .filterNot(r => entities.contains(r.target))

    if (invalidTargets.isEmpty) {
      Right(())
    } else {
      Left(ConfigError.UnresolvedReferences(
        s"Relationships reference unknown entities: ${invalidTargets.map(_.target).mkString(", ")}"
      ))
    }
  }

  private def validateMappingReferences(
    entities: Map[String, Entity],
    mappings: Map[String, Mapping]
  ): Either[ConfigError, Unit] = {
    mappings.values.traverse { mapping =>
      for {
        _ <- validateEntity(entities, mapping.source.entity)
        _ <- validateEntity(entities, mapping.target.entity)
        _ <- validatePaths(entities, mapping)
      } yield ()
    }.map(_ => ())
  }

  private def validatePaths(
    entities: Map[String, Entity],
    mapping: Mapping
  ): Either[ConfigError, Unit] = {
    mapping.projections.traverse { proj =>
      PathValidator.validate(entities, proj.source)
    }.map(_ => ())
  }
}
```

**Benefits**:
- REQ-CFG-02: Cross-reference validation
- Clear error messages identifying missing references
- Separation of syntax errors (phase 1) from semantic errors (phase 2)

### 6. Parameterized Configuration

Support `${variable}` placeholders in config:

```scala
case class ParameterizedString(template: String) {
  def resolve(params: Map[String, String]): Either[String, String] = {
    val pattern = "\\$\\{([^}]+)\\}".r
    val unresolved = pattern.findAllMatchIn(template)
      .map(_.group(1))
      .filterNot(params.contains)
      .toList

    if (unresolved.isEmpty) {
      val resolved = pattern.replaceAllIn(template, m => params(m.group(1)))
      Right(resolved)
    } else {
      Left(s"Unresolved parameters: ${unresolved.mkString(", ")}")
    }
  }
}

// In mapping config
case class SourceConfig(
  entity: String,
  epoch: ParameterizedString  // "${epoch}"
)

// Resolve at runtime
val params = Map("epoch" -> "2024-12-15")
val resolvedEpoch: Either[String, String] = sourceConfig.epoch.resolve(params)
```

---

## Implementation Example

### Complete Config Loader

```scala
package com.cdme.config

import cats.implicits._
import io.circe._
import io.circe.generic.semiauto._
import io.circe.refined._
import io.circe.yaml.parser
import java.io.File
import scala.io.Source

object ConfigLoader {
  def load(configPath: String): Either[ConfigError, CdmeConfig] = {
    for {
      // Load master config
      masterConfig <- loadYamlFile[CdmeConfig](configPath)

      // Load registry
      registry <- loadRegistry(masterConfig.registry)

      // Validate
      _ <- validateConfig(masterConfig, registry)
    } yield masterConfig
  }

  private def loadYamlFile[A: Decoder](path: String): Either[ConfigError, A] = {
    Either.catchNonFatal {
      Source.fromFile(path).mkString
    }.leftMap(e => ConfigError.FileNotFound(path, e.getMessage))
      .flatMap { yamlString =>
        parser.parse(yamlString)
          .leftMap(e => ConfigError.ParseError(path, e.getMessage))
          .flatMap(_.as[A].leftMap(e => ConfigError.DecodingError(path, e.getMessage)))
      }
  }

  private def loadRegistry(config: RegistryConfig): Either[ConfigError, LoadedRegistry] = {
    for {
      entities <- loadEntitiesFromDirectory(config.ldm_path)
      bindings <- loadBindingsFromDirectory(config.pdm_path)
      mappings <- loadMappingsFromDirectory(config.mappings_path)

      _ <- validateEntityReferences(entities)
      _ <- validateBindingReferences(entities, bindings)
      _ <- validateMappingReferences(entities, mappings)
    } yield LoadedRegistry(entities, bindings, mappings)
  }

  private def loadEntitiesFromDirectory(path: String): Either[ConfigError, Map[String, Entity]] = {
    listYamlFiles(path).traverse { file =>
      loadYamlFile[EntityDefinition](file).map { def =>
        def.entity.name -> def.entity
      }
    }.map(_.toMap)
  }

  private def listYamlFiles(path: String): List[String] = {
    val dir = new File(path)
    if (dir.exists && dir.isDirectory) {
      dir.listFiles
        .filter(_.getName.endsWith(".yaml"))
        .map(_.getAbsolutePath)
        .toList
    } else {
      List.empty
    }
  }
}

// Error domain
sealed trait ConfigError
object ConfigError {
  case class FileNotFound(path: String, message: String) extends ConfigError
  case class ParseError(path: String, message: String) extends ConfigError
  case class DecodingError(path: String, message: String) extends ConfigError
  case class ValidationError(message: String) extends ConfigError
  case class UnresolvedReferences(message: String) extends ConfigError
}
```

---

## Error Handling

```scala
// Usage with rich error reporting
ConfigLoader.load("config/cdme_config.yaml") match {
  case Right(config) =>
    println(s"Configuration loaded successfully: ${config.version}")

  case Left(ConfigError.FileNotFound(path, msg)) =>
    println(s"ERROR: Configuration file not found: $path")
    println(s"Details: $msg")
    sys.exit(1)

  case Left(ConfigError.ParseError(path, msg)) =>
    println(s"ERROR: Failed to parse YAML: $path")
    println(s"Details: $msg")
    sys.exit(1)

  case Left(ConfigError.DecodingError(path, msg)) =>
    println(s"ERROR: Configuration validation failed: $path")
    println(s"Details: $msg")
    sys.exit(1)

  case Left(ConfigError.ValidationError(msg)) =>
    println(s"ERROR: Configuration validation failed")
    println(s"Details: $msg")
    sys.exit(1)

  case Left(ConfigError.UnresolvedReferences(msg)) =>
    println(s"ERROR: Unresolved references in configuration")
    println(s"Details: $msg")
    sys.exit(1)
}
```

---

## Consequences

### Positive

- **REQ-CFG-01**: Type-safe parsing with compile-time guarantees
- **REQ-CFG-02**: Parse-time validation catches errors early
- **REQ-CFG-03**: Automatic derivation reduces boilerplate
- **REQ-CFG-04**: Parameterized configs support runtime substitution
- **Functional**: Cats integration, Either-based error handling
- **Composable**: Decoder instances compose naturally
- **Rich errors**: Detailed error messages with file/field context

### Negative

- **Compile time**: Generic derivation increases compilation time
- **Error messages**: Decoder errors can be verbose
- **Library size**: circe adds dependency footprint
- **Learning curve**: Requires understanding Decoder API

### Mitigations

- **Incremental compilation**: sbt mitigates compile time impact
- **Custom decoders**: Improve error messages for critical paths
- **circe is standard**: Widely used in Scala ecosystem
- **Documentation**: Provide decoder cookbook and examples

---

## Alternatives Considered

### Alternative A: pureconfig (Considered)

**Pros**: Convention-based, simpler API, refinement type support
**Cons**: HOCON format, less control over validation, separate ecosystem from circe

**Decision**: circe-yaml chosen for consistency with JSON processing and better Cats integration.

### Alternative B: Typesafe Config (Rejected)

**Pros**: Java standard, mature, widely used
**Cons**: Imperative API, manual type conversion, no automatic derivation

### Alternative C: SnakeYAML (Rejected)

**Pros**: Low-level control
**Cons**: Java-based, imperative, no type safety

---

## References

- [circe Documentation](https://circe.github.io/circe/)
- [circe-yaml](https://github.com/circe/circe-yaml)
- [circe-refined](https://github.com/circe/circe/tree/master/modules/refined)
- [Refined Types](https://github.com/fthomas/refined)
- [YAML Specification](https://yaml.org/spec/1.2/spec.html)
- REQ-CFG-01: Type-Safe Configuration Loading
- REQ-CFG-02: Parse-Time Validation
- REQ-CFG-03: Automatic Schema Derivation
