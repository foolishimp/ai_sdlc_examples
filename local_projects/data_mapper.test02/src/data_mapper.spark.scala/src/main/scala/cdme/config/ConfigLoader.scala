package cdme.config

import cats.implicits._
import cdme.core._
import cdme.config.ConfigModel._
import io.circe._
import io.circe.yaml.parser
import java.io.File
import scala.io.Source
import scala.util.{Try, Success, Failure}

/**
 * Configuration loader.
 * Implements: ADR-010 (YAML Configuration Parsing)
 * Implements: REQ-CFG-01, REQ-CFG-02, REQ-CFG-03
 */
object ConfigLoader {

  /**
   * Load master configuration.
   */
  def load(configPath: String): Either[ConfigError, CdmeConfig] = {
    loadYamlFile[CdmeConfig](configPath)
  }

  /**
   * Load all entities from LDM directory.
   */
  def loadEntities(ldmPath: String): Either[ConfigError, Map[String, Entity]] = {
    listYamlFiles(ldmPath).traverse { file =>
      loadYamlFile[EntityDefinition](file).map { def =>
        val entity = fromEntityConfig(def.entity)
        entity.name -> entity
      }
    }.map(_.toMap)
  }

  /**
   * Load all bindings from PDM directory.
   */
  def loadBindings(pdmPath: String): Either[ConfigError, Map[String, PhysicalBinding]] = {
    listYamlFiles(pdmPath).traverse { file =>
      loadYamlFile[BindingDefinition](file).map { def =>
        val binding = fromBindingConfig(def.binding)
        binding.entity -> binding
      }
    }.map(_.toMap)
  }

  /**
   * Load YAML file with type-safe parsing.
   */
  private def loadYamlFile[A: Decoder](path: String): Either[ConfigError, A] = {
    for {
      content <- Either.catchNonFatal {
        Source.fromFile(path).mkString
      }.leftMap(e => ConfigError.FileNotFound(path, e.getMessage))

      json <- parser.parse(content)
        .leftMap(e => ConfigError.ParseError(path, e.getMessage))

      result <- json.as[A]
        .leftMap(e => ConfigError.DecodingError(path, e.getMessage))
    } yield result
  }

  /**
   * List all YAML files in directory.
   */
  private def listYamlFiles(path: String): List[String] = {
    val dir = new File(path)
    if (dir.exists && dir.isDirectory) {
      dir.listFiles
        .filter(f => f.getName.endsWith(".yaml") || f.getName.endsWith(".yml"))
        .map(_.getAbsolutePath)
        .toList
    } else {
      List.empty
    }
  }

  /**
   * Convert configuration model to domain model.
   */
  private def fromEntityConfig(config: EntityConfig): Entity = {
    val grain = Grain(
      level = GrainLevel.fromString(config.grain.level).getOrElse(GrainLevel.Atomic),
      key = config.grain.key
    )

    val attributes = config.attributes.map { attr =>
      Attribute(
        name = attr.name,
        dataType = attr.`type`,
        nullable = attr.nullable,
        primaryKey = attr.primary_key.getOrElse(false),
        refinement = attr.refinement
      )
    }

    val relationships = config.relationships.getOrElse(List.empty).map { rel =>
      Relationship(
        name = rel.name,
        target = rel.target,
        cardinality = Cardinality.fromString(rel.cardinality).getOrElse(Cardinality.NToOne),
        joinKey = rel.join_key,
        description = rel.description
      )
    }

    Entity(
      name = config.name,
      grain = grain,
      attributes = attributes,
      relationships = relationships,
      description = config.description
    )
  }

  private def fromBindingConfig(config: BindingConfig): PhysicalBinding = {
    PhysicalBinding(
      entity = config.entity,
      storageType = config.physical.`type`,
      location = config.physical.location,
      partitionColumns = config.physical.partition_columns.getOrElse(List.empty)
    )
  }
}

/**
 * Configuration error domain.
 */
sealed trait ConfigError {
  def message: String
}

object ConfigError {
  case class FileNotFound(path: String, details: String) extends ConfigError {
    def message: String = s"Configuration file not found: $path - $details"
  }

  case class ParseError(path: String, details: String) extends ConfigError {
    def message: String = s"Failed to parse YAML: $path - $details"
  }

  case class DecodingError(path: String, details: String) extends ConfigError {
    def message: String = s"Configuration validation failed: $path - $details"
  }

  case class ValidationError(details: String) extends ConfigError {
    def message: String = s"Configuration validation failed: $details"
  }

  case class UnresolvedReferences(details: String) extends ConfigError {
    def message: String = s"Unresolved references: $details"
  }
}
