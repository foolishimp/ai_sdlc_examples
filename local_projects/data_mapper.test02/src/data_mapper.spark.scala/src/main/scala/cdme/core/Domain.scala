package cdme.core

import cats.implicits._
import org.apache.spark.sql.{Dataset, SparkSession}

/**
 * Domain abstractions for CDME.
 * Implements: REQ-LDM-01, REQ-LDM-02, REQ-LDM-06
 */

/**
 * Grain levels for data.
 * Implements: REQ-LDM-06 (Grain Hierarchy)
 */
sealed trait GrainLevel {
  def level: Int
  def name: String
}

object GrainLevel {
  case object Atomic extends GrainLevel {
    val level = 0
    val name = "ATOMIC"
  }

  case object Daily extends GrainLevel {
    val level = 10
    val name = "DAILY"
  }

  case object Monthly extends GrainLevel {
    val level = 20
    val name = "MONTHLY"
  }

  case object Customer extends GrainLevel {
    val level = 15
    val name = "CUSTOMER"
  }

  case object Summary extends GrainLevel {
    val level = 30
    val name = "SUMMARY"
  }

  def fromString(s: String): Option[GrainLevel] = s.toUpperCase match {
    case "ATOMIC" => Some(Atomic)
    case "DAILY" => Some(Daily)
    case "MONTHLY" => Some(Monthly)
    case "CUSTOMER" => Some(Customer)
    case "SUMMARY" => Some(Summary)
    case _ => None
  }
}

/**
 * Grain configuration for an entity.
 */
case class Grain(
  level: GrainLevel,
  key: List[String]  // Grain key fields
)

/**
 * Cardinality for relationships.
 * Implements: REQ-LDM-02
 */
sealed trait Cardinality
object Cardinality {
  case object OneToOne extends Cardinality
  case object NToOne extends Cardinality
  case object OneToN extends Cardinality

  def fromString(s: String): Option[Cardinality] = s match {
    case "1:1" => Some(OneToOne)
    case "N:1" => Some(NToOne)
    case "1:N" => Some(OneToN)
    case _ => None
  }
}

/**
 * Attribute definition.
 */
case class Attribute(
  name: String,
  dataType: String,
  nullable: Boolean,
  primaryKey: Boolean = false,
  refinement: Option[String] = None
)

/**
 * Relationship definition.
 */
case class Relationship(
  name: String,
  target: String,
  cardinality: Cardinality,
  joinKey: String,
  description: Option[String] = None
)

/**
 * Entity definition (LDM).
 * Implements: REQ-LDM-01
 */
case class Entity(
  name: String,
  grain: Grain,
  attributes: List[Attribute],
  relationships: List[Relationship],
  description: Option[String] = None
)

/**
 * Physical binding (PDM).
 * Implements: REQ-PDM-01
 */
case class PhysicalBinding(
  entity: String,
  storageType: String,  // PARQUET, DELTA, etc.
  location: String,
  partitionColumns: List[String] = List.empty
)

/**
 * Morphism trait.
 * Implements: REQ-TRV-01
 */
trait Morphism[A, B] {
  def apply(input: A)(implicit ctx: ExecutionContext): Either[CdmeError, B]
  def morphismPath: String
}

/**
 * Execution context.
 */
case class ExecutionContext(
  runId: String,
  epoch: String,
  spark: SparkSession,
  registry: SchemaRegistry,
  config: CdmeConfig
)

/**
 * Schema registry interface.
 */
trait SchemaRegistry {
  def getEntity(name: String): Either[CdmeError, Entity]
  def getRelationship(entityName: String, relName: String): Either[CdmeError, Relationship]
  def getBinding(entityName: String): Either[CdmeError, PhysicalBinding]
  def validatePath(path: String): Either[CdmeError, PathValidationResult]
}

/**
 * Path validation result.
 */
case class PathValidationResult(
  path: String,
  segments: List[PathSegment],
  finalType: String,
  valid: Boolean
)

sealed trait PathSegment
case class EntitySegment(name: String) extends PathSegment
case class RelationshipSegment(name: String, cardinality: Cardinality) extends PathSegment
case class AttributeSegment(name: String, dataType: String) extends PathSegment
