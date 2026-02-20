// Implements: REQ-F-TYP-003, REQ-F-TYP-004
package cdme.compiler

import cdme.model.types.{CdmeType, SubtypeRegistry, TypeUnifier as ModelTypeUnifier}
import cdme.model.error.ValidationError

/**
 * Compiler-layer type unification.
 *
 * Delegates to the model-layer TypeUnifier and adds compiler-specific
 * validation (e.g., accumulating multiple type errors across a path).
 */
object TypeUnifier:

  /**
   * Validate type compatibility for a sequence of morphism compositions.
   *
   * @param typeChain pairs of (outputType, inputType) for each composition point
   * @param registry  the subtype registry
   * @return list of type errors (empty if all compositions are valid)
   */
  def validateChain(
      typeChain: List[(CdmeType, CdmeType)],
      registry: SubtypeRegistry
  ): List[ValidationError] =
    typeChain.zipWithIndex.flatMap { case ((output, input), idx) =>
      ModelTypeUnifier.unify(output, input, registry) match
        case Left(err) =>
          Some(ValidationError.TypeMismatch(
            s"At composition step $idx: ${err.message}"
          ))
        case Right(_) => None
    }

  /**
   * Unify two types, delegating to the model layer.
   *
   * @param output   output type of preceding morphism
   * @param input    input type of following morphism
   * @param registry subtype registry
   * @return Either[ValidationError, CdmeType]
   */
  def unify(
      output: CdmeType,
      input: CdmeType,
      registry: SubtypeRegistry
  ): Either[ValidationError, CdmeType] =
    ModelTypeUnifier.unify(output, input, registry)
