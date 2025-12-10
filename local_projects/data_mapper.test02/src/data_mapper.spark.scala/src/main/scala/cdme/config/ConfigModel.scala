package cdme.config

import cdme.core._
import io.circe._
import io.circe.generic.semiauto._

/**
 * Configuration models.
 * Implements: ADR-010 (YAML Configuration Parsing)
 */

/**
 * Master CDME configuration.
 */
case class CdmeConfig(
  version: String,
  registry: RegistryConfig,
  execution: ExecutionConfig,
  output: OutputConfig
)

case class RegistryConfig(
  ldm_path: String,
  pdm_path: String,
  types_path: Option[String] = None
)

case class ExecutionConfig(
  mode: String,  // BATCH or STREAMING
  error_threshold: Double,
  lineage_mode: String  // BASIC, FULL, SAMPLED
) {
  // Alias for cleaner test access
  def execution_mode: String = mode
}

case class OutputConfig(
  data_path: String,
  error_path: String,
  lineage_path: String
)

/**
 * LDM entity definition file format.
 */
case class EntityDefinition(
  entity: EntityConfig
)

case class EntityConfig(
  name: String,
  description: Option[String],
  grain: GrainConfig,
  attributes: List[AttributeConfig],
  relationships: Option[List[RelationshipConfig]]
)

case class GrainConfig(
  level: String,
  key: List[String]
)

case class AttributeConfig(
  name: String,
  `type`: String,
  nullable: Boolean,
  primary_key: Option[Boolean],
  refinement: Option[String],
  semantic_type: Option[String],
  allowed_values: Option[List[String]]
)

case class RelationshipConfig(
  name: String,
  target: String,
  cardinality: String,
  join_key: String,
  description: Option[String]
)

/**
 * PDM physical binding file format.
 */
case class BindingDefinition(
  binding: BindingConfig
)

case class BindingConfig(
  entity: String,
  physical: PhysicalConfig,
  boundaries: Option[BoundariesConfig],
  generation_grain: Option[String]
)

case class PhysicalConfig(
  `type`: String,
  location: String,
  partition_columns: Option[List[String]]
)

case class BoundariesConfig(
  epoch: Option[EpochConfig]
)

case class EpochConfig(
  `type`: String,
  field: String
)

/**
 * Mapping definition file format.
 */
case class MappingDefinition(
  mapping: MappingConfig
)

case class MappingConfig(
  name: String,
  description: Option[String],
  source: SourceConfig,
  target: TargetConfig,
  morphisms: Option[List[MorphismConfig]],
  projections: List[ProjectionConfig],
  validations: Option[List[ValidationConfig]]
)

case class SourceConfig(
  entity: String,
  epoch: String  // Can be parameterized "${epoch}"
)

case class TargetConfig(
  entity: String,
  grain: GrainConfig
)

case class MorphismConfig(
  name: String,
  `type`: String,
  predicate: Option[String] = None,
  path: Option[String] = None,
  cardinality: Option[String] = None,
  groupBy: Option[List[String]] = None,
  orderBy: Option[List[String]] = None
) {
  // Alias for cleaner access without backticks
  def morphismType: String = `type`
}

case class ProjectionConfig(
  name: String,
  source: String,
  aggregation: Option[String]
)

case class ValidationConfig(
  field: String,
  validationType: String,  // NOT_NULL, RANGE, PATTERN, etc.
  expression: Option[String] = None,
  errorMessage: String
)

/**
 * Circe decoders.
 */
object ConfigModel {

  implicit val cdmeConfigDecoder: Decoder[CdmeConfig] = deriveDecoder
  implicit val registryConfigDecoder: Decoder[RegistryConfig] = deriveDecoder
  implicit val executionConfigDecoder: Decoder[ExecutionConfig] = deriveDecoder
  implicit val outputConfigDecoder: Decoder[OutputConfig] = deriveDecoder

  implicit val entityDefinitionDecoder: Decoder[EntityDefinition] = deriveDecoder
  implicit val entityConfigDecoder: Decoder[EntityConfig] = deriveDecoder
  implicit val grainConfigDecoder: Decoder[GrainConfig] = deriveDecoder
  implicit val attributeConfigDecoder: Decoder[AttributeConfig] = deriveDecoder
  implicit val relationshipConfigDecoder: Decoder[RelationshipConfig] = deriveDecoder

  implicit val bindingDefinitionDecoder: Decoder[BindingDefinition] = deriveDecoder
  implicit val bindingConfigDecoder: Decoder[BindingConfig] = deriveDecoder
  implicit val physicalConfigDecoder: Decoder[PhysicalConfig] = deriveDecoder
  implicit val boundariesConfigDecoder: Decoder[BoundariesConfig] = deriveDecoder
  implicit val epochConfigDecoder: Decoder[EpochConfig] = deriveDecoder

  implicit val mappingDefinitionDecoder: Decoder[MappingDefinition] = deriveDecoder
  implicit val mappingConfigDecoder: Decoder[MappingConfig] = deriveDecoder
  implicit val sourceConfigDecoder: Decoder[SourceConfig] = deriveDecoder
  implicit val targetConfigDecoder: Decoder[TargetConfig] = deriveDecoder
  implicit val morphismConfigDecoder: Decoder[MorphismConfig] = deriveDecoder
  implicit val projectionConfigDecoder: Decoder[ProjectionConfig] = deriveDecoder
  implicit val validationConfigDecoder: Decoder[ValidationConfig] = deriveDecoder
}
