// Implements: REQ-F-PDM-001, REQ-F-PDM-002, REQ-F-PDM-003, REQ-F-PDM-004, REQ-F-PDM-005
package cdme.pdm

import cdme.model.category.EntityId
import cdme.model.error.ValidationError

/**
 * PDM binding: functorial mapping from an LDM entity to a physical source.
 *
 * The PDM is modelled as a category (physical tables as objects, physical joins
 * as morphisms) and the binding is a functor between categories.
 *
 * Changing PDM configuration (re-pointing an entity to a different physical
 * source) requires no changes to LDM or business logic morphisms.
 *
 * @param entityId        the LDM entity being bound
 * @param physicalSource  the physical storage location
 * @param generationGrain how the source generates data (Event or Snapshot)
 * @param epochBoundary   how the source data is sliced into epochs
 */
final case class PdmBinding(
    entityId: EntityId,
    physicalSource: PhysicalSource,
    generationGrain: GenerationGrain,
    epochBoundary: EpochBoundary
):
  /**
   * Validate this binding.
   *
   * Checks:
   *   - Physical source is not empty
   *   - Generation grain and epoch boundary are present (always true by construction)
   *
   * @return list of validation errors
   */
  def validate(): List[ValidationError] =
    val errors = scala.collection.mutable.ListBuffer[ValidationError]()
    if physicalSource.schema.columnCount == 0 then
      errors += ValidationError.General(
        s"Physical source ${physicalSource.id.show} has no columns"
      )
    errors.toList

/**
 * Container for all PDM bindings in a deployment.
 *
 * @param bindings map of entity ID to PDM binding
 * @param lookupBindings map of lookup entity ID to lookup binding
 * @param temporalBindings optional temporal bindings
 */
final case class PdmConfiguration(
    bindings: Map[EntityId, PdmBinding],
    lookupBindings: Map[EntityId, LookupBinding] = Map.empty,
    temporalBindings: Map[EntityId, TemporalBinding] = Map.empty
):
  /**
   * Validate all bindings.
   *
   * @return list of validation errors
   */
  def validate(): List[ValidationError] =
    val bindingErrors = bindings.values.flatMap(_.validate()).toList
    val temporalErrors = temporalBindings.values.flatMap(_.validate()).toList
    bindingErrors ++ temporalErrors

  /**
   * Get the binding for a specific entity.
   *
   * @param entityId the entity
   * @return the PDM binding, if configured
   */
  def bindingFor(entityId: EntityId): Option[PdmBinding] =
    bindings.get(entityId)
